package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import java.util.stream.Stream;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.support.Finance;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Spec 001 AC-24 and 002 AC-15: a delete is blocked while other rows still use the row. */
class DeletionRulesTest extends FinanceTest {

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
                    test.createYear(2026);
                    var category = f.income.createCategory(2026, "Donations");
                    var item = f.income.createItem(category.id(), "Church support", "months");
                    return new InUse("income_category", category.id(),
                            () -> f.income.deleteCategory(category.id()),
                            () -> f.income.deleteItem(item.id()), "income item");
                }),
                new Case("income item ← income entry", test -> {
                    Finance f = test.finance;
                    test.createYear(2026);
                    var item = test.incomeItem(2026, "Donations", "Church support", "months");
                    test.record(f.incomeSheet, item.id(), t(2026, 1), 2, "1", 50_000);
                    return new InUse("income_item", item.id(),
                            () -> f.income.deleteItem(item.id()),
                            () -> {
                                f.incomeSheet.setQuantity(item.id(), t(2026, 1), 2, null);
                                f.incomeSheet.setRate(item.id(), t(2026, 1), 2, null);
                            }, "income entry");
                }),
                new Case("income category ← expected amount", test -> {
                    Finance f = test.finance;
                    test.createYear(2026);
                    var category = f.income.createCategory(2026, "Donations");
                    f.incomeSheet.setPlan(category.id(), t(2026, 2), Ugx.of(300_000));
                    return new InUse("income_category", category.id(),
                            () -> f.income.deleteCategory(category.id()),
                            () -> f.incomeSheet.setPlan(category.id(), t(2026, 2), null),
                            "income plan");
                }),
                new Case("expense item ← expense entry", test -> {
                    Finance f = test.finance;
                    test.createYear(2026);
                    var item = test.expenseItem(2026, "Feeding", "Maize flour", "kg");
                    test.record(f.expenseSheet, item.id(), t(2026, 1), 1, "12.5", 3_500);
                    return new InUse("expense_item", item.id(),
                            () -> f.expenses.deleteItem(item.id()),
                            () -> {
                                f.expenseSheet.setQuantity(item.id(), t(2026, 1), 1, null);
                                f.expenseSheet.setRate(item.id(), t(2026, 1), 1, null);
                            }, "expense entry");
                }),
                new Case("expense category ← budgeted amount", test -> {
                    Finance f = test.finance;
                    test.createYear(2026);
                    var category = f.expenses.createCategory(2026, "Feeding");
                    f.expenseSheet.setPlan(category.id(), t(2026, 1), Ugx.ZERO);
                    return new InUse("expense_category", category.id(),
                            () -> f.expenses.deleteCategory(category.id()),
                            () -> f.expenseSheet.setPlan(category.id(), t(2026, 1), null),
                            "expense plan");
                }),
                new Case("school year ← category", test -> {
                    Finance f = test.finance;
                    var year = test.createYear(2026);
                    var category = f.expenses.createCategory(2026, "Feeding");
                    return new InUse("school_year", year.id(),
                            () -> f.calendar.deleteYear(year.id()),
                            () -> f.expenses.deleteCategory(category.id()), "expense category");
                }),
                new Case("student ← term register", test -> {
                    Finance f = test.finance;
                    test.createYear(2026);
                    var student = test.createStudent("Amina", "Nakato", SchoolClass.P4);
                    f.studentAccounts.openTerm(t(2026, 1));
                    return new InUse("student", student.id(),
                            () -> f.students.delete(student.id()),
                            () -> f.studentAccounts.removeFromTerm(student.id(), t(2026, 1)),
                            "student term");
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("AC-24 (001) / AC-15 (002): a delete is blocked while something uses the row")
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
