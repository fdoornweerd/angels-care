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
import org.angelscare.management.student.model.StudentGroup;
import org.angelscare.management.support.Finance;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Spec 001, AC-3 to AC-5: the same name rules for every kind of thing the user names. */
class NamingRulesTest extends FinanceTest {

    /** Creates and deletes one kind of named thing; items share one parent category. */
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

    static Stream<Kind> allKinds() {
        return Stream.of(
                new Kind("income category", true, f -> new Namer() {
                    public Created create(String name) {
                        IncomeCategory c = f.income.createCategory(name);
                        return new Created(c.id(), c.name());
                    }

                    public void delete(String id) {
                        f.income.deleteCategory(id);
                    }
                }),
                new Kind("income item", true, f -> {
                    String parentId = f.income.createCategory("Parent").id();
                    return new Namer() {
                        public Created create(String name) {
                            IncomeItem i = f.income.createItem(parentId, name);
                            return new Created(i.id(), i.name());
                        }

                        public void delete(String id) {
                            f.income.deleteItem(id);
                        }
                    };
                }),
                new Kind("expense category", true, f -> new Namer() {
                    public Created create(String name) {
                        ExpenseCategory c = f.expenses.createCategory(name);
                        return new Created(c.id(), c.name());
                    }

                    public void delete(String id) {
                        f.expenses.deleteCategory(id);
                    }
                }),
                new Kind("expense item", true, f -> {
                    String parentId = f.expenses.createCategory("Parent").id();
                    return new Namer() {
                        public Created create(String name) {
                            ExpenseItem i = f.expenses.createItem(parentId, name);
                            return new Created(i.id(), i.name());
                        }

                        public void delete(String id) {
                            f.expenses.deleteItem(id);
                        }
                    };
                }),
                new Kind("student group", true, f -> new Namer() {
                    public Created create(String name) {
                        StudentGroup g = f.groups.create(name, null);
                        return new Created(g.id(), g.name());
                    }

                    public void delete(String id) {
                        f.groups.delete(id);
                    }
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
    @DisplayName("AC-3: missing, blank and over-long names are rejected")
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
    @DisplayName("AC-3: a 100-character name is accepted, and surrounding spaces are trimmed")
    void acceptsAndTrimsNames(Kind kind) {
        Namer namer = kind.namer().apply(finance);

        assertThat(namer.create("x".repeat(100)).name()).hasSize(100);
        assertThat(namer.create("  Feeding  ").name()).isEqualTo("Feeding");
        // 100 characters once trimmed is still fine.
        assertThat(namer.create(" " + "y".repeat(100) + " ").name()).hasSize(100);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("uniquelyNamedKinds")
    @DisplayName("AC-4: a duplicate name is rejected regardless of case and spaces")
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
    @DisplayName("AC-4: the name of a deleted row can be used again")
    void reusesDeletedNames(Kind kind) {
        Namer namer = kind.namer().apply(finance);
        Created first = namer.create("Feeding");

        namer.delete(first.id());

        assertThat(namer.create("Feeding").id()).isNotEqualTo(first.id());
    }

    @Test
    @DisplayName("AC-4: two students may share a name")
    void studentNamesNeedNotBeUnique() {
        Student first = createStudent("Amina", "Nakato", SchoolClass.P1);
        Student second = createStudent("Amina", "Nakato", SchoolClass.P1);

        assertThat(second.id()).isNotEqualTo(first.id());
    }

    @Test
    @DisplayName("AC-4: renaming to another row's name is rejected; changing a name's case is not")
    void renameRules() {
        ExpenseCategory feeding = finance.expenses.createCategory("Feeding");
        ExpenseCategory admin = finance.expenses.createCategory("Administrative Costs");

        assertThatThrownBy(() -> finance.expenses.renameCategory(admin.id(), "FEEDING"))
                .isInstanceOf(ValidationException.class);
        assertThat(finance.expenses.renameCategory(feeding.id(), "FEEDING").name())
                .isEqualTo("FEEDING");
        assertThatThrownBy(() -> finance.income.renameItem(
                incomeItem("Student Fees", "Tuition").id(), " "))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("AC-5: item names are unique per category, not across categories")
    void itemNamesArePerCategory() {
        ExpenseCategory a = finance.expenses.createCategory("Administrative Costs");
        ExpenseCategory b = finance.expenses.createCategory("Staff Welfare");
        finance.expenses.createItem(a.id(), "Airtime");

        assertThat(finance.expenses.createItem(b.id(), "Airtime").name()).isEqualTo("Airtime");
        assertThatThrownBy(() -> finance.expenses.createItem(a.id(), "airtime"))
                .isInstanceOf(ValidationException.class);

        IncomeCategory fees = finance.income.createCategory("Student Fees");
        IncomeCategory other = finance.income.createCategory("Other Income");
        finance.income.createItem(fees.id(), "Uniform");
        assertThat(finance.income.createItem(other.id(), "Uniform").name()).isEqualTo("Uniform");
    }

    @Test
    @DisplayName("AC-5: an income category and an expense category may share a name")
    void categoryNamesArePerKind() {
        finance.income.createCategory("Transport");

        assertThat(finance.expenses.createCategory("Transport").name()).isEqualTo("Transport");
    }
}
