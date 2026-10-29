package org.angelscare.management.common.ui.fx;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.application.HostServices;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.angelscare.management.common.ui.FileDialogs;

/**
 * The real file dialogs: JavaFX's {@link FileChooser}, and {@link HostServices} to open the
 * saved file. Not {@code java.awt.Desktop}: Spring Boot runs headless, where AWT can't open files.
 * Tests use a fake instead.
 */
public class JavaFxFileDialogs implements FileDialogs {

    /** The application's host services, available only once the window has been built. */
    private final Supplier<HostServices> hostServices;

    public JavaFxFileDialogs(Supplier<HostServices> hostServices) {
        this.hostServices = hostServices;
    }

    @Override
    public Path documentsFolder() {
        Path home = Path.of(System.getProperty("user.home"));
        Path documents = home.resolve("Documents");
        return Files.isDirectory(documents) ? documents : home;
    }

    @Override
    public Optional<Path> choosePdfToSave(Path folder, String suggestedName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save report");
        // A missing initial folder makes the dialog throw, so only set one that exists.
        if (folder != null && Files.isDirectory(folder)) {
            chooser.setInitialDirectory(folder.toFile());
        }
        chooser.setInitialFileName(suggestedName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF (*.pdf)", "*.pdf"));
        File chosen = chooser.showSaveDialog(focusedWindow());
        return Optional.ofNullable(chosen).map(File::toPath);
    }

    @Override
    public void open(Path file) throws Exception {
        HostServices services = hostServices.get();
        if (services == null) {
            throw new IllegalStateException("No host services to open " + file);
        }
        services.showDocument(file.toUri().toString());
    }

    private static Window focusedWindow() {
        return Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
    }
}
