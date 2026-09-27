package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import java.util.stream.Stream;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.expense.model.ExpenseCategory;
import org.angelscare.management.expense.model.ExpenseItem;
import org.angelscare.management.income.model.IncomeCategory;
import org.angelscare.management.income.model.IncomeItem;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentDetails;
import org.angelscare.management.support.Finance;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Spec 001 AC-3 to AC-5 (and 002 AC-2): the same name rules for every kind of thing the user
 * names. Categories now belong to a school year, so everything here is in 2026-2027.
 */
class NamingRulesTest extends FinanceTest {

    interface Namer {
        Created create(String name);

        void delete(String id);
    }

    record Created(String id, String name) {
    }

    record Kind(String label, boolean uniqueName, Function<Finance, Namer> namer) {
        @Override
        public String toString() {
            return label;
        }
    }

    @BeforeEach
    void schoolYear() {
        createYear(2026);
    }

    static Stream<Kind> allKinds() {
        return Stream.of(
                new Kind("income category", true, f -> new Namer() {
                    public Created create(String name) {
                        IncomeCategory c = f.income.createCategory(2026, name);
                        return new Created(c.id(), c.name());
                    }

                    public void delete(String id) {
                        f.income.deleteCategory(id);
                    }
                }),
                new Kind("income item", true, f -> {
                    String parentId = f.income.createCategory(2026, "Parent").id();
                    return new Namer() {
                        public Created create(String name) {
                            IncomeItem i = f.income.createItem(parentId, name, "months");
                            return new Created(i.id(), i.name());
                        }

                        public void delete(String id) {
                            f.income.deleteItem(id);
                        }
                    };
                }),
                new Kind("income item unit", false, f -> {
                    String parentId = f.income.createCategory(2026, "Parent").id();
                    int[] n = {0};
                    return new Namer() {
                        public Created create(String unit) {
                            IncomeItem i = f.income.createItem(parentId, "Item " + n[0]++, unit);
                            return new Created(i.id(), i.unit());
                        }

                        public void delete(String id) {
                            f.income.deleteItem(id);
                        }
                    };
                }),
                new Kind("expense category", true, f -> new Namer() {
                    public Created create(String name) {
                        ExpenseCategory c = f.expenses.createCategory(2026, name);
                        return new Created(c.id(), c.name());
                    }

                    public void delete(String id) {
                        f.expenses.deleteCategory(id);
                    }
                }),
                new Kind("expense item", true, f -> {
                    String parentId = f.expenses.createCategory(2026, "Parent").id();
                    return new Namer() {
                        public Created create(String name) {
                            ExpenseItem i = f.expenses.createItem(parentId, name, "kg");
                            return new Created(i.id(), i.name());
                        }

                        public void delete(String id) {
                            f.expenses.deleteItem(id);
                        }
                    };
                }),
                new Kind("expense item unit", false, f -> {
                    String parentId = f.expenses.createCategory(2026, "Parent").id();
                    int[] n = {0};
                    return new Namer() {
                        public Created create(String unit) {
                            ExpenseItem i = f.expenses.createItem(parentId, "Item " + n[0]++, unit);
                            return new Created(i.id(), i.unit());
                        }

                        public void delete(String id) {
                            f.expenses.deleteItem(id);
                        }
                    };
                }),
                new Kind("student first name", false, f -> new Namer() {
                    public Created create(String name) {
                        Student s = f.students.create(
                                StudentDetails.of(name, "Okello", SchoolClass.P1, Residency.NATIONAL));
                        return new Created(s.id(), s.firstName());
                    }

                    public void delete(String id) {
                        f.students.delete(id);
                    }
                }),
                new Kind("student last name", false, f -> new Namer() {
                    public Created create(String name) {
                        Student s = f.students.create(
                                StudentDetails.of("Amina", name, SchoolClass.P1, Residency.NATIONAL));
                        return new Created(s.id(), s.lastName());
                    }

                    public void delete(String id) {
                        f.students.delete(id);
                    }
                }));
    }

    static Stream<Kind> uniquelyNamedKinds() {
        return allKinds().filter(Kind::uniqueName);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allKinds")
    @DisplayName("AC-3 (001) / AC-2 (002): missing, blank and over-long names and units are rejected")
    void rejectsBadNames(Kind kind) {
        Namer namer = kind.namer().apply(finance);

        assertThatThrownBy(() -> namer.create(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> namer.create("")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> namer.create("   ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> namer.create("x".repeat(101)))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allKinds")
    @DisplayName("AC-3 (001) / AC-2 (002): 100 characters are accepted, and spaces are trimmed")
    void acceptsAndTrimsNames(Kind kind) {
        Namer namer = kind.namer().apply(finance);

        assertThat(namer.create("x".repeat(100)).name()).hasSize(100);
        assertThat(namer.create("  Feeding  ").name()).isEqualTo("Feeding");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("uniquelyNamedKinds")
    @DisplayName("AC-4 (001): a duplicate name is rejected regardless of case and spaces")
    void rejectsDuplicates(Kind kind) {
        Namer namer = kind.namer().apply(finance);
        namer.create("Feeding");

        assertThatThrownBy(() -> namer.create(" feeding "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Feeding")
                .hasMessageContaining("already exists");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("uniquelyNamedKinds")
    @DisplayName("AC-4 (001): the name of a deleted row can be used again")
    void reusesDeletedNames(Kind kind) {
        Namer namer = kind.namer().apply(finance);
        Created first = namer.create("Feeding");

        namer.delete(first.id());

        assertThat(namer.create("Feeding").id()).isNotEqualTo(first.id());
    }

    @Test
    @DisplayName("AC-4 (001): renaming to another row's name is rejected; changing its case is not")
    void renameRules() {
        ExpenseCategory feeding = finance.expenses.createCategory(2026, "Feeding");
        ExpenseCategory admin = finance.expenses.createCategory(2026, "Administrative Costs");

        assertThatThrownBy(() -> finance.expenses.renameCategory(admin.id(), "FEEDING"))
                .isInstanceOf(ValidationException.class);
        assertThat(finance.expenses.renameCategory(feeding.id(), "FEEDING").name())
                .isEqualTo("FEEDING");
    }

    @Test
    @DisplayName("AC-5 (001): item names are unique per category, not across categories")
    void itemNamesArePerCategory() {
        ExpenseCategory a = finance.expenses.createCategory(2026, "Administrative Costs");
        ExpenseCategory b = finance.expenses.createCategory(2026, "Staff Welfare");
        finance.expenses.createItem(a.id(), "Airtime", "bundles");

        assertThat(finance.expenses.createItem(b.id(), "Airtime", "bundles").name())
                .isEqualTo("Airtime");
        assertThatThrownBy(() -> finance.expenses.createItem(a.id(), "airtime", "bundles"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("AC-5 (001): an income category and an expense category may share a name")
    void categoryNamesArePerKind() {
        finance.income.createCategory(2026, "Transport");

        assertThat(finance.expenses.createCategory(2026, "Transport").name()).isEqualTo("Transport");
    }
}
