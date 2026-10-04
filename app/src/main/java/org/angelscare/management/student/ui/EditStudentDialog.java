package org.angelscare.management.student.ui;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.ui.fx.Fx;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.StudentStatus;

/** Shows {@link EditStudentViewModel} as a dialog. Save stays open, with the reason, if refused. */
final class EditStudentDialog {

    private EditStudentDialog() {
    }

    static void show(Window owner, EditStudentViewModel editor) {
        TextField first = new TextField();
        first.textProperty().bindBidirectional(editor.firstNameProperty());
        TextField last = new TextField();
        last.textProperty().bindBidirectional(editor.lastNameProperty());
        ComboBox<Residency> residency = choice(Residency.values(), Choices.RESIDENCY);
        residency.valueProperty().bindBidirectional(editor.residencyProperty());
        ComboBox<TermRef> joined =
                new ComboBox<>(FXCollections.observableArrayList(editor.joinedChoices()));
        joined.setConverter(Choices.TERM);
        joined.valueProperty().bindBidirectional(editor.joinedProperty());
        ComboBox<StudentStatus> status = choice(StudentStatus.values(), Choices.STATUS);
        status.valueProperty().bindBidirectional(editor.statusProperty());

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(8);
        form.addRow(0, new Label("First name"), first);
        form.addRow(1, new Label("Last name"), last);
        form.addRow(2, new Label("National / Refugee"), residency);
        form.addRow(3, new Label("Joined"), joined);
        form.addRow(4, new Label("Active / Left"), status);
        Label note = Fx.hint("Marked Left, they stay on this term and earlier ones and are not"
                + " added to later terms.");
        note.setWrapText(true);
        note.setMaxWidth(380);
        Label error = new Label();
        Fx.bindError(error, editor.errorProperty());
        error.setMaxWidth(380);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(editor.title());
        dialog.setHeaderText(editor.title());
        dialog.getDialogPane().setContent(new VBox(12, form, note, error));
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        // A message appearing or going grows or shrinks the dialog, rather than squeezing the note.
        note.setMinHeight(Region.USE_PREF_SIZE);
        editor.errorProperty().addListener((obs, old, now) ->
                dialog.getDialogPane().getScene().getWindow().sizeToScene());
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (!editor.save()) {
                event.consume();
            }
        });
        dialog.showAndWait();
    }

    private static <T> ComboBox<T> choice(T[] values, StringConverter<T> converter) {
        ComboBox<T> box = new ComboBox<>(FXCollections.observableArrayList(values));
        box.setConverter(converter);
        return box;
    }
}
