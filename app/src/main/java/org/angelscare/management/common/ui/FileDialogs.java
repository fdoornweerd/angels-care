package org.angelscare.management.common.ui;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Choosing where to save a file and opening it afterwards. View models ask through this
 * interface, so tests answer with a fake and the real app shows the system's dialogs.
 */
public interface FileDialogs {

    /** The user's Documents folder, or their home folder if there is none. */
    Path documentsFolder();

    /** A Save dialog for a PDF, in {@code folder} with {@code suggestedName}; empty if cancelled. */
    Optional<Path> choosePdfToSave(Path folder, String suggestedName);

    /** Opens the file in the PC's default program for it; throws if that isn't possible. */
    void open(Path file) throws Exception;
}
