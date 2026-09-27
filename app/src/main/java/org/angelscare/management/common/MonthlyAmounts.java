package org.angelscare.management.common;

/** Money for each of a term's three months. */
public record MonthlyAmounts(Ugx first, Ugx second, Ugx third) {

    public static final MonthlyAmounts ZERO = new MonthlyAmounts(Ugx.ZERO, Ugx.ZERO, Ugx.ZERO);

    /** The amount for month 1, 2 or 3. */
    public Ugx month(int month) {
        return switch (month) {
            case 1 -> first;
            case 2 -> second;
            case 3 -> third;
            default -> throw new IllegalArgumentException("A term has months 1 to 3, not " + month);
        };
    }

    /** A copy with {@code month} (1-3) set to {@code amount}. */
    public MonthlyAmounts with(int month, Ugx amount) {
        return switch (month) {
            case 1 -> new MonthlyAmounts(amount, second, third);
            case 2 -> new MonthlyAmounts(first, amount, third);
            case 3 -> new MonthlyAmounts(first, second, amount);
            default -> throw new IllegalArgumentException("A term has months 1 to 3, not " + month);
        };
    }

    public MonthlyAmounts plus(MonthlyAmounts other) {
        return new MonthlyAmounts(first.plus(other.first), second.plus(other.second),
                third.plus(other.third));
    }

    public Ugx total() {
        return first.plus(second).plus(third);
    }
}
