package org.example.gui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.gui.controller.*;
import org.example.gui.service.ConfigManager;
import org.example.gui.service.SyncEngine;
import org.example.gui.service.TaskScheduler;

import java.util.function.BiConsumer;

public class MainWindow {

    private final BorderPane root;
    private final StackPane contentArea;
    private final ConfigManager configManager;
    private final SyncEngine syncEngine;
    private final TaskScheduler taskScheduler;
    private ToggleButton activeNavButton;

    private DataSourcePage dataSourcePage;
    private JdyConfigPage jdyConfigPage;
    private SyncTaskPage syncTaskPage;
    private FieldMappingPage fieldMappingPage;
    private LogPage logPage;

    private String currentPageId = "dataSource";

    public MainWindow() {
        this.configManager = new ConfigManager();
        this.syncEngine = new SyncEngine(configManager);
        this.taskScheduler = new TaskScheduler(configManager, syncEngine);

        this.root = new BorderPane();
        this.contentArea = new StackPane();

        root.setLeft(createSidebar());
        root.setCenter(contentArea);

        BiConsumer<String, String> navigator = this::navigateTo;

        dataSourcePage = new DataSourcePage(configManager);
        jdyConfigPage = new JdyConfigPage(configManager);
        syncTaskPage = new SyncTaskPage(configManager, taskScheduler);
        syncTaskPage.setNavigator(navigator);
        fieldMappingPage = new FieldMappingPage(configManager);
        fieldMappingPage.setNavigator(navigator);

        showPage("dataSource");
        taskScheduler.startAll();
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(5);
        sidebar.setPrefWidth(220);
        sidebar.setPadding(new Insets(20, 10, 10, 10));
        sidebar.getStyleClass().add("sidebar");

        Label title = new Label("简道云数据同步");
        title.getStyleClass().add("sidebar-title");
        title.setPadding(new Insets(0, 0, 15, 10));

        Separator separator = new Separator();
        separator.setPadding(new Insets(0, 0, 10, 0));

        ToggleButton btnDataSource = createNavButton("数据源配置", "dataSource");
        ToggleButton btnJdyConfig = createNavButton("简道云配置", "jdyConfig");
        ToggleButton btnSyncTask = createNavButton("同步任务", "syncTask");
        ToggleButton btnFieldMapping = createNavButton("字段映射", "fieldMapping");
        ToggleButton btnLog = createNavButton("运行日志", "log");

        sidebar.getChildren().addAll(title, separator, btnDataSource, btnJdyConfig, btnSyncTask, btnFieldMapping, btnLog);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        Label version = new Label("V1.1.0");
        version.getStyleClass().add("version-label");
        version.setAlignment(Pos.CENTER);
        sidebar.getChildren().add(version);

        return sidebar;
    }

    private ToggleButton createNavButton(String text, String pageId) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setPadding(new Insets(12, 15, 12, 15));

        button.setOnAction(e -> {
            if (button.isSelected()) {
                if (activeNavButton != null && activeNavButton != button) {
                    activeNavButton.setSelected(false);
                }
                activeNavButton = button;
                showPage(pageId);
            } else {
                button.setSelected(true);
            }
        });

        return button;
    }

    private void showPage(String pageId) {
        currentPageId = pageId;
        switch (pageId) {
            case "dataSource":
                contentArea.getChildren().setAll(dataSourcePage.getContent());
                break;
            case "jdyConfig":
                contentArea.getChildren().setAll(jdyConfigPage.getContent());
                break;
            case "fieldMapping":
                fieldMappingPage.refreshData();
                contentArea.getChildren().setAll(fieldMappingPage.getContent());
                break;
            case "syncTask":
                syncTaskPage.refreshData();
                contentArea.getChildren().setAll(syncTaskPage.getContent());
                break;
            case "log":
                if (logPage == null) logPage = new LogPage();
                contentArea.getChildren().setAll(logPage.getContent());
                break;
        }
    }

    private void setActiveNavButton(String pageId) {
        ToggleButton target = null;
        for (javafx.scene.Node node : ((VBox) root.getLeft()).getChildren()) {
            if (node instanceof ToggleButton) {
                ToggleButton tb = (ToggleButton) node;
                if (tb.getText().equals(getNavButtonText(pageId))) {
                    target = tb;
                    break;
                }
            }
        }
        if (target != null && target != activeNavButton) {
            if (activeNavButton != null) activeNavButton.setSelected(false);
            target.setSelected(true);
            activeNavButton = target;
        }
    }

    private String getNavButtonText(String pageId) {
        switch (pageId) {
            case "dataSource": return "数据源配置";
            case "jdyConfig": return "简道云配置";
            case "syncTask": return "同步任务";
            case "fieldMapping": return "字段映射";
            case "log": return "运行日志";
            default: return "";
        }
    }

    public void navigateTo(String pageId, String selectId) {
        showPage(pageId);
        setActiveNavButton(pageId);
        switch (pageId) {
            case "dataSource":
                dataSourcePage.selectItem(selectId);
                break;
            case "jdyConfig":
                jdyConfigPage.selectItem(selectId);
                break;
            case "syncTask":
                syncTaskPage.selectItem(selectId);
                break;
        }
    }

    public BorderPane getView() {
        return root;
    }

    public void shutdown() {
        taskScheduler.shutdown();
    }
}
