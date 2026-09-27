package org.angelscare.management.accounts.ui;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.angelscare.management.accounts.model.ItemMonth;
import org.angelscare.management.accounts.model.Ledger;
import org.angelscare.management.accounts.service.Catalog;
import org.angelscare.management.accounts.service.LedgerSheetService;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Quantity;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.common.ui.ConfirmDialogs;
import org.angelscare.management.common.ui.ErrorMessages;
import org.angelscare.management.common.ui.UgxField;
import org.angelscare.management.student.model.StudentsTotals;
import org.angelscare.management.student.service.StudentAccountService;

/**
 * Pages 3 and 4: one table per category, with quantity and rate per item for the chosen month.
 * Every edit is saved at once. The same class serves income (with the Students row) and expenses.
 *
 * <p>An edited cell updates its own line and its category's totals in place; only adding,
 * renaming or deleting rebuilds the page, so typing from cell to cell isn't interrupted.
 */
public class DetailViewModel {

    /** The two editable cells of an item line. */
    public enum Cell { QUANTITY, RATE }

    /** One item's line for the chosen month; the texts are what the cells show. */
    public static final class ItemLine {
        private final Catalog.Item item;
        private final ReadOnlyStringWrapper quantityText = new ReadOnlyStringWrapper("");
        private final ReadOnlyStringWrapper rateText = new ReadOnlyStringWrapper("");
        private final ReadOnlyStringWrapper amountText = new ReadOnlyStringWrapper("");
        private final ReadOnlyObjectWrapper<Cell> invalidCell = new ReadOnlyObjectWrapper<>();

        public ItemLine(Catalog.Item item) {
            this.item = item;
        }

        public Catalog.Item item() {
            return item;
        }

        public ReadOnlyStringProperty quantityTextProperty() {
            return quantityText.getReadOnlyProperty();
        }

        public ReadOnlyStringProperty rateTextProperty() {
            return rateText.getReadOnlyProperty();
        }

        /** Blank until both quantity and rate are filled in. */
        public ReadOnlyStringProperty amountTextProperty() {
            return amountText.getReadOnlyProperty();
        }

        /** The cell whose last edit could not be read, or null. */
        public ReadOnlyObjectProperty<Cell> invalidCellProperty() {
            return invalidCell.getReadOnlyProperty();
        }

        void show(ItemMonth entry) {
            quantityText.set(entry.quantity() == null ? "" : entry.quantity().format());
            rateText.set(entry.rate() == null ? "" : UgxField.format(entry.rate()));
            amountText.set(entry.amount().map(UgxField::format).orElse(""));
            invalidCell.set(null);
        }
    }

    /** One category's table, its totals and its expected/budgeted amount. */
    public static final class CategorySection {
        private final Catalog.Category category;
        private final ObservableList<ItemLine> lines = FXCollections.observableArrayList();
        private final ReadOnlyObjectWrapper<Ugx> monthTotal = new ReadOnlyObjectWrapper<>(Ugx.ZERO);
        private final ReadOnlyObjectWrapper<Ugx> termTotal = new ReadOnlyObjectWrapper<>(Ugx.ZERO);
        private final ReadOnlyStringWrapper planText = new ReadOnlyStringWrapper("");

        public CategorySection(Catalog.Category category) {
            this.category = category;
        }

        public Catalog.Category category() {
            return category;
        }

        public ObservableList<ItemLine> lines() {
            return lines;
        }

        public ReadOnlyObjectProperty<Ugx> monthTotalProperty() {
            return monthTotal.getReadOnlyProperty();
        }

        public ReadOnlyObjectProperty<Ugx> termTotalProperty() {
            return termTotal.getReadOnlyProperty();
        }

        /** The Expected/Budgeted amount as shown, blank when not set. */
        public ReadOnlyStringProperty planTextProperty() {
            return planText.getReadOnlyProperty();
        }
    }

    private final Catalog catalog;
    private final LedgerSheetService sheet;
    private final StudentAccountService students;
    private final ConfirmDialogs dialogs;

    private final IntegerProperty month = new SimpleIntegerProperty(1);
    private final ObservableList<CategorySection> sections = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<StudentsTotals> studentsRow = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper("");
    private TermRef term;
    private String focusCategoryId;

    /** {@code students} is null for the expense page. */
    public DetailViewModel(Catalog catalog, LedgerSheetService sheet,
            StudentAccountService students, ConfirmDialogs dialogs) {
        this.catalog = catalog;
        this.sheet = sheet;
        this.students = students;
        this.dialogs = dialogs;
        month.addListener((obs, old, now) -> {
            if (term != null) {
                showMonth();
            }
        });
    }

    /** Loads the term; {@code focusCategoryId} (or null) is the table to scroll to. */
    public void show(TermRef term, String focusCategoryId) {
        // Back to the first month without the month listener redrawing the old term's tables.
        this.term = null;
        month.set(1);
        this.term = term;
        this.focusCategoryId = focusCategoryId;
        error.set("");
        reload();
    }

    /** "Detailed incomes" or "Detailed expenses". */
    public String title() {
        return isIncome() ? "Detailed incomes" : "Detailed expenses";
    }

    /** "Expected" or "Budgeted". */
    public String planLabel() {
        return isIncome() ? "Expected" : "Budgeted";
    }

