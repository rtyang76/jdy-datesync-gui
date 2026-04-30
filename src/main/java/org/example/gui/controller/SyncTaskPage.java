package org.example.gui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import org.example.gui.model.DataSourceConfig;
import org.example.gui.model.FormMappingConfig;
import org.example.gui.model.JdyAppConfig;
import org.example.gui.model.SyncTaskConfig;
import org.example.gui.service.ConfigManager;
import org.example.gui.service.SyncEngine;
import org.example.gui.service.TaskScheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final VBox selectedMappingsBox;
    private final Spinner<Integer> intervalSpinner;
    private final Spinner<Integer> batchSizeSpinner;
    private final Spinner<Integer> retrySpinner;
    private final CheckBox enabledCheck;
    private final ComboBox<String> directionCombo;
    private final Label statusLabel;
    private final HBox breadcrumbBar;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private VBox formPanel;
    private List<SyncTaskConfig> tasks;
    private List<FormMappingConfig> formMappings;
    private List<DataSourceConfig> dataSources;
    private List<JdyAppConfig> jdyApps;
    private SyncTaskConfig selectedTask;
    
    private final ObservableList<FormMappingConfig> selectedMappings = FXCollections.observableArrayList();
    private Popup mappingPopup;
    private Button dropDownBtn;

    private BiConsumer<String, String> navigator;

    public SyncTaskPage(ConfigManager configManager, TaskScheduler taskScheduler) {
        this.configManager = configManager;
        this.taskScheduler = taskScheduler;
        this.tasks = new ArrayList<>(configManager.loadSyncTasks());
        this.formMappings = new ArrayList<>(configManager.loadFormMappings());
        this.dataSources = new ArrayList<>(configManager.loadDataSources());
        this.jdyApps = new ArrayList<>(configManager.loadJdyApps());
        this.root = new VBox(15);
        this.root.setPadding(new Insets(25));

        this.taskTable = new TableView<>();
        this.nameField = new TextField();
        this.nameField.setPromptText("任务名称");
        this.selectedMappingsBox = new VBox(5);

        this.intervalSpinner = new Spinner<>(1, 1440, 5);
        this.intervalSpinner.setEditable(true);
        this.batchSizeSpinner = new Spinner<>(1, 500, 50);
        this.batchSizeSpinner.setEditable(true);
        this.retrySpinner = new Spinner<>(1, 50, 3);
        this.retrySpinner.setEditable(true);
        this.enabledCheck = new CheckBox("启用");
        this.enabledCheck.setSelected(true);
        this.directionCombo = new ComboBox<>();
        this.directionCombo.getItems().addAll("推送（本地→简道云）", "拉取（简道云→本地）", "双向同步");
        this.directionCombo.setValue("推送（本地→简道云）");
        this.statusLabel = new Label();
        this.statusLabel.getStyleClass().add("status-label");
        this.breadcrumbBar = new HBox(10);
        this.breadcrumbBar.setAlignment(Pos.CENTER_LEFT);
        this.breadcrumbBar.getStyleClass().add("breadcrumb-bar");
        this.breadcrumbBar.setVisible(false);

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
        formMappings = new ArrayList<>(configManager.loadFormMappings());
        tasks = new ArrayList<>(configManager.loadSyncTasks());
        refreshList();
    }

    public void selectItem(String id) {
        tasks = new ArrayList<>(configManager.loadSyncTasks());
        formMappings = new ArrayList<>(configManager.loadFormMappings());
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

        Label descLabel = new Label("同步任务只负责调度，增量配置和水印在表单映射中管理。一个任务可包含多个表单映射，按顺序执行。");
        descLabel.getStyleClass().add("description-label");
        descLabel.setWrapText(true);

        HBox mainContent = new HBox(20);
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        VBox listPanel = new VBox(10);
        listPanel.setPrefWidth(400);
        Label listTitle = new Label("同步任务列表");
        listTitle.getStyleClass().add("section-title");
        
        Button newTaskBtn = new Button("新建同步任务");
        newTaskBtn.getStyleClass().add("btn-primary");
        newTaskBtn.setOnAction(e -> startNewTask());
        
        VBox.setVgrow(taskTable, Priority.ALWAYS);
        listPanel.getChildren().addAll(listTitle, newTaskBtn, taskTable);

        this.formPanel = new VBox(15);
        this.formPanel.setVisible(false);
        this.formPanel.setManaged(false);
        HBox.setHgrow(this.formPanel, Priority.ALWAYS);
        Label formTitle = new Label("任务配置");
        formTitle.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);

        grid.add(new Label("任务名称"), 0, 0);
        grid.add(nameField, 1, 0);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        grid.add(new Label("表单映射配置"), 0, 1);
        VBox mappingBox = new VBox(8);
        
        HBox dropDownRow = new HBox(10);
        dropDownRow.setAlignment(Pos.CENTER_LEFT);
        dropDownBtn = new Button("选择表单映射 ▼");
        dropDownBtn.getStyleClass().add("btn-secondary");
        dropDownBtn.setOnAction(e -> showMappingPopup());
        
        Button goMappingBtn = new Button("去配置");
        goMappingBtn.getStyleClass().add("btn-secondary");
        goMappingBtn.setOnAction(e -> {
            if (navigator != null) navigator.accept("formMapping", "");
        });
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        dropDownRow.getChildren().addAll(dropDownBtn, spacer, goMappingBtn);
        
        Label selectedLabel = new Label("已选择的配置：");
        selectedLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        
        selectedMappingsBox.setPadding(new Insets(8));
        selectedMappingsBox.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 4; -fx-border-color: #ddd; -fx-border-radius: 4;");
        
        Label emptyHint = new Label("暂无选择");
        emptyHint.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
        selectedMappingsBox.getChildren().add(emptyHint);
        
        mappingBox.getChildren().addAll(dropDownRow, selectedLabel, selectedMappingsBox);
        grid.add(mappingBox, 1, 1);
        GridPane.setHgrow(mappingBox, Priority.ALWAYS);

        grid.add(new Label("同步方向"), 0, 3);
        grid.add(directionCombo, 1, 3);
        GridPane.setHgrow(directionCombo, Priority.ALWAYS);

        grid.add(new Separator(), 0, 4);
        GridPane.setColumnSpan(grid.getChildren().get(grid.getChildren().size() - 1), 2);

        grid.add(new Label("同步间隔(分钟)"), 0, 5);
        grid.add(intervalSpinner, 1, 5);

        grid.add(new Label("批量大小"), 0, 6);
        grid.add(batchSizeSpinner, 1, 6);

        grid.add(new Label("最大重试次数"), 0, 7);
        grid.add(retrySpinner, 1, 7);

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

        formPanel.getChildren().addAll(formTitle, breadcrumbBar, enabledCheck, grid, buttonBar, statusLabel);

        mainContent.getChildren().addAll(listPanel, formPanel);
        HBox.setHgrow(formPanel, Priority.ALWAYS);

        root.getChildren().addAll(titleLabel, descLabel, mainContent);

        setupListeners();
    }

    private void showMappingPopup() {
        if (mappingPopup != null && mappingPopup.isShowing()) {
            mappingPopup.hide();
            return;
        }
        
        VBox content = new VBox(5);
        content.setPadding(new Insets(10));
        content.setStyle("-fx-background-color: white; -fx-border-color: #ccc; -fx-border-width: 1; -fx-background-radius: 4;");
        content.setPrefWidth(400);
        
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setPrefHeight(200);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        
        VBox checkBoxContainer = new VBox(3);
        
        Set<String> selectedIds = new HashSet<>();
        for (FormMappingConfig fm : selectedMappings) {
            selectedIds.add(fm.getId());
        }
        
        for (FormMappingConfig fm : formMappings) {
            CheckBox cb = new CheckBox();
            cb.setSelected(selectedIds.contains(fm.getId()));
            
            DataSourceConfig ds = dataSources.stream()
                    .filter(d -> d.getId().equals(fm.getDataSourceId()))
                    .findFirst().orElse(null);
            String dsName = ds != null ? ds.getName() : "?";
            String mode = "id".equals(fm.getIncrementMode()) ? "ID" : "时间戳";
            cb.setText(fm.getName() + "  [" + dsName + "] (" + mode + ")");
            cb.setUserData(fm);
            cb.setMaxWidth(Double.MAX_VALUE);
            
            cb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                FormMappingConfig mapping = (FormMappingConfig) cb.getUserData();
                if (newVal) {
                    if (!selectedMappings.contains(mapping)) {
                        selectedMappings.add(mapping);
                    }
                } else {
                    selectedMappings.remove(mapping);
                }
                updateSelectedMappingsDisplay();
                updateBreadcrumb();
            });
            
            checkBoxContainer.getChildren().add(cb);
        }
        
        scrollPane.setContent(checkBoxContainer);
        
        if (formMappings.isEmpty()) {
            Label noData = new Label("暂无表单映射配置，请先去配置");
            noData.setStyle("-fx-text-fill: #999;");
            content.getChildren().add(noData);
        } else {
            content.getChildren().add(scrollPane);
        }
        
        mappingPopup = new Popup();
        mappingPopup.getContent().clear();
        mappingPopup.getContent().add(content);
        mappingPopup.setAutoHide(true);
        mappingPopup.setHideOnEscape(true);
        
        content.setStyle("-fx-background-color: white; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 2); -fx-background-radius: 4;");
        
        javafx.stage.Window window = dropDownBtn.getScene().getWindow();
        double x = window.getX() + dropDownBtn.localToScene(0, 0).getX() + dropDownBtn.getScene().getX();
        double y = window.getY() + dropDownBtn.localToScene(0, 0).getY() + dropDownBtn.getScene().getY() + dropDownBtn.getHeight();
        mappingPopup.show(dropDownBtn, x, y);
    }

    private void updateSelectedMappingsDisplay() {
        selectedMappingsBox.getChildren().clear();
        
        if (selectedMappings.isEmpty()) {
            Label emptyHint = new Label("暂无选择");
            emptyHint.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
            selectedMappingsBox.getChildren().add(emptyHint);
            return;
        }
        
        int index = 1;
        for (FormMappingConfig fm : selectedMappings) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 0, 4, 0));
            row.setStyle("-fx-border-color: transparent transparent #eee transparent; -fx-border-width: 0 0 1 0;");
            
            Label indexLabel = new Label(index + ".");
            indexLabel.setStyle("-fx-font-weight: bold; -fx-min-width: 20px;");
            
            DataSourceConfig ds = dataSources.stream()
                    .filter(d -> d.getId().equals(fm.getDataSourceId()))
                    .findFirst().orElse(null);
            String dsName = ds != null ? ds.getName() : "?";
            String mode = "id".equals(fm.getIncrementMode()) ? "ID" : "时间戳";
            
            Label nameLabel = new Label(fm.getName());
            nameLabel.setStyle("-fx-font-weight: bold;");
            
            Label infoLabel = new Label("[" + dsName + "] (" + mode + ")");
            infoLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            Button removeBtn = new Button("×");
            removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #c00; -fx-font-size: 14px; -fx-padding: 0 5 0 5; -fx-cursor: hand;");
            removeBtn.setOnAction(e -> {
                selectedMappings.remove(fm);
                updateSelectedMappingsDisplay();
                updateBreadcrumb();
            });
            
            row.getChildren().addAll(indexLabel, nameLabel, infoLabel, spacer, removeBtn);
            selectedMappingsBox.getChildren().add(row);
            index++;
        }
        
        dropDownBtn.setText("选择表单映射 ▼ (" + selectedMappings.size() + ")");
    }

    private void setupListeners() {
        taskTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                selectedTask = newVal;
                loadTaskToForm(newVal);
            }
        });
    }

    private void updateBreadcrumb() {
        breadcrumbBar.getChildren().clear();

        if (selectedMappings.isEmpty()) {
            breadcrumbBar.setVisible(false);
            return;
        }

        breadcrumbBar.setVisible(true);

        FormMappingConfig firstMapping = selectedMappings.get(0);
        DataSourceConfig ds = dataSources.stream()
                .filter(d -> d.getId().equals(firstMapping.getDataSourceId()))
                .findFirst().orElse(null);

        JdyAppConfig app = jdyApps.stream()
                .filter(a -> a.getId().equals(firstMapping.getJdyAppId()))
                .findFirst().orElse(null);

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

        if (app != null) {
            Label arrow = new Label("→");
            arrow.getStyleClass().add("breadcrumb-arrow");
            breadcrumbBar.getChildren().add(arrow);
        }

        Label mapLabel = new Label("📋 " + firstMapping.getName() + (selectedMappings.size() > 1 ? " (+" + (selectedMappings.size() - 1) + ")" : ""));
        mapLabel.getStyleClass().add("breadcrumb-label");
        breadcrumbBar.getChildren().add(mapLabel);
    }

    private Hyperlink createBreadcrumbLink(String text) {
        Hyperlink link = new Hyperlink(text);
        link.setPadding(new Insets(0));
        link.getStyleClass().add("breadcrumb-link");
        link.setUnderline(false);
        link.setCursor(Cursor.HAND);
        return link;
    }

    private void setupTable() {
        TableColumn<SyncTaskConfig, String> nameCol = new TableColumn<>("任务名称");
        nameCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getName()));
        nameCol.setPrefWidth(120);

        TableColumn<SyncTaskConfig, String> mappingCol = new TableColumn<>("表单映射");
        mappingCol.setCellValueFactory(cell -> {
            List<String> ids = cell.getValue().getFormMappingIds();
            if (ids != null && !ids.isEmpty()) {
                FormMappingConfig fm = configManager.findFormMappingById(ids.get(0));
                String fmName = fm != null ? fm.getName() : "?";
                if (ids.size() > 1) {
                    fmName += " (+" + (ids.size() - 1) + ")";
                }
                return new javafx.beans.property.SimpleStringProperty(fmName);
            } else {
                return new javafx.beans.property.SimpleStringProperty("?");
            }
        });
        mappingCol.setPrefWidth(150);

        TableColumn<SyncTaskConfig, String> intervalCol = new TableColumn<>("间隔(分)");
        intervalCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(String.valueOf(cell.getValue().getSyncIntervalMinutes())));
        intervalCol.setPrefWidth(70);

        TableColumn<SyncTaskConfig, String> directionCol = new TableColumn<>("方向");
        directionCol.setCellValueFactory(cell -> {
            String dir = cell.getValue().getSyncDirection();
            String display;
            if (SyncTaskConfig.DIRECTION_PULL.equals(dir)) display = "拉取";
            else if (SyncTaskConfig.DIRECTION_BOTH.equals(dir)) display = "双向";
            else display = "推送";
            return new javafx.beans.property.SimpleStringProperty(display);
        });
        directionCol.setPrefWidth(60);

        TableColumn<SyncTaskConfig, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().isEnabled() ? "启用" : "禁用"));
        statusCol.setPrefWidth(60);

        taskTable.getColumns().addAll(nameCol, mappingCol, intervalCol, directionCol, statusCol);
    }

    private void loadTaskToForm(SyncTaskConfig task) {
        selectedTask = task;
        nameField.setText(task.getName());

        selectedMappings.clear();
        List<String> ids = task.getFormMappingIds();
        if (ids != null && !ids.isEmpty()) {
            for (String id : ids) {
                FormMappingConfig fm = configManager.findFormMappingById(id);
                if (fm != null) {
                    selectedMappings.add(fm);
                }
            }
        }
        updateSelectedMappingsDisplay();

        intervalSpinner.getValueFactory().setValue(task.getSyncIntervalMinutes());
        batchSizeSpinner.getValueFactory().setValue(task.getMaxBatchSize());
        retrySpinner.getValueFactory().setValue(task.getMaxRetry());
        enabledCheck.setSelected(task.isEnabled());
        directionCombo.setValue(directionToDisplay(task.getSyncDirection()));

        statusLabel.setText("");
        updateBreadcrumb();
        showFormPanel(true);
    }

    private void clearForm() {
        selectedTask = null;
        nameField.clear();
        selectedMappings.clear();
        updateSelectedMappingsDisplay();
        intervalSpinner.getValueFactory().setValue(5);
        batchSizeSpinner.getValueFactory().setValue(50);
        retrySpinner.getValueFactory().setValue(3);
        enabledCheck.setSelected(true);
        directionCombo.setValue("推送（本地→简道云）");
        statusLabel.setText("");
        updateBreadcrumb();
        showFormPanel(false);
    }

    private void refreshList() {
        taskTable.getItems().clear();
        taskTable.getItems().addAll(tasks);
    }

    private void saveTask() {
        String name = nameField.getText().trim();

        if (name.isEmpty()) {
            showStatus(false, "请输入任务名称");
            return;
        }
        if (selectedMappings.isEmpty()) {
            showStatus(false, "请选择至少一个表单映射配置");
            return;
        }

        List<String> mappingIds = new ArrayList<>();
        for (FormMappingConfig fm : selectedMappings) {
            mappingIds.add(fm.getId());
        }

        if (selectedTask == null) {
            SyncTaskConfig task = new SyncTaskConfig();
            task.setId(UUID.randomUUID().toString());
            task.setName(name);
            task.setFormMappingIds(mappingIds);
            task.setSyncDirection(displayToDirection(directionCombo.getValue()));
            task.setSyncIntervalMinutes(intervalSpinner.getValue());
            task.setMaxBatchSize(batchSizeSpinner.getValue());
            task.setMaxRetry(retrySpinner.getValue());
            task.setEnabled(enabledCheck.isSelected());
            tasks.add(task);
            selectedTask = task;
        } else {
            selectedTask.setName(name);
            selectedTask.setFormMappingIds(mappingIds);
            selectedTask.setSyncDirection(displayToDirection(directionCombo.getValue()));
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
        showStatus(true, "任务已保存，包含 " + mappingIds.size() + " 个表单映射");
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

    private void startNewTask() {
        clearForm();
        showFormPanel(true);
    }

    private void showFormPanel(boolean show) {
        if (formPanel != null) {
            formPanel.setVisible(show);
            formPanel.setManaged(show);
        }
    }

    public VBox getContent() {
        return root;
    }

    private String directionToDisplay(String direction) {
        if (SyncTaskConfig.DIRECTION_PULL.equals(direction)) return "拉取（简道云→本地）";
        if (SyncTaskConfig.DIRECTION_BOTH.equals(direction)) return "双向同步";
        return "推送（本地→简道云）";
    }

    private String displayToDirection(String display) {
        if (display == null) return SyncTaskConfig.DIRECTION_PUSH;
        if (display.startsWith("拉取")) return SyncTaskConfig.DIRECTION_PULL;
        if (display.startsWith("双向")) return SyncTaskConfig.DIRECTION_BOTH;
        return SyncTaskConfig.DIRECTION_PUSH;
    }
}
