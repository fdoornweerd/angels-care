package org.angelscare.management.student;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.angelscare.management.student.model.Level;
import org.angelscare.management.student.model.SchoolClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SchoolClassTest {

    @Test
    @DisplayName("AC-10: Baby, Middle and Top are Nursery; P1-P7 are Primary")
    void eachClassHasItsLevel() {
        assertThat(Arrays.stream(SchoolClass.values()).filter(c -> c.level() == Level.NURSERY))
                .containsExactly(SchoolClass.BABY, SchoolClass.MIDDLE, SchoolClass.TOP);
        assertThat(Arrays.stream(SchoolClass.values()).filter(c -> c.level() == Level.PRIMARY))
                .containsExactly(SchoolClass.P1, SchoolClass.P2, SchoolClass.P3, SchoolClass.P4,
                        SchoolClass.P5, SchoolClass.P6, SchoolClass.P7);
    }
}
