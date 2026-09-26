package org.angelscare.management.income.service;

import java.util.List;
import org.angelscare.management.common.DeletionGuard;
import org.angelscare.management.common.Names;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.income.model.IncomeCategory;
import org.angelscare.management.income.model.IncomeItem;
import org.angelscare.management.income.repository.IncomeCategoryRepository;
import org.angelscare.management.income.repository.IncomeItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The user-defined income categories and the items within them. */
@Service
@Transactional
public class IncomeCatalogService {

    private final IncomeCategoryRepository categories;
    private final IncomeItemRepository items;
    private final DeletionGuard deletionGuard;

    public IncomeCatalogService(IncomeCategoryRepository categories, IncomeItemRepository items,
            DeletionGuard deletionGuard) {
        this.categories = categories;
        this.items = items;
        this.deletionGuard = deletionGuard;
    }

    public IncomeCategory createCategory(String name) {
        return categories.insert(validCategoryName(name, null));
    }

    public IncomeCategory renameCategory(String categoryId, String name) {
        requireCategory(categoryId);
        categories.rename(categoryId, validCategoryName(name, categoryId));
        return requireCategory(categoryId);
    }

    /** Sorted by name. */
    @Transactional(readOnly = true)
    public List<IncomeCategory> listCategories() {
        return categories.findAll();
    }

    public void deleteCategory(String categoryId) {
        IncomeCategory category = requireCategory(categoryId);
        deletionGuard.requireUnused("income_category", categoryId,
                "income category '" + category.name() + "'");
        categories.softDelete(categoryId);
    }

    public IncomeItem createItem(String categoryId, String name) {
        requireCategory(categoryId);
        return items.insert(categoryId, validItemName(categoryId, name, null));
    }

    public IncomeItem renameItem(String itemId, String name) {
        IncomeItem item = requireItem(itemId);
        items.rename(itemId, validItemName(item.categoryId(), name, itemId));
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

    private IncomeCategory requireCategory(String categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new ValidationException("That income category no longer exists."));
    }

    private IncomeItem requireItem(String itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> new ValidationException("That income item no longer exists."));
    }

    private String validCategoryName(String name, String categoryId) {
        String clean = Names.require(name, "Category name");
        categories.nameClash(clean, categoryId).ifPresent(existing -> {
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
}
