package org.angelscare.management.accounts.ui;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.angelscare.management.accounts.model.SummaryRow;
import org.angelscare.management.accounts.model.TermSummary;
import org.angelscare.management.accounts.service.TermAccountsService;
import org.angelscare.management.calendar.model.TermRef;

/** Page 2: the income, expense and summary tables for one term. Nothing is edited here. */
public class TermSummaryViewModel {

    private final TermAccountsService accounts;
    private final ObservableList<SummaryRow> incomeRows = FXCollections.observableArrayList();
    private final ObservableList<SummaryRow> expenseRows = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<TermSummary> summary = new ReadOnlyObjectWrapper<>();

    public TermSummaryViewModel(TermAccountsService accounts) {
        this.accounts = accounts;
    }

    /** Loads the term's figures. */
    public void show(TermRef term) {
        TermSummary loaded = accounts.summary(term);
        incomeRows.setAll(loaded.income());
        expenseRows.setAll(loaded.expense());
        summary.set(loaded);
    }

    /** Students first, then the income categories. */
    public ObservableList<SummaryRow> incomeRows() {
        return incomeRows;
    }

    public ObservableList<SummaryRow> expenseRows() {
        return expenseRows;
    }

    /** The whole summary, including the totals rows and surplus. */
    public ReadOnlyObjectProperty<TermSummary> summaryProperty() {
        return summary.getReadOnlyProperty();
    }

    /** "Surplus" or "Deficit". */
    public String surplusLabel() {
        return summary.get() != null && summary.get().surplus().isNegative() ? "Deficit" : "Surplus";
    }

    /** Shown in the expense table when there are no expense categories. */
    public String expenseHint() {
        return "No expense categories yet. Add them on Detailed expenses.";
    }
}
