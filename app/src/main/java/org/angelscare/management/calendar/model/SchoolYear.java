package org.angelscare.management.calendar.model;

import java.util.List;

/** A calendar year of school, always with exactly three terms, in order. */
public record SchoolYear(String id, int year, List<Term> terms) {
}
