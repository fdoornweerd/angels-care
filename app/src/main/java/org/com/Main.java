package org.com;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        String dbMessage = Database.testConnection();

        Label label = new Label("Hello, Angels Care!\n" + dbMessage);
        StackPane root = new StackPane(label);
        Scene scene = new Scene(root, 400, 250);

        stage.setTitle("Angels Care - Hello World");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}