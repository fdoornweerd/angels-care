package org.angelscare.management.student.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.student.model.Boarding;
import org.angelscare.management.student.model.ClassFee;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentDetails;
import org.angelscare.management.student.model.StudentEdit;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.student.model.StudentTermLine;
import org.angelscare.management.student.model.StudentsTotals;
import org.angelscare.management.student.repository.ClassFeeRepository;
import org.angelscare.management.student.repository.StudentRepository;
import org.angelscare.management.student.repository.StudentTermRepository;
import org.angelscare.management.student.repository.StudentTermRepository.Row;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Page 5: who is on a term's register, the class fees (carried forward until changed), each
 * student's amount, debt (carried from their previous term), ream, payments and remarks.
 */
@Service
@Transactional
public class StudentAccountService {

    private static final int MAX_REMARKS_LENGTH = 500;

    private final StudentTermRepository terms;
    private final ClassFeeRepository classFees;
    private final StudentRepository studentRepository;
    private final StudentService students;
    private final CalendarService calendar;

    public StudentAccountService(StudentTermRepository terms, ClassFeeRepository classFees,
            StudentRepository studentRepository, StudentService students,
            CalendarService calendar) {
        this.terms = terms;
        this.classFees = classFees;
        this.studentRepository = studentRepository;
        this.students = students;
        this.calendar = calendar;
    }

    /**
     * Puts on the term every student who belongs to it (joined by then, and not left before it)
     * and was never on it, under their class and Day/Boarding now. A student on no term yet joins
     * this one. Students who joined later or left earlier are never added, so an earlier term
     * keeps only the students who were there.
     */
    public void openTerm(TermRef term) {
        Term stored = calendar.requireTerm(term);
        for (Student student : studentRepository.findAll()) {
            if (student.belongsTo(term) && !terms.everOnTerm(student.id(), stored.id())) {
                terms.insert(student.id(), stored.id(), student.schoolClass(), student.boarding());
                if (student.joinedTerm() == null) {
                    studentRepository.updateJoinedTerm(student.id(), stored.id());
                }
            }
        }
    }

