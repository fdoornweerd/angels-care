package org.angelscare.management.expense.service;

import java.util.List;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.DeletionGuard;
import org.angelscare.management.common.Names;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.expense.model.ExpenseCategory;
import org.angelscare.management.expense.model.ExpenseItem;
import org.angelscare.management.expense.repository.ExpenseCategoryRepository;
import org.angelscare.management.expense.repository.ExpenseItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user-defined expense categories and items. Each category belongs to one school year; a
 * school year is named by the calendar year it starts in ({@code year}).
 */
@Service
@Transactional
public class ExpenseCatalogService {

    private final ExpenseCategoryRepository categories;
    private final ExpenseItemRepository items;
    private final CalendarService calendar;
    private final DeletionGuard deletionGuard;

    public ExpenseCatalogService(ExpenseCategoryRepository categories, ExpenseItemRepository items,
            CalendarService calendar, DeletionGuard deletionGuard) {
        this.categories = categories;
        this.items = items;
        this.calendar = calendar;
        this.deletionGuard = deletionGuard;
    }

    /** A category in the school year that starts in {@code year}. */
    public ExpenseCategory createCategory(int year, String name) {
        SchoolYear schoolYear = calendar.requireYear(year);
        return categories.insert(schoolYear.id(), validCategoryName(schoolYear.id(), name, null));
    }

    public ExpenseCategory renameCategory(String categoryId, String name) {
        ExpenseCategory category = requireCategory(categoryId);
        categories.rename(categoryId, validCategoryName(category.schoolYearId(), name, categoryId));
        return requireCategory(categoryId);
    }

    /** The school year's categories, sorted by name; none if the year doesn't exist. */
    @Transactional(readOnly = true)
    public List<ExpenseCategory> listCategories(int year) {
        return calendar.findYear(year)
                .map(schoolYear -> categories.findByYear(schoolYear.id()))
                .orElse(List.of());
    }

    public void deleteCategory(String categoryId) {
        ExpenseCategory category = requireCategory(categoryId);
        deletionGuard.requireUnused("expense_category", categoryId,
                "expense category '" + category.name() + "'");
        categories.softDelete(categoryId);
    }

    /** An item needs a name and a unit ("kg", "bags", "months"). */
    public ExpenseItem createItem(String categoryId, String name, String unit) {
        requireCategory(categoryId);
        return items.insert(categoryId, validItemName(categoryId, name, null), validUnit(unit));
    }

    public ExpenseItem updateItem(String itemId, String name, String unit) {
        ExpenseItem item = requireItem(itemId);
        items.update(itemId, validItemName(item.categoryId(), name, itemId), validUnit(unit));
        return requireItem(itemId);
    }

    /** Sorted by name. */
    @Transactional(readOnly = true)
    public List<ExpenseItem> listItems(String categoryId) {
        return items.findByCategory(categoryId);
    }

    public void deleteItem(String itemId) {
        ExpenseItem item = requireItem(itemId);
        deletionGuard.requireUnused("expense_item", itemId, "expense item '" + item.name() + "'");
        items.softDelete(itemId);
    }

    /**
     * Copies the categories and items (names and units only) of the year starting in
     * {@code fromYear} into the year starting in {@code toYear}.
     */
    public void copyFromYear(int fromYear, int toYear) {
        SchoolYear from = calendar.requireYear(fromYear);
        SchoolYear to = calendar.requireYear(toYear);
        for (ExpenseCategory category : categories.findByYear(from.id())) {
            ExpenseCategory copy = categories.insert(to.id(),
                    validCategoryName(to.id(), category.name(), null));
            for (ExpenseItem item : items.findByCategory(category.id())) {
                items.insert(copy.id(), item.name(), item.unit());
            }
        }
    }

    private ExpenseCategory requireCategory(String categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new ValidationException("That expense category no longer exists."));
    }

    private ExpenseItem requireItem(String itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> new ValidationException("That expense item no longer exists."));
    }

    private String validCategoryName(String schoolYearId, String name, String categoryId) {
        String clean = Names.require(name, "Category name");
        categories.nameClash(schoolYearId, clean, categoryId).ifPresent(existing -> {
            throw new ValidationException(
                    "An expense category named '" + existing + "' already exists.");
        });
        return clean;
    }

    private String validItemName(String categoryId, String name, String itemId) {
        String clean = Names.require(name, "Item name");
        items.nameClash(categoryId, clean, itemId).ifPresent(existing -> {
            throw new ValidationException(
                    "An item named '" + existing + "' already exists in this category.");
        });
        return clean;
    }

    private static String validUnit(String unit) {
        return Names.require(unit, "Unit");
    }
}
