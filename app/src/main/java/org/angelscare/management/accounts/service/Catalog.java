package org.angelscare.management.accounts.service;

import java.util.List;
import org.angelscare.management.accounts.model.Ledger;

/**
 * One ledger's categories and items, the way the accounts pages need them. Income and expenses
 * each adapt their own catalogue service to this, so the pages and sheets are written once.
 */
public interface Catalog {

    record Category(String id, String name) {
    }

    record Item(String id, String categoryId, String name, String unit) {
    }

    Ledger ledger();

    /** The categories of the school year starting in {@code year}, by name. */
    List<Category> categories(int year);

    /** By name. */
    List<Item> items(String categoryId);

    Category createCategory(int year, String name);

    Category renameCategory(String categoryId, String name);

    void deleteCategory(String categoryId);

    Item createItem(String categoryId, String name, String unit);

    Item updateItem(String itemId, String name, String unit);

    void deleteItem(String itemId);
}
