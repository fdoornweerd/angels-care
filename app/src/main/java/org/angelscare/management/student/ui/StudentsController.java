package org.angelscare.management.student.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
import javafx.util.StringConverter;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ui.fx.Fx;
import org.angelscare.management.shell.ui.Navigator;
import org.angelscare.management.shell.ui.PageController;
import org.angelscare.management.student.model.Level;
import org.angelscare.management.student.model.RegisterTotals;
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
    private CheckBox showLeft;
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
        showLeft.selectedProperty().bindBidirectional(page.showLeftProperty());
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
        TextField fee = moneyField(section.feeProperty().get().amount());
        Fx.commitOnLeave(fee, (field, text) ->
                text.equals(Fx.money(section.feeProperty().get().amount()))
                        || page.editClassFee(section, text));
        TextField ream = moneyField(section.feeProperty().get().ream());
        Fx.commitOnLeave(ream, (field, text) ->
                text.equals(Fx.money(section.feeProperty().get().ream()))
                        || page.editClassReam(section, text));
        HBox header = new HBox(12, name, new Label("Fee (UGX)"), fee, new Label("Ream (UGX)"), ream);
        header.setAlignment(Pos.CENTER_LEFT);

        ObservableList<StudentTermLine> rows = FXCollections.observableArrayList();
        Runnable fill = () -> {
            List<StudentTermLine> all = new ArrayList<>(section.lines());
            if (!all.isEmpty()) {
                all.add(totalsLine(schoolClass, section.totalsProperty().get()));
            }
            rows.setAll(all);
        };
        fill.run();
        section.lines().addListener((ListChangeListener<StudentTermLine>) change -> fill.run());

        TableView<StudentTermLine> table = new TableView<>(rows);
        table.getColumns().setAll(List.of(
                Fx.column("Name", StudentTermLine::name),
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
                removeColumn()));
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
        residency.setConverter(new StringConverter<>() {
            @Override
            public String toString(Residency value) {
                return value == Residency.REFUGEE ? "Refugee" : "National";
            }

            @Override
            public Residency fromString(String text) {
                return "Refugee".equalsIgnoreCase(text) ? Residency.REFUGEE : Residency.NATIONAL;
            }
        });
        residency.setValue(Residency.NATIONAL);
        Button add = new Button("Add student to " + schoolClass.label());
        add.setMinWidth(Region.USE_PREF_SIZE);
        add.setOnAction(event -> {
            if (page.addStudent(schoolClass, first.getText(), last.getText(), residency.getValue())) {
                first.clear();
                last.clear();
            }
        });
        HBox addRow = new HBox(8, first, last, residency, add);
        addRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, header, table, addRow);
        box.getStyleClass().add("panel");
        return box;
    }

    /** An editable amount column; the totals row is read-only. */
    private static TableColumn<StudentTermLine, String> money(String title,
            Function<StudentTermLine, Ugx> amount,
            BiPredicate<StudentTermLine, String> commit) {
        return Fx.editableColumn(title, (StudentTermLine l) -> constant(Fx.money(amount.apply(l))),
                commit, StudentsController::isStudent, true);
    }

    private TableColumn<StudentTermLine, String> removeColumn() {
        TableColumn<StudentTermLine, String> column = new TableColumn<>("");
        column.setSortable(false);
        column.setMinWidth(90);
        column.setMaxWidth(90);
        column.setCellFactory(c -> new TableCell<>() {
            private final Button remove = new Button("Remove");

            {
                remove.setOnAction(e -> page.removeFromTerm(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                StudentTermLine line = empty || getTableRow() == null ? null : getTableRow().getItem();
                setGraphic(line == null || !isStudent(line) ? null : remove);
            }
        });
        return column;
    }

    /** The totals row shown under a class's students, in the same columns. */
    private static StudentTermLine totalsLine(SchoolClass schoolClass, RegisterTotals totals) {
        return new StudentTermLine(null, "Total", null, schoolClass, totals.amount(), false,
                totals.debt(), false, totals.ream(), false, totals.paid().first(),
                totals.paid().second(), totals.paid().third(), null);
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
