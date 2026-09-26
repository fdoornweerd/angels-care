package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.income.model.FeeTarget;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.support.Finance;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.DisplayName;

/** Spec 001, AC-24: one case per row of the "Deleting" table. */
class DeletionRulesTest extends FinanceTest {

    /**
     * A parent and the child row that keeps it from being deleted: how to delete each, the table
     * and id of the parent, and a word the "still in use" message must contain.
     */
    record InUse(String parentTable, String parentId, Runnable deleteParent, Runnable deleteChild,
            String expectedWord) {
    }

    record Case(String label, Function<DeletionRulesTest, InUse> setUp) {
        @Override
        public String toString() {
            return label;
        }
    }

    static Stream<Case> cases() {
        return Stream.of(
                new Case("income category ← income item", test -> {
                    Finance f = test.finance;
                    var category = f.income.createCategory("Student Fees");
                    var item = f.income.createItem(category.id(), "Tuition");
                    return new InUse("income_category", category.id(),
                            () -> f.income.deleteCategory(category.id()),
                            () -> f.income.deleteItem(item.id()), "income item");
                }),
                new Case("income item ← fee assignment", test -> {
                    Finance f = test.finance;
                    test.createYear(2026);
                    var item = test.incomeItem("Student Fees", "Tuition");
                    var fee = test.assign(item, FeeTarget.schoolClass(SchoolClass.P7), 300_000,
                            TermRange.from(t(2026, 1)));
                    return new InUse("income_item", item.id(),
                            () -> f.income.deleteItem(item.id()),
                            () -> f.fees.delete(fee.id()), "fee assignment");
                }),
                new Case("expense category ← expense item", test -> {
                    Finance f = test.finance;
                    var category = f.expenses.createCategory("Feeding");
                    var item = f.expenses.createItem(category.id(), "Maize flour");
                    return new InUse("expense_category", category.id(),
                            () -> f.expenses.deleteCategory(category.id()),
                            () -> f.expenses.deleteItem(item.id()), "expense item");
                }),
                new Case("expense item ← budget", test -> {
                    Finance f = test.finance;
                    test.createYear(2026);
                    var category = f.expenses.createCategory("Feeding");
                    var item = f.expenses.createItem(category.id(), "Maize flour");
                    var budget = f.budgets.setMonthlyBudget(item.id(), 2026, Ugx.of(400_000));
                    return new InUse("expense_item", item.id(),
                            () -> f.expenses.deleteItem(item.id()),
                            () -> f.budgets.deleteBudget(budget.id()), "budget");
                }),
                new Case("student group ← membership", test -> {
                    Finance f = test.finance;
                    test.createYear(2026);
                    var group = f.groups.create("Boarders", null);
                    var student = test.createStudent("Amina", "Nakato", SchoolClass.P4);
                    var membership = f.groups.addMember(group.id(), student.id(),
                            TermRange.from(t(2026, 1)));
                    return new InUse("student_group", group.id(),
                            () -> f.groups.delete(group.id()),
                            () -> f.groups.removeMembership(membership.id()), "membership");
                }),
                new Case("student group ← fee assignment", test -> {
                    Finance f = test.finance;
                    test.createYear(2026);
                    var group = f.groups.create("Boarders", null);
                    var fee = test.assign(test.incomeItem("Student Fees", "Boarding"),
                            FeeTarget.group(group.id()), 250_000, TermRange.from(t(2026, 1)));
                    return new InUse("student_group", group.id(),
                            () -> f.groups.delete(group.id()),
                            () -> f.fees.delete(fee.id()), "fee assignment");
                }),
                new Case("student ← membership", test -> {
                    Finance f = test.finance;
                    test.createYear(2026);
                    var group = f.groups.create("Boarders", null);
                    var student = test.createStudent("Amina", "Nakato", SchoolClass.P4);
                    var membership = f.groups.addMember(group.id(), student.id(),
                            TermRange.from(t(2026, 1)));
                    return new InUse("student", student.id(),
                            () -> f.students.delete(student.id()),
                            () -> f.groups.removeMembership(membership.id()), "membership");
                }),
                new Case("student ← fee assignment", test -> {
                    Finance f = test.finance;
                    test.createYear(2026);
                    var student = test.createStudent("Amina", "Nakato", SchoolClass.P4);
                    var fee = test.assign(test.incomeItem("Student Fees", "Medical"),
                            FeeTarget.student(student.id()), 20_000, TermRange.from(t(2026, 1)));
                    return new InUse("student", student.id(),
                            () -> f.students.delete(student.id()),
                            () -> f.fees.delete(fee.id()), "fee assignment");
                }),
                new Case("school year ← membership", test -> {
                    Finance f = test.finance;
                    var year = test.createYear(2026);
                    var group = f.groups.create("Boarders", null);
                    var student = test.createStudent("Amina", "Nakato", SchoolClass.P4);
                    var membership = f.groups.addMember(group.id(), student.id(),
                            TermRange.from(t(2026, 2)));
                    return new InUse("school_year", year.id(),
                            () -> f.calendar.deleteYear(year.id()),
                            () -> f.groups.removeMembership(membership.id()), "membership");
                }),
                new Case("school year ← fee assignment", test -> {
                    Finance f = test.finance;
                    var year = test.createYear(2026);
                    var fee = test.assign(test.incomeItem("Student Fees", "Tuition"),
                            FeeTarget.schoolClass(SchoolClass.P7), 300_000,
                            TermRange.from(t(2026, 3)));
                    return new InUse("school_year", year.id(),
                            () -> f.calendar.deleteYear(year.id()),
                            () -> f.fees.delete(fee.id()), "fee assignment");
                }),
                new Case("school year ← budget", test -> {
                    Finance f = test.finance;
                    var year = test.createYear(2026);
                    var category = f.expenses.createCategory("Feeding");
                    var item = f.expenses.createItem(category.id(), "Maize flour");
                    var budget = f.budgets.setMonthlyBudget(item.id(), 2026, Ugx.of(400_000));
                    return new InUse("school_year", year.id(),
                            () -> f.calendar.deleteYear(year.id()),
                            () -> f.budgets.deleteBudget(budget.id()), "budget");
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("AC-24: a delete is blocked while something uses the row, and allowed once it doesn't")
    void blockedWhileInUse(Case testCase) {
        InUse inUse = testCase.setUp().apply(this);

        assertThatThrownBy(inUse.deleteParent()::run)
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(inUse.expectedWord());
        assertThat(isSoftDeleted(inUse.parentTable(), inUse.parentId())).isFalse();

        inUse.deleteChild().run();
        inUse.deleteParent().run();

        assertThat(isSoftDeleted(inUse.parentTable(), inUse.parentId())).isTrue();
    }
}