    /** The term's register, by class then name, Left students included. */
    @Transactional(readOnly = true)
    public List<StudentTermLine> lines(TermRef term) {
        Term stored = calendar.requireTerm(term);
        Map<String, Student> byId = studentsById();
        Register register = register(byId);
        return terms.findAll().stream()
                .filter(row -> row.termId().equals(stored.id()))
                .map(register::line)
                .sorted(Comparator.comparing(StudentTermLine::schoolClass)
                        .thenComparing(line -> lastName(byId, line))
                        .thenComparing(line -> firstName(byId, line)))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentTermLine line(String studentId, TermRef term) {
        return register(studentsById()).line(requireRow(studentId, term));
    }

    @Transactional(readOnly = true)
    public Student student(String studentId) {
        return requireStudent(studentId);
    }

    /** The class fees in force in {@code term}: set in it or in the latest earlier term. */
    @Transactional(readOnly = true)
    public ClassFee classFee(SchoolClass schoolClass, TermRef term) {
        calendar.requireTerm(term);
        return new Register(List.of(), classFees.findAll(), Map.of()).classFee(schoolClass, term);
    }

    /**
     * Sets the class's Day fee, Boarding fee and ream from {@code term} on, until changed in a
     * later term. A fee left null is 0.
     */
    public void setClassFee(SchoolClass schoolClass, TermRef term, Ugx dayFee, Ugx boardingFee,
            Ugx ream) {
        requireNotNegative(dayFee, "A class fee");
        requireNotNegative(boardingFee, "A boarding fee");
        requireNotNegative(ream, "A ream charge");
        Term stored = calendar.requireTerm(term);
        long day = shillings(dayFee);
        long boarding = shillings(boardingFee);
        long reamShillings = shillings(ream);
        classFees.find(schoolClass, stored.id()).ifPresentOrElse(
                existing -> classFees.update(existing.id(), day, boarding, reamShillings),
                () -> classFees.insert(schoolClass, stored.id(), day, boarding, reamShillings));
    }

    /**
     * Day or Boarding on {@code term} and on the later terms the student is already on; earlier
     * terms keep theirs. It also becomes the student's own value, for the terms they join next.
     */
    public StudentTermLine setBoarding(String studentId, TermRef term, Boarding boarding) {
        if (boarding == null) {
            throw new ValidationException("Choose Day or Boarding.");
        }
        requireRow(studentId, term);
        for (Row row : terms.findByStudent(studentId)) {
            if (row.term().compareTo(term) >= 0 && row.boarding() != boarding) {
                terms.update(row.id(), "boarding", boarding.name());
            }
        }
        if (requireStudent(studentId).boarding() != boarding) {
            studentRepository.updateBoarding(studentId, boarding);
        }
        return line(studentId, term);
    }

    /** The student's own amount for the term; null goes back to the class fee. */
    public StudentTermLine setAmount(String studentId, TermRef term, Ugx amount) {
        requireNotNegative(amount, "An amount");
        return set(studentId, term, "amount", amount == null ? null : amount.shillings());
    }

    /** The student's debt for the term (may be negative: credit); null goes back to carried. */
    public StudentTermLine setDebt(String studentId, TermRef term, Ugx debt) {
        return set(studentId, term, "debt", debt == null ? null : debt.shillings());
    }

    public StudentTermLine setReam(String studentId, TermRef term, Ugx ream) {
        requireNotNegative(ream, "A ream charge");
        return set(studentId, term, "ream", ream == null ? null : ream.shillings());
    }

    /** What was paid in {@code month} (1-3); null clears it. */
    public StudentTermLine setPayment(String studentId, TermRef term, int month, Ugx amount) {
        if (month < 1 || month > 3) {
            throw new ValidationException("A term has months 1 to 3.");
        }
        requireNotNegative(amount, "A payment");
        return set(studentId, term, "paid_" + month, amount == null ? null : amount.shillings());
    }

    public StudentTermLine setRemarks(String studentId, TermRef term, String remarks) {
        String clean = remarks == null || remarks.isBlank() ? null : remarks.strip();
        if (clean != null && clean.length() > MAX_REMARKS_LENGTH) {
            throw new ValidationException("Remarks can be at most " + MAX_REMARKS_LENGTH
                    + " characters; these have " + clean.length() + ".");
        }
        return set(studentId, term, "remarks", clean);
    }

    /** As the method below, for a Day student. */
    public Student addStudent(TermRef term, String firstName, String lastName,
            SchoolClass schoolClass, Residency residency) {
        return addStudent(term, firstName, lastName, schoolClass, residency, null);
    }

    /**
     * Creates an Active student in {@code schoolClass} who joined in {@code term}, and puts them
     * on it. National and Day unless chosen otherwise.
     */
    public Student addStudent(TermRef term, String firstName, String lastName,
            SchoolClass schoolClass, Residency residency, Boarding boarding) {
        Term stored = calendar.requireTerm(term);
        Boarding chosen = boarding == null ? Boarding.DAY : boarding;
        Student created = students.create(StudentDetails.of(firstName, lastName, schoolClass,
                residency == null ? Residency.NATIONAL : residency));
        if (chosen != created.boarding()) {
            studentRepository.updateBoarding(created.id(), chosen);
        }
        studentRepository.updateJoinedTerm(created.id(), stored.id());
        terms.insert(created.id(), stored.id(), created.schoolClass(), chosen);
        return requireStudent(created.id());
    }

    /**
     * Edit student, saved while viewing {@code term}. Everything is checked before anything is
     * written, so a refused save changes nothing.
     * <ul>
     * <li>Names and National/Refugee belong to the student, so every term shows them.</li>
     * <li>A later joined term takes them off the terms before it; refused if they paid in one.
     *     An earlier one lets those terms add them when opened.</li>
     * <li>Left makes {@code term} their last term (one they already had stays): they come off
     *     later terms they haven't paid for, and are never added to later terms.</li>
     * <li>Active again clears the last term and puts them back on {@code term}.</li>
     * </ul>
     */
    public Student editStudent(String studentId, TermRef term, StudentEdit edit) {
        Student current = requireStudent(studentId);
        Term viewed = calendar.requireTerm(term);
        if (edit.joinedTerm() == null) {
            throw new ValidationException("Choose the term " + current.fullName() + " joined.");
        }
        if (edit.status() == null) {
            throw new ValidationException("Choose Active or Left for " + current.fullName() + ".");
        }
        Term joined = calendar.requireTerm(edit.joinedTerm());
        boolean left = edit.status() == StudentStatus.LEFT;
        TermRef lastTerm = !left ? null : current.lastTerm() != null ? current.lastTerm() : term;
        if (lastTerm != null && joined.ref().compareTo(lastTerm) > 0) {
            throw new ValidationException(current.fullName() + " left after " + lastTerm.label()
                    + ", so they can't have joined in " + joined.ref().label() + ".");
        }
        List<Row> lines = terms.findByStudent(studentId);
        List<Row> beforeJoining = lines.stream()
                .filter(row -> row.term().compareTo(joined.ref()) < 0).toList();
        beforeJoining.stream().filter(Row::hasPayments)
                .min(Comparator.comparing(Row::term))
                .ifPresent(paid -> {
                    throw new ValidationException(current.fullName() + " has payments in "
                            + paid.term().label() + ", so they joined in that term or before.");
                });
        // Checks the names and National/Refugee before it writes anything.
        students.update(studentId, new StudentDetails(edit.firstName(), edit.lastName(),
                current.admissionNo(), current.schoolClass(), edit.residency()));

        if (!joined.ref().equals(current.joinedTerm())) {
            studentRepository.updateJoinedTerm(studentId, joined.id());
        }
        beforeJoining.forEach(row -> terms.softDelete(row.id()));
        if (left) {
            if (current.status() != StudentStatus.LEFT || current.lastTerm() == null) {
                studentRepository.updateStatus(studentId, StudentStatus.LEFT,
                        calendar.requireTerm(lastTerm).id());
            }
            lines.stream()
                    .filter(row -> row.term().compareTo(lastTerm) > 0 && !row.hasPayments())
                    .forEach(row -> terms.softDelete(row.id()));
        } else if (current.status() == StudentStatus.LEFT) {
            studentRepository.updateStatus(studentId, StudentStatus.ACTIVE, null);
            if (joined.ref().compareTo(term) <= 0 && terms.find(studentId, viewed.id()).isEmpty()) {
                Student back = requireStudent(studentId);
                terms.insert(studentId, viewed.id(), back.schoolClass(), back.boarding());
            }
        }
        return requireStudent(studentId);
    }

    /** Takes the student off the term (a mistake); refused when they have payments this term. */
    public void removeFromTerm(String studentId, TermRef term) {
        Row row = requireRow(studentId, term);
        StudentTermLine line = register(studentsById()).line(row);
        if (line.payments().total().shillings() > 0) {
            throw new ValidationException(line.name() + " has payments this term, so they can't"
                    + " be removed from it.");
        }
        terms.softDelete(row.id());
    }

    /** The Students row of pages 2 and 3. */
    @Transactional(readOnly = true)
    public StudentsTotals totals(TermRef term) {
        Ugx expected = Ugx.ZERO;
        MonthlyAmounts paid = MonthlyAmounts.ZERO;
        for (StudentTermLine line : lines(term)) {
            expected = expected.plus(line.total());
            paid = paid.plus(line.payments());
        }
        return new StudentsTotals(expected, paid);
    }

    private StudentTermLine set(String studentId, TermRef term, String column, Object value) {
        terms.update(requireRow(studentId, term).id(), column, value);
        return line(studentId, term);
    }

    private Row requireRow(String studentId, TermRef term) {
        Term stored = calendar.requireTerm(term);
        return terms.find(studentId, stored.id()).orElseThrow(() -> new ValidationException(
                studentRepository.findById(studentId).map(Student::fullName).orElse("That student")
                        + " is not on " + term.label() + "."));
    }

    private Student requireStudent(String studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ValidationException("That student no longer exists."));
    }

    private Register register(Map<String, Student> byId) {
        return new Register(terms.findAll(), classFees.findAll(), byId);
    }

    private Map<String, Student> studentsById() {
        return studentRepository.findAll().stream()
                .collect(Collectors.toMap(Student::id, Function.identity()));
    }

    private static String lastName(Map<String, Student> byId, StudentTermLine line) {
        Student student = byId.get(line.studentId());
        return student == null ? "" : student.lastName().toLowerCase(Locale.ROOT);
    }

    private static String firstName(Map<String, Student> byId, StudentTermLine line) {
        Student student = byId.get(line.studentId());
        return student == null ? "" : student.firstName().toLowerCase(Locale.ROOT);
    }

    private static long shillings(Ugx amount) {
        return amount == null ? 0 : amount.shillings();
    }

    private static void requireNotNegative(Ugx amount, String what) {
        if (amount != null && amount.isNegative()) {
            throw new ValidationException(what + " can't be less than UGX 0.");
        }
    }
}
