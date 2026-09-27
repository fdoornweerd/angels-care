package org.angelscare.management.accounts.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import org.angelscare.management.accounts.model.Ledger;
import org.angelscare.management.accounts.model.SummaryRow;
import org.angelscare.management.accounts.model.TermSummary;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.ui.DateFormats;
import org.angelscare.management.common.ui.fx.Fx;
import org.angelscare.management.shell.ui.Navigator;
import org.angelscare.management.shell.ui.PageController;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds term-summary.fxml (page 2) to {@link TermSummaryViewModel}. */
@Component
@Scope("prototype")
class TermSummaryController implements PageController {

    private final TermSummaryViewModel page;
    private final Navigator navigator;
    private final CalendarService calendar;
    private boolean switchingTerm;

    @FXML
    private Label title;
    @FXML
    private ToggleButton term1;
    @FXML
    private ToggleButton term2;
    @FXML
    private ToggleButton term3;
    @FXML
    private Label termDates;
    @FXML
    private TableView<SummaryRow> income;
    @FXML
    private TableView<SummaryRow> expenses;
    @FXML
    private Label collected;
    @FXML
    private Label spent;
    @FXML
    private Label surplusLabel;
    @FXML
    private Label surplus;

    TermSummaryController(TermSummaryViewModel page, Navigator navigator,
            CalendarService calendar) {
        this.page = page;
        this.navigator = navigator;
        this.calendar = calendar;
    }

    @FXML
    void initialize() {
        setUp(income, "Income", "Expected", Ledger.INCOME);
        setUp(expenses, "Expense", "Budgeted", Ledger.EXPENSE);
        expenses.setPlaceholder(Fx.hint(page.expenseHint()));
        ToggleGroup tabs = new ToggleGroup();
        List<ToggleButton> buttons = List.of(term1, term2, term3);
        for (int i = 0; i < buttons.size(); i++) {
            int number = i + 1;
            buttons.get(i).setToggleGroup(tabs);
            buttons.get(i).setOnAction(event -> {
                if (!switchingTerm) {
                    navigator.selectTerm(number);
                    show();
                }
            });
        }
    }

    @Override
    public void show() {
        TermRef term = navigator.term();
        page.show(term);
        switchingTerm = true;
        List.of(term1, term2, term3).get(term.number() - 1).setSelected(true);
        switchingTerm = false;
        title.setText(SchoolYear.label(term.year()));
        Term stored = calendar.requireTerm(term);
        termDates.setText(DateFormats.format(stored.dates().start()) + " – "
                + DateFormats.format(stored.dates().end()));

        TermSummary summary = page.summaryProperty().get();
        income.getItems().setAll(withTotals(page.incomeRows(), summary.incomeTotals()));
        List<SummaryRow> expenseRows = page.expenseRows().isEmpty()
                ? List.of() : withTotals(page.expenseRows(), summary.expenseTotals());
        expenses.getItems().setAll(expenseRows);
        collected.setText("UGX " + Fx.money(summary.incomesCollected()));
        spent.setText("UGX " + Fx.money(summary.expenditures()));
        surplusLabel.setText(page.surplusLabel());
        surplus.setText("UGX " + Fx.money(summary.surplus()));
        surplus.getStyleClass().remove("deficit");
        if (summary.surplus().isNegative()) {
            surplus.getStyleClass().add("deficit");
        }
    }

    @FXML
    void detailedIncomes() {
        navigator.openDetail(Ledger.INCOME, null);
    }

    @FXML
    void detailedExpenses() {
        navigator.openDetail(Ledger.EXPENSE, null);
    }

    private void setUp(TableView<SummaryRow> table, String nameTitle, String planTitle,
            Ledger ledger) {
        table.getColumns().setAll(List.of(
                Fx.column(nameTitle, SummaryRow::name),
                Fx.moneyColumn(planTitle, SummaryRow::planned),
                Fx.moneyColumn("1st month", (SummaryRow r) -> r.actual().month(1)),
                Fx.moneyColumn("2nd month", (SummaryRow r) -> r.actual().month(2)),
                Fx.moneyColumn("3rd month", (SummaryRow r) -> r.actual().month(3)),
                Fx.moneyColumn("Total", SummaryRow::total)));
        Fx.fitHeight(table);
        table.setRowFactory(t -> {
            TableRow<SummaryRow> row = new TableRow<>() {
                @Override
                protected void updateItem(SummaryRow item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("totals-row", "clickable-row");
                    if (item != null) {
                        getStyleClass().add(item.isTotal() ? "totals-row" : "clickable-row");
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                SummaryRow clicked = row.getItem();
                if (clicked != null && !clicked.isTotal()) {
                    navigator.openDetail(ledger, clicked.isStudents() ? null : clicked.categoryId());
                }
            });
            return row;
        });
    }

    private static List<SummaryRow> withTotals(List<SummaryRow> rows, SummaryRow totals) {
        List<SummaryRow> all = new ArrayList<>(rows);
        all.add(totals);
        return all;
    }
}
