package org.angelscare.management.student.model;

/** The classes of the school, youngest first. Each belongs to exactly one {@link Level}. */
public enum SchoolClass {
    BABY(Level.NURSERY, "Baby"),
    MIDDLE(Level.NURSERY, "Middle"),
    TOP(Level.NURSERY, "Top"),
    P1(Level.PRIMARY, "P1"),
    P2(Level.PRIMARY, "P2"),
    P3(Level.PRIMARY, "P3"),
    P4(Level.PRIMARY, "P4"),
    P5(Level.PRIMARY, "P5"),
    P6(Level.PRIMARY, "P6"),
    P7(Level.PRIMARY, "P7");

    private final Level level;
    private final String label;

    SchoolClass(Level level, String label) {
        this.level = level;
        this.label = label;
    }

    public Level level() {
        return level;
    }

    /** How the school writes it: "Baby", "P7". */
    public String label() {
        return label;
    }
}
