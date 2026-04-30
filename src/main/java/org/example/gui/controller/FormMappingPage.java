package org.example.gui.controller;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import org.example.gui.model.*;
import org.example.gui.service.ConfigManager;
import org.example.gui.service.ConnectionTestService;
import org.example.gui.service.JdbcUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormMappingPage {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(FormMappingPage.class.getName());

    private final ConfigManager configManager;
    private final BorderPane root;
    private final ComboBox<DataSourceConfig> dataSourceCombo;
    private final ComboBox<JdyAppConfig> jdyAppCombo;
    private final ComboBox<FormMappingConfig> mappingCombo;
    private final Label statusLabel;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private List<DataSourceConfig> dataSources;
    private List<JdyAppConfig> jdyApps;
    private List<FormMappingConfig> formMappings;
    private FormMappingConfig selectedMapping;
    private boolean isRefreshing = false;

    private TextField mainTableNameField;
    private TextField mainEntryIdField;
    private ComboBox<String> incrementModeCombo;
    private TextField incrementFieldField;
    private TextField watermarkField;
    private TableView<ColumnMapping> mainMappingTable;
    private List<ColumnMapping> mainColumnMappings;
    private final Map<String, List<ColumnMapping>> mainMappingCache = new HashMap<>();

    private final VBox subTableContainers = new VBox(10);
    private final List<SubTableEditor> subTableEditors = new ArrayList<>();

    private final VBox conditionsContainer = new VBox(6);
    private final List<QueryConditionEditor> queryConditionEditors = new ArrayList<>();
    private final ToggleGroup relationGroup = new ToggleGroup();
    private final CheckBox allowMultipleUpdateCheck = new CheckBox("允许多条更新");

    private TabPane mainTabPane;
    private Tab mainTableTab;
    private Tab queryTab;
    private Tab subTablesTab;
    private Tab pullTab;

    private TableView<ColumnMapping> pullMappingTable;
    private List<ColumnMapping> pullColumnMappings;
    private TextField pullMatchFieldField;

    private BiConsumer<String, String> navigator;

    public FormMappingPage(ConfigManager configManager) {
        this.configManager = configManager;
        this.dataSources = new ArrayList<>(configManager.loadDataSources());
        this.jdyApps = new ArrayList<>(configManager.loadJdyApps());
        this.formMappings = new ArrayList<>(configManager.loadFormMappings());
        this.root = new BorderPane();

        this.dataSourceCombo = createDataSourceCombo();
        this.jdyAppCombo = createJdyAppCombo();
        this.mappingCombo = createMappingCombo();

        this.statusLabel = new Label();
        this.statusLabel.getStyleClass().add("status-label");

        setupLayout();
        setupListeners();
    }

    private ComboBox<DataSourceConfig> createDataSourceCombo() {
        ComboBox<DataSourceConfig> combo = new ComboBox<>();
        combo.getItems().addAll(dataSources);
        combo.setPromptText("选择数据源");
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DataSourceConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getName());
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DataSourceConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getName());
            }
        });
        combo.setPrefWidth(180);
        return combo;
    }

    private ComboBox<JdyAppConfig> createJdyAppCombo() {
        ComboBox<JdyAppConfig> combo = new ComboBox<>();
        combo.getItems().addAll(jdyApps);
        combo.setPromptText("选择简道云应用");
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(JdyAppConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getName());
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(JdyAppConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getName());
            }
        });
        combo.setPrefWidth(180);
        return combo;
    }

    private ComboBox<FormMappingConfig> createMappingCombo() {
        ComboBox<FormMappingConfig> combo = new ComboBox<>();
        combo.getItems().addAll(formMappings);
        combo.setPromptText("选择映射");
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(FormMappingConfig item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    DataSourceConfig ds = dataSources.stream()
                            .filter(d -> d.getId().equals(item.getDataSourceId()))
                            .findFirst().orElse(null);
                    String dsName = ds != null ? ds.getName() : "?";
                    String displayName = item.getName();
                    if (displayName.contains(" -> ")) {
                        displayName = displayName.split(" -> ")[0];
                    }
                    setText(displayName + " [" + dsName + "]");
                }
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(FormMappingConfig item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    DataSourceConfig ds = dataSources.stream()
                            .filter(d -> d.getId().equals(item.getDataSourceId()))
                            .findFirst().orElse(null);
                    String dsName = ds != null ? ds.getName() : "?";
                    String displayName = item.getName();
                    if (displayName.contains(" -> ")) {
                        displayName = displayName.split(" -> ")[0];
                    }
                    setText(displayName + " [" + dsName + "]");
                }
            }
        });
        combo.setPrefWidth(180);
        return combo;
    }

    public void setNavigator(BiConsumer<String, String> navigator) {
        this.navigator = navigator;
    }

    public void refreshData() {
        dataSources = new ArrayList<>(configManager.loadDataSources());
        jdyApps = new ArrayList<>(configManager.loadJdyApps());
        formMappings = new ArrayList<>(configManager.loadFormMappings());

        dataSourceCombo.getItems().clear();
        dataSourceCombo.getItems().addAll(dataSources);
        jdyAppCombo.getItems().clear();
        jdyAppCombo.getItems().addAll(jdyApps);
        mappingCombo.getItems().clear();
        mappingCombo.getItems().addAll(formMappings);
    }

    private void setupLayout() {
        VBox topSection = createTopSection();
        mainTabPane = createMainTabPane();
        HBox bottomBar = createBottomBar();

        VBox contentBox = new VBox(12);
        contentBox.setPadding(new Insets(15, 20, 15, 20));
        contentBox.getChildren().addAll(topSection, mainTabPane, bottomBar);
        VBox.setVgrow(mainTabPane, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("transparent-scroll");
        scrollPane.setContent(contentBox);
        scrollPane.setFitToHeight(true);

        root.setCenter(scrollPane);

        showConfigSections(false);
    }

    private VBox createTopSection() {
        VBox topBox = new VBox(10);

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("表单映射配置");
        titleLabel.getStyleClass().add("page-title");
        titleRow.getChildren().add(titleLabel);

        HBox selectorRow = new HBox(8);
        selectorRow.setAlignment(Pos.CENTER_LEFT);
        selectorRow.getStyleClass().add("selector-bar");

        Label mapLabel = new Label("映射:");
        Button newConfigBtn = new Button("+ 新建");
        newConfigBtn.getStyleClass().add("btn-primary");
        newConfigBtn.setStyle("-fx-padding: 6 12;");
        newConfigBtn.setOnAction(e -> startNewConfig());

        Label dsLabel = new Label("数据源:");
        Label appLabel = new Label("应用:");

        selectorRow.getChildren().addAll(mapLabel, mappingCombo, newConfigBtn, 
                new Separator(Orientation.VERTICAL), dsLabel, dataSourceCombo, appLabel, jdyAppCombo);

        topBox.getChildren().addAll(titleRow, selectorRow);
        return topBox;
    }

    private TabPane createMainTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("main-tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        mainTableTab = createMainTableTab();
        queryTab = createQueryTab();
        subTablesTab = createSubTablesTab();
        pullTab = createPullTab();

        tabPane.getTabs().addAll(mainTableTab, queryTab, subTablesTab, pullTab);

        return tabPane;
    }

    private Tab createMainTableTab() {
        Tab tab = new Tab("主表映射");
        tab.getStyleClass().add("main-tab");

        VBox content = new VBox(10);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("tab-content");

        VBox basicInfoCard = createBasicInfoCard();
        VBox mappingCard = createMappingCard();

        content.getChildren().addAll(basicInfoCard, mappingCard);
        VBox.setVgrow(mappingCard, Priority.ALWAYS);

        tab.setContent(content);
        return tab;
    }

    private VBox createBasicInfoCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("info-card");

        HBox row1 = new HBox(8);
        row1.setAlignment(Pos.CENTER_LEFT);
        Button loadTablesBtn = new Button("加载表");
        loadTablesBtn.getStyleClass().add("btn-secondary");
        loadTablesBtn.setStyle("-fx-padding: 5 10;");
        loadTablesBtn.setOnAction(e -> loadTablesForMain());
        mainTableNameField = new TextField();
        mainTableNameField.setPromptText("表名");
        mainTableNameField.setPrefWidth(180);
        mainEntryIdField = new TextField();
        mainEntryIdField.setPromptText("简道云表单ID");
        mainEntryIdField.setPrefWidth(200);
        row1.getChildren().addAll(loadTablesBtn, new Label("主表:"), mainTableNameField, new Label("表单ID:"), mainEntryIdField);

        HBox row2 = new HBox(8);
        row2.setAlignment(Pos.CENTER_LEFT);
        incrementModeCombo = new ComboBox<>();
        incrementModeCombo.getItems().addAll("自增ID", "时间戳");
        incrementModeCombo.setValue("自增ID");
        incrementModeCombo.setPrefWidth(100);
        incrementFieldField = new TextField();
        incrementFieldField.setPromptText("如 id 或 update_time");
        incrementFieldField.setPrefWidth(150);
        watermarkField = new TextField();
        watermarkField.setPromptText("同步起始位置");
        watermarkField.setPrefWidth(150);
        row2.getChildren().addAll(new Label("增量方式:"), incrementModeCombo, new Label("增量字段:"), incrementFieldField, new Label("当前水印:"), watermarkField);

        card.getChildren().addAll(row1, row2);
        return card;
    }

    private VBox createMappingCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("mapping-card");
        VBox.setVgrow(card, Priority.ALWAYS);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("字段映射");
        title.getStyleClass().add("section-title");

        Button loadColumnsBtn = new Button("加载字段");
        loadColumnsBtn.getStyleClass().add("btn-secondary");
        loadColumnsBtn.setStyle("-fx-padding: 5 10;");
        loadColumnsBtn.setOnAction(e -> loadMainColumns());

        Button quickFillBtn = new Button("快速填充");
        quickFillBtn.getStyleClass().add("btn-secondary");
        quickFillBtn.setStyle("-fx-padding: 5 10;");
        quickFillBtn.setTooltip(new Tooltip("从第一个已填字段ID开始递增填充"));
        quickFillBtn.setOnAction(e -> quickFillMain());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, loadColumnsBtn, quickFillBtn);

        mainMappingTable = new TableView<>();
        mainMappingTable.getStyleClass().add("data-table");
        setupMainMappingTable();
        VBox.setVgrow(mainMappingTable, Priority.ALWAYS);

        card.getChildren().addAll(header, mainMappingTable);
        return card;
    }

    private void setupMainMappingTable() {
        TableColumn<ColumnMapping, String> colName = new TableColumn<>("数据库字段");
        colName.setCellValueFactory(cell -> cell.getValue().columnNameProperty());
        colName.setPrefWidth(140);
        colName.setSortable(false);

        TableColumn<ColumnMapping, String> colType = new TableColumn<>("类型");
        colType.setCellValueFactory(cell -> cell.getValue().columnTypeProperty());
        colType.setPrefWidth(80);
        colType.setSortable(false);

        TableColumn<ColumnMapping, String> colComment = new TableColumn<>("注释");
        colComment.setCellValueFactory(cell -> cell.getValue().columnCommentProperty());
        colComment.setPrefWidth(120);
        colComment.setSortable(false);

        TableColumn<ColumnMapping, String> colWidget = new TableColumn<>("简道云字段ID");
        colWidget.setCellValueFactory(cell -> cell.getValue().widgetIdProperty());
        colWidget.setSortable(false);
        colWidget.setCellFactory(column -> new EditingTextCell<>(cm -> cm.getWidgetId(), (cm, val) -> cm.setWidgetId(val)));

        mainMappingTable.getColumns().addAll(colName, colType, colComment, colWidget);
        mainMappingTable.setEditable(true);
        mainMappingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        mainMappingTable.setMinHeight(200);
    }

    private Tab createQueryTab() {
        Tab tab = new Tab("查询条件");
        tab.getStyleClass().add("main-tab");

        VBox content = new VBox(10);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("tab-content");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("查询匹配条件");
        title.getStyleClass().add("section-title");
        Label hint = new Label("（用于判断数据是否已存在）");
        hint.getStyleClass().add("description-label");
        header.getChildren().addAll(title, hint);

        HBox relationBar = new HBox(15);
        relationBar.setAlignment(Pos.CENTER_LEFT);
        Label relationLabel = new Label("条件关系:");
        RadioButton andRadio = new RadioButton("AND（全部满足）");
        andRadio.setToggleGroup(relationGroup);
        andRadio.setSelected(true);
        RadioButton orRadio = new RadioButton("OR（满足任一）");
        orRadio.setToggleGroup(relationGroup);
        relationBar.getChildren().addAll(relationLabel, andRadio, orRadio, new Separator(Orientation.VERTICAL), allowMultipleUpdateCheck);

        HBox btnBar = new HBox(8);
        btnBar.setAlignment(Pos.CENTER_LEFT);
        Button addConditionBtn = new Button("+ 添加条件");
        addConditionBtn.getStyleClass().add("btn-secondary");
        addConditionBtn.setStyle("-fx-padding: 5 10;");
        addConditionBtn.setOnAction(e -> {
            if (queryConditionEditors.size() < 3) {
                addQueryConditionEditor();
            } else {
                showStatus(false, "最多只能添加 3 个查询条件");
            }
        });
        btnBar.getChildren().add(addConditionBtn);

        conditionsContainer.getStyleClass().add("conditions-container");

        content.getChildren().addAll(header, relationBar, btnBar, conditionsContainer);
        VBox.setVgrow(conditionsContainer, Priority.ALWAYS);

        tab.setContent(content);
        return tab;
    }

    private void addQueryConditionEditor() {
        QueryConditionEditor editor = new QueryConditionEditor();
        if (selectedMapping != null && selectedMapping.getMainFieldMapping() != null) {
            editor.setAvailableFields(selectedMapping.getMainFieldMapping());
        }
        queryConditionEditors.add(editor);
        conditionsContainer.getChildren().add(editor.getContent());
    }

    private Tab createPullTab() {
        Tab tab = new Tab("拉取映射");
        tab.getStyleClass().add("main-tab");

        VBox content = new VBox(10);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("tab-content");

        Label descLabel = new Label("配置从简道云拉取数据到本地数据库的字段映射。映射方向：简道云控件ID → 数据库字段。");
        descLabel.getStyleClass().add("description-label");
        descLabel.setWrapText(true);

        HBox matchFieldRow = new HBox(10);
        matchFieldRow.setAlignment(Pos.CENTER_LEFT);
        Label matchFieldLabel = new Label("匹配字段（数据库）：");
        pullMatchFieldField = new TextField();
        pullMatchFieldField.setPromptText("用于判断本地是否已存在记录的字段名，如 id 或 order_no");
        pullMatchFieldField.setPrefWidth(300);
        matchFieldRow.getChildren().addAll(matchFieldLabel, pullMatchFieldField);

        VBox mappingCard = new VBox(10);
        mappingCard.setPadding(new Insets(10));
        mappingCard.getStyleClass().add("mapping-card");

        Label mappingTitle = new Label("拉取字段映射（简道云控件ID → 数据库字段）");
        mappingTitle.getStyleClass().add("section-title");

        pullMappingTable = new TableView<>();
        pullMappingTable.getStyleClass().add("data-table");
        pullMappingTable.setEditable(true);
        pullMappingTable.setPrefHeight(400);

        TableColumn<ColumnMapping, String> widgetIdCol = new TableColumn<>("简道云控件ID");
        widgetIdCol.setCellValueFactory(cell -> cell.getValue().widgetIdProperty());
        widgetIdCol.setCellFactory(TextFieldTableCell.forTableColumn());
        widgetIdCol.setOnEditCommit(e -> e.getRowValue().setWidgetId(e.getNewValue()));
        widgetIdCol.setPrefWidth(250);

        TableColumn<ColumnMapping, String> dbFieldCol = new TableColumn<>("数据库字段");
        dbFieldCol.setCellValueFactory(cell -> cell.getValue().columnNameProperty());
        dbFieldCol.setCellFactory(TextFieldTableCell.forTableColumn());
        dbFieldCol.setOnEditCommit(e -> e.getRowValue().setColumnName(e.getNewValue()));
        dbFieldCol.setPrefWidth(250);

        pullMappingTable.getColumns().addAll(widgetIdCol, dbFieldCol);

        HBox pullBtnBar = new HBox(10);
        Button addPullRowBtn = new Button("添加行");
        addPullRowBtn.getStyleClass().add("btn-secondary");
        addPullRowBtn.setOnAction(e -> {
            if (pullColumnMappings == null) pullColumnMappings = new ArrayList<>();
            pullColumnMappings.add(new ColumnMapping("", "", "", ""));
            pullMappingTable.getItems().setAll(pullColumnMappings);
        });

        Button removePullRowBtn = new Button("删除选中行");
        removePullRowBtn.getStyleClass().add("btn-danger");
        removePullRowBtn.setOnAction(e -> {
            ColumnMapping selected = pullMappingTable.getSelectionModel().getSelectedItem();
            if (selected != null && pullColumnMappings != null) {
                pullColumnMappings.remove(selected);
                pullMappingTable.getItems().setAll(pullColumnMappings);
            }
        });

        Button autoFillPullBtn = new Button("从推送映射自动填充");
        autoFillPullBtn.getStyleClass().add("btn-secondary");
        autoFillPullBtn.setOnAction(e -> autoFillPullMapping());

        pullBtnBar.getChildren().addAll(addPullRowBtn, removePullRowBtn, autoFillPullBtn);

        mappingCard.getChildren().addAll(mappingTitle, pullMappingTable, pullBtnBar);
        VBox.setVgrow(pullMappingTable, Priority.ALWAYS);

        content.getChildren().addAll(descLabel, matchFieldRow, mappingCard);
        VBox.setVgrow(mappingCard, Priority.ALWAYS);

        tab.setContent(content);
        return tab;
    }

    private void autoFillPullMapping() {
        if (selectedMapping == null || mainColumnMappings == null) {
            showStatus(false, "请先配置推送映射");
            return;
        }

        pullColumnMappings = new ArrayList<>();
        for (ColumnMapping cm : mainColumnMappings) {
            if (cm.getWidgetId() != null && !cm.getWidgetId().trim().isEmpty()
                    && cm.getColumnName() != null && !cm.getColumnName().trim().isEmpty()) {
                pullColumnMappings.add(new ColumnMapping(cm.getColumnName().trim(), "", "", cm.getWidgetId().trim()));
            }
        }
        pullMappingTable.getItems().setAll(pullColumnMappings);
        showStatus(true, "已从推送映射自动填充 " + pullColumnMappings.size() + " 个字段");
    }

    private Tab createSubTablesTab() {
        Tab tab = new Tab("子表映射");
        tab.getStyleClass().add("main-tab");

        VBox content = new VBox(10);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("tab-content");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("子表映射配置");
        title.getStyleClass().add("section-title");

        Button addSubTableBtn = new Button("+ 添加子表");
        addSubTableBtn.getStyleClass().add("btn-primary");
        addSubTableBtn.setStyle("-fx-padding: 5 12;");
        addSubTableBtn.setOnAction(e -> addSubTableEditor());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, addSubTableBtn);

        subTableContainers.setSpacing(10);

        ScrollPane subTableScroll = new ScrollPane();
        subTableScroll.setFitToWidth(true);
        subTableScroll.setContent(subTableContainers);
        subTableScroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(subTableScroll, Priority.ALWAYS);

        content.getChildren().addAll(header, subTableScroll);

        tab.setContent(content);
        return tab;
    }

    private void addSubTableEditor() {
        SubTableEditor editor = new SubTableEditor();
        subTableEditors.add(editor);
        subTableContainers.getChildren().add(editor.getContent());
    }

    private void removeSubTableEditor(SubTableEditor editor) {
        subTableEditors.remove(editor);
        subTableContainers.getChildren().remove(editor.getContent());
    }

    private HBox createBottomBar() {
        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 0, 0, 0));

        Button newBtn = new Button("新建");
        newBtn.getStyleClass().add("btn-secondary");
        newBtn.setOnAction(e -> clearForm());

        Button saveBtn = new Button("保存配置");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setStyle("-fx-padding: 8 24;");
        saveBtn.setOnAction(e -> saveMapping());

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> deleteMapping());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomBar.getChildren().addAll(newBtn, saveBtn, deleteBtn, spacer, statusLabel);
        return bottomBar;
    }

    private void setupListeners() {
        mappingCombo.setOnAction(e -> {
            if (isRefreshing) return;
            FormMappingConfig mapping = mappingCombo.getValue();
            if (mapping != null) {
                selectedMapping = mapping;
                loadMappingToForm(mapping);
            } else {
                selectedMapping = null;
                clearForm();
            }
        });

        dataSourceCombo.setOnAction(e -> {
            mainMappingCache.clear();
            mainColumnMappings = null;
            mainMappingTable.getItems().clear();
            for (SubTableEditor editor : subTableEditors) {
                editor.clearCache();
            }
        });
    }

    private void loadMappingToForm(FormMappingConfig mapping) {
        showConfigSections(true);

        DataSourceConfig ds = dataSources.stream()
                .filter(d -> d.getId().equals(mapping.getDataSourceId()))
                .findFirst().orElse(null);
        dataSourceCombo.setValue(ds);

        JdyAppConfig app = jdyApps.stream()
                .filter(a -> a.getId().equals(mapping.getJdyAppId()))
                .findFirst().orElse(null);
        jdyAppCombo.setValue(app);

        mainTableNameField.setText(mapping.getMainTableName());
        mainEntryIdField.setText(mapping.getMainEntryId());

        incrementModeCombo.setValue("id".equals(mapping.getIncrementMode()) ? "自增ID" : "时间戳");
        incrementFieldField.setText(mapping.getIncrementField() != null ? mapping.getIncrementField() : "id");

        SyncProgress progress = configManager.loadSyncProgress();
        String watermark = progress.getLastSyncId(mapping.getId());
        if (watermark == null || watermark.isEmpty()) {
            if ("timestamp".equals(mapping.getIncrementMode())) {
                java.time.LocalDateTime todayStart = java.time.LocalDate.now().atStartOfDay();
                watermark = todayStart.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                watermark = "0";
            }
        }
        watermarkField.setText(watermark);

        if (ds == null) {
            return;
        }

        String cacheKey = ds.getId() + "::" + mapping.getMainTableName();

        if (mainMappingCache.containsKey(cacheKey)) {
            mainColumnMappings = mainMappingCache.get(cacheKey);
            mainMappingTable.getItems().clear();
            mainMappingTable.getItems().addAll(mainColumnMappings);
            loadExistingMainMapping(mapping);
            updateAllSubTableJoinConditionMainFields();
            showStatus(true, "已从缓存加载 " + mainColumnMappings.size() + " 个字段");
        } else {
            loadMainColumnsAndApplyMapping(mapping, ds);
        }

        subTableContainers.getChildren().clear();
        subTableEditors.clear();
        if (mapping.getSubTableMappings() != null) {
            for (SubTableMapping sub : mapping.getSubTableMappings()) {
                SubTableEditor editor = new SubTableEditor();
                editor.loadSubMapping(sub);
                subTableEditors.add(editor);
                subTableContainers.getChildren().add(editor.getContent());
            }
        }
        updateAllSubTableJoinConditionMainFields();

        pullMatchFieldField.setText(mapping.getPullMatchField() != null ? mapping.getPullMatchField() : "");
        pullColumnMappings = new ArrayList<>();
        if (mapping.getPullFieldMapping() != null) {
            for (Map.Entry<String, String> entry : mapping.getPullFieldMapping().entrySet()) {
                pullColumnMappings.add(new ColumnMapping(entry.getValue(), "", "", entry.getKey()));
            }
        }
        pullMappingTable.getItems().setAll(pullColumnMappings);

        conditionsContainer.getChildren().clear();
        queryConditionEditors.clear();
        QueryMatchConfig queryConfig = mapping.getQueryMatchConfig();
        if (queryConfig != null) {
            if ("or".equals(queryConfig.getRelation())) {
                relationGroup.getToggles().get(1).setSelected(true);
            } else {
                relationGroup.getToggles().get(0).setSelected(true);
            }
            allowMultipleUpdateCheck.setSelected(queryConfig.isAllowMultipleUpdate());

            if (queryConfig.getConditions() != null) {
                for (QueryCondition cond : queryConfig.getConditions()) {
                    QueryConditionEditor editor = new QueryConditionEditor();
                    editor.setAvailableFields(mapping.getMainFieldMapping());
                    editor.loadCondition(cond);
                    queryConditionEditors.add(editor);
                    conditionsContainer.getChildren().add(editor.getContent());
                }
            }
        }
    }

    private void loadMainColumnsAndApplyMapping(FormMappingConfig mapping, DataSourceConfig ds) {
        String tableName = mapping.getMainTableName();
        if (tableName == null || tableName.trim().isEmpty()) {
            return;
        }

        statusLabel.setText("正在加载主表字段...");
        executor.submit(() -> {
            List<JdbcUtils.ColumnDetail> details;
            try {
                details = JdbcUtils.loadColumnDetails(ds, tableName);
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        showStatus(false, "加载主表字段失败: " + e.getMessage()));
                return;
            }

            List<ColumnMapping> mappings = new ArrayList<>();
            for (JdbcUtils.ColumnDetail d : details) {
                mappings.add(new ColumnMapping(d.name, d.type, d.comment, ""));
            }

            javafx.application.Platform.runLater(() -> {
                mainColumnMappings = mappings;
                String cacheKey = ds.getId() + "::" + tableName;
                mainMappingCache.put(cacheKey, new ArrayList<>(mappings));
                mainMappingTable.getItems().clear();
                mainMappingTable.getItems().addAll(mappings);
                loadExistingMainMapping(mapping);
                mainMappingTable.refresh();
                updateAllSubTableJoinConditionMainFields();
                showStatus(true, "已加载 " + tableName + " 的 " + mappings.size() + " 个字段");
            });
        });
    }

    private void loadExistingMainMapping(FormMappingConfig mapping) {
        if (mapping.getMainFieldMapping() != null && mainColumnMappings != null) {
            Map<String, String> fm = mapping.getMainFieldMapping();
            for (ColumnMapping cm : mainColumnMappings) {
                String widgetId = fm.get(cm.getColumnName());
                if (widgetId != null) {
                    cm.setWidgetId(widgetId);
                }
            }
            mainMappingTable.refresh();
        }
    }

    private void loadTablesForMain() {
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
                    dialog.setHeaderText("选择主表");
                    dialog.setContentText("表名:");
                    dialog.showAndWait().ifPresent(tableName -> {
                        mainTableNameField.setText(tableName);
                        showStatus(true, "已选择: " + tableName);
                    });
                }
            });
        });
    }

    private void loadMainColumns() {
        DataSourceConfig ds = dataSourceCombo.getValue();
        String tableName = mainTableNameField.getText().trim();
        if (ds == null || tableName.isEmpty()) {
            showStatus(false, "请先选择数据源并填写表名");
            return;
        }

        String cacheKey = ds.getId() + "::" + tableName;
        if (mainMappingCache.containsKey(cacheKey)) {
            mainColumnMappings = mainMappingCache.get(cacheKey);
            mainMappingTable.getItems().clear();
            mainMappingTable.getItems().addAll(mainColumnMappings);
            if (selectedMapping != null) loadExistingMainMapping(selectedMapping);
            updateAllSubTableJoinConditionMainFields();
            showStatus(true, "已从缓存加载 " + mainColumnMappings.size() + " 个字段");
            return;
        }

        statusLabel.setText("正在加载字段...");
        executor.submit(() -> {
            List<JdbcUtils.ColumnDetail> details;
            try {
                details = JdbcUtils.loadColumnDetails(ds, tableName);
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        showStatus(false, "加载字段失败: " + e.getMessage()));
                return;
            }

            List<ColumnMapping> mappings = new ArrayList<>();
            for (JdbcUtils.ColumnDetail d : details) {
                mappings.add(new ColumnMapping(d.name, d.type, d.comment, ""));
            }

            javafx.application.Platform.runLater(() -> {
                mainColumnMappings = mappings;
                mainMappingCache.put(cacheKey, new ArrayList<>(mappings));
                mainMappingTable.getItems().clear();
                mainMappingTable.getItems().addAll(mappings);
                if (selectedMapping != null) loadExistingMainMapping(selectedMapping);
                updateAllSubTableJoinConditionMainFields();
                showStatus(true, "已加载 " + mappings.size() + " 个字段");
                updateQueryConditionFields();
            });
        });
    }

    private void updateAllSubTableJoinConditionMainFields() {
        if (mainColumnMappings == null) return;
        List<String> fields = new ArrayList<>();
        for (ColumnMapping cm : mainColumnMappings) {
            fields.add(cm.getColumnName());
        }
        for (SubTableEditor editor : subTableEditors) {
            editor.updateJoinConditionMainFields(fields);
        }
    }

    private void updateQueryConditionFields() {
        Map<String, String> availableFields = new LinkedHashMap<>();
        if (mainColumnMappings != null) {
            for (ColumnMapping cm : mainColumnMappings) {
                if (cm.getWidgetId() != null && !cm.getWidgetId().trim().isEmpty()) {
                    availableFields.put(cm.getColumnName(), cm.getWidgetId());
                }
            }
        }
        for (QueryConditionEditor editor : queryConditionEditors) {
            editor.setAvailableFields(availableFields);
        }
    }

    private void saveMapping() {
        try {
            DataSourceConfig ds = dataSourceCombo.getValue();
            JdyAppConfig app = jdyAppCombo.getValue();
            String mainTable = mainTableNameField.getText().trim();
            String mainEntry = mainEntryIdField.getText().trim();

            if (!validateSaveInput(ds, app, mainTable, mainEntry)) return;

            commitAllEdits();

            Map<String, String> mainMapping = collectMainMapping();
            if (mainMapping.isEmpty()) {
                showAlert("保存失败", "主表至少需要映射一个字段");
                return;
            }

            List<SubTableMapping> subMappings = collectSubMappings();
            QueryMatchConfig queryConfig = collectQueryConfig();

            if (selectedMapping == null) {
                selectedMapping = new FormMappingConfig();
                selectedMapping.setId(UUID.randomUUID().toString());
            }

            applyMappingToConfig(selectedMapping, ds, app, mainTable, mainEntry, mainMapping, subMappings, queryConfig);

            persistMapping(selectedMapping);

            String watermarkValue = watermarkField.getText().trim();
            if (!watermarkValue.isEmpty()) {
                SyncProgress progress = configManager.loadSyncProgress();
                progress.setLastSyncId(selectedMapping.getId(), watermarkValue);
                configManager.saveSyncProgress(progress);
            }

            refreshMappingCombo(selectedMapping);

            String subInfo = subMappings.isEmpty() ? "" : "，含 " + subMappings.size() + " 个子表";
            showStatus(true, "已保存: " + selectedMapping.getName() + " (" + mainMapping.size() + " 字段" + subInfo + ")");
            showAlert("保存成功", "配置已保存\n名称: " + selectedMapping.getName());
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            showAlert("保存异常", "错误: " + e.getMessage());
            showStatus(false, "保存失败: " + e.getClass().getName());
        }
    }

    private boolean validateSaveInput(DataSourceConfig ds, JdyAppConfig app, String mainTable, String mainEntry) {
        if (ds == null) {
            showAlert("保存失败", "请选择数据源");
            return false;
        }
        if (app == null) {
            showAlert("保存失败", "请选择简道云应用");
            return false;
        }
        if (mainTable.isEmpty()) {
            showAlert("保存失败", "请填写主表名");
            return false;
        }
        if (mainEntry.isEmpty()) {
            showAlert("保存失败", "请填写表单ID");
            return false;
        }
        return true;
    }

    private Map<String, String> collectMainMapping() {
        Map<String, String> mainMapping = new LinkedHashMap<>();
        if (mainColumnMappings != null) {
            for (ColumnMapping cm : mainColumnMappings) {
                if (cm == null) continue;
                String colName = cm.getColumnName();
                String wid = cm.getWidgetId();
                if (colName == null || colName.trim().isEmpty()) continue;
                if (wid != null) wid = wid.trim();
                if (wid == null || wid.isEmpty()) continue;
                mainMapping.put(colName, wid);
            }
        }
        return mainMapping;
    }

    private List<SubTableMapping> collectSubMappings() {
        List<SubTableMapping> subMappings = new ArrayList<>();
        if (subTableEditors != null) {
            for (SubTableEditor editor : subTableEditors) {
                try {
                    SubTableMapping sub = editor.buildSubMapping();
                    if (sub != null) {
                        subMappings.add(sub);
                    }
                } catch (Exception e) {
                    logger.warning("构建子表映射失败: " + e.getMessage());
                }
            }
        }
        return subMappings;
    }

    private QueryMatchConfig collectQueryConfig() {
        QueryMatchConfig queryConfig = new QueryMatchConfig();
        RadioButton selectedRelation = (RadioButton) relationGroup.getSelectedToggle();
        queryConfig.setRelation(selectedRelation != null && selectedRelation.getText().contains("OR") ? "or" : "and");
        queryConfig.setAllowMultipleUpdate(allowMultipleUpdateCheck.isSelected());

        List<QueryCondition> queryConditions = new ArrayList<>();
        for (QueryConditionEditor editor : queryConditionEditors) {
            QueryCondition cond = editor.buildCondition();
            if (cond != null) {
                queryConditions.add(cond);
            }
        }
        queryConfig.setConditions(queryConditions);
        return queryConfig;
    }

    private void applyMappingToConfig(FormMappingConfig config, DataSourceConfig ds, JdyAppConfig app,
                                       String mainTable, String mainEntry,
                                       Map<String, String> mainMapping, List<SubTableMapping> subMappings,
                                       QueryMatchConfig queryConfig) {
        config.setName(mainTable);
        config.setDataSourceId(ds.getId());
        config.setJdyAppId(app.getId());
        config.setMainTableName(mainTable);
        config.setMainEntryId(mainEntry);
        config.setMainFieldMapping(new LinkedHashMap<>(mainMapping));
        config.setSubTableMappings(new ArrayList<>(subMappings));
        config.setQueryMatchConfig(queryConfig);
        config.setIncrementMode("自增ID".equals(incrementModeCombo.getValue()) ? "id" : "timestamp");
        config.setIncrementField(incrementFieldField.getText().trim());
        config.setPullMatchField(pullMatchFieldField.getText().trim());

        Map<String, String> pullMapping = new LinkedHashMap<>();
        if (pullColumnMappings != null) {
            for (ColumnMapping cm : pullColumnMappings) {
                String widgetId = cm.getWidgetId();
                String dbField = cm.getColumnName();
                if (widgetId != null && !widgetId.trim().isEmpty() && dbField != null && !dbField.trim().isEmpty()) {
                    pullMapping.put(widgetId.trim(), dbField.trim());
                }
            }
        }
        config.setPullFieldMapping(pullMapping);
    }

    private void persistMapping(FormMappingConfig mapping) {
        List<FormMappingConfig> mappings = new ArrayList<>(configManager.loadFormMappings());
        boolean found = false;
        for (int i = 0; i < mappings.size(); i++) {
            if (mappings.get(i).getId().equals(mapping.getId())) {
                mappings.set(i, mapping);
                found = true;
                break;
            }
        }
        if (!found) {
            mappings.add(mapping);
        }
        configManager.saveFormMappings(mappings);
    }

    private void refreshMappingCombo(FormMappingConfig savedMapping) {
        isRefreshing = true;
        try {
            mappingCombo.getItems().clear();
            mappingCombo.getItems().addAll(configManager.loadFormMappings());
            mappingCombo.setValue(savedMapping);
        } finally {
            isRefreshing = false;
        }
    }

    private void deleteMapping() {
        if (selectedMapping == null) {
            showStatus(false, "请先选择要删除的映射配置");
            return;
        }

        List<SyncTaskConfig> allTasks = configManager.loadSyncTasks();
        List<String> referencingTasks = new ArrayList<>();
        for (SyncTaskConfig task : allTasks) {
            if (task.getFormMappingIds() != null && task.getFormMappingIds().contains(selectedMapping.getId())) {
                referencingTasks.add(task.getName());
            }
        }

        if (!referencingTasks.isEmpty()) {
            String taskList = String.join("\n- ", referencingTasks);
            showAlert("无法删除", "该映射被以下任务引用：\n- " + taskList);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("删除表单映射配置");
        confirm.setContentText("确定要删除 \"" + selectedMapping.getName() + "\" 吗？");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                List<FormMappingConfig> mappings = new ArrayList<>(configManager.loadFormMappings());
                mappings.removeIf(m -> m.getId().equals(selectedMapping.getId()));
                configManager.saveFormMappings(mappings);

                isRefreshing = true;
                try {
                    mappingCombo.getItems().clear();
                    mappingCombo.getItems().addAll(mappings);
                    mappingCombo.getSelectionModel().clearSelection();
                } finally {
                    isRefreshing = false;
                }

                clearForm();
                showStatus(true, "映射配置已删除");
            }
        });
    }

    private void showAlert(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void commitAllEdits() {
        mainMappingTable.refresh();
        for (SubTableEditor editor : subTableEditors) {
            editor.commitEdit();
        }
    }

    private void quickFillMain() {
        commitAllEdits();
        int filledCount = 0;

        if (mainColumnMappings != null && !mainColumnMappings.isEmpty()) {
            filledCount += quickFillMappings(mainColumnMappings);
        }

        if (filledCount > 0) {
            mainMappingTable.refresh();
            showStatus(true, "已自动填充 " + filledCount + " 个字段");
        } else {
            showStatus(false, "请先填写一个字段ID");
        }
    }

    private int quickFillMappings(List<ColumnMapping> mappings) {
        int lastFilledIndex = -1;
        String lastWidgetId = null;
        String prefix = null;
        long lastNum = -1;

        Pattern pattern = Pattern.compile("^(.+?)(\\d+)$");

        for (int i = mappings.size() - 1; i >= 0; i--) {
            String wid = mappings.get(i).getWidgetId();
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
            return 0;
        }

        int fillCount = 0;
        long currentNum = lastNum;
        for (int i = lastFilledIndex + 1; i < mappings.size(); i++) {
            ColumnMapping cm = mappings.get(i);
            if (cm.getWidgetId() == null || cm.getWidgetId().trim().isEmpty()) {
                currentNum++;
                cm.setWidgetId(prefix + currentNum);
                fillCount++;
            }
        }
        return fillCount;
    }

    private void startNewConfig() {
        clearForm();
        showConfigSections(true);
    }

    private void clearForm() {
        selectedMapping = null;
        mappingCombo.getSelectionModel().clearSelection();
        dataSourceCombo.getSelectionModel().clearSelection();
        jdyAppCombo.getSelectionModel().clearSelection();
        mainTableNameField.clear();
        mainEntryIdField.clear();
        incrementModeCombo.setValue("自增ID");
        incrementFieldField.clear();
        watermarkField.clear();
        mainColumnMappings = null;
        mainMappingTable.getItems().clear();
        mainMappingCache.clear();

        subTableContainers.getChildren().clear();
        subTableEditors.clear();

        conditionsContainer.getChildren().clear();
        queryConditionEditors.clear();
        relationGroup.getToggles().get(0).setSelected(true);
        allowMultipleUpdateCheck.setSelected(false);

        statusLabel.setText("");

        showConfigSections(false);
    }

    private void showConfigSections(boolean show) {
        mainTabPane.setVisible(show);
        mainTabPane.setManaged(show);
    }

    private void showStatus(boolean success, String message) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-success", "status-error");
        statusLabel.getStyleClass().add(success ? "status-success" : "status-error");
    }

    public class SubColumnMapping {
        private final javafx.beans.property.SimpleStringProperty columnName;
        private final javafx.beans.property.SimpleStringProperty columnType;
        private final javafx.beans.property.SimpleStringProperty columnComment;
        private final javafx.beans.property.SimpleStringProperty widgetPrefix;
        private final javafx.beans.property.SimpleStringProperty widgetSuffix;

        public SubColumnMapping(String columnName, String columnType, String columnComment, String widgetPrefix, String widgetSuffix) {
            this.columnName = new javafx.beans.property.SimpleStringProperty(columnName != null ? columnName : "");
            this.columnType = new javafx.beans.property.SimpleStringProperty(columnType != null ? columnType : "");
            this.columnComment = new javafx.beans.property.SimpleStringProperty(columnComment != null ? columnComment : "");
            this.widgetPrefix = new javafx.beans.property.SimpleStringProperty(widgetPrefix != null ? widgetPrefix : "_widget_");
            this.widgetSuffix = new javafx.beans.property.SimpleStringProperty(widgetSuffix != null ? widgetSuffix : "");
        }

        public String getColumnName() { return columnName.get(); }
        public void setColumnName(String v) { columnName.set(v); }
        public javafx.beans.property.SimpleStringProperty columnNameProperty() { return columnName; }

        public String getColumnType() { return columnType.get(); }
        public void setColumnType(String v) { columnType.set(v); }
        public javafx.beans.property.SimpleStringProperty columnTypeProperty() { return columnType; }

        public String getColumnComment() { return columnComment.get(); }
        public void setColumnComment(String v) { columnComment.set(v); }
        public javafx.beans.property.SimpleStringProperty columnCommentProperty() { return columnComment; }

        public String getWidgetPrefix() { return widgetPrefix.get(); }
        public void setWidgetPrefix(String v) { widgetPrefix.set(v); }
        public javafx.beans.property.SimpleStringProperty widgetPrefixProperty() { return widgetPrefix; }

        public String getWidgetSuffix() { return widgetSuffix.get(); }
        public void setWidgetSuffix(String v) { widgetSuffix.set(v); }
        public javafx.beans.property.SimpleStringProperty widgetSuffixProperty() { return widgetSuffix; }
    }

    private class SubTableEditor {
        private final TitledPane titledPane;
        private final TextField subTableNameField;
        private final TextField subFormWidgetIdField;
        private final TableView<SubColumnMapping> subMappingTable;
        private List<SubColumnMapping> subColumnMappings;
        private final Map<String, List<SubColumnMapping>> subMappingCache = new HashMap<>();
        private final VBox content;
        private final VBox joinConditionsBox;
        private final List<JoinConditionRow> joinConditionRows = new ArrayList<>();

        SubTableEditor() {
            content = new VBox(8);
            content.setPadding(new Insets(8));
            content.getStyleClass().add("sub-table-card");

            titledPane = new TitledPane();
            titledPane.setText("子表");
            titledPane.setCollapsible(true);
            titledPane.setExpanded(true);
            titledPane.getStyleClass().add("sub-table-titled");

            VBox innerContent = new VBox(8);

            HBox row1 = new HBox(8);
            row1.setAlignment(Pos.CENTER_LEFT);
            Button loadSubTablesBtn = new Button("加载");
            loadSubTablesBtn.getStyleClass().add("btn-secondary");
            loadSubTablesBtn.setStyle("-fx-padding: 4 8;");
            loadSubTablesBtn.setOnAction(e -> loadSubTables());
            subTableNameField = new TextField();
            subTableNameField.setPromptText("子表名");
            subTableNameField.setPrefWidth(180);
            subFormWidgetIdField = new TextField();
            subFormWidgetIdField.setPromptText("容器Widget ID（如 _widget_123456）");
            subFormWidgetIdField.setPrefWidth(250);
            row1.getChildren().addAll(loadSubTablesBtn, new Label("表:"), subTableNameField, new Label("Widget:"), subFormWidgetIdField);

            joinConditionsBox = new VBox(4);
            joinConditionsBox.setPadding(new Insets(6));
            joinConditionsBox.getStyleClass().add("join-conditions-box");

            HBox joinHeader = new HBox(5);
            joinHeader.setAlignment(Pos.CENTER_LEFT);
            Label joinTitle = new Label("关联条件");
            joinTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");
            joinHeader.getChildren().addAll(joinTitle);
            joinConditionsBox.getChildren().add(joinHeader);

            addJoinConditionRow();

            subMappingTable = new TableView<>();
            subMappingTable.getStyleClass().add("data-table");
            subMappingTable.setMinHeight(120);
            setupSubMappingTable();
            VBox.setVgrow(subMappingTable, Priority.ALWAYS);

            HBox btnBar = new HBox(6);
            btnBar.setAlignment(Pos.CENTER_LEFT);
            Button loadSubColumnsBtn = new Button("加载字段");
            loadSubColumnsBtn.getStyleClass().add("btn-secondary");
            loadSubColumnsBtn.setStyle("-fx-padding: 4 8;");
            loadSubColumnsBtn.setOnAction(e -> loadSubColumns());
            Button subQuickFillBtn = new Button("快速填充");
            subQuickFillBtn.getStyleClass().add("btn-secondary");
            subQuickFillBtn.setStyle("-fx-padding: 4 8;");
            subQuickFillBtn.setOnAction(e -> quickFill());
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button removeBtn = new Button("删除");
            removeBtn.getStyleClass().add("btn-danger");
            removeBtn.setStyle("-fx-padding: 4 8;");
            removeBtn.setOnAction(e -> removeSubTableEditor(SubTableEditor.this));
            btnBar.getChildren().addAll(loadSubColumnsBtn, subQuickFillBtn, spacer, removeBtn);

            innerContent.getChildren().addAll(row1, joinConditionsBox, subMappingTable, btnBar);
            titledPane.setContent(innerContent);

            content.getChildren().add(titledPane);
        }

        private void addJoinConditionRow() {
            addJoinConditionRow(null, null);
        }

        private void addJoinConditionRow(String subField, String mainField) {
            JoinConditionRow row = new JoinConditionRow(subField, mainField);
            joinConditionRows.add(row);
            joinConditionsBox.getChildren().add(row.getRow());
            updateJoinConditionButtons();
        }

        private void removeJoinConditionRow(JoinConditionRow row) {
            joinConditionRows.remove(row);
            joinConditionsBox.getChildren().remove(row.getRow());
            updateJoinConditionButtons();
        }

        private void updateJoinConditionButtons() {
            for (int i = 0; i < joinConditionRows.size(); i++) {
                JoinConditionRow row = joinConditionRows.get(i);
                row.setRemovable(joinConditionRows.size() > 1);
                row.setCanAdd(i == joinConditionRows.size() - 1 && joinConditionRows.size() < 3);
            }
        }

        private class JoinConditionRow {
            private final HBox row;
            private final ComboBox<String> subFieldCombo;
            private final ComboBox<String> mainFieldCombo;
            private final Button addBtn;

            JoinConditionRow(String subField, String mainField) {
                row = new HBox(6);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(3, 0, 3, 0));

                subFieldCombo = new ComboBox<>();
                subFieldCombo.setPromptText("选择子表字段");
                subFieldCombo.setPrefWidth(160);
                if (subColumnMappings != null) {
                    for (SubColumnMapping scm : subColumnMappings) {
                        subFieldCombo.getItems().add(scm.getColumnName());
                    }
                }
                if (subField != null) {
                    subFieldCombo.setValue(subField);
                }

                Label eqLabel = new Label(" = ");

                mainFieldCombo = new ComboBox<>();
                mainFieldCombo.setPromptText("选择主表字段");
                mainFieldCombo.setPrefWidth(160);
                if (mainColumnMappings != null) {
                    for (ColumnMapping cm : mainColumnMappings) {
                        mainFieldCombo.getItems().add(cm.getColumnName());
                    }
                }
                if (mainField != null) {
                    mainFieldCombo.setValue(mainField);
                }

                Button removeBtn = new Button("×");
                removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #c00; -fx-cursor: hand; -fx-padding: 2 6; -fx-font-size: 14;");
                removeBtn.setOnAction(e -> removeJoinConditionRow(this));

                addBtn = new Button("+");
                addBtn.getStyleClass().add("btn-secondary");
                addBtn.setStyle("-fx-padding: 2 10; -fx-font-size: 14; -fx-font-weight: bold;");
                addBtn.setOnAction(e -> {
                    if (joinConditionRows.size() < 3) {
                        addJoinConditionRow();
                    }
                });

                row.getChildren().addAll(subFieldCombo, eqLabel, mainFieldCombo, removeBtn, addBtn);
            }

            HBox getRow() { return row; }

            String getSubField() {
                return subFieldCombo.getValue();
            }

            String getMainField() {
                return mainFieldCombo.getValue();
            }

            void setRemovable(boolean removable) {
                if (row.getChildren().size() >= 4) {
                    Button btn = (Button) row.getChildren().get(3);
                    btn.setVisible(removable);
                    btn.setManaged(removable);
                }
            }

            void setCanAdd(boolean canAdd) {
                addBtn.setVisible(canAdd);
                addBtn.setManaged(canAdd);
            }

            void updateSubFieldOptions(List<String> fields) {
                String current = subFieldCombo.getValue();
                subFieldCombo.getItems().clear();
                subFieldCombo.getItems().addAll(fields);
                if (current != null && fields.contains(current)) {
                    subFieldCombo.setValue(current);
                }
            }

            void updateMainFieldOptions(List<String> fields) {
                String current = mainFieldCombo.getValue();
                mainFieldCombo.getItems().clear();
                mainFieldCombo.getItems().addAll(fields);
                if (current != null && fields.contains(current)) {
                    mainFieldCombo.setValue(current);
                }
            }
        }

        private void setupSubMappingTable() {
            TableColumn<SubColumnMapping, String> colName = new TableColumn<>("字段");
            colName.setCellValueFactory(cell -> cell.getValue().columnNameProperty());
            colName.setPrefWidth(100);
            colName.setSortable(false);

            TableColumn<SubColumnMapping, String> colType = new TableColumn<>("类型");
            colType.setCellValueFactory(cell -> cell.getValue().columnTypeProperty());
            colType.setPrefWidth(60);
            colType.setSortable(false);

            TableColumn<SubColumnMapping, String> colComment = new TableColumn<>("注释");
            colComment.setCellValueFactory(cell -> cell.getValue().columnCommentProperty());
            colComment.setPrefWidth(80);
            colComment.setSortable(false);

            TableColumn<SubColumnMapping, String> colPrefix = new TableColumn<>("前缀");
            colPrefix.setCellValueFactory(cell -> cell.getValue().widgetPrefixProperty());
            colPrefix.setPrefWidth(80);
            colPrefix.setSortable(false);
            colPrefix.setCellFactory(column -> new EditingTextCell<>(scm -> scm.getWidgetPrefix(), (scm, val) -> scm.setWidgetPrefix(val.isEmpty() ? "_widget_" : val)));

            TableColumn<SubColumnMapping, String> colSuffix = new TableColumn<>("后缀");
            colSuffix.setCellValueFactory(cell -> cell.getValue().widgetSuffixProperty());
            colSuffix.setSortable(false);
            colSuffix.setCellFactory(column -> new EditingTextCell<>(scm -> scm.getWidgetSuffix(), (scm, val) -> scm.setWidgetSuffix(val)));

            subMappingTable.getColumns().addAll(colName, colType, colComment, colPrefix, colSuffix);
            subMappingTable.setEditable(true);
            subMappingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        }

        private void loadSubTables() {
            DataSourceConfig ds = dataSourceCombo.getValue();
            if (ds == null) {
                showStatus(false, "请先选择数据源");
                return;
            }
            executor.submit(() -> {
                List<String> tables = ConnectionTestService.fetchTableList(ds);
                javafx.application.Platform.runLater(() -> {
                    if (tables.isEmpty()) {
                        showStatus(false, "无法连接数据源或无表");
                    } else {
                        ChoiceDialog<String> dialog = new ChoiceDialog<>(tables.get(0), tables);
                        dialog.setTitle("选择子表");
                        dialog.setHeaderText("选择子表");
                        dialog.setContentText("表名:");
                        dialog.showAndWait().ifPresent(tableName -> {
                            subTableNameField.setText(tableName);
                            showStatus(true, "已选择: " + tableName);
                        });
                    }
                });
            });
        }

        private void loadSubColumns() {
            DataSourceConfig ds = dataSourceCombo.getValue();
            String tableName = subTableNameField.getText().trim();
            if (ds == null || tableName.isEmpty()) {
                showStatus(false, "请先填写子表名");
                return;
            }

            String cacheKey = ds.getId() + "::sub::" + tableName;
            if (subMappingCache.containsKey(cacheKey)) {
                subColumnMappings = subMappingCache.get(cacheKey);
                subMappingTable.getItems().clear();
                subMappingTable.getItems().addAll(subColumnMappings);
                updateJoinConditionSubFields();
                showStatus(true, "已从缓存加载 " + subColumnMappings.size() + " 个字段");
                return;
            }

            executor.submit(() -> {
                List<JdbcUtils.ColumnDetail> details;
                try {
                    details = JdbcUtils.loadColumnDetails(ds, tableName);
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() ->
                            showStatus(false, "加载子表字段失败: " + e.getMessage()));
                    return;
                }

                List<SubColumnMapping> mappings = new ArrayList<>();
                for (JdbcUtils.ColumnDetail d : details) {
                    mappings.add(new SubColumnMapping(d.name, d.type, d.comment, "_widget_", ""));
                }

                javafx.application.Platform.runLater(() -> {
                    subColumnMappings = mappings;
                    subMappingCache.put(cacheKey, new ArrayList<>(mappings));
                    subMappingTable.getItems().clear();
                    subMappingTable.getItems().addAll(mappings);
                    updateJoinConditionSubFields();
                    showStatus(true, "已加载 " + mappings.size() + " 个字段");
                });
            });
        }

        private void updateJoinConditionSubFields() {
            if (subColumnMappings == null) return;
            List<String> fields = new ArrayList<>();
            for (SubColumnMapping scm : subColumnMappings) {
                fields.add(scm.getColumnName());
            }
            for (JoinConditionRow row : joinConditionRows) {
                row.updateSubFieldOptions(fields);
            }
        }

        void updateJoinConditionMainFields(List<String> fields) {
            for (JoinConditionRow row : joinConditionRows) {
                row.updateMainFieldOptions(fields);
            }
        }

        SubTableMapping buildSubMapping() {
            String subTable = subTableNameField.getText().trim();
            String widgetId = subFormWidgetIdField.getText().trim();
            if (subTable.isEmpty() || widgetId.isEmpty()) {
                return null;
            }

            if (subColumnMappings == null || subColumnMappings.isEmpty()) {
                return null;
            }

            List<SubTableJoinCondition> joinConditions = new ArrayList<>();
            for (JoinConditionRow row : joinConditionRows) {
                String subField = row.getSubField();
                String mainField = row.getMainField();
                if (subField != null && !subField.isEmpty() && mainField != null && !mainField.isEmpty()) {
                    joinConditions.add(new SubTableJoinCondition(subField, mainField));
                }
            }

            if (joinConditions.isEmpty()) {
                showStatus(false, "请至少配置一个关联条件");
                return null;
            }

            Map<String, String> fieldMapping = new LinkedHashMap<>();
            for (SubColumnMapping scm : subMappingTable.getItems()) {
                String suffix = scm.getWidgetSuffix() != null ? scm.getWidgetSuffix().trim() : "";
                if (!suffix.isEmpty()) {
                    String prefix = scm.getWidgetPrefix() != null ? scm.getWidgetPrefix().trim() : "_widget_";
                    fieldMapping.put(scm.getColumnName(), prefix + suffix);
                }
            }

            if (fieldMapping.isEmpty()) {
                return null;
            }

            SubTableMapping mapping = new SubTableMapping();
            mapping.setId(UUID.randomUUID().toString());
            mapping.setSubTableName(subTable);
            mapping.setSubFormWidgetId(widgetId);
            mapping.setJoinConditions(joinConditions);
            mapping.setFieldMapping(fieldMapping);
            return mapping;
        }

        void loadSubMapping(SubTableMapping sub) {
            subTableNameField.setText(sub.getSubTableName());
            subFormWidgetIdField.setText(sub.getSubFormWidgetId());

            joinConditionRows.clear();
            joinConditionsBox.getChildren().clear();

            HBox joinHeader = new HBox(5);
            joinHeader.setAlignment(Pos.CENTER_LEFT);
            Label joinTitle = new Label("关联条件");
            joinTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");
            joinHeader.getChildren().addAll(joinTitle);
            joinConditionsBox.getChildren().add(joinHeader);

            DataSourceConfig ds = dataSourceCombo.getValue();

            List<SubTableJoinCondition> conditions = sub.getJoinConditions();
            if (conditions != null && !conditions.isEmpty()) {
                for (SubTableJoinCondition cond : conditions) {
                    addJoinConditionRow(cond.getSubTableField(), cond.getMainTableField());
                }
            } else {
                addJoinConditionRow();
            }

            if (ds == null) {
                return;
            }

            String cacheKey = ds.getId() + "::sub::" + sub.getSubTableName();

            if (subMappingCache.containsKey(cacheKey)) {
                subColumnMappings = subMappingCache.get(cacheKey);
                subMappingTable.getItems().clear();
                subMappingTable.getItems().addAll(subColumnMappings);
                applySubFieldMapping(sub);
                updateJoinConditionSubFields();
                subMappingTable.refresh();
            } else {
                loadSubColumnsAndApplyMapping(sub, ds);
            }
        }

        private void applySubFieldMapping(SubTableMapping sub) {
            if (sub.getFieldMapping() != null && subColumnMappings != null) {
                for (SubColumnMapping scm : subColumnMappings) {
                    String fullWidgetId = sub.getFieldMapping().get(scm.getColumnName());
                    if (fullWidgetId != null && fullWidgetId.startsWith("_widget_")) {
                        scm.setWidgetSuffix(fullWidgetId.substring("_widget_".length()));
                        scm.setWidgetPrefix("_widget_");
                    }
                }
            }
        }

        private void loadSubColumnsAndApplyMapping(SubTableMapping sub, DataSourceConfig ds) {
            executor.submit(() -> {
                List<JdbcUtils.ColumnDetail> details;
                try {
                    details = JdbcUtils.loadColumnDetails(ds, sub.getSubTableName());
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() ->
                            showStatus(false, "加载子表字段失败: " + e.getMessage()));
                    return;
                }

                List<SubColumnMapping> mappings = new ArrayList<>();
                for (JdbcUtils.ColumnDetail d : details) {
                    mappings.add(new SubColumnMapping(d.name, d.type, d.comment, "_widget_", ""));
                }

                javafx.application.Platform.runLater(() -> {
                    subColumnMappings = mappings;
                    String cacheKey = ds.getId() + "::sub::" + sub.getSubTableName();
                    subMappingCache.put(cacheKey, new ArrayList<>(mappings));
                    subMappingTable.getItems().clear();
                    subMappingTable.getItems().addAll(mappings);
                    applySubFieldMapping(sub);
                    updateJoinConditionSubFields();
                    subMappingTable.refresh();
                    showStatus(true, "已加载 " + sub.getSubTableName() + " 的 " + mappings.size() + " 个字段");
                });
            });
        }

        void clearCache() {
            subMappingCache.clear();
            subColumnMappings = null;
            subMappingTable.getItems().clear();
        }

        void commitEdit() {
            subMappingTable.refresh();
        }

        int quickFill() {
            commitEdit();

            if (subColumnMappings == null || subColumnMappings.isEmpty()) {
                showStatus(false, "请先加载子表字段");
                return 0;
            }

            int lastFilledIndex = -1;
            long lastNum = -1;

            for (int i = subColumnMappings.size() - 1; i >= 0; i--) {
                String suffix = subColumnMappings.get(i).getWidgetSuffix();
                if (suffix != null && !suffix.trim().isEmpty()) {
                    lastFilledIndex = i;
                    try {
                        lastNum = Long.parseLong(suffix.trim());
                    } catch (NumberFormatException e) {
                        lastNum = -1;
                    }
                    break;
                }
            }

            if (lastFilledIndex == -1 || lastNum == -1) {
                showStatus(false, "请先填写一个后缀数字");
                return 0;
            }

            int fillCount = 0;
            long currentNum = lastNum;
            for (int i = lastFilledIndex + 1; i < subColumnMappings.size(); i++) {
                SubColumnMapping scm = subColumnMappings.get(i);
                if (scm.getWidgetSuffix() == null || scm.getWidgetSuffix().trim().isEmpty()) {
                    currentNum++;
                    scm.setWidgetSuffix(String.valueOf(currentNum));
                    fillCount++;
                }
            }

            if (fillCount > 0) {
                subMappingTable.refresh();
                showStatus(true, "已填充 " + fillCount + " 个字段");
            } else {
                showStatus(false, "没有空白行需要填充");
            }
            return fillCount;
        }

        VBox getContent() {
            return content;
        }
    }

    public BorderPane getContent() {
        return root;
    }

    @FunctionalInterface
    public interface Getter<T, R> {
        R get(T t);
    }

    @FunctionalInterface
    public interface Setter<T, R> {
        void set(T t, R r);
    }

    private static class EditingTextCell<T> extends TableCell<T, String> {
        private final TextField textField;
        private final Getter<T, String> getter;
        private final Setter<T, String> setter;

        EditingTextCell(Getter<T, String> getter, Setter<T, String> setter) {
            this.getter = getter;
            this.setter = setter;
            this.textField = new TextField();
            textField.setOnAction(e -> commit());
            textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) commit();
            });
        }

        private void commit() {
            T row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null && !isEmpty()) {
                setter.set(row, textField.getText() != null ? textField.getText().trim() : "");
            }
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null) {
                setGraphic(null);
                return;
            }
            T row = getTableRow().getItem();
            if (row == null) {
                setGraphic(null);
                return;
            }
            try {
                String val = getter.get(row);
                textField.setText(val != null ? val : "");
                setGraphic(textField);
            } catch (Exception e) {
                logger.warning("EditingTextCell updateItem error: " + e.getMessage());
                setGraphic(null);
            }
        }
    }

    private class QueryConditionEditor {
        private final HBox content;
        private final ComboBox<String> fieldCombo;
        private final ComboBox<String> methodCombo;
        private final ComboBox<String> typeCombo;
        private QueryCondition condition;

        QueryConditionEditor() {
            content = new HBox(8);
            content.setAlignment(Pos.CENTER_LEFT);
            content.getStyleClass().add("condition-row");

            fieldCombo = new ComboBox<>();
            fieldCombo.setPromptText("选择字段");
            fieldCombo.setPrefWidth(180);

            methodCombo = new ComboBox<>();
            methodCombo.getItems().addAll(
                "eq", "ne", "in", "nin", "like", "empty", "not_empty",
                "range", "gt", "lt", "verified", "unverified", "all"
            );
            methodCombo.setValue("eq");
            methodCombo.setPrefWidth(100);

            typeCombo = new ComboBox<>();
            typeCombo.getItems().addAll("text", "number", "datetime", "phone", "flowstate", "combocheck");
            typeCombo.setValue("text");
            typeCombo.setPrefWidth(100);

            Button removeBtn = new Button("×");
            removeBtn.getStyleClass().add("btn-danger");
            removeBtn.setStyle("-fx-padding: 2 8;");
            removeBtn.setOnAction(e -> {
                queryConditionEditors.remove(this);
                conditionsContainer.getChildren().remove(content);
            });

            content.getChildren().addAll(
                new Label("字段:"), fieldCombo,
                new Label("方法:"), methodCombo,
                new Label("类型:"), typeCombo,
                removeBtn
            );
        }

        void setAvailableFields(Map<String, String> fieldMapping) {
            fieldCombo.getItems().clear();
            if (fieldMapping != null) {
                fieldCombo.getItems().addAll(fieldMapping.keySet());
            }
        }

        void loadCondition(QueryCondition cond) {
            this.condition = cond;
            if (cond != null) {
                fieldCombo.setValue(cond.getField());
                methodCombo.setValue(cond.getMethod());
                typeCombo.setValue(cond.getType());
            }
        }

        QueryCondition buildCondition() {
            String field = fieldCombo.getValue();
            String method = methodCombo.getValue();
            String type = typeCombo.getValue();

            if (field == null || field.trim().isEmpty()) {
                return null;
            }

            String widgetId = null;
            if (mainColumnMappings != null) {
                for (ColumnMapping cm : mainColumnMappings) {
                    if (cm.getColumnName().equals(field)) {
                        widgetId = cm.getWidgetId();
                        break;
                    }
                }
            }

            QueryCondition cond = new QueryCondition();
            cond.setField(field);
            cond.setWidgetId(widgetId);
            cond.setMethod(method != null ? method : "eq");
            cond.setType(type != null ? type : "text");
            return cond;
        }

        HBox getContent() {
            return content;
        }
    }
}
