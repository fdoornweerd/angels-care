package org.angelscare.management.student.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.student.model.ClassFee;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.student.model.StudentTermLine;
import org.angelscare.management.student.repository.ClassFeeRepository;
import org.angelscare.management.student.repository.StudentTermRepository.Row;

/**
 * The page-5 arithmetic, done in memory from one read of the lines and class fees: which class
 * fee is in force in a term, and each student's debt carried from their previous term. Pure: no
 * database.
 */
final class Register {

    private final List<Row> rows;
    private final List<ClassFeeRepository.Row> fees;
    private final Map<String, Student> students;

    Register(List<Row> rows, List<ClassFeeRepository.Row> fees, Map<String, Student> students) {
        this.rows = rows;
        this.fees = fees;
        this.students = students;
    }

    /** The fee set in {@code term}, or in the latest earlier term; 0 if none was ever set. */
    ClassFee classFee(SchoolClass schoolClass, TermRef term) {
        return fees.stream()
                .filter(fee -> fee.schoolClass() == schoolClass && fee.term().compareTo(term) <= 0)
                .max(Comparator.comparing(ClassFeeRepository.Row::term))
                .map(fee -> new ClassFee(schoolClass, Ugx.of(fee.amount()),
                        Ugx.of(fee.boardingAmount()), Ugx.of(fee.ream())))
                .orElse(new ClassFee(schoolClass, Ugx.ZERO, Ugx.ZERO, Ugx.ZERO));
    }

    StudentTermLine line(Row row) {
        Student student = students.get(row.studentId());
        ClassFee fee = classFee(row.schoolClass(), row.term());
        return new StudentTermLine(
                row.studentId(),
                student == null ? "(deleted student)" : student.fullName(),
                student == null ? null : student.status(),
                hadLeft(student, row.term()),
                row.schoolClass(),
                row.boarding(),
                row.amount() == null ? fee.feeFor(row.boarding()) : Ugx.of(row.amount()),
                row.amount() != null,
                row.debt() == null ? carriedDebt(row) : Ugx.of(row.debt()),
                row.debt() != null,
                row.ream() == null ? fee.ream() : Ugx.of(row.ream()),
                row.ream() != null,
                ugx(row.paid1()), ugx(row.paid2()), ugx(row.paid3()),
                row.remarks());
    }

    /** On their last term, or a later one they are still on (because they paid for it). */
    private static boolean hadLeft(Student student, TermRef term) {
        return student != null && student.status() == StudentStatus.LEFT
                && (student.lastTerm() == null || term.compareTo(student.lastTerm()) >= 0);
    }

    /** The balance of the student's latest earlier line, or 0 if this is their first term. */
    private Ugx carriedDebt(Row row) {
        Optional<Row> previous = rows.stream()
                .filter(other -> other.studentId().equals(row.studentId()))
                .filter(other -> other.term().compareTo(row.term()) < 0)
                .max(Comparator.comparing(Row::term));
        return previous.map(p -> line(p).balance()).orElse(Ugx.ZERO);
    }

    private static Ugx ugx(Long shillings) {
        return shillings == null ? null : Ugx.of(shillings);
    }
}
