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
import org.angelscare.management.student.model.ClassFee;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentDetails;
import org.angelscare.management.student.model.StudentFilter;
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

    /** Puts every Active student who has never been on the term on it, under their class now. */
    public void openTerm(TermRef term) {
        Term stored = calendar.requireTerm(term);
        for (Student student : studentRepository.find(StudentFilter.ALL.withStatus(StudentStatus.ACTIVE))) {
            if (!terms.everOnTerm(student.id(), stored.id())) {
                terms.insert(student.id(), stored.id(), student.schoolClass());
            }
        }
    }

    /** The term's register, by class then name; Left students only when asked for. */
    @Transactional(readOnly = true)
    public List<StudentTermLine> lines(TermRef term, boolean includeLeft) {
        Term stored = calendar.requireTerm(term);
        Map<String, Student> byId = studentsById();
        Register register = register(byId);
        return terms.findAll().stream()
                .filter(row -> row.termId().equals(stored.id()))
                .map(register::line)
                .filter(line -> includeLeft || line.status() != StudentStatus.LEFT)
                .sorted(Comparator.comparing(StudentTermLine::schoolClass)
                        .thenComparing(line -> lastName(byId, line))
                        .thenComparing(line -> firstName(byId, line)))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentTermLine line(String studentId, TermRef term) {
        return register(studentsById()).line(requireRow(studentId, term));
    }

    /** The class fee in force in {@code term}: the one set in it or the latest earlier term. */
    @Transactional(readOnly = true)
    public ClassFee classFee(SchoolClass schoolClass, TermRef term) {
        calendar.requireTerm(term);
        return new Register(List.of(), classFees.findAll(), Map.of()).classFee(schoolClass, term);
    }

    /** Sets the class fee from {@code term} on (until changed in a later term). */
    public void setClassFee(SchoolClass schoolClass, TermRef term, Ugx amount, Ugx ream) {
        requireNotNegative(amount, "A class fee");
        requireNotNegative(ream, "A ream charge");
        Term stored = calendar.requireTerm(term);
        long amountShillings = amount == null ? 0 : amount.shillings();
        long reamShillings = ream == null ? 0 : ream.shillings();
        classFees.find(schoolClass, stored.id()).ifPresentOrElse(
                existing -> classFees.update(existing.id(), amountShillings, reamShillings),
                () -> classFees.insert(schoolClass, stored.id(), amountShillings, reamShillings));
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

    /** Creates an Active student in {@code schoolClass} and puts them on the term. */
    public Student addStudent(TermRef term, String firstName, String lastName,
            SchoolClass schoolClass, Residency residency) {
        Term stored = calendar.requireTerm(term);
        Student student = students.create(StudentDetails.of(firstName, lastName, schoolClass,
                residency == null ? Residency.NATIONAL : residency));
        terms.insert(student.id(), stored.id(), student.schoolClass());
        return student;
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
        for (StudentTermLine line : lines(term, true)) {
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

    private static void requireNotNegative(Ugx amount, String what) {
        if (amount != null && amount.isNegative()) {
            throw new ValidationException(what + " can't be less than UGX 0.");
        }
    }
}
