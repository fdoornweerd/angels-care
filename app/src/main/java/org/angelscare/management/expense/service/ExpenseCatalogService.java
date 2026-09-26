package org.angelscare.management.expense.service;

import java.util.List;
import org.angelscare.management.common.DeletionGuard;
import org.angelscare.management.common.Names;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.expense.model.ExpenseCategory;
import org.angelscare.management.expense.model.ExpenseItem;
import org.angelscare.management.expense.repository.ExpenseCategoryRepository;
import org.angelscare.management.expense.repository.ExpenseItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The user-defined expense categories and the items within them. */
@Service
@Transactional
public class ExpenseCatalogService {

    private final ExpenseCategoryRepository categories;
    private final ExpenseItemRepository items;
    private final DeletionGuard deletionGuard;

    public ExpenseCatalogService(ExpenseCategoryRepository categories, ExpenseItemRepository items,
            DeletionGuard deletionGuard) {
        this.categories = categories;
        this.items = items;
        this.deletionGuard = deletionGuard;
    }

    public ExpenseCategory createCategory(String name) {
        return categories.insert(validCategoryName(name, null));
    }

    public ExpenseCategory renameCategory(String categoryId, String name) {
        requireCategory(categoryId);
        categories.rename(categoryId, validCategoryName(name, categoryId));
        return requireCategory(categoryId);
    }

    /** Sorted by name. */
    @Transactional(readOnly = true)
    public List<ExpenseCategory> listCategories() {
        return categories.findAll();
    }

    public void deleteCategory(String categoryId) {
        ExpenseCategory category = requireCategory(categoryId);
        deletionGuard.requireUnused("expense_category", categoryId,
                "expense category '" + category.name() + "'");
        categories.softDelete(categoryId);
    }

    public ExpenseItem createItem(String categoryId, String name) {
        requireCategory(categoryId);
        return items.insert(categoryId, validItemName(categoryId, name, null));
    }

    public ExpenseItem renameItem(String itemId, String name) {
        ExpenseItem item = requireItem(itemId);
        items.rename(itemId, validItemName(item.categoryId(), name, itemId));
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

    private ExpenseCategory requireCategory(String categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new ValidationException("That expense category no longer exists."));
    }

    private ExpenseItem requireItem(String itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> new ValidationException("That expense item no longer exists."));
    }

    private String validCategoryName(String name, String categoryId) {
        String clean = Names.require(name, "Category name");
        categories.nameClash(clean, categoryId).ifPresent(existing -> {
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
}
