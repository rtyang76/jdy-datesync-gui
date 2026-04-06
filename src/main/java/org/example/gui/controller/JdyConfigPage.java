package org.example.gui.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.gui.model.JdyAppConfig;
import org.example.gui.service.ConfigManager;
import org.example.gui.service.JdyApiTestService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JdyConfigPage {

    private final ConfigManager configManager;
    private final VBox root;
    private final ListView<JdyAppConfig> appList;
    private final TextField nameField;
    private final TextField appIdField;
    private final TextField apiTokenField;
    private final CheckBox startWorkflowCheck;
    private final Label statusLabel;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private List<JdyAppConfig> apps;
    private JdyAppConfig selectedApp;

    public JdyConfigPage(ConfigManager configManager) {
        this.configManager = configManager;
        this.apps = new ArrayList<>(configManager.loadJdyApps());
        this.root = new VBox(15);
        this.root.setPadding(new Insets(25));

        this.appList = new ListView<>();
        this.nameField = new TextField();
        this.nameField.setPromptText("例如：生产简道云");
        this.appIdField = new TextField();
        this.appIdField.setPromptText("简道云应用ID");
        this.apiTokenField = new TextField();
        this.apiTokenField.setPromptText("Bearer your_api_token");
        this.startWorkflowCheck = new CheckBox("触发工作流");
        this.statusLabel = new Label();
        this.statusLabel.getStyleClass().add("status-label");

        setupLayout();
        setupListeners();
        refreshList();
    }

    public void selectItem(String id) {
        apps = new ArrayList<>(configManager.loadJdyApps());
        refreshList();
        apps.stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .ifPresent(app -> appList.getSelectionModel().select(app));
    }

    private void setupLayout() {
        Label titleLabel = new Label("简道云应用配置");
        titleLabel.getStyleClass().add("page-title");

        Label descLabel = new Label("管理多个简道云应用。每个同步任务可独立选择关联的简道云应用。");
        descLabel.getStyleClass().add("description-label");
        descLabel.setWrapText(true);

        HBox mainContent = new HBox(20);
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        VBox listPanel = new VBox(10);
        listPanel.setPrefWidth(220);
        Label listTitle = new Label("已配置应用");
        listTitle.getStyleClass().add("section-title");
        appList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(JdyAppConfig item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName());
                }
            }
        });
        VBox.setVgrow(appList, Priority.ALWAYS);
        listPanel.getChildren().addAll(listTitle, appList);

        VBox formPanel = new VBox(15);
        HBox.setHgrow(formPanel, Priority.ALWAYS);
        Label formTitle = new Label("应用配置");
        formTitle.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);

        grid.add(new Label("名称"), 0, 0);
        grid.add(nameField, 1, 0);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        grid.add(new Label("应用 ID (App ID)"), 0, 1);
        grid.add(appIdField, 1, 1);
        GridPane.setHgrow(appIdField, Priority.ALWAYS);

        grid.add(new Label("API Token"), 0, 2);
        grid.add(apiTokenField, 1, 2);
        GridPane.setHgrow(apiTokenField, Priority.ALWAYS);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(140);
        col1.setMaxWidth(160);
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
        saveBtn.setOnAction(e -> saveConfig());

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> deleteConfig());

        Button testBtn = new Button("测试连接");
        testBtn.getStyleClass().add("btn-secondary");
        testBtn.setOnAction(e -> testConnection());

        buttonBar.getChildren().addAll(newBtn, saveBtn, deleteBtn, testBtn);

        formPanel.getChildren().addAll(formTitle, grid, startWorkflowCheck, buttonBar, statusLabel);
        mainContent.getChildren().addAll(listPanel, formPanel);

        root.getChildren().addAll(titleLabel, descLabel, mainContent);
    }

    private void setupListeners() {
        appList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                selectedApp = newVal;
                loadConfigToForm(newVal);
            }
        });
    }

    private void loadConfigToForm(JdyAppConfig config) {
        nameField.setText(config.getName());
        appIdField.setText(config.getAppId());
        apiTokenField.setText(config.getApiToken());
        startWorkflowCheck.setSelected(config.isStartWorkflow());
        statusLabel.setText("");
    }

    private void clearForm() {
        selectedApp = null;
        nameField.clear();
        appIdField.clear();
        apiTokenField.clear();
        startWorkflowCheck.setSelected(true);
        statusLabel.setText("");
    }

    private void refreshList() {
        appList.getItems().clear();
        appList.getItems().addAll(apps);
    }

    private void saveConfig() {
        String name = nameField.getText().trim();
        String appId = appIdField.getText().trim();
        String apiToken = apiTokenField.getText().trim();

        if (name.isEmpty()) {
            showStatus(false, "请输入应用名称");
            return;
        }
        if (appId.isEmpty()) {
            showStatus(false, "请输入应用ID");
            return;
        }
        if (apiToken.isEmpty()) {
            showStatus(false, "请输入API Token");
            return;
        }

        if (selectedApp == null) {
            JdyAppConfig config = new JdyAppConfig();
            config.setId(UUID.randomUUID().toString());
            config.setName(name);
            config.setAppId(appId);
            config.setApiToken(apiToken);
            config.setStartWorkflow(startWorkflowCheck.isSelected());
            apps.add(config);
            selectedApp = config;
        } else {
            selectedApp.setName(name);
            selectedApp.setAppId(appId);
            selectedApp.setApiToken(apiToken);
            selectedApp.setStartWorkflow(startWorkflowCheck.isSelected());
        }

        configManager.saveJdyApps(apps);
        refreshList();
        appList.getSelectionModel().select(selectedApp);
        showStatus(true, "应用已保存");
    }

    private void deleteConfig() {
        if (selectedApp == null) {
            showStatus(false, "请先选择要删除的应用");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("删除简道云应用");
        alert.setContentText("确定要删除应用 \"" + selectedApp.getName() + "\" 吗？关联此应用的同步任务将无法执行。");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                apps.remove(selectedApp);
                configManager.saveJdyApps(apps);
                clearForm();
                refreshList();
                showStatus(true, "应用已删除");
            }
        });
    }

    private void testConnection() {
        JdyAppConfig config = new JdyAppConfig();
        config.setAppId(appIdField.getText().trim());
        config.setApiToken(apiTokenField.getText().trim());

        if (config.getAppId().isEmpty() || config.getApiToken().isEmpty()) {
            showStatus(false, "请先填写应用ID和API Token");
            return;
        }

        statusLabel.setText("正在测试连接...");
        statusLabel.getStyleClass().removeAll("status-success", "status-error");

        executor.submit(() -> {
            JdyApiTestService.TestResult result = JdyApiTestService.testConnection(config);
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
