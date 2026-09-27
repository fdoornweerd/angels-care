package org.angelscare.management.accounts.ui;

import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.angelscare.management.accounts.ui.DetailViewModel.CategorySection;
import org.angelscare.management.accounts.ui.DetailViewModel.ItemLine;
import org.angelscare.management.common.ui.fx.Fx;
import org.angelscare.management.shell.ui.Navigator;
import org.angelscare.management.shell.ui.PageController;
import org.angelscare.management.student.model.StudentsTotals;

/** Binds detail.fxml (pages 3 and 4) to a {@link DetailViewModel}. */
public class DetailController implements PageController {

    private final DetailViewModel page;
    private final Navigator navigator;

    @FXML
    private Label title;
    @FXML
    private ToggleButton month1;
    @FXML
    private ToggleButton month2;
    @FXML
    private ToggleButton month3;
    @FXML
    private TextField newCategory;
    @FXML
    private Label error;
    @FXML
    private ScrollPane scroll;
    @FXML
    private VBox sections;

    public DetailController(DetailViewModel page, Navigator navigator) {
        this.page = page;
        this.navigator = navigator;
    }

    @FXML
    void initialize() {
        title.setText(page.title());
        Fx.bindError(error, page.errorProperty());
        ToggleGroup months = new ToggleGroup();
        List<ToggleButton> buttons = List.of(month1, month2, month3);
        for (int i = 0; i < buttons.size(); i++) {
            int month = i + 1;
            buttons.get(i).setToggleGroup(months);
            buttons.get(i).setOnAction(event -> page.monthProperty().set(month));
        }
        page.monthProperty().addListener((obs, old, month) ->
                buttons.get(month.intValue() - 1).setSelected(true));
        page.sections().addListener((ListChangeListener<CategorySection>) change -> rebuild());
        page.studentsRowProperty().addListener((obs, old, row) -> rebuild());
    }

    @Override
    public void show() {
        String focus = navigator.current() instanceof Navigator.Page.Detail detail
                ? detail.focusCategoryId() : null;
        page.show(navigator.term(), focus);
        month1.setSelected(true);
        rebuild();
        scrollTo(page.focusCategoryId());
    }

    @FXML
    void addCategory() {
        if (page.addCategory(newCategory.getText())) {
            newCategory.clear();
        }
    }

    private void rebuild() {
        sections.getChildren().clear();
        StudentsTotals students = page.studentsRowProperty().get();
        if (students != null) {
            sections.getChildren().add(studentsRow(students));
        }
        if (page.sections().isEmpty()) {
            sections.getChildren().add(Fx.hint(page.emptyHint()));
        }
        for (CategorySection section : page.sections()) {
            sections.getChildren().add(section(section));
        }
    }

    /** The one-row Students table at the top of the income page; opens page 5. */
    private Node studentsRow(StudentsTotals students) {
        Label name = new Label("Students");
        name.getStyleClass().add("section-title");
        Label amount = new Label("Amount owed: UGX " + Fx.money(students.expected()));
        Label total = new Label("Paid this term: UGX " + Fx.money(students.total()));
        total.getStyleClass().add("total");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        Button open = new Button("Open students ›");
        open.setMinWidth(Region.USE_PREF_SIZE);
        open.setOnAction(event -> navigator.openStudents());
        HBox row = new HBox(24, name, amount, total, gap, open);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("panel", "clickable-row");
        row.setOnMouseClicked(event -> navigator.openStudents());
        return row;
    }

