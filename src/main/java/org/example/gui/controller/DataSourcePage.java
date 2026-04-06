package org.example.gui.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.gui.model.DataSourceConfig;
import org.example.gui.service.ConfigManager;
import org.example.gui.service.ConnectionTestService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataSourcePage {

    private final ConfigManager configManager;
    private final VBox root;
    private final ListView<DataSourceConfig> dataSourceList;
    private final TextField nameField;
    private final TextField hostField;
    private final Spinner<Integer> portSpinner;
    private final TextField databaseField;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final Label statusLabel;
    private final ListView<String> tableListView;
    private final Button loadTablesBtn;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private List<DataSourceConfig> dataSources;
    private DataSourceConfig selectedConfig;

    public DataSourcePage(ConfigManager configManager) {
        this.configManager = configManager;
        this.dataSources = new ArrayList<>(configManager.loadDataSources());
        this.root = new VBox(15);
        this.root.setPadding(new Insets(25));

        this.dataSourceList = new ListView<>();
        this.nameField = new TextField();
        this.nameField.setPromptText("例如：生产数据库");
        this.hostField = new TextField();
        this.hostField.setPromptText("127.0.0.1");
        this.portSpinner = new Spinner<>(1, 65535, 3306);
        this.portSpinner.setEditable(true);
        this.databaseField = new TextField();
        this.databaseField.setPromptText("数据库名称");
        this.usernameField = new TextField();
        this.usernameField.setPromptText("用户名");
        this.passwordField = new PasswordField();
        this.passwordField.setPromptText("密码");
        this.statusLabel = new Label();
        this.statusLabel.getStyleClass().add("status-label");
        this.tableListView = new ListView<>();
        this.loadTablesBtn = new Button("加载表列表");
        this.loadTablesBtn.getStyleClass().add("btn-secondary");
        this.loadTablesBtn.setDisable(true);

        setupLayout();
        setupListeners();
        refreshList();
    }

    public void refreshData() {
        this.dataSources = new ArrayList<>(configManager.loadDataSources());
        refreshList();
    }

    public void selectItem(String id) {
        dataSources = new ArrayList<>(configManager.loadDataSources());
        refreshList();
        dataSources.stream()
                .filter(d -> d.getId().equals(id))
                .findFirst()
                .ifPresent(config -> dataSourceList.getSelectionModel().select(config));
    }

    private void setupLayout() {
        Label titleLabel = new Label("数据源配置");
        titleLabel.getStyleClass().add("page-title");

        HBox mainContent = new HBox(20);
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        VBox listPanel = new VBox(10);
        listPanel.setPrefWidth(220);
        Label listTitle = new Label("已配置数据源");
        listTitle.getStyleClass().add("section-title");
        dataSourceList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DataSourceConfig item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName());
                }
            }
        });
        VBox.setVgrow(dataSourceList, Priority.ALWAYS);
        listPanel.getChildren().addAll(listTitle, dataSourceList);

        VBox formPanel = new VBox(15);
        HBox.setHgrow(formPanel, Priority.ALWAYS);
        Label formTitle = new Label("MySQL 连接配置");
        formTitle.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);

        grid.add(new Label("名称"), 0, 0);
        grid.add(nameField, 1, 0);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        grid.add(new Label("主机地址"), 0, 1);
        grid.add(hostField, 1, 1);
        GridPane.setHgrow(hostField, Priority.ALWAYS);

        grid.add(new Label("端口"), 0, 2);
        grid.add(portSpinner, 1, 2);

        grid.add(new Label("数据库名"), 0, 3);
        grid.add(databaseField, 1, 3);
        GridPane.setHgrow(databaseField, Priority.ALWAYS);

        grid.add(new Label("用户名"), 0, 4);
        grid.add(usernameField, 1, 4);
        GridPane.setHgrow(usernameField, Priority.ALWAYS);

        grid.add(new Label("密码"), 0, 5);
        grid.add(passwordField, 1, 5);
        GridPane.setHgrow(passwordField, Priority.ALWAYS);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(80);
        col1.setMaxWidth(100);
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

        VBox tablePanel = new VBox(10);
        Label tableTitle = new Label("数据库表");
        tableTitle.getStyleClass().add("section-title");
        HBox tableBar = new HBox(10);
        tableBar.getChildren().add(loadTablesBtn);
        tableBar.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(tableListView, Priority.ALWAYS);
        tablePanel.getChildren().addAll(tableTitle, tableBar, tableListView);
        tablePanel.setPrefWidth(200);

        HBox bottomContent = new HBox(20);
        HBox.setHgrow(bottomContent, Priority.ALWAYS);

        VBox formWithButtons = new VBox(10);
        formWithButtons.getChildren().addAll(formTitle, grid, buttonBar, statusLabel);
        bottomContent.getChildren().addAll(formWithButtons, tablePanel);
        HBox.setHgrow(formWithButtons, Priority.ALWAYS);

        mainContent.getChildren().addAll(listPanel, bottomContent);
        HBox.setHgrow(bottomContent, Priority.ALWAYS);

        root.getChildren().addAll(titleLabel, mainContent);
    }

    private void setupListeners() {
        dataSourceList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                selectedConfig = newVal;
                loadConfigToForm(newVal);
                loadTablesBtn.setDisable(false);
            }
        });

        loadTablesBtn.setOnAction(e -> loadTables());
    }

    private void loadConfigToForm(DataSourceConfig config) {
        nameField.setText(config.getName());
        hostField.setText(config.getHost());
        portSpinner.getValueFactory().setValue(config.getPort());
        databaseField.setText(config.getDatabase());
        usernameField.setText(config.getUsername());
        passwordField.setText(config.getPassword());
        statusLabel.setText("");
        tableListView.getItems().clear();
    }

    private void clearForm() {
        selectedConfig = null;
        nameField.clear();
        hostField.clear();
        portSpinner.getValueFactory().setValue(3306);
        databaseField.clear();
        usernameField.clear();
        passwordField.clear();
        statusLabel.setText("");
        tableListView.getItems().clear();
        loadTablesBtn.setDisable(true);
    }

    private void refreshList() {
        dataSourceList.getItems().clear();
        dataSourceList.getItems().addAll(dataSources);
    }

    private void saveConfig() {
        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        Integer port = portSpinner.getValue();
        String database = databaseField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (name.isEmpty()) {
            showStatus(false, "请输入数据源名称");
            return;
        }
        if (host.isEmpty()) {
            showStatus(false, "请输入主机地址");
            return;
        }
        if (database.isEmpty()) {
            showStatus(false, "请输入数据库名");
            return;
        }
        if (username.isEmpty()) {
            showStatus(false, "请输入用户名");
            return;
        }
        if (port == null) {
            port = 3306;
        }

        if (selectedConfig == null) {
            DataSourceConfig config = new DataSourceConfig();
            config.setId(UUID.randomUUID().toString());
            config.setName(name);
            config.setHost(host);
            config.setPort(port);
            config.setDatabase(database);
            config.setUsername(username);
            config.setPassword(password != null ? password : "");
            dataSources.add(config);
            selectedConfig = config;
        } else {
            selectedConfig.setName(name);
            selectedConfig.setHost(host);
            selectedConfig.setPort(port);
            selectedConfig.setDatabase(database);
            selectedConfig.setUsername(username);
            selectedConfig.setPassword(password != null ? password : "");
        }

        configManager.saveDataSources(dataSources);
        refreshList();
        dataSourceList.getSelectionModel().select(selectedConfig);
        loadTablesBtn.setDisable(false);
        showStatus(true, "数据源已保存");
    }

    private void deleteConfig() {
        if (selectedConfig == null) {
            showStatus(false, "请先选择要删除的数据源");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("删除数据源");
        alert.setContentText("确定要删除数据源 \"" + selectedConfig.getName() + "\" 吗？");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                dataSources.remove(selectedConfig);
                configManager.saveDataSources(dataSources);
                clearForm();
                refreshList();
                showStatus(true, "数据源已删除");
            }
        });
    }

    private void testConnection() {
        DataSourceConfig config;
        if (selectedConfig != null) {
            config = selectedConfig;
        } else {
            config = new DataSourceConfig();
            config.setHost(hostField.getText().trim());
            config.setPort(portSpinner.getValue() != null ? portSpinner.getValue() : 3306);
            config.setDatabase(databaseField.getText().trim());
            config.setUsername(usernameField.getText().trim());
            config.setPassword(passwordField.getText() != null ? passwordField.getText() : "");
        }

        if (config.getHost() == null || config.getHost().isEmpty() || config.getDatabase() == null || config.getDatabase().isEmpty()) {
            showStatus(false, "请先填写主机地址和数据库名");
            return;
        }

        statusLabel.setText("正在测试连接...");
        statusLabel.getStyleClass().removeAll("status-success", "status-error");

        executor.submit(() -> {
            ConnectionTestService.TestResult result = ConnectionTestService.testConnection(config);
            javafx.application.Platform.runLater(() -> {
                showStatus(result.isSuccess(), result.getMessage());
                if (result.isSuccess()) {
                    loadTables();
                }
            });
        });
    }

    private void loadTables() {
        if (selectedConfig == null) return;

        statusLabel.setText("正在加载表列表...");
        statusLabel.getStyleClass().removeAll("status-success", "status-error");

        executor.submit(() -> {
            List<String> tables = ConnectionTestService.fetchTableList(selectedConfig);
            javafx.application.Platform.runLater(() -> {
                tableListView.getItems().clear();
                tableListView.getItems().addAll(tables);
                if (tables.isEmpty()) {
                    showStatus(false, "未找到表或连接失败");
                } else {
                    showStatus(true, "已加载 " + tables.size() + " 个表");
                }
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
