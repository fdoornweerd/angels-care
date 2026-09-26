package org.angelscare.management.calendar.model;

/** Every term has three months. In spec 001 they are labels only, with no dates of their own. */
public enum TermMonth {
    MONTH_1, MONTH_2, MONTH_3;

    public static final int PER_TERM = values().length;
}
