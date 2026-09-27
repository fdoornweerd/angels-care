package org.angelscare.management.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TermRangeTest {

    private static TermRef t(int year, int number) {
        return TermRef.of(year, number);
    }

    @Test
    @DisplayName("AC-9: terms are ordered by year, then term number")
    void ordersTermsAcrossYears() {
        List<TermRef> terms = new ArrayList<>(List.of(t(2027, 1), t(2026, 3), t(2026, 1), t(2026, 2)));

        terms.sort(null);

        assertThat(terms).containsExactly(t(2026, 1), t(2026, 2), t(2026, 3), t(2027, 1));
        assertThat(t(2026, 3)).isLessThan(t(2027, 1));
        assertThat(t(2026, 2).compareTo(t(2026, 2))).isZero();
    }

    @Test
    @DisplayName("AC-9: a range with no end contains its start and every later term")
    void openRangeContainsLaterTerms() {
        TermRange range = TermRange.from(t(2026, 2));

        assertThat(range.contains(t(2026, 1))).isFalse();
        assertThat(range.contains(t(2026, 2))).isTrue();
        assertThat(range.contains(t(2027, 1))).isTrue();
        assertThat(range.contains(t(2040, 3))).isTrue();
    }

    @Test
    @DisplayName("AC-9: a closed range contains both ends and nothing outside them")
    void closedRangeIsInclusive() {
        TermRange range = TermRange.between(t(2026, 3), t(2027, 1));

        assertThat(range.contains(t(2026, 2))).isFalse();
        assertThat(range.contains(t(2026, 3))).isTrue();
        assertThat(range.contains(t(2027, 1))).isTrue();
        assertThat(range.contains(t(2027, 2))).isFalse();
    }

    @Test
    @DisplayName("AC-9: a range may be a single term, but may not end before it starts")
    void rangeBounds() {
        assertThat(TermRange.between(t(2026, 2), t(2026, 2)).contains(t(2026, 2))).isTrue();

        assertThatThrownBy(() -> TermRange.between(t(2026, 2), t(2026, 1)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("AC-9: ranges overlap when they share at least one term")
    void overlaps() {
        TermRange t1ToT2 = TermRange.between(t(2026, 1), t(2026, 2));

        assertThat(t1ToT2.overlaps(TermRange.between(t(2026, 2), t(2026, 3)))).isTrue();
        assertThat(t1ToT2.overlaps(TermRange.between(t(2026, 3), t(2026, 3)))).isFalse();
        assertThat(t1ToT2.overlaps(TermRange.from(t(2025, 1)))).isTrue();
        assertThat(t1ToT2.overlaps(TermRange.from(t(2026, 3)))).isFalse();
        assertThat(TermRange.from(t(2026, 1)).overlaps(TermRange.from(t(2030, 1)))).isTrue();
        assertThat(TermRange.between(t(2026, 3), t(2027, 1))
                .overlaps(TermRange.between(t(2025, 1), t(2026, 2)))).isFalse();
    }

    @Test
    @DisplayName("AC-9 (changed later): terms are labelled with their school year's name")
    void labels() {
        assertThat(t(2026, 2).label()).isEqualTo("2026-2027 Term 2");
        assertThat(TermRange.from(t(2026, 1)).label()).isEqualTo("from 2026-2027 Term 1 onwards");
        assertThat(TermRange.between(t(2026, 3), t(2027, 1)).label())
                .isEqualTo("from 2026-2027 Term 3 to 2027-2028 Term 1");
    }
}
