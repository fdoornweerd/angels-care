package org.angelscare.management.accounts.service;

import java.util.List;
import org.angelscare.management.accounts.model.Ledger;
import org.angelscare.management.income.model.IncomeCategory;
import org.angelscare.management.income.model.IncomeItem;
import org.angelscare.management.income.service.IncomeCatalogService;
import org.springframework.stereotype.Component;

/** The income catalogue as a {@link Catalog}. */
@Component
public class IncomeCatalog implements Catalog {

    private final IncomeCatalogService service;

    public IncomeCatalog(IncomeCatalogService service) {
        this.service = service;
    }

    @Override
    public Ledger ledger() {
        return Ledger.INCOME;
    }

    @Override
    public List<Category> categories(int year) {
        return service.listCategories(year).stream().map(IncomeCatalog::category).toList();
    }

    @Override
    public List<Item> items(String categoryId) {
        return service.listItems(categoryId).stream().map(IncomeCatalog::item).toList();
    }

    @Override
    public Category createCategory(int year, String name) {
        return category(service.createCategory(year, name));
    }

    @Override
    public Category renameCategory(String categoryId, String name) {
        return category(service.renameCategory(categoryId, name));
    }

    @Override
    public void deleteCategory(String categoryId) {
        service.deleteCategory(categoryId);
    }

    @Override
    public Item createItem(String categoryId, String name, String unit) {
        return item(service.createItem(categoryId, name, unit));
    }

    @Override
    public Item updateItem(String itemId, String name, String unit) {
        return item(service.updateItem(itemId, name, unit));
    }

    @Override
    public void deleteItem(String itemId) {
        service.deleteItem(itemId);
    }

    private static Category category(IncomeCategory category) {
        return new Category(category.id(), category.name());
    }

    private static Item item(IncomeItem item) {
        return new Item(item.id(), item.categoryId(), item.name(), item.unit());
    }
}
