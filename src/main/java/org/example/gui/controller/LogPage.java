package org.example.gui.controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

public class LogPage {

    private final VBox root;
    private final TextArea logArea;
    private final ComboBox<String> levelCombo;
    private final Label logCountLabel;

    private int logCount = 0;

    public LogPage() {
        this.root = new VBox(10);
        this.root.setPadding(new Insets(20, 25, 15, 25));
        this.logArea = new TextArea();
        this.logArea.setEditable(false);
        this.logArea.setFont(javafx.scene.text.Font.font("Monaco", 12));
        this.logArea.setWrapText(true);
        this.levelCombo = new ComboBox<>();
        this.levelCombo.getItems().addAll("全部", "INFO", "WARN", "ERROR");
        this.levelCombo.setValue("全部");
        this.logCountLabel = new Label("日志条数: 0");
        this.logCountLabel.getStyleClass().add("log-count-label");

        setupLayout();
        setupLogHandler();
    }

    private void setupLayout() {
        Label titleLabel = new Label("运行日志");
        titleLabel.getStyleClass().add("page-title");

        HBox toolBar = new HBox(10);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        Button clearBtn = new Button("清空日志");
        clearBtn.getStyleClass().add("btn-secondary");
        Button exportBtn = new Button("导出日志");
        exportBtn.getStyleClass().add("btn-secondary");

        Label filterLabel = new Label("日志级别:");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolBar.getChildren().addAll(clearBtn, exportBtn, filterLabel, levelCombo, spacer, logCountLabel);

        VBox logPanel = new VBox(5);
        logPanel.getChildren().add(logArea);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        root.getChildren().addAll(titleLabel, toolBar, logPanel);

        clearBtn.setOnAction(e -> {
            logArea.clear();
            logCount = 0;
            logCountLabel.setText("日志条数: 0");
        });

        exportBtn.setOnAction(e -> exportLog());
    }

    private void setupLogHandler() {
        Logger rootLogger = Logger.getLogger("");
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                String level = record.getLevel().getName();
                String message = record.getMessage();
                if (record.getThrown() != null) {
                    message += " - " + record.getThrown().getMessage();
                }
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String logLine = String.format("[%s] [%s] %s%n", timestamp, level, message);

                Platform.runLater(() -> {
                    logArea.appendText(logLine);
                    logCount++;
                    logCountLabel.setText("日志条数: " + logCount);
                    logArea.setScrollTop(Double.MAX_VALUE);
                });
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        handler.setLevel(Level.ALL);
        rootLogger.addHandler(handler);
    }

    private void exportLog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出日志");
        chooser.setInitialFileName("sync_log_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("文本文件", "*.txt"));

        var file = chooser.showSaveDialog(logArea.getScene().getWindow());
        if (file != null) {
            try {
                java.nio.file.Files.writeString(file.toPath(), logArea.getText());
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "导出失败: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    public VBox getContent() {
        return root;
    }
}
