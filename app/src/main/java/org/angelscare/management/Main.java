package org.angelscare.management;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        String dbMessage;
        try {
            dbMessage = Database.testConnection();
        } catch (Throwable t) {
            // Database.testConnection() reports failures rather than throwing, so reaching this is
            // unexpected - but an exception escaping start() means a window that never appears, and
            // a blank screen is the one outcome worth any amount of defensiveness to avoid.
            Diagnostics.log("Database check threw unexpectedly", t);
            dbMessage = "Database check failed:\n" + Diagnostics.describe(t);
        }

        Label heading = new Label("Hello, Angels Care!");
        heading.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label status = new Label(dbMessage);
        status.setWrapText(true);
        status.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox root = new VBox(16, heading, status);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));

        stage.setTitle("Angels Care");
        stage.setScene(new Scene(root, 800, 500));
        stage.show();
        Diagnostics.log("Window shown.");
    }

    public static void main(String[] args) {
        // Before anything else, so that a crash during start-up still leaves a log behind.
        Diagnostics.startLogFile();
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, t) -> Diagnostics.log("Uncaught exception on thread " + thread.getName(), t));
        try {
            launch(args);
        } catch (Throwable t) {
            Diagnostics.log("FATAL: application failed to launch", t);
            System.exit(1);
        }
    }
}
