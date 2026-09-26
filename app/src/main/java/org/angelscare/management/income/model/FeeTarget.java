package org.angelscare.management.income.model;

import org.angelscare.management.student.model.SchoolClass;

/** Who a fee is assigned to: one student, everyone in a group, or everyone in a class. */
public sealed interface FeeTarget {

    record OneStudent(String studentId) implements FeeTarget {
    }

    record Group(String groupId) implements FeeTarget {
    }

    record WholeClass(SchoolClass schoolClass) implements FeeTarget {
    }

    static FeeTarget student(String studentId) {
        return new OneStudent(studentId);
    }

    static FeeTarget group(String groupId) {
        return new Group(groupId);
    }

    static FeeTarget schoolClass(SchoolClass schoolClass) {
        return new WholeClass(schoolClass);
    }
}