    /** The category table to scroll to the top, or null to open at the top of the page. */
    public String focusCategoryId() {
        return focusCategoryId;
    }

    /** 1, 2 or 3: the month the quantity and rate cells are for. */
    public IntegerProperty monthProperty() {
        return month;
    }

    public ObservableList<CategorySection> sections() {
        return sections;
    }

    /** The Students row (income page only; null on the expense page). */
    public ReadOnlyObjectProperty<StudentsTotals> studentsRowProperty() {
        return studentsRow.getReadOnlyProperty();
    }

    public String emptyHint() {
        return "No " + (isIncome() ? "income" : "expense")
                + " categories yet. Click Add category.";
    }

    public boolean editQuantity(ItemLine line, String text) {
        Quantity quantity;
        try {
            quantity = text == null || text.isBlank() ? null : Quantity.parse(text);
        } catch (ValidationException e) {
            return invalid(line, Cell.QUANTITY, e);
        }
        return saveEntry(line, Cell.QUANTITY,
                () -> sheet.setQuantity(line.item().id(), term, month.get(), quantity));
    }

    public boolean editRate(ItemLine line, String text) {
        Ugx rate;
        try {
            rate = text == null || text.isBlank() ? null : UgxField.parse(text);
        } catch (ValidationException e) {
            return invalid(line, Cell.RATE, e);
        }
        return saveEntry(line, Cell.RATE,
                () -> sheet.setRate(line.item().id(), term, month.get(), rate));
    }

    public boolean editPlan(CategorySection section, String text) {
        error.set("");
        try {
            Ugx amount = text == null || text.isBlank() ? null : UgxField.parse(text);
            sheet.setPlan(section.category().id(), term, amount);
        } catch (Exception e) {
            error.set(section.category().name() + ": " + ErrorMessages.forException(e));
            return false;
        }
        showTotals(section);
        return true;
    }

    public boolean addCategory(String name) {
        return change(() -> catalog.createCategory(term.year(), name));
    }

    public boolean renameCategory(CategorySection section, String name) {
        return change(() -> catalog.renameCategory(section.category().id(), name));
    }

    public boolean addItem(CategorySection section, String name, String unit) {
        return change(() -> catalog.createItem(section.category().id(), name, unit));
    }

    public boolean editItem(ItemLine line, String name, String unit) {
        return change(() -> catalog.updateItem(line.item().id(), name, unit));
    }

    /** After "Delete '<name>'?"; false if cancelled or blocked. */
    public boolean deleteCategory(CategorySection section) {
        return dialogs.confirm("Delete '" + section.category().name() + "'?")
                && change(() -> catalog.deleteCategory(section.category().id()));
    }

    public boolean deleteItem(ItemLine line) {
        return dialogs.confirm("Delete '" + line.item().name() + "'?")
                && change(() -> catalog.deleteItem(line.item().id()));
    }

    /** The message under the tables; empty when there is none. */
    public ReadOnlyStringProperty errorProperty() {
        return error.getReadOnlyProperty();
    }

    public String error() {
        return error.get();
    }

    private boolean isIncome() {
        return catalog.ledger() == Ledger.INCOME;
    }

    /** Rebuilds every section: after something was added, renamed or deleted. */
    private void reload() {
        sections.setAll(catalog.categories(term.year()).stream().map(category -> {
            CategorySection section = new CategorySection(category);
            catalog.items(category.id()).forEach(item -> section.lines.add(new ItemLine(item)));
            return section;
        }).toList());
        showMonth();
        studentsRow.set(students == null ? null : students.totals(term));
    }

    /** Fills every line with the chosen month's entries, and every section's totals. */
    private void showMonth() {
        Map<String, ItemMonth> entries = sheet.monthEntries(term, month.get());
        for (CategorySection section : sections) {
            for (ItemLine line : section.lines) {
                line.show(Optional.ofNullable(entries.get(line.item().id()))
                        .orElse(new ItemMonth(line.item().id(), term, month.get(), null, null)));
            }
            showTotals(section);
        }
    }

    private void showTotals(CategorySection section) {
        MonthlyAmounts totals = sheet.categoryTotals(section.category().id(), term);
        section.monthTotal.set(totals.month(month.get()));
        section.termTotal.set(totals.total());
        section.planText.set(sheet.plan(section.category().id(), term)
                .map(UgxField::format).orElse(""));
    }

    private boolean saveEntry(ItemLine line, Cell cell, Supplier<ItemMonth> save) {
        ItemMonth saved;
        try {
            saved = save.get();
        } catch (ValidationException e) {
            return invalid(line, cell, e);
        } catch (Exception e) {
            error.set(ErrorMessages.forException(e));
            return false;
        }
        error.set("");
        line.show(saved);
        sections.stream().filter(s -> s.lines.contains(line)).findFirst().ifPresent(this::showTotals);
        return true;
    }

    private boolean invalid(ItemLine line, Cell cell, ValidationException e) {
        line.invalidCell.set(cell);
        error.set(line.item().name() + ": " + e.getMessage());
        return false;
    }

    private boolean change(Runnable action) {
        error.set("");
        try {
            action.run();
        } catch (Exception e) {
            error.set(ErrorMessages.forException(e));
            return false;
        }
        reload();
        return true;
    }
}
