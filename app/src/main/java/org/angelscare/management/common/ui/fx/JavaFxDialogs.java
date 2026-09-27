package org.angelscare.management.common.ui.fx;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import org.angelscare.management.common.ui.ConfirmDialogs;

/** The real questions: small modal JavaFX dialogs. Tests use a fake instead. */
public class JavaFxDialogs implements ConfirmDialogs {

    private static final ButtonType SAVE = new ButtonType("Save", ButtonData.YES);
    private static final ButtonType DISCARD = new ButtonType("Discard", ButtonData.NO);
    private static final ButtonType CANCEL = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
    private static final ButtonType YES = new ButtonType("Yes", ButtonData.OK_DONE);

    @Override
    public UnsavedChoice askUnsavedChanges() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "You have changes that are not saved yet.", SAVE, DISCARD, CANCEL);
        alert.setTitle("Unsaved changes");
        alert.setHeaderText("Save your changes?");
        ButtonType answer = alert.showAndWait().orElse(CANCEL);
        if (answer == SAVE) {
            return UnsavedChoice.SAVE;
        }
        return answer == DISCARD ? UnsavedChoice.DISCARD : UnsavedChoice.CANCEL;
    }

    @Override
    public boolean confirm(String question) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, question, YES, CANCEL);
        alert.setTitle("Please confirm");
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(CANCEL) == YES;
    }
}
