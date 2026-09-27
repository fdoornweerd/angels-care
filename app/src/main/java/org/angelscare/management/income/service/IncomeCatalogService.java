package org.angelscare.management.income.service;

import java.util.List;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.DeletionGuard;
import org.angelscare.management.common.Names;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.income.model.IncomeCategory;
import org.angelscare.management.income.model.IncomeItem;
import org.angelscare.management.income.repository.IncomeCategoryRepository;
import org.angelscare.management.income.repository.IncomeItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user-defined income categories and items. Each category belongs to one school year; a
 * school year is named by the calendar year it starts in ({@code year}).
 */
@Service
@Transactional
public class IncomeCatalogService {

    private final IncomeCategoryRepository categories;
    private final IncomeItemRepository items;
    private final CalendarService calendar;
    private final DeletionGuard deletionGuard;

    public IncomeCatalogService(IncomeCategoryRepository categories, IncomeItemRepository items,
            CalendarService calendar, DeletionGuard deletionGuard) {
        this.categories = categories;
        this.items = items;
        this.calendar = calendar;
        this.deletionGuard = deletionGuard;
    }

    /** A category in the school year that starts in {@code year}. */
    public IncomeCategory createCategory(int year, String name) {
        SchoolYear schoolYear = calendar.requireYear(year);
        return categories.insert(schoolYear.id(), validCategoryName(schoolYear.id(), name, null));
    }

    public IncomeCategory renameCategory(String categoryId, String name) {
        IncomeCategory category = requireCategory(categoryId);
        categories.rename(categoryId, validCategoryName(category.schoolYearId(), name, categoryId));
        return requireCategory(categoryId);
    }

    /** The school year's categories, sorted by name; none if the year doesn't exist. */
    @Transactional(readOnly = true)
    public List<IncomeCategory> listCategories(int year) {
        return calendar.findYear(year)
                .map(schoolYear -> categories.findByYear(schoolYear.id()))
                .orElse(List.of());
    }

    public void deleteCategory(String categoryId) {
        IncomeCategory category = requireCategory(categoryId);
        deletionGuard.requireUnused("income_category", categoryId,
                "income category '" + category.name() + "'");
        categories.softDelete(categoryId);
    }

    /** An item needs a name and a unit ("kg", "bags", "months"). */
    public IncomeItem createItem(String categoryId, String name, String unit) {
        requireCategory(categoryId);
        return items.insert(categoryId, validItemName(categoryId, name, null), validUnit(unit));
    }

    public IncomeItem updateItem(String itemId, String name, String unit) {
        IncomeItem item = requireItem(itemId);
        items.update(itemId, validItemName(item.categoryId(), name, itemId), validUnit(unit));
        return requireItem(itemId);
    }

    /** Sorted by name. */
    @Transactional(readOnly = true)
    public List<IncomeItem> listItems(String categoryId) {
        return items.findByCategory(categoryId);
    }

    public void deleteItem(String itemId) {
        IncomeItem item = requireItem(itemId);
        deletionGuard.requireUnused("income_item", itemId, "income item '" + item.name() + "'");
        items.softDelete(itemId);
    }

    /**
     * Copies the categories and items (names and units only) of the year starting in
     * {@code fromYear} into the year starting in {@code toYear}.
     */
    public void copyFromYear(int fromYear, int toYear) {
        SchoolYear from = calendar.requireYear(fromYear);
        SchoolYear to = calendar.requireYear(toYear);
        for (IncomeCategory category : categories.findByYear(from.id())) {
            IncomeCategory copy = categories.insert(to.id(),
                    validCategoryName(to.id(), category.name(), null));
            for (IncomeItem item : items.findByCategory(category.id())) {
                items.insert(copy.id(), item.name(), item.unit());
            }
        }
    }

    private IncomeCategory requireCategory(String categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new ValidationException("That income category no longer exists."));
    }

    private IncomeItem requireItem(String itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> new ValidationException("That income item no longer exists."));
    }

    private String validCategoryName(String schoolYearId, String name, String categoryId) {
        String clean = Names.require(name, "Category name");
        categories.nameClash(schoolYearId, clean, categoryId).ifPresent(existing -> {
            throw new ValidationException(
                    "An income category named '" + existing + "' already exists.");
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
