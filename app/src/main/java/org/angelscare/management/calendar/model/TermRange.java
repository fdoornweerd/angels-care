package org.angelscare.management.calendar.model;

import java.util.Optional;
import org.angelscare.management.common.ValidationException;

/** The terms from {@code start} to {@code end} inclusive; no end means "from start onwards". */
public record TermRange(TermRef start, TermRef end) {

    /** From {@code start} onwards, with no end. */
    public static TermRange from(TermRef start) {
        return new TermRange(start, null);
    }

    /** From {@code start} to {@code end} inclusive. Rejects an end before the start. */
    public static TermRange between(TermRef start, TermRef end) {
        if (end.compareTo(start) < 0) {
            throw new ValidationException("The last term (" + end.label()
                    + ") can't be before the first term (" + start.label() + ").");
        }
        return new TermRange(start, end);
    }

    /** For messages: "from 2026-2027 Term 1 to 2026-2027 Term 3", or "… onwards". */
    public String label() {
        return end == null
                ? "from " + start.label() + " onwards"
                : "from " + start.label() + " to " + end.label();
    }

    public Optional<TermRef> endTerm() {
        return Optional.ofNullable(end);
    }

    public boolean contains(TermRef term) {
        return term.compareTo(start) >= 0 && (end == null || term.compareTo(end) <= 0);
    }

    public boolean overlaps(TermRange other) {
        return intersection(other).isPresent();
    }

    /** The terms in both ranges, if there are any. */
    public Optional<TermRange> intersection(TermRange other) {
        TermRef laterStart = start.compareTo(other.start) >= 0 ? start : other.start;
        TermRef earlierEnd;
        if (end == null) {
            earlierEnd = other.end;
        } else if (other.end == null) {
            earlierEnd = end;
        } else {
            earlierEnd = end.compareTo(other.end) <= 0 ? end : other.end;
        }
        if (earlierEnd != null && earlierEnd.compareTo(laterStart) < 0) {
            return Optional.empty();
        }
        return Optional.of(new TermRange(laterStart, earlierEnd));
    }
}
