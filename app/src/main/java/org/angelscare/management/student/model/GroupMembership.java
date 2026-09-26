package org.angelscare.management.student.model;

import org.angelscare.management.calendar.model.TermRange;

public record GroupMembership(String id, String groupId, String studentId, TermRange terms) {
}
