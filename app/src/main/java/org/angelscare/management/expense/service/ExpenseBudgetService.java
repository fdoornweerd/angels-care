package org.angelscare.management.expense.service;

import java.util.List;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.expense.model.BudgetLine;
import org.angelscare.management.expense.model.ExpenseBudget;
import org.angelscare.management.expense.repository.ExpenseBudgetRepository;
import org.angelscare.management.expense.repository.ExpenseItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExpenseBudgetService {

    private final ExpenseBudgetRepository budgets;
    private final ExpenseItemRepository items;
    private final CalendarService calendar;

    public ExpenseBudgetService(ExpenseBudgetRepository budgets, ExpenseItemRepository items,
            CalendarService calendar) {
        this.budgets = budgets;
        this.items = items;
        this.calendar = calendar;
    }

    /** Creates the item's budget for the year, or replaces it if one exists. */
    public ExpenseBudget setMonthlyBudget(String expenseItemId, int year, Ugx monthlyAmount) {
        items.findById(expenseItemId)
                .orElseThrow(() -> new ValidationException("That expense item no longer exists."));
        if (monthlyAmount == null || monthlyAmount.isNegative()) {
            throw new ValidationException("A budget can't be less than UGX 0.");
        }
        SchoolYear schoolYear = calendar.requireYear(year);
        return budgets.find(expenseItemId, schoolYear.id())
                .map(existing -> {
                    budgets.updateAmount(existing.id(), monthlyAmount);
                    return budgets.findById(existing.id()).orElseThrow();
                })
                .orElseGet(() -> budgets.insert(expenseItemId, schoolYear.id(), monthlyAmount));
    }

    /** Sorted by category name, then item name. Items with no budget are not listed. */
    @Transactional(readOnly = true)
    public List<BudgetLine> budgetsFor(int year) {
        return calendar.findYear(year)
                .map(schoolYear -> budgets.linesFor(schoolYear.id()))
                .orElse(List.of());
    }

    public void deleteBudget(String budgetId) {
        budgets.findById(budgetId)
                .orElseThrow(() -> new ValidationException("That budget no longer exists."));
        budgets.softDelete(budgetId);
    }
}
