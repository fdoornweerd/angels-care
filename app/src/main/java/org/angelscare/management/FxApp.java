package org.angelscare.management;

import java.nio.file.Path;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The JavaFX application. Spring starts in {@link #init()} (off the UI thread); {@link #start}
 * shows either the main window or, if anything failed, an error window. It never lets an exception
 * escape, since that would mean no window at all.
 */
public class FxApp extends Application {

    private StartupResult startup;
    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        Path dataDir = Diagnostics.appDataDir().toPath();
        startup = Bootstrap.start(dataDir);
    }

    @Override
    public void start(Stage stage) {
        Parent root;
        try {
            root = switch (startup) {
                case StartupResult.Started started -> {
                    context = started.context();
                    yield loadView("/fxml/shell/main.fxml");
                }
                case StartupResult.Failed failed -> errorView(failed.message());
            };
        } catch (Throwable t) {
            Diagnostics.log("Could not build the main window", t);
            root = errorView("The main window could not be shown.\n\n" + Diagnostics.describe(t)
                    + "\n\nLog file:\n" + Diagnostics.logFilePath());
        }

        stage.setTitle("Angels Care");
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
        Diagnostics.log("Window shown.");
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }

    /** Loads an FXML view whose controller is a Spring bean. */
    private Parent loadView(String resource) throws Exception {
        FXMLLoader loader = new FXMLLoader(FxApp.class.getResource(resource));
        loader.setControllerFactory(context::getBean);
        return loader.load();
    }

    /** Plain code rather than FXML, so that it works even when FXML loading is what broke. */
    private static Parent errorView(String message) {
        Label heading = new Label("Something went wrong");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        // A read-only text area rather than a label, so the user can select and copy the message.
        TextArea details = new TextArea(message);
        details.setEditable(false);
        details.setWrapText(true);

        VBox box = new VBox(16, heading, details);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(24));
        return box;
    }
}
