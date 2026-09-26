package org.angelscare.management.student.model;

/** A user-made set of students (Boarders, Soccer team…); {@code description} may be null. */
public record StudentGroup(String id, String name, String description) {
}
