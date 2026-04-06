package org.example.gui.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.gui.model.DataSourceConfig;
import org.example.gui.model.JdyAppConfig;
import org.example.gui.model.SyncProgress;
import org.example.gui.model.SyncTaskConfig;
import org.example.gui.service.ConfigManager;
import org.example.gui.service.ConnectionTestService;
import org.example.gui.service.SyncEngine;
import org.example.gui.service.TaskScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class SyncTaskPage {

    private final ConfigManager configManager;
    private final TaskScheduler taskScheduler;
    private final VBox root;
    private final TableView<SyncTaskConfig> taskTable;
    private final TextField nameField;
    private final ComboBox<DataSourceConfig> dataSourceCombo;
    private final ComboBox<JdyAppConfig> jdyAppCombo;
    private final TextField sourceTableField;
    private final Button loadTablesBtn;
    private final TextField entryIdField;
    private final ComboBox<String> incrementModeCombo;
    private final TextField incrementFieldField;
    private final Spinner<Integer> intervalSpinner;
    private final Spinner<Integer> batchSizeSpinner;
    private final Spinner<Integer> retrySpinner;
    private final CheckBox enabledCheck;
    private final Label statusLabel;
    private final HBox breadcrumbBar;
    private final HBox watermarkBar;
    private final TextField watermarkField;
    private final Button saveWatermarkBtn;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private List<SyncTaskConfig> tasks;
    private List<DataSourceConfig> dataSources;
    private List<JdyAppConfig> jdyApps;
    private SyncTaskConfig selectedTask;

    private BiConsumer<String, String> navigator;

    public SyncTaskPage(ConfigManager configManager, TaskScheduler taskScheduler) {
        this.configManager = configManager;
        this.taskScheduler = taskScheduler;
        this.tasks = new ArrayList<>(configManager.loadSyncTasks());
        this.dataSources = new ArrayList<>(configManager.loadDataSources());
        this.jdyApps = new ArrayList<>(configManager.loadJdyApps());
        this.root = new VBox(15);
        this.root.setPadding(new Insets(25));

        this.taskTable = new TableView<>();
        this.nameField = new TextField();
        this.nameField.setPromptText("任务名称");
        this.dataSourceCombo = new ComboBox<>();
        this.dataSourceCombo.getItems().addAll(dataSources);
        this.dataSourceCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DataSourceConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getName());
            }
        });
        this.dataSourceCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DataSourceConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getName());
            }
        });

        this.jdyAppCombo = new ComboBox<>();
        this.jdyAppCombo.getItems().addAll(jdyApps);
        this.jdyAppCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(JdyAppConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getName());
            }
        });
        this.jdyAppCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(JdyAppConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getName());
            }
        });

        this.sourceTableField = new TextField();
        this.sourceTableField.setPromptText("MySQL 表名");
        this.loadTablesBtn = new Button("从数据源加载");
        this.loadTablesBtn.getStyleClass().add("btn-secondary");
        this.entryIdField = new TextField();
        this.entryIdField.setPromptText("简道云表单ID (Entry ID)");
        this.incrementModeCombo = new ComboBox<>();
        this.incrementModeCombo.getItems().addAll("自增ID", "时间戳字段");
        this.incrementModeCombo.setValue("自增ID");
        this.incrementFieldField = new TextField();
        this.incrementFieldField.setPromptText("增量字段名，如 id 或 update_time");
        this.intervalSpinner = new Spinner<>(1, 1440, 5);
        this.intervalSpinner.setEditable(true);
        this.batchSizeSpinner = new Spinner<>(1, 500, 50);
        this.batchSizeSpinner.setEditable(true);
        this.retrySpinner = new Spinner<>(1, 50, 3);
        this.retrySpinner.setEditable(true);
        this.enabledCheck = new CheckBox("启用");
        this.enabledCheck.setSelected(true);
        this.statusLabel = new Label();
        this.statusLabel.getStyleClass().add("status-label");
        this.breadcrumbBar = new HBox(10);
        this.breadcrumbBar.setAlignment(Pos.CENTER_LEFT);
        this.breadcrumbBar.getStyleClass().add("breadcrumb-bar");
        this.breadcrumbBar.setVisible(false);

        this.watermarkBar = new HBox(10);
        this.watermarkBar.setAlignment(Pos.CENTER_LEFT);
        this.watermarkBar.getStyleClass().add("watermark-bar");
        this.watermarkBar.setVisible(false);

        this.watermarkField = new TextField();
        this.watermarkField.setPromptText("上次同步的水印值（最大ID或时间戳）");
        this.watermarkField.setPrefWidth(200);

        this.saveWatermarkBtn = new Button("更新水印");
        this.saveWatermarkBtn.getStyleClass().add("btn-secondary");
        this.saveWatermarkBtn.setOnAction(e -> saveWatermark());

        setupLayout();
        setupTable();
        refreshList();
    }

    public void setNavigator(BiConsumer<String, String> navigator) {
        this.navigator = navigator;
    }

    public void refreshData() {
        dataSources = new ArrayList<>(configManager.loadDataSources());
        jdyApps = new ArrayList<>(configManager.loadJdyApps());
        tasks = new ArrayList<>(configManager.loadSyncTasks());

        dataSourceCombo.getItems().clear();
        dataSourceCombo.getItems().addAll(dataSources);

        jdyAppCombo.getItems().clear();
        jdyAppCombo.getItems().addAll(jdyApps);

        refreshList();
    }

    public void selectItem(String id) {
        tasks = new ArrayList<>(configManager.loadSyncTasks());
        dataSources = new ArrayList<>(configManager.loadDataSources());
        jdyApps = new ArrayList<>(configManager.loadJdyApps());
        refreshList();
        tasks.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .ifPresent(task -> taskTable.getSelectionModel().select(task));
    }

    private void setupLayout() {
        Label titleLabel = new Label("同步任务管理");
        titleLabel.getStyleClass().add("page-title");

        HBox mainContent = new HBox(20);
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        VBox listPanel = new VBox(10);
        listPanel.setPrefWidth(400);
        Label listTitle = new Label("同步任务列表");
        listTitle.getStyleClass().add("section-title");
        VBox.setVgrow(taskTable, Priority.ALWAYS);
        listPanel.getChildren().addAll(listTitle, taskTable);

        VBox formPanel = new VBox(15);
        HBox.setHgrow(formPanel, Priority.ALWAYS);
        Label formTitle = new Label("任务配置");
        formTitle.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);

        grid.add(new Label("任务名称"), 0, 0);
        grid.add(nameField, 1, 0);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        grid.add(new Label("数据源"), 0, 1);
        HBox dsBox = new HBox(10);
        dsBox.getChildren().addAll(dataSourceCombo, loadTablesBtn);
        grid.add(dsBox, 1, 1);
        GridPane.setHgrow(dataSourceCombo, Priority.ALWAYS);

        grid.add(new Label("简道云应用"), 0, 2);
        grid.add(jdyAppCombo, 1, 2);
        GridPane.setHgrow(jdyAppCombo, Priority.ALWAYS);

        grid.add(new Label("源表名"), 0, 3);
        grid.add(sourceTableField, 1, 3);
        GridPane.setHgrow(sourceTableField, Priority.ALWAYS);

        grid.add(new Label("表单 ID (Entry ID)"), 0, 4);
        grid.add(entryIdField, 1, 4);
        GridPane.setHgrow(entryIdField, Priority.ALWAYS);

        grid.add(new Separator(), 0, 5);
        GridPane.setColumnSpan(grid.getChildren().get(grid.getChildren().size() - 1), 2);

        grid.add(new Label("增量同步方式"), 0, 6);
        grid.add(incrementModeCombo, 1, 6);

        grid.add(new Label("增量字段名"), 0, 7);
        grid.add(incrementFieldField, 1, 7);
        GridPane.setHgrow(incrementFieldField, Priority.ALWAYS);

        grid.add(new Separator(), 0, 8);
        GridPane.setColumnSpan(grid.getChildren().get(grid.getChildren().size() - 1), 2);

        grid.add(new Label("同步间隔(分钟)"), 0, 9);
        grid.add(intervalSpinner, 1, 9);

        grid.add(new Label("批量大小"), 0, 10);
        grid.add(batchSizeSpinner, 1, 10);

        grid.add(new Label("最大重试次数"), 0, 11);
        grid.add(retrySpinner, 1, 11);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(120);
        col1.setMaxWidth(140);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        Button newBtn = new Button("新建");
        newBtn.getStyleClass().add("btn-primary");
        newBtn.setOnAction(e -> clearForm());

        Button saveBtn = new Button("保存");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> saveTask());

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> deleteTask());

        Button runBtn = new Button("立即执行");
        runBtn.getStyleClass().add("btn-secondary");
        runBtn.setOnAction(e -> executeTaskNow());

        Button refreshBtn = new Button("刷新调度");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setOnAction(e -> {
            taskScheduler.refresh();
            showStatus(true, "调度器已刷新");
        });

        buttonBar.getChildren().addAll(newBtn, saveBtn, deleteBtn, runBtn, refreshBtn);

        formPanel.getChildren().addAll(formTitle, breadcrumbBar, watermarkBar, enabledCheck, grid, buttonBar, statusLabel);

        mainContent.getChildren().addAll(listPanel, formPanel);
        HBox.setHgrow(formPanel, Priority.ALWAYS);

        root.getChildren().addAll(titleLabel, mainContent);

        setupListeners();
    }

    private void setupListeners() {
        loadTablesBtn.setOnAction(e -> loadTablesFromDataSource());

        dataSourceCombo.setOnAction(e -> {
            DataSourceConfig ds = dataSourceCombo.getValue();
            if (ds != null) {
                sourceTableField.clear();
            }
        });

        taskTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                selectedTask = newVal;
                loadTaskToForm(newVal);
            }
        });
    }

    private void updateBreadcrumb() {
        breadcrumbBar.getChildren().clear();

        DataSourceConfig ds = dataSourceCombo.getValue();
        JdyAppConfig app = jdyAppCombo.getValue();

        if (ds == null && app == null) {
            breadcrumbBar.setVisible(false);
            watermarkBar.setVisible(false);
            return;
        }

        breadcrumbBar.setVisible(true);

        if (ds != null) {
            Hyperlink dsLink = createBreadcrumbLink("📁 " + ds.getName());
            dsLink.setOnAction(e -> {
                if (navigator != null) navigator.accept("dataSource", ds.getId());
            });
            breadcrumbBar.getChildren().add(dsLink);
        }

        if (ds != null && app != null) {
            Label arrow = new Label("→");
            arrow.getStyleClass().add("breadcrumb-arrow");
            breadcrumbBar.getChildren().add(arrow);
        }

        if (app != null) {
            Hyperlink appLink = createBreadcrumbLink("☁️ " + app.getName());
            appLink.setOnAction(e -> {
                if (navigator != null) navigator.accept("jdyConfig", app.getId());
            });
            breadcrumbBar.getChildren().add(appLink);
        }

        updateWatermarkBar();
    }

    private void updateWatermarkBar() {
        watermarkBar.getChildren().clear();

        if (selectedTask == null) {
            watermarkBar.setVisible(false);
            return;
        }

        watermarkBar.setVisible(true);

        SyncProgress progress = configManager.loadSyncProgress();
        String lastSyncId = progress.getLastSyncId(selectedTask.getId());

        Label watermarkLabel = new Label("💧 水印:");
        watermarkLabel.getStyleClass().add("watermark-label");

        watermarkField.setText(lastSyncId);

        watermarkBar.getChildren().addAll(watermarkLabel, watermarkField, saveWatermarkBtn);
    }

    private void saveWatermark() {
        if (selectedTask == null) {
            showStatus(false, "请先选择同步任务");
            return;
        }

        String watermark = watermarkField.getText().trim();
        if (watermark.isEmpty()) {
            watermark = "0";
        }

        SyncProgress progress = configManager.loadSyncProgress();
        progress.setLastSyncId(selectedTask.getId(), watermark);
        configManager.saveSyncProgress(progress);
        showStatus(true, "水印已更新为: " + watermark);
    }

    private Hyperlink createBreadcrumbLink(String text) {
        Hyperlink link = new Hyperlink(text);
        link.setPadding(new Insets(0));
        link.getStyleClass().add("breadcrumb-link");
        link.setUnderline(false);
        link.setCursor(Cursor.HAND);
        return link;
    }

    private void loadTablesFromDataSource() {
        DataSourceConfig ds = dataSourceCombo.getValue();
        if (ds == null) {
            showStatus(false, "请先选择数据源");
            return;
        }

        statusLabel.setText("正在加载表列表...");
        executor.submit(() -> {
            List<String> tables = ConnectionTestService.fetchTableList(ds);
            javafx.application.Platform.runLater(() -> {
                if (tables.isEmpty()) {
                    showStatus(false, "无法连接数据源或无表");
                } else {
                    ChoiceDialog<String> dialog = new ChoiceDialog<>(tables.get(0), tables);
                    dialog.setTitle("选择表");
                    dialog.setHeaderText("选择要同步的表");
                    dialog.setContentText("表名:");
                    dialog.showAndWait().ifPresent(tableName -> {
                        sourceTableField.setText(tableName);
                        showStatus(true, "已选择表: " + tableName);
                    });
                }
            });
        });
    }

    private void setupTable() {
        TableColumn<SyncTaskConfig, String> nameCol = new TableColumn<>("任务名称");
        nameCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getName()));
        nameCol.setPrefWidth(100);

        TableColumn<SyncTaskConfig, String> dsCol = new TableColumn<>("数据源");
        dsCol.setCellValueFactory(cell -> {
            String dsName = "";
            DataSourceConfig ds = dataSources.stream()
                    .filter(d -> d.getId().equals(cell.getValue().getDataSourceId()))
                    .findFirst().orElse(null);
            if (ds != null) dsName = ds.getName();
            return new javafx.beans.property.SimpleStringProperty(dsName);
        });
        dsCol.setPrefWidth(100);

        TableColumn<SyncTaskConfig, String> appCol = new TableColumn<>("简道云应用");
        appCol.setCellValueFactory(cell -> {
            String appName = "";
            JdyAppConfig app = jdyApps.stream()
                    .filter(a -> a.getId().equals(cell.getValue().getJdyAppId()))
                    .findFirst().orElse(null);
            if (app != null) appName = app.getName();
            return new javafx.beans.property.SimpleStringProperty(appName);
        });
        appCol.setPrefWidth(100);

        TableColumn<SyncTaskConfig, String> tableCol = new TableColumn<>("源表");
        tableCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getSourceTable()));
        tableCol.setPrefWidth(90);

        TableColumn<SyncTaskConfig, String> entryCol = new TableColumn<>("表单ID");
        entryCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getEntryId()));
        entryCol.setPrefWidth(70);

        TableColumn<SyncTaskConfig, String> intervalCol = new TableColumn<>("间隔(分)");
        intervalCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(String.valueOf(cell.getValue().getSyncIntervalMinutes())));
        intervalCol.setPrefWidth(60);

        TableColumn<SyncTaskConfig, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().isEnabled() ? "启用" : "禁用"));
        statusCol.setPrefWidth(50);

        taskTable.getColumns().addAll(nameCol, dsCol, appCol, tableCol, entryCol, intervalCol, statusCol);
    }

    private void loadTaskToForm(SyncTaskConfig task) {
        selectedTask = task;
        nameField.setText(task.getName());

        DataSourceConfig ds = dataSources.stream()
                .filter(d -> d.getId().equals(task.getDataSourceId()))
                .findFirst().orElse(null);
        dataSourceCombo.setValue(ds);

        JdyAppConfig app = jdyApps.stream()
                .filter(a -> a.getId().equals(task.getJdyAppId()))
                .findFirst().orElse(null);
        jdyAppCombo.setValue(app);

        sourceTableField.setText(task.getSourceTable());
        entryIdField.setText(task.getEntryId());
        incrementModeCombo.setValue("id".equals(task.getIncrementMode()) ? "自增ID" : "时间戳字段");
        incrementFieldField.setText(task.getIncrementField());
        intervalSpinner.getValueFactory().setValue(task.getSyncIntervalMinutes());
        batchSizeSpinner.getValueFactory().setValue(task.getMaxBatchSize());
        retrySpinner.getValueFactory().setValue(task.getMaxRetry());
        enabledCheck.setSelected(task.isEnabled());
        statusLabel.setText("");
        updateBreadcrumb();
    }

    private void clearForm() {
        selectedTask = null;
        nameField.clear();
        dataSourceCombo.getSelectionModel().clearSelection();
        jdyAppCombo.getSelectionModel().clearSelection();
        sourceTableField.clear();
        entryIdField.clear();
        incrementModeCombo.setValue("自增ID");
        incrementFieldField.clear();
        intervalSpinner.getValueFactory().setValue(5);
        batchSizeSpinner.getValueFactory().setValue(50);
        retrySpinner.getValueFactory().setValue(3);
        enabledCheck.setSelected(true);
        statusLabel.setText("");
        updateBreadcrumb();
    }

    private void refreshList() {
        taskTable.getItems().clear();
        taskTable.getItems().addAll(tasks);
    }

    private void saveTask() {
        String name = nameField.getText().trim();
        String entryId = entryIdField.getText().trim();
        String sourceTable = sourceTableField.getText().trim();
        DataSourceConfig ds = dataSourceCombo.getValue();
        JdyAppConfig app = jdyAppCombo.getValue();

        if (name.isEmpty()) {
            showStatus(false, "请输入任务名称");
            return;
        }
        if (ds == null) {
            showStatus(false, "请选择数据源");
            return;
        }
        if (app == null) {
            showStatus(false, "请选择简道云应用");
            return;
        }
        if (sourceTable.isEmpty()) {
            showStatus(false, "请输入源表名");
            return;
        }
        if (entryId.isEmpty()) {
            showStatus(false, "请输入简道云表单ID");
            return;
        }

        if (selectedTask == null) {
            SyncTaskConfig task = new SyncTaskConfig();
            task.setId(UUID.randomUUID().toString());
            task.setName(name);
            task.setDataSourceId(ds.getId());
            task.setJdyAppId(app.getId());
            task.setSourceTable(sourceTable);
            task.setEntryId(entryId);
            task.setIncrementMode("自增ID".equals(incrementModeCombo.getValue()) ? "id" : "timestamp");
            task.setIncrementField(incrementFieldField.getText().trim());
            task.setSyncIntervalMinutes(intervalSpinner.getValue());
            task.setMaxBatchSize(batchSizeSpinner.getValue());
            task.setMaxRetry(retrySpinner.getValue());
            task.setEnabled(enabledCheck.isSelected());
            tasks.add(task);
            selectedTask = task;
        } else {
            selectedTask.setName(name);
            selectedTask.setDataSourceId(ds.getId());
            selectedTask.setJdyAppId(app.getId());
            selectedTask.setSourceTable(sourceTable);
            selectedTask.setEntryId(entryId);
            selectedTask.setIncrementMode("自增ID".equals(incrementModeCombo.getValue()) ? "id" : "timestamp");
            selectedTask.setIncrementField(incrementFieldField.getText().trim());
            selectedTask.setSyncIntervalMinutes(intervalSpinner.getValue());
            selectedTask.setMaxBatchSize(batchSizeSpinner.getValue());
            selectedTask.setMaxRetry(retrySpinner.getValue());
            selectedTask.setEnabled(enabledCheck.isSelected());
        }

        configManager.saveSyncTasks(tasks);
        taskScheduler.refresh();
        refreshList();
        taskTable.getSelectionModel().select(selectedTask);
        updateBreadcrumb();
        showStatus(true, "任务已保存");
    }

    private void deleteTask() {
        if (selectedTask == null) {
            showStatus(false, "请先选择要删除的任务");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("删除同步任务");
        alert.setContentText("确定要删除任务 \"" + selectedTask.getName() + "\" 吗？");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                taskScheduler.cancelTask(selectedTask.getId());
                tasks.remove(selectedTask);
                configManager.saveSyncTasks(tasks);
                clearForm();
                refreshList();
                showStatus(true, "任务已删除");
            }
        });
    }

    private void executeTaskNow() {
        if (selectedTask == null) {
            showStatus(false, "请先选择要执行的任务");
            return;
        }

        statusLabel.setText("正在执行同步任务...");
        statusLabel.getStyleClass().removeAll("status-success", "status-error");

        executor.submit(() -> {
            SyncEngine.SyncResult result = taskScheduler.executeTaskNow(selectedTask.getId());
            javafx.application.Platform.runLater(() -> {
                showStatus(result.isSuccess(), result.getMessage());
            });
        });
    }

    private void showStatus(boolean success, String message) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-success", "status-error");
        statusLabel.getStyleClass().add(success ? "status-success" : "status-error");
    }

    public VBox getContent() {
        return root;
    }
}
