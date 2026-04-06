package org.example.gui.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import org.example.gui.model.*;
import org.example.gui.service.ConfigManager;
import org.example.gui.service.ConnectionTestService;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FieldMappingPage {

    private final ConfigManager configManager;
    private final VBox root;
    private final ComboBox<SyncTaskConfig> taskCombo;
    private final HBox breadcrumbBar;
    private final TableView<ColumnMapping> mappingTable;
    private final Label statusLabel;
    private final Button loadColumnsBtn;
    private final Button saveMappingBtn;
    private final Button quickFillBtn;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private List<SyncTaskConfig> tasks;
    private List<DataSourceConfig> dataSources;
    private List<JdyAppConfig> jdyApps;
    private List<ColumnMapping> columnMappings;
    private SyncTaskConfig selectedTask;
    private final Map<String, List<ColumnMapping>> mappingCache = new HashMap<>();

    private BiConsumer<String, String> navigator;

    public FieldMappingPage(ConfigManager configManager) {
        this.configManager = configManager;
        this.tasks = new ArrayList<>(configManager.loadSyncTasks());
        this.dataSources = new ArrayList<>(configManager.loadDataSources());
        this.jdyApps = new ArrayList<>(configManager.loadJdyApps());
        this.root = new VBox(15);
        this.root.setPadding(new Insets(25));

        this.taskCombo = new ComboBox<>();
        this.taskCombo.getItems().addAll(tasks);
        this.taskCombo.setPromptText("选择同步任务");
        this.taskCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SyncTaskConfig item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    DataSourceConfig ds = dataSources.stream()
                            .filter(d -> d.getId().equals(item.getDataSourceId()))
                            .findFirst().orElse(null);
                    String dsName = ds != null ? ds.getName() : "?";
                    setText(item.getName() + "  [" + dsName + "]");
                }
            }
        });
        this.taskCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(SyncTaskConfig item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    DataSourceConfig ds = dataSources.stream()
                            .filter(d -> d.getId().equals(item.getDataSourceId()))
                            .findFirst().orElse(null);
                    String dsName = ds != null ? ds.getName() : "?";
                    setText(item.getName() + "  [" + dsName + "]");
                }
            }
        });

        this.breadcrumbBar = new HBox(10);
        this.breadcrumbBar.setAlignment(Pos.CENTER_LEFT);
        this.breadcrumbBar.getStyleClass().add("breadcrumb-bar");
        this.breadcrumbBar.setVisible(false);

        this.mappingTable = new TableView<>();
        this.statusLabel = new Label();
        this.statusLabel.getStyleClass().add("status-label");

        this.loadColumnsBtn = new Button("加载表字段");
        this.loadColumnsBtn.getStyleClass().add("btn-secondary");
        this.loadColumnsBtn.setDisable(true);

        this.saveMappingBtn = new Button("保存映射");
        this.saveMappingBtn.getStyleClass().add("btn-primary");
        this.saveMappingBtn.setDisable(true);

        this.quickFillBtn = new Button("快速递增填充");
        this.quickFillBtn.getStyleClass().add("btn-secondary");
        this.quickFillBtn.setTooltip(new Tooltip("从第一个已填的简道云字段ID开始，自动递增填充下方空白行"));

        setupLayout();
        setupListeners();
    }

    public void setNavigator(BiConsumer<String, String> navigator) {
        this.navigator = navigator;
    }

    public void refreshData() {
        tasks = new ArrayList<>(configManager.loadSyncTasks());
        dataSources = new ArrayList<>(configManager.loadDataSources());
        jdyApps = new ArrayList<>(configManager.loadJdyApps());

        taskCombo.getItems().clear();
        taskCombo.getItems().addAll(tasks);
    }

    private void setupLayout() {
        Label titleLabel = new Label("字段映射配置");
        titleLabel.getStyleClass().add("page-title");

        Label descLabel = new Label("选择同步任务后，自动加载关联数据源的表字段。在右侧输入框中粘贴简道云字段ID（如 _widget_1742010071756），留空表示不推送该字段。");
        descLabel.getStyleClass().add("description-label");
        descLabel.setWrapText(true);

        HBox selectorBar = new HBox(10);
        selectorBar.setAlignment(Pos.CENTER_LEFT);
        Label taskLabel = new Label("同步任务:");
        selectorBar.getChildren().addAll(taskLabel, taskCombo);

        setupMappingTable();

        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.getChildren().addAll(saveMappingBtn, quickFillBtn, statusLabel);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bottomBar.getChildren().add(0, spacer);

        root.getChildren().addAll(titleLabel, descLabel, selectorBar, breadcrumbBar, mappingTable, bottomBar);
    }

    private void setupMappingTable() {
        TableColumn<ColumnMapping, String> colName = new TableColumn<>("数据库字段");
        colName.setCellValueFactory(cell -> cell.getValue().columnNameProperty());
        colName.setPrefWidth(180);
        colName.setSortable(false);

        TableColumn<ColumnMapping, String> colType = new TableColumn<>("数据类型");
        colType.setCellValueFactory(cell -> cell.getValue().columnTypeProperty());
        colType.setPrefWidth(120);
        colType.setSortable(false);

        TableColumn<ColumnMapping, String> colComment = new TableColumn<>("注释");
        colComment.setCellValueFactory(cell -> cell.getValue().columnCommentProperty());
        colComment.setPrefWidth(200);
        colComment.setSortable(false);

        TableColumn<ColumnMapping, String> colWidget = new TableColumn<>("简道云字段ID");
        colWidget.setCellValueFactory(cell -> cell.getValue().widgetIdProperty());
        colWidget.setPrefWidth(300);
        colWidget.setSortable(false);
        colWidget.setCellFactory(column -> {
            TextField cellField = new TextField();
            cellField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    int idx = cellField.getProperties() != null && cellField.getProperties().containsKey("rowIndex")
                            ? (int) cellField.getProperties().get("rowIndex") : -1;
                    if (idx >= 0 && idx < columnMappings.size()) {
                        columnMappings.get(idx).setWidgetId(cellField.getText() != null ? cellField.getText().trim() : "");
                    }
                }
            });
            cellField.textProperty().addListener((obs, oldVal, newVal) -> {
                int idx = cellField.getProperties() != null && cellField.getProperties().containsKey("rowIndex")
                        ? (int) cellField.getProperties().get("rowIndex") : -1;
                if (idx >= 0 && idx < columnMappings.size()) {
                    columnMappings.get(idx).setWidgetId(newVal != null ? newVal.trim() : "");
                }
            });

            return new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getTableRow() == null) {
                        setGraphic(null);
                    } else {
                        int row = getTableRow().getIndex();
                        cellField.getProperties().put("rowIndex", row);
                        cellField.setText(item != null ? item : "");
                        setGraphic(cellField);
                    }
                }
            };
        });

        mappingTable.getColumns().addAll(colName, colType, colComment, colWidget);
        mappingTable.setEditable(true);
        mappingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        mappingTable.getSelectionModel().setCellSelectionEnabled(false);
    }

    private void setupListeners() {
        taskCombo.setOnAction(e -> {
            SyncTaskConfig task = taskCombo.getValue();
            if (task != null) {
                selectedTask = task;
                loadColumnsForTask(task);
            } else {
                selectedTask = null;
                breadcrumbBar.setVisible(false);
                columnMappings = null;
                mappingTable.getItems().clear();
                loadColumnsBtn.setDisable(true);
                saveMappingBtn.setDisable(true);
            }
        });

        loadColumnsBtn.setOnAction(e -> loadColumns());
        saveMappingBtn.setOnAction(e -> saveMapping());
        quickFillBtn.setOnAction(e -> quickFill());
    }

    private void updateBreadcrumb() {
        breadcrumbBar.getChildren().clear();

        if (selectedTask == null) {
            breadcrumbBar.setVisible(false);
            return;
        }

        breadcrumbBar.setVisible(true);

        DataSourceConfig ds = dataSources.stream()
                .filter(d -> d.getId().equals(selectedTask.getDataSourceId()))
                .findFirst().orElse(null);

        JdyAppConfig app = jdyApps.stream()
                .filter(a -> a.getId().equals(selectedTask.getJdyAppId()))
                .findFirst().orElse(null);

        if (ds != null) {
            Hyperlink dsLink = createBreadcrumbLink("📁 " + ds.getName());
            dsLink.setOnAction(e -> {
                if (navigator != null) navigator.accept("dataSource", ds.getId());
            });
            breadcrumbBar.getChildren().add(dsLink);
        }

        addBreadcrumbArrow();

        if (app != null) {
            Hyperlink appLink = createBreadcrumbLink("☁️ " + app.getName());
            appLink.setOnAction(e -> {
                if (navigator != null) navigator.accept("jdyConfig", app.getId());
            });
            breadcrumbBar.getChildren().add(appLink);
        }

        addBreadcrumbArrow();

        Hyperlink taskLink = createBreadcrumbLink("📋 " + selectedTask.getName());
        taskLink.setOnAction(e -> {
            if (navigator != null) navigator.accept("syncTask", selectedTask.getId());
        });
        breadcrumbBar.getChildren().add(taskLink);

        addBreadcrumbArrow();

        Label tableLabel = new Label("📄 " + selectedTask.getSourceTable());
        tableLabel.getStyleClass().add("breadcrumb-label");
        breadcrumbBar.getChildren().add(tableLabel);
    }

    private void addBreadcrumbArrow() {
        Label arrow = new Label("→");
        arrow.getStyleClass().add("breadcrumb-arrow");
        breadcrumbBar.getChildren().add(arrow);
    }

    private Hyperlink createBreadcrumbLink(String text) {
        Hyperlink link = new Hyperlink(text);
        link.setPadding(new Insets(0));
        link.getStyleClass().add("breadcrumb-link");
        link.setUnderline(false);
        link.setCursor(Cursor.HAND);
        return link;
    }

    private void loadColumnsForTask(SyncTaskConfig task) {
        DataSourceConfig ds = dataSources.stream()
                .filter(d -> d.getId().equals(task.getDataSourceId()))
                .findFirst().orElse(null);

        if (ds == null) {
            showStatus(false, "任务关联的数据源不存在");
            loadColumnsBtn.setDisable(true);
            saveMappingBtn.setDisable(true);
            updateBreadcrumb();
            return;
        }

        updateBreadcrumb();
        loadColumnsBtn.setDisable(false);
        saveMappingBtn.setDisable(false);

        String cacheKey = ds.getId() + "::" + task.getSourceTable();
        if (mappingCache.containsKey(cacheKey)) {
            columnMappings = mappingCache.get(cacheKey);
            mappingTable.getItems().clear();
            mappingTable.getItems().addAll(columnMappings);
            loadExistingMapping(task);
            showStatus(true, "已从缓存加载 " + columnMappings.size() + " 个字段");
            return;
        }

        loadColumns();
    }

    private void loadExistingMapping(SyncTaskConfig task) {
        if (task.getFieldMapping() != null && columnMappings != null) {
            Map<String, String> fm = task.getFieldMapping();
            for (ColumnMapping cm : columnMappings) {
                String widgetId = fm.get(cm.getColumnName());
                if (widgetId != null) {
                    cm.setWidgetId(widgetId);
                }
            }
            mappingTable.refresh();
        }
    }

    private void loadColumns() {
        if (selectedTask == null) return;

        DataSourceConfig ds = dataSources.stream()
                .filter(d -> d.getId().equals(selectedTask.getDataSourceId()))
                .findFirst().orElse(null);

        String tableName = selectedTask.getSourceTable();
        if (ds == null || tableName == null) return;

        String cacheKey = ds.getId() + "::" + tableName;
        if (mappingCache.containsKey(cacheKey)) {
            columnMappings = mappingCache.get(cacheKey);
            mappingTable.getItems().clear();
            mappingTable.getItems().addAll(columnMappings);
            loadExistingMapping(selectedTask);
            showStatus(true, "已从缓存加载 " + columnMappings.size() + " 个字段");
            return;
        }

        statusLabel.setText("正在加载字段...");
        statusLabel.getStyleClass().removeAll("status-success", "status-error");

        executor.submit(() -> {
            List<ColumnDetail> details = new ArrayList<>();
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(ds.getJdbcUrl(), ds.getUsername(), ds.getPassword())) {
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(ds.getDatabase(), null, tableName, "%")) {
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME");
                        String colType = rs.getString("TYPE_NAME");
                        String colComment = rs.getString("REMARKS");
                        details.add(new ColumnDetail(
                                colName != null ? colName : "",
                                colType != null ? colType : "",
                                colComment != null ? colComment : ""
                        ));
                    }
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        showStatus(false, "加载字段失败: " + e.getMessage()));
                return;
            }

            List<ColumnMapping> mappings = new ArrayList<>();
            for (ColumnDetail d : details) {
                mappings.add(new ColumnMapping(d.name, d.type, d.comment, ""));
            }

            javafx.application.Platform.runLater(() -> {
                columnMappings = mappings;
                mappingCache.put(cacheKey, new ArrayList<>(mappings));
                mappingTable.getItems().clear();
                mappingTable.getItems().addAll(mappings);
                loadExistingMapping(selectedTask);
                showStatus(true, "已加载 " + mappings.size() + " 个字段");
            });
        });
    }

    private void saveMapping() {
        if (selectedTask == null) {
            showStatus(false, "请先选择同步任务");
            return;
        }
        if (columnMappings == null || columnMappings.isEmpty()) {
            showStatus(false, "请先加载表字段");
            return;
        }

        commitCurrentEdit();

        Map<String, String> mapping = new LinkedHashMap<>();
        for (ColumnMapping cm : columnMappings) {
            String wid = cm.getWidgetId() != null ? cm.getWidgetId().trim() : "";
            if (!wid.isEmpty()) {
                mapping.put(cm.getColumnName(), wid);
            }
        }

        String cacheKey = getCacheKey();
        if (cacheKey != null) {
            mappingCache.put(cacheKey, new ArrayList<>(columnMappings));
        }

        List<SyncTaskConfig> taskList = configManager.loadSyncTasks();
        for (int i = 0; i < taskList.size(); i++) {
            if (taskList.get(i).getId().equals(selectedTask.getId())) {
                selectedTask.setFieldMapping(mapping);
                taskList.set(i, selectedTask);
                break;
            }
        }
        configManager.saveSyncTasks(taskList);

        long mappedCount = mapping.size();
        showStatus(true, "已保存 " + mappedCount + " 个字段映射到任务: " + selectedTask.getName());
    }

    private void quickFill() {
        if (columnMappings == null || columnMappings.isEmpty()) {
            showStatus(false, "请先加载表字段");
            return;
        }

        commitCurrentEdit();

        int lastFilledIndex = -1;
        String lastWidgetId = null;
        String prefix = null;
        long lastNum = -1;

        Pattern pattern = Pattern.compile("^(.+?)(\\d+)$");

        for (int i = columnMappings.size() - 1; i >= 0; i--) {
            String wid = columnMappings.get(i).getWidgetId();
            if (wid != null && !wid.trim().isEmpty()) {
                lastFilledIndex = i;
                lastWidgetId = wid.trim();
                Matcher m = pattern.matcher(lastWidgetId);
                if (m.find()) {
                    prefix = m.group(1);
                    try {
                        lastNum = Long.parseLong(m.group(2));
                    } catch (NumberFormatException e) {
                        lastNum = -1;
                    }
                }
                break;
            }
        }

        if (lastFilledIndex == -1 || prefix == null || lastNum == -1) {
            showStatus(false, "请先至少填写一个简道云字段ID（如 _widget_1742010071756）");
            return;
        }

        int fillCount = 0;
        long currentNum = lastNum;
        for (int i = lastFilledIndex + 1; i < columnMappings.size(); i++) {
            ColumnMapping cm = columnMappings.get(i);
            if (cm.getWidgetId() == null || cm.getWidgetId().trim().isEmpty()) {
                currentNum++;
                cm.setWidgetId(prefix + currentNum);
                fillCount++;
            }
        }

        if (fillCount > 0) {
            mappingTable.refresh();
            String cacheKey = getCacheKey();
            if (cacheKey != null) {
                mappingCache.put(cacheKey, new ArrayList<>(columnMappings));
            }
            showStatus(true, "已自动填充 " + fillCount + " 个字段");
        } else {
            showStatus(false, "下方没有空白行需要填充");
        }
    }

    private void commitCurrentEdit() {
        mappingTable.refresh();
    }

    private String getCacheKey() {
        if (selectedTask == null) return null;
        DataSourceConfig ds = dataSources.stream()
                .filter(d -> d.getId().equals(selectedTask.getDataSourceId()))
                .findFirst().orElse(null);
        if (ds == null || selectedTask.getSourceTable() == null) return null;
        return ds.getId() + "::" + selectedTask.getSourceTable();
    }

    private void showStatus(boolean success, String message) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-success", "status-error");
        statusLabel.getStyleClass().add(success ? "status-success" : "status-error");
    }

    private static class ColumnDetail {
        String name;
        String type;
        String comment;
        ColumnDetail(String name, String type, String comment) {
            this.name = name;
            this.type = type;
            this.comment = comment;
        }
    }

    public VBox getContent() {
        return root;
    }
}
