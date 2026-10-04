package org.angelscare.management.student.ui;

import java.util.function.Function;
import javafx.util.StringConverter;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.student.model.Boarding;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.StudentStatus;

/** How page 5's drop-downs show their choices. */
final class Choices {

    static final StringConverter<Residency> RESIDENCY =
            shownAs(r -> r == Residency.REFUGEE ? "Refugee" : "National");
    static final StringConverter<Boarding> BOARDING =
            shownAs(b -> b == Boarding.BOARDING ? "Boarding" : "Day");
    static final StringConverter<StudentStatus> STATUS =
            shownAs(s -> s == StudentStatus.LEFT ? "Left" : "Active");
    static final StringConverter<TermRef> TERM = shownAs(TermRef::label);

    private Choices() {
    }

    /** Shows each value as {@code label} gives it; the drop-downs aren't typed into. */
    private static <T> StringConverter<T> shownAs(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                throw new UnsupportedOperationException("choices are picked, not typed");
            }
        };
    }
}
