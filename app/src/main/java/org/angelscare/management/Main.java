package org.angelscare.management;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        // Whatever goes wrong from here on, the user must end up looking at a window that says
        // what happened, rather than at nothing at all.
        String body;
        try {
            body = Database.testConnection();
        } catch (Throwable t) {
            Diagnostics.log("Database.testConnection() threw", t);
            body = "Database check crashed:\n" + Diagnostics.describe(t);
        }

        Label heading = new Label("Hello, Angels Care!");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        TextArea details = new TextArea(body + "\n\n--- start-up log ---\n" + Diagnostics.transcript());
        details.setEditable(false);
        details.setWrapText(true);

        Label footer = new Label("Log file: " + Diagnostics.logFilePath());
        footer.setWrapText(true);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setTop(heading);
        root.setCenter(details);
        root.setBottom(footer);
        BorderPane.setMargin(details, new Insets(10, 0, 10, 0));

        stage.setTitle("Angels Care - Hello World");
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
        Diagnostics.log("Window shown.");
    }

    public static void main(String[] args) {
        Diagnostics.startLogFile();
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, t) -> Diagnostics.log("Uncaught exception on thread " + thread.getName(), t));
        try {
            Diagnostics.logEnvironment();
            launch(args);
        } catch (Throwable t) {
            // A failure here (missing JavaFX native, bad runtime image, ...) would otherwise exit
            // silently with no window and no message. At least leave a log behind.
            Diagnostics.log("FATAL: application failed to launch", t);
            System.exit(1);
        }
    }
}