    private Node section(CategorySection section) {
        TextField name = new TextField(section.category().name());
        name.getStyleClass().add("section-title");
        name.setPrefColumnCount(20);
        Fx.commitOnLeave(name, (field, text) ->
                text.equals(section.category().name()) || page.renameCategory(section, text));
        Button delete = new Button("Delete category");
        delete.setMinWidth(Region.USE_PREF_SIZE);
        delete.setOnAction(event -> page.deleteCategory(section));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(8, name, gap, delete);
        header.setAlignment(Pos.CENTER_LEFT);

        TableView<ItemLine> table = new TableView<>(section.lines());
        table.getColumns().setAll(List.of(
                Fx.editableColumn("Item", (ItemLine l) -> constant(l.item().name()),
                        (l, text) -> page.editItem(l, text, l.item().unit()), l -> true, false),
                Fx.editableColumn("Units", (ItemLine l) -> constant(l.item().unit()),
                        (l, text) -> page.editItem(l, l.item().name(), text), l -> true, false),
                Fx.editableColumn("Quantity", ItemLine::quantityTextProperty,
                        page::editQuantity, l -> true, true),
                Fx.editableColumn("Rate (UGX)", ItemLine::rateTextProperty,
                        page::editRate, l -> true, true),
                amountColumn(),
                deleteColumn()));
        table.setPlaceholder(Fx.hint("No items yet. Add one below."));
        Fx.fitHeight(table);

        TextField itemName = new TextField();
        itemName.setPromptText("New item");
        TextField unit = new TextField();
        unit.setPromptText("Unit (kg, bags…)");
        unit.setPrefColumnCount(8);
        Button add = new Button("Add item");
        add.setMinWidth(Region.USE_PREF_SIZE);
        Runnable addItem = () -> {
            if (page.addItem(section, itemName.getText(), unit.getText())) {
                itemName.clear();
                unit.clear();
            }
        };
        add.setOnAction(event -> addItem.run());
        unit.setOnAction(event -> addItem.run());
        HBox addRow = new HBox(8, itemName, unit, add);
        addRow.setAlignment(Pos.CENTER_LEFT);

        Label monthTotal = new Label();
        monthTotal.getStyleClass().add("total");
        monthTotal.textProperty().bind(section.monthTotalProperty().map(
                total -> "Total this month: UGX " + Fx.money(total)));
        Label termTotal = new Label();
        termTotal.getStyleClass().add("total");
        termTotal.textProperty().bind(section.termTotalProperty().map(
                total -> "Term total: UGX " + Fx.money(total)));
        TextField plan = new TextField(section.planTextProperty().get());
        plan.setPrefColumnCount(10);
        plan.setAlignment(Pos.CENTER_RIGHT);
        plan.setPromptText("not set");
        section.planTextProperty().addListener((obs, old, text) -> {
            if (!plan.isFocused()) {
                plan.setText(text);
            }
        });
        Fx.commitOnLeave(plan, (field, text) ->
                text.equals(section.planTextProperty().get()) || page.editPlan(section, text));
        Region gap2 = new Region();
        HBox.setHgrow(gap2, Priority.ALWAYS);
        HBox footer = new HBox(24, monthTotal, termTotal, gap2,
                new Label(page.planLabel() + " (UGX)"), plan);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, header, table, addRow, footer);
        box.getStyleClass().add("panel");
        box.setUserData(section.category().id());
        return box;
    }

    private static TableColumn<ItemLine, String> amountColumn() {
        TableColumn<ItemLine, String> amount = new TableColumn<>("Amount (UGX)");
        amount.setCellValueFactory(cell -> cell.getValue().amountTextProperty());
        amount.setStyle("-fx-alignment: CENTER-RIGHT;");
        amount.setSortable(false);
        return amount;
    }

    private TableColumn<ItemLine, String> deleteColumn() {
        TableColumn<ItemLine, String> column = new TableColumn<>("");
        column.setSortable(false);
        column.setMinWidth(80);
        column.setMaxWidth(80);
        column.setCellFactory(c -> new TableCell<>() {
            private final Button delete = new Button("Delete");

            {
                delete.setOnAction(e -> page.deleteItem(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null
                        ? null : delete);
            }
        });
        return column;
    }

    /** Scrolls so the category's table is at the top, once the page has been laid out. */
    private void scrollTo(String categoryId) {
        scroll.setVvalue(0);
        if (categoryId == null) {
            return;
        }
        Platform.runLater(() -> {
            scroll.layout();
            for (Node node : sections.getChildren()) {
                if (categoryId.equals(node.getUserData())) {
                    double contentHeight = sections.getHeight();
                    double viewport = scroll.getViewportBounds().getHeight();
                    double y = node.getBoundsInParent().getMinY();
                    scroll.setVvalue(contentHeight > viewport
                            ? Math.min(1, y / (contentHeight - viewport)) : 0);
                }
            }
        });
    }

    private static ObservableValue<String> constant(String text) {
        return new ReadOnlyStringWrapper(text).getReadOnlyProperty();
    }
}
