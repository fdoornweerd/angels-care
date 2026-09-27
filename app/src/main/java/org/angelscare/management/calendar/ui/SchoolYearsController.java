package org.angelscare.management.calendar.ui;

import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.TermDates;
import org.angelscare.management.calendar.ui.SchoolYearsViewModel.YearRow;
import org.angelscare.management.common.ui.DateFormats;
import org.angelscare.management.common.ui.fx.Fx;
import org.angelscare.management.shell.ui.Navigator;
import org.angelscare.management.shell.ui.PageController;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds school-years.fxml (page 1) to {@link SchoolYearsViewModel}. */
@Component
@Scope("prototype")
class SchoolYearsController implements PageController {

    private final SchoolYearsViewModel page;
    private final Navigator navigator;

    @FXML
    private Label listError;
    @FXML
    private TableView<YearRow> table;
    @FXML
    private VBox form;
    @FXML
    private Label formTitle;
    @FXML
    private Label error;
    @FXML
    private TextField startYear;
    @FXML
    private Label yearName;
    @FXML
    private DatePicker term1Start;
    @FXML
    private DatePicker term1End;
    @FXML
    private DatePicker term2Start;
    @FXML
    private DatePicker term2End;
    @FXML
    private DatePicker term3Start;
    @FXML
    private DatePicker term3End;
    @FXML
    private CheckBox copy;

    SchoolYearsController(SchoolYearsViewModel page, Navigator navigator) {
        this.page = page;
        this.navigator = navigator;
    }

    @FXML
    void initialize() {
        TableColumn<YearRow, String> surplus = Fx.moneyColumn("Surplus/Deficit",
                (YearRow r) -> r.totals().surplus());
        table.getColumns().setAll(List.of(
                Fx.column("School year", (YearRow r) -> r.year().label()),
                Fx.column("Term 1", (YearRow r) -> dates(r.year(), 0)),
                Fx.column("Term 2", (YearRow r) -> dates(r.year(), 1)),
                Fx.column("Term 3", (YearRow r) -> dates(r.year(), 2)),
                Fx.moneyColumn("Income collected", (YearRow r) -> r.totals().incomeCollected()),
                Fx.moneyColumn("Expenditure", (YearRow r) -> r.totals().expenditure()),
                surplus,
                actionsColumn()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setItems(page.rows());
        table.setPlaceholder(Fx.hint(page.emptyHint()));
        table.setFixedCellSize(Fx.ROW_HEIGHT + 4);
        table.setRowFactory(t -> {
            TableRow<YearRow> row = new TableRow<>();
            row.getStyleClass().add("clickable-row");
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && !(event.getTarget() instanceof Button)) {
                    navigator.openYear(row.getItem().year().year());
                }
            });
            return row;
        });

        Fx.bindError(listError, page.errorProperty());
        Fx.bindError(error, page.errorProperty());
        form.visibleProperty().bind(page.editingProperty());
        form.managedProperty().bind(form.visibleProperty());
        listError.visibleProperty().unbind();
        listError.visibleProperty().bind(page.editingProperty().not()
                .and(listError.textProperty().isNotEmpty()));

        yearName.textProperty().bind(page.yearNameProperty());
        page.startYearProperty().addListener((obs, old, value) -> {
            String text = value == null ? "" : value.toString();
            if (!text.equals(startYear.getText().strip())) {
                startYear.setText(text);
            }
        });
        startYear.textProperty().addListener((obs, old, text) -> {
            String digits = text.strip();
            page.startYearProperty().set(digits.matches("\\d{4}") ? Integer.valueOf(digits) : null);
        });
        DatePicker[][] pickers = {{term1Start, term1End}, {term2Start, term2End}, {term3Start, term3End}};
        for (int i = 0; i < pickers.length; i++) {
            Fx.datePicker(pickers[i][0]);
            Fx.datePicker(pickers[i][1]);
            pickers[i][0].valueProperty().bindBidirectional(page.termStart(i + 1));
            pickers[i][1].valueProperty().bindBidirectional(page.termEnd(i + 1));
        }
        copy.textProperty().bind(page.copyLabelProperty());
        copy.selectedProperty().bindBidirectional(page.copyFromPreviousProperty());
        copy.visibleProperty().bind(page.copyOfferedProperty());
        copy.managedProperty().bind(copy.visibleProperty());
    }

    @Override
    public void show() {
        page.refresh();
    }

    @FXML
    void newYear() {
        page.newYear();
        formTitle.setText("New school year");
        startYear.setEditable(true);
        startYear.setText(String.valueOf(page.startYearProperty().get()));
        term1Start.requestFocus();
    }

    @FXML
    void save() {
        page.save();
    }

    @FXML
    void cancel() {
        page.cancel();
    }

    private void edit(SchoolYear year) {
        page.edit(year);
        formTitle.setText("School year " + year.label());
        startYear.setEditable(false);
        startYear.setText(String.valueOf(year.year()));
    }

    /** "Edit dates" and "Delete" for each year. */
    private TableColumn<YearRow, String> actionsColumn() {
        TableColumn<YearRow, String> column = new TableColumn<>("");
        column.setSortable(false);
        column.setMinWidth(150);
        column.setCellFactory(c -> new TableCell<>() {
            private final Button edit = new Button("Edit dates");
            private final Button delete = new Button("Delete");
            private final HBox buttons = new HBox(6, edit, delete);

            {
                edit.setOnAction(e -> edit(getTableRow().getItem().year()));
                delete.setOnAction(e -> page.delete(getTableRow().getItem().year()));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null
                        ? null : buttons);
            }
        });
        return column;
    }

    private static String dates(SchoolYear year, int index) {
        TermDates dates = year.terms().get(index).dates();
        return DateFormats.format(dates.start()) + " – " + DateFormats.format(dates.end());
    }
}
