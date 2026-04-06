package org.example.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.gui.view.MainWindow;

public class MainApp extends Application {

    private MainWindow mainWindow;

    @Override
    public void start(Stage primaryStage) {
        mainWindow = new MainWindow();
        Scene scene = new Scene(mainWindow.getView(), 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        primaryStage.setTitle("简道云数据同步工具");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (mainWindow != null) {
            mainWindow.shutdown();
        }
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
