package org.angelscare.management.student.ui;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ui.fx.Fx;
import org.angelscare.management.shell.ui.Navigator;
import org.angelscare.management.shell.ui.PageController;
import org.angelscare.management.student.model.Boarding;
import org.angelscare.management.student.model.ClassFee;
import org.angelscare.management.student.model.Level;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.StudentTermLine;
import org.angelscare.management.student.ui.StudentsViewModel.ClassSection;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds students.fxml (page 5) to {@link StudentsViewModel}. */
@Component
@Scope("prototype")
class StudentsController implements PageController {

    private final StudentsViewModel page;
    private final Navigator navigator;

    @FXML
    private Label title;
    @FXML
    private Label pageTotals;
    @FXML
    private Label error;
    @FXML
    private VBox sections;

    StudentsController(StudentsViewModel page, Navigator navigator) {
        this.page = page;
        this.navigator = navigator;
    }

    @FXML
    void initialize() {
        Fx.bindError(error, page.errorProperty());
        page.sections().addListener((ListChangeListener<ClassSection>) change -> rebuild());
        pageTotals.textProperty().bind(page.pageTotalsProperty().map(totals ->
                "Owed UGX " + Fx.money(totals.total()) + "  ·  Paid UGX "
                        + Fx.money(totals.paid().total()) + "  ·  Balance UGX "
                        + Fx.money(totals.balance())));
    }

    @Override
    public void show() {
        TermRef term = navigator.term();
        title.setText("Students, " + term.label());
        page.show(term);
    }

    private void rebuild() {
        sections.getChildren().clear();
        Level level = null;
        for (ClassSection section : page.sections()) {
            if (section.schoolClass().level() != level) {
                level = section.schoolClass().level();
                Label heading = new Label(level == Level.NURSERY ? "Nursery" : "Primary");
                heading.getStyleClass().add("level-title");
                sections.getChildren().add(heading);
            }
            sections.getChildren().add(section(section));
        }
    }

    private Node section(ClassSection section) {
        SchoolClass schoolClass = section.schoolClass();
        Label name = new Label(schoolClass.label());
        name.getStyleClass().add("section-title");
        name.setMinWidth(70);
        HBox header = new HBox(12, name,
                new Label("Day fee (UGX)"), feeField(section, ClassFee::amount, page::editClassFee),
                new Label("Boarding fee (UGX)"),
                feeField(section, ClassFee::boardingFee, page::editClassBoardingFee),
                new Label("Ream (UGX)"), feeField(section, ClassFee::ream, page::editClassReam));
        header.setAlignment(Pos.CENTER_LEFT);

        // The view model keeps the rows, totals row included, so the table only shows them.
        TableView<StudentTermLine> table = new TableView<>(section.rows());
        table.getColumns().setAll(List.of(
                Fx.column("Name", StudentTermLine::shownName),
                boardingColumn(),
                money("Amount", StudentTermLine::amount, page::editAmount),
                money("Debt", StudentTermLine::debt, page::editDebt),
                money("Ream", StudentTermLine::ream, page::editReam),
                Fx.moneyColumn("Total", StudentTermLine::total),
                money("1st Month", StudentTermLine::paid1, (l, t) -> page.editPayment(l, 1, t)),
                money("2nd Month", StudentTermLine::paid2, (l, t) -> page.editPayment(l, 2, t)),
                money("3rd Month", StudentTermLine::paid3, (l, t) -> page.editPayment(l, 3, t)),
                Fx.moneyColumn("Balance", StudentTermLine::balance),
                Fx.editableColumn("Remarks",
                        (StudentTermLine l) -> constant(l.remarks() == null ? "" : l.remarks()),
                        page::editRemarks, StudentsController::isStudent, false),
                buttonColumn("Edit", 64, line ->
                        EditStudentDialog.show(sections.getScene().getWindow(), page.editor(line))),
                buttonColumn("Remove", 90, page::removeFromTerm)));
        table.getColumns().get(0).setMinWidth(150);
        table.setPlaceholder(Fx.hint("No students in " + schoolClass.label() + "."));
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(StudentTermLine item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("totals-row");
                if (item != null && !isStudent(item)) {
                    getStyleClass().add("totals-row");
                }
            }
        });
        Fx.fitHeight(table);

        TextField first = new TextField();
        first.setPromptText("First name");
        TextField last = new TextField();
        last.setPromptText("Last name");
        ComboBox<Residency> residency = new ComboBox<>(
                FXCollections.observableArrayList(Residency.values()));
        residency.setConverter(Choices.RESIDENCY);
        residency.setValue(Residency.NATIONAL);
        ComboBox<Boarding> boarding = new ComboBox<>(
                FXCollections.observableArrayList(Boarding.values()));
        boarding.setConverter(Choices.BOARDING);
        boarding.setValue(Boarding.DAY);
        Button add = new Button("Add student to " + schoolClass.label());
        add.setMinWidth(Region.USE_PREF_SIZE);
        add.setOnAction(event -> {
            if (page.addStudent(schoolClass, first.getText(), last.getText(), residency.getValue(),
                    boarding.getValue())) {
                first.clear();
                last.clear();
            }
        });
        HBox addRow = new HBox(8, first, last, residency, boarding, add);
        addRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, header, table, addRow);
        box.getStyleClass().add("panel");
        return box;
    }

    /** A class fee field in the heading; saved when left, if it changed. */
    private TextField feeField(ClassSection section, Function<ClassFee, Ugx> fee,
            BiPredicate<ClassSection, String> save) {
        TextField field = moneyField(fee.apply(section.feeProperty().get()));
        Fx.commitOnLeave(field, (f, text) ->
                text.equals(Fx.money(fee.apply(section.feeProperty().get())))
                        || save.test(section, text));
        return field;
    }

    /** An editable amount column; the totals row is read-only. */
    private static TableColumn<StudentTermLine, String> money(String title,
            Function<StudentTermLine, Ugx> amount,
            BiPredicate<StudentTermLine, String> commit) {
        return Fx.editableColumn(title, (StudentTermLine l) -> constant(Fx.money(amount.apply(l))),
                commit, StudentsController::isStudent, true);
    }

    /** Day or Boarding, picked per student; blank on the totals row. */
    private TableColumn<StudentTermLine, String> boardingColumn() {
        TableColumn<StudentTermLine, String> column = new TableColumn<>("Day/Boarding");
        column.setSortable(false);
        column.setMinWidth(110);
        column.setMaxWidth(110);
        column.setCellValueFactory(cell -> constant(cell.getValue().boarding() == null
                ? "" : cell.getValue().boarding().name()));
        column.setCellFactory(c -> new TableCell<>() {
            private final ComboBox<Boarding> choice = new ComboBox<>(
                    FXCollections.observableArrayList(Boarding.values()));
            private boolean showing;

            {
                choice.setConverter(Choices.BOARDING);
                choice.setMaxWidth(Double.MAX_VALUE);
                choice.setOnAction(e -> {
                    StudentTermLine line = getTableRow() == null ? null : getTableRow().getItem();
                    if (!showing && line != null && isStudent(line)
                            && choice.getValue() != null && choice.getValue() != line.boarding()) {
                        page.editBoarding(line, choice.getValue());
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                StudentTermLine line = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (line == null || !isStudent(line)) {
                    setGraphic(null);
                    return;
                }
                showing = true;
                choice.setValue(line.boarding());
                showing = false;
                setGraphic(choice);
            }
        });
        return column;
    }

    /** A button on every student's line (not the totals row). */
    private static TableColumn<StudentTermLine, String> buttonColumn(String text, double width,
            Consumer<StudentTermLine> action) {
        TableColumn<StudentTermLine, String> column = new TableColumn<>("");
        column.setSortable(false);
        column.setMinWidth(width);
        column.setMaxWidth(width);
        column.setCellFactory(c -> new TableCell<>() {
            private final Button button = new Button(text);

            {
                button.setOnAction(e -> action.accept(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                StudentTermLine line = empty || getTableRow() == null ? null : getTableRow().getItem();
                setGraphic(line == null || !isStudent(line) ? null : button);
            }
        });
        return column;
    }

    private static boolean isStudent(StudentTermLine line) {
        return line.studentId() != null;
    }

    private static TextField moneyField(Ugx amount) {
        TextField field = new TextField(Fx.money(amount));
        field.setPrefColumnCount(8);
        field.setAlignment(Pos.CENTER_RIGHT);
        return field;
    }

    private static ObservableValue<String> constant(String text) {
        return new ReadOnlyStringWrapper(text).getReadOnlyProperty();
    }
}
