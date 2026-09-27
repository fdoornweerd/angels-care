package org.angelscare.management.common.ui.fx;

import java.time.LocalDate;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.common.ui.DateFormats;
import org.angelscare.management.common.ui.UgxField;

/**
 * Small, generic JavaFX pieces shared by every page, so controllers stay a list of "bind this
 * control to that property" lines. Nothing here knows about a particular page.
 */
public final class Fx {

    /** Rows are all the same height, so a table can be sized to show all its rows. */
    public static final double ROW_HEIGHT = 30;

    private Fx() {
    }

    /** A read-only text column. */
    public static <R> TableColumn<R, String> column(String title, Function<R, String> text) {
        TableColumn<R, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(text.apply(cell.getValue())).getReadOnlyProperty());
        column.setSortable(false);
        return column;
    }

    /** A read-only amount column: right-aligned, "300,000", blank for null. */
    public static <R> TableColumn<R, String> moneyColumn(String title, Function<R, Ugx> amount) {
        TableColumn<R, String> column = column(title, row -> money(amount.apply(row)));
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
        return column;
    }

    /**
     * A column whose cells are always text fields. What is typed is handed to {@code commit} when
     * the user leaves the cell (Enter, Tab or a click elsewhere); if it returns false the cell turns
     * red and keeps the text. {@code editable} decides per row (e.g. not on a totals row).
     */
    public static <R> TableColumn<R, String> editableColumn(String title,
            Function<R, ObservableValue<String>> value, BiPredicate<R, String> commit,
            Function<R, Boolean> editable, boolean rightAligned) {
        TableColumn<R, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> value.apply(cell.getValue()));
        column.setCellFactory(c -> new EditCell<>(commit, editable, rightAligned));
        column.setSortable(false);
        if (rightAligned) {
            // Also for rows that aren't editable (a totals row shows plain text).
            column.setStyle("-fx-alignment: CENTER-RIGHT;");
        }
        return column;
    }

    /** "300,000", or blank for null. */
    public static String money(Ugx amount) {
        return amount == null ? "" : UgxField.format(amount);
    }

    /** The red line above or below a table: visible only while there is a message. */
    public static void bindError(Label label, ObservableStringValue message) {
        label.getStyleClass().add("error-message");
        label.setWrapText(true);
        label.textProperty().bind(message);
        label.visibleProperty().bind(label.textProperty().isNotEmpty());
        label.managedProperty().bind(label.visibleProperty());
    }

    /** A grey hint, e.g. a table's placeholder. */
    public static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        label.setWrapText(true);
        return label;
    }

    /** Sizes the table to show all its rows, so a page of several tables scrolls as one. */
    public static void fitHeight(TableView<?> table) {
        table.setFixedCellSize(ROW_HEIGHT);
        table.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> ROW_HEIGHT * (Math.max(table.getItems().size(), 1) + 1) + 4,
                table.itemsProperty(), table.getItems()));
        table.minHeightProperty().bind(table.prefHeightProperty());
        table.maxHeightProperty().bind(table.prefHeightProperty());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    /** DatePickers read and show dd/MM/yyyy; a date that can't be read leaves the field empty. */
    public static void datePicker(DatePicker picker) {
        picker.setPromptText("dd/mm/yyyy");
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return DateFormats.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }
                try {
                    return DateFormats.parse(text);
                } catch (ValidationException e) {
                    return null;
                }
            }
        });
    }

    /** A text field that saves when the user leaves it; red while its last value was refused. */
    public static void commitOnLeave(TextField field, BiPredicate<TextField, String> commit) {
        Runnable save = () -> {
            boolean ok = commit.test(field, field.getText());
            field.getStyleClass().remove("invalid");
            if (!ok) {
                field.getStyleClass().add("invalid");
            }
        };
        field.setOnAction(event -> save.run());
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (was && !focused) {
                save.run();
            }
        });
    }

    /** See {@link #editableColumn}. */
    private static final class EditCell<R> extends TableCell<R, String> {
        private final TextField field = new TextField();
        private final BiPredicate<R, String> commit;
        private final Function<R, Boolean> editable;
        private String shown = "";

        EditCell(BiPredicate<R, String> commit, Function<R, Boolean> editable,
                boolean rightAligned) {
            this.commit = commit;
            this.editable = editable;
            if (rightAligned) {
                field.setAlignment(Pos.CENTER_RIGHT);
            }
            field.getStyleClass().add("cell-field");
            commitOnLeave(field, (f, text) -> save(text));
        }

        private boolean save(String text) {
            R row = getTableRow() == null ? null : getTableRow().getItem();
            if (row == null || text.equals(shown)) {
                return true;
            }
            return commit.test(row, text);
        }

        @Override
        protected void updateItem(String text, boolean empty) {
            super.updateItem(text, empty);
            R row = empty || getTableRow() == null ? null : getTableRow().getItem();
            if (row == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            shown = text == null ? "" : text;
            if (!Boolean.TRUE.equals(editable.apply(row))) {
                setGraphic(null);
                setText(shown);
                return;
            }
            if (!field.isFocused()) {
                field.setText(shown);
                field.getStyleClass().remove("invalid");
            }
            setText(null);
            setGraphic(field);
        }
    }
}
