package org.example.gui.controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

public class LogPage {

    private final VBox root;
    private final TextArea logArea;
    private final ComboBox<String> levelCombo;
    private final Label logCountLabel;
    private int logCount = 0;

    private static final String LOG_DIR = System.getProperty("user.home") + "/.jdy-sync/logs/";
    private static final String LOG_FILE = LOG_DIR + "sync.log";
    private static final int MAX_LOG_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_DISPLAY_LINES = 2000; // 最多显示2000行

    private final ExecutorService logWriter = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "log-writer");
        t.setDaemon(true);
        return t;
    });

    private final StringBuilder pendingLogs = new StringBuilder();
    private final Object logLock = new Object();

    public LogPage() {
        this.root = new VBox(10);
        this.root.setPadding(new Insets(20));
        this.root.getStyleClass().add("page-container");

        this.logArea = new TextArea();
        this.logArea.setEditable(false);
        this.logArea.getStyleClass().add("log-area");
        this.logArea.setStyle("-fx-font-family: 'Monaco', 'Consolas', monospace; -fx-font-size: 12;");
        this.logArea.setWrapText(true);

        this.levelCombo = new ComboBox<>();
        this.levelCombo.getItems().addAll("全部", "INFO", "WARN", "ERROR");
        this.levelCombo.setValue("全部");
        this.logCountLabel = new Label("日志条数: 0");
        this.logCountLabel.getStyleClass().add("log-count-label");

        setupLayout();
        setupLogHandler();
        loadHistoryLogs();
        startLogFlushTask();
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
        Button openLogDirBtn = new Button("打开日志目录");
        openLogDirBtn.getStyleClass().add("btn-secondary");

        Label filterLabel = new Label("日志级别:");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolBar.getChildren().addAll(clearBtn, exportBtn, openLogDirBtn, filterLabel, levelCombo, spacer, logCountLabel);

        VBox logPanel = new VBox(5);
        logPanel.getChildren().add(logArea);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        root.getChildren().addAll(titleLabel, toolBar, logPanel);
        VBox.setVgrow(logPanel, Priority.ALWAYS);

        clearBtn.setOnAction(e -> {
            logArea.clear();
            logCount = 0;
            logCountLabel.setText("日志条数: 0");
        });

        exportBtn.setOnAction(e -> exportLog());

        openLogDirBtn.setOnAction(e -> openLogDirectory());

        levelCombo.setOnAction(e -> filterLogs());
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

                appendLog(logLine, level);
            }

            @Override
            public void flush() {
                flushLogs();
            }

            @Override
            public void close() throws SecurityException {
                shutdown();
            }
        };

        rootLogger.addHandler(handler);
    }

    private void appendLog(String logLine, String level) {
        Platform.runLater(() -> {
            String filterLevel = levelCombo.getValue();
            if ("全部".equals(filterLevel) || level.equals(filterLevel) || shouldShow(level, filterLevel)) {
                logArea.appendText(logLine);
                logCount++;
                logCountLabel.setText("日志条数: " + logCount);

                limitLogLines();
            }
        });

        synchronized (logLock) {
            pendingLogs.append(logLine);
        }
    }

    private boolean shouldShow(String level, String filterLevel) {
        if ("ERROR".equals(filterLevel)) {
            return "SEVERE".equals(level) || "ERROR".equals(level);
        }
        if ("WARN".equals(filterLevel)) {
            return "WARNING".equals(level) || "WARN".equals(level) || "SEVERE".equals(level) || "ERROR".equals(level);
        }
        return true;
    }

    private void limitLogLines() {
        String text = logArea.getText();
        String[] lines = text.split("\n");
        if (lines.length > MAX_DISPLAY_LINES) {
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - MAX_DISPLAY_LINES; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
            logArea.setText(sb.toString());
        }
    }

    private void flushLogs() {
        String logsToWrite;
        synchronized (logLock) {
            if (pendingLogs.length() == 0) {
                return;
            }
            logsToWrite = pendingLogs.toString();
            pendingLogs.setLength(0);
        }

        logWriter.submit(() -> {
            try {
                writeLogToFile(logsToWrite);
            } catch (Exception e) {
                System.err.println("写入日志文件失败: " + e.getMessage());
            }
        });
    }

    private void writeLogToFile(String content) throws IOException {
        Path logPath = Paths.get(LOG_FILE);
        Files.createDirectories(logPath.getParent());

        if (Files.exists(logPath) && Files.size(logPath) > MAX_LOG_SIZE) {
            rotateLogFile();
        }

        try (BufferedWriter writer = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(content);
        }
    }

    private void rotateLogFile() throws IOException {
        Path logPath = Paths.get(LOG_FILE);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backupPath = Paths.get(LOG_DIR, "sync_" + timestamp + ".log");

        Files.move(logPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

        cleanOldLogs();
    }

    private void cleanOldLogs() throws IOException {
        Path logDir = Paths.get(LOG_DIR);
        if (!Files.exists(logDir)) return;

        File[] files = logDir.toFile().listFiles((dir, name) -> name.startsWith("sync_") && name.endsWith(".log"));
        if (files != null && files.length > 10) {
            java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - 10; i++) {
                files[i].delete();
            }
        }
    }

    private void loadHistoryLogs() {
        try {
            Path logPath = Paths.get(LOG_FILE);
            if (!Files.exists(logPath)) {
                return;
            }

            java.util.List<String> allLines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            if (allLines.isEmpty()) {
                return;
            }

            int start = Math.max(0, allLines.size() - MAX_DISPLAY_LINES);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < allLines.size(); i++) {
                sb.append(allLines.get(i)).append("\n");
            }

            final int count = Math.min(allLines.size(), MAX_DISPLAY_LINES);
            final String history = sb.toString();

            logArea.setText(history);
            logCount = count;
            logCountLabel.setText("日志条数: " + count);
        } catch (Exception e) {
            System.err.println("加载历史日志失败: " + e.getMessage());
        }
    }

    private void startLogFlushTask() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-flush-task");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(this::flushLogs, 5, 5, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void filterLogs() {
        String filterLevel = levelCombo.getValue();

        logWriter.submit(() -> {
            try {
                Path logPath = Paths.get(LOG_FILE);
                if (!Files.exists(logPath)) {
                    return;
                }

                StringBuilder sb = new StringBuilder();
                int count = 0;

                try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
                    String line;
                    java.util.List<String> lines = new java.util.ArrayList<>();

                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }

                    int start = Math.max(0, lines.size() - MAX_DISPLAY_LINES);
                    for (int i = start; i < lines.size(); i++) {
                        String l = lines.get(i);
                        if ("全部".equals(filterLevel) || matchesFilter(l, filterLevel)) {
                            sb.append(l).append("\n");
                            count++;
                        }
                    }
                }

                final int finalCount = count;
                final String filtered = sb.toString();

                Platform.runLater(() -> {
                    logArea.setText(filtered);
                    logCount = finalCount;
                    logCountLabel.setText("日志条数: " + finalCount);
                });
            } catch (Exception e) {
                System.err.println("过滤日志失败: " + e.getMessage());
            }
        });
    }

    private boolean matchesFilter(String line, String filterLevel) {
        if (line.contains("[INFO]") && "INFO".equals(filterLevel)) return true;
        if (line.contains("[WARN]") || line.contains("[WARNING]") && "WARN".equals(filterLevel)) return true;
        if ((line.contains("[ERROR]") || line.contains("[SEVERE]")) && "ERROR".equals(filterLevel)) return true;
        if ("全部".equals(filterLevel)) return true;
        return false;
    }

    private void exportLog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出日志");
        chooser.setInitialFileName("sync_log_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("文本文件", "*.txt"));

        Stage stage = (Stage) logArea.getScene().getWindow();
        var file = chooser.showSaveDialog(stage);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), logArea.getText());
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("导出成功");
                alert.setHeaderText(null);
                alert.setContentText("日志已导出到: " + file.getAbsolutePath());
                alert.showAndWait();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("导出失败");
                alert.setHeaderText(null);
                alert.setContentText("导出失败: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    private void openLogDirectory() {
        try {
            Path logDir = Paths.get(LOG_DIR);
            Files.createDirectories(logDir);

            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("mac")) {
                pb = new ProcessBuilder("open", logDir.toString());
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", logDir.toString());
            } else {
                pb = new ProcessBuilder("xdg-open", logDir.toString());
            }
            pb.start();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("打开失败");
            alert.setHeaderText(null);
            alert.setContentText("无法打开日志目录: " + e.getMessage());
            alert.showAndWait();
        }
    }

    public void shutdown() {
        flushLogs();
        logWriter.shutdown();
    }

    public VBox getContent() {
        return root;
    }
}
