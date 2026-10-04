package org.angelscare.management.student.service;

import java.util.List;
import java.util.Optional;
import org.angelscare.management.common.DeletionGuard;
import org.angelscare.management.common.Names;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentDetails;
import org.angelscare.management.student.model.StudentFilter;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.student.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StudentService {

    private final StudentRepository students;
    private final DeletionGuard deletionGuard;

    public StudentService(StudentRepository students, DeletionGuard deletionGuard) {
        this.students = students;
        this.deletionGuard = deletionGuard;
    }

    /**
     * New students are {@link StudentStatus#ACTIVE} and Day, on no term yet. Marking them Left
     * needs a term, so it is done by {@link StudentAccountService#editStudent}.
     */
    public Student create(StudentDetails details) {
        StudentDetails clean = validate(details, null);
        return students.insert(clean, StudentStatus.ACTIVE);
    }

    public Student update(String studentId, StudentDetails details) {
        require(studentId);
        StudentDetails clean = validate(details, studentId);
        students.update(studentId, clean);
        return require(studentId);
    }

    @Transactional(readOnly = true)
    public Optional<Student> find(String studentId) {
        return students.findById(studentId);
    }

    /** Sorted by last name, then first name. */
    @Transactional(readOnly = true)
    public List<Student> list(StudentFilter filter) {
        return students.find(filter == null ? StudentFilter.ALL : filter);
    }

    public void delete(String studentId) {
        Student student = require(studentId);
        deletionGuard.requireUnused("student", studentId, student.fullName());
        students.softDelete(studentId);
    }

    private Student require(String studentId) {
        return students.findById(studentId)
                .orElseThrow(() -> new ValidationException("That student no longer exists."));
    }

    private StudentDetails validate(StudentDetails details, String studentId) {
        String firstName = Names.require(details.firstName(), "First name");
        String lastName = Names.require(details.lastName(), "Last name");
        String admissionNo = Names.optional(details.admissionNo(), "Admission number");
        if (details.schoolClass() == null) {
            throw new ValidationException("Choose a class for the student.");
        }
        if (details.residency() == null) {
            throw new ValidationException("Choose National or Refugee for the student.");
        }
        if (admissionNo != null) {
            students.findByAdmissionNo(admissionNo, studentId).ifPresent(other -> {
                throw new ValidationException("Admission number " + other.admissionNo()
                        + " is already used by " + other.fullName() + ".");
            });
        }
        return new StudentDetails(firstName, lastName, admissionNo, details.schoolClass(),
                details.residency());
    }
}
