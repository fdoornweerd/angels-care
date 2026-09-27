package org.angelscare.management.common.ui;

/**
 * The questions a screen asks the user. View models ask through this interface, so tests answer
 * with a fake and the real app shows JavaFX dialogs.
 */
public interface ConfirmDialogs {

    enum UnsavedChoice { SAVE, DISCARD, CANCEL }

    /** "You have unsaved changes." Save / Discard / Cancel. */
    UnsavedChoice askUnsavedChanges();

    /** A yes/no question such as "Delete 'Tuition'?"; true means go ahead. */
    boolean confirm(String question);
}
