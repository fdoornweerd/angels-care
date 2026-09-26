package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.support.FinanceTables;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Spec 001: the columns cloud sync will depend on, checked across every finance table. */
class SyncColumnsTest extends FinanceTest {

    @Test
    @DisplayName("AC-2: every finance table has a TEXT id primary key and the three sync columns")
    void everyTableHasSyncColumns() {
        for (String table : FinanceTables.ALL) {
            List<Map<String, Object>> columns =
                    jdbc.queryForList("SELECT name, type, pk FROM pragma_table_info(?)", table);
            assertThat(columns).as(table).isNotEmpty();
            assertThat(columns).as(table + ".id").anySatisfy(column -> {
                assertThat(column.get("name")).isEqualTo("id");
                assertThat(column.get("type")).isEqualTo("TEXT");
                assertThat(((Number) column.get("pk")).intValue()).isEqualTo(1);
            });
            assertThat(columns).extracting(column -> column.get("name")).as(table)
                    .contains("created_at", "updated_at", "deleted_at");
        }
    }

    @Test
    @DisplayName("AC-2: new rows get a UUID id, created_at = updated_at = now, and no deleted_at")
    void newRowsAreStamped() {
        createOneOfEverything();

        for (String table : FinanceTables.ALL) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, created_at, updated_at, deleted_at FROM " + table);
            assertThat(rows).as(table).isNotEmpty();
            for (Map<String, Object> row : rows) {
                String id = (String) row.get("id");
                assertThat(UUID.fromString(id).toString()).as(table).isEqualTo(id);
                assertThat(row.get("created_at")).as(table).isEqualTo(T0.toString());
                assertThat(row.get("updated_at")).as(table).isEqualTo(T0.toString());
                assertThat(row.get("deleted_at")).as(table).isNull();
            }
        }
    }

    @Test
    @DisplayName("AC-2: an edit moves updated_at to now and leaves created_at alone")
    void editsMoveUpdatedAt() {
        Everything everything = createOneOfEverything();
        clock.advance(Duration.ofHours(3));
        Instant later = clock.instant();

        finance.expenses.renameCategory(everything.expenseCategory().id(), "Admin Costs");
        finance.students.setStatus(everything.student().id(), StudentStatus.LEFT);

        String categoryId = everything.expenseCategory().id();
        assertThat(timestampOf("expense_category", "updated_at", categoryId)).isEqualTo(later);
        assertThat(timestampOf("expense_category", "created_at", categoryId)).isEqualTo(T0);
        String studentId = everything.student().id();
        assertThat(timestampOf("student", "updated_at", studentId)).isEqualTo(later);
        assertThat(timestampOf("student", "created_at", studentId)).isEqualTo(T0);
        // A row that was not edited keeps its original stamp.
        assertThat(timestampOf("expense_item", "updated_at", everything.expenseItem().id()))
                .isEqualTo(T0);
    }
}
