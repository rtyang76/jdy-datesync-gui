package org.example.gui.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import javafx.scene.layout.*;
import org.example.gui.model.*;
import org.example.gui.service.ConfigManager;
import org.example.gui.service.ConnectionTestService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormMappingPage {

    private final ConfigManager configManager;
    private final BorderPane root;
    private final ComboBox<DataSourceConfig> dataSourceCombo;
    private final ComboBox<JdyAppConfig> jdyAppCombo;
    private final ComboBox<FormMappingConfig> mappingCombo;
    private final Label statusLabel;
    private final VBox mainTableSection;
    private final VBox subTablesSection;
    private final Button saveMappingBtn;
    private final Button mainQuickFillBtn;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private List<DataSourceConfig> dataSources;
    private List<JdyAppConfig> jdyApps;
    private List<FormMappingConfig> formMappings;
    private FormMappingConfig selectedMapping;

    private TextField mainTableNameField;
    private TextField mainEntryIdField;
    private TableView<ColumnMapping> mainMappingTable;
    private List<ColumnMapping> mainColumnMappings;
    private final Map<String, List<ColumnMapping>> mainMappingCache = new HashMap<>();

    private final VBox subTableContainers = new VBox(15);
    private final List<SubTableEditor> subTableEditors = new ArrayList<>();

    private BiConsumer<String, String> navigator;

    public FormMappingPage(ConfigManager configManager) {
        this.configManager = configManager;
        this.dataSources = new ArrayList<>(configManager.loadDataSources());
        this.jdyApps = new ArrayList<>(configManager.loadJdyApps());
        this.formMappings = new ArrayList<>(configManager.loadFormMappings());
        this.root = new BorderPane();

        this.dataSourceCombo = new ComboBox<>();
        this.dataSourceCombo.getItems().addAll(dataSources);
        this.dataSourceCombo.setPromptText("选择数据源");
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
        this.jdyAppCombo.setPromptText("选择简道云应用");
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

        this.mappingCombo = new ComboBox<>();
        this.mappingCombo.getItems().addAll(formMappings);
        this.mappingCombo.setPromptText("选择已有映射或新建");
        this.mappingCombo.setCellFactory(lv -> new ListCell<>() {
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
                    setText(item.getName() + "  [" + dsName + "]");
                }
            }
        });
        this.mappingCombo.setButtonCell(new ListCell<>() {
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
                    setText(item.getName() + "  [" + dsName + "]");
                }
            }
        });

        this.statusLabel = new Label();
        this.statusLabel.getStyleClass().add("status-label");

        this.mainTableSection = new VBox(10);
        this.subTablesSection = new VBox(10);

        this.saveMappingBtn = new Button("保存映射配置");
        this.saveMappingBtn.getStyleClass().add("btn-primary");
        this.saveMappingBtn.setOnAction(e -> saveMapping());

        this.mainQuickFillBtn = new Button("快速递增填充");
        this.mainQuickFillBtn.getStyleClass().add("btn-secondary");
        this.mainQuickFillBtn.setTooltip(new Tooltip("从第一个已填的简道云字段ID开始，自动递增填充下方空白行"));
        this.mainQuickFillBtn.setOnAction(e -> quickFillMain());

        setupLayout();
        setupListeners();
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
        Label titleLabel = new Label("表单映射配置");
        titleLabel.getStyleClass().add("page-title");

        Label descLabel = new Label("配置数据库表与简道云表单的映射关系，支持主表和多个子表。配置完成后可在同步任务中引用。");
        descLabel.getStyleClass().add("description-label");
        descLabel.setWrapText(true);

        HBox selectorBar = new HBox(10);
        selectorBar.setAlignment(Pos.CENTER_LEFT);
        Label mapLabel = new Label("映射配置:");
        Label dsLabel = new Label("数据源:");
        Label appLabel = new Label("简道云应用:");
        selectorBar.getChildren().addAll(mapLabel, mappingCombo, dsLabel, dataSourceCombo, appLabel, jdyAppCombo);

        setupMainTableSection();
        setupSubTablesSection();

        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 0, 0, 0));
        Button newBtn = new Button("新建");
        newBtn.getStyleClass().add("btn-secondary");
        newBtn.setOnAction(e -> clearForm());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bottomBar.getChildren().addAll(newBtn, saveMappingBtn, spacer, statusLabel);

        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));
        contentBox.getChildren().addAll(titleLabel, descLabel, selectorBar, mainTableSection, subTablesSection, bottomBar);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("transparent-scroll");
        scrollPane.setContent(contentBox);

        root.setCenter(scrollPane);
    }

    private void setupMainTableSection() {
        mainTableSection.getStyleClass().add("section-panel");
        mainTableSection.setPadding(new Insets(15));
        mainTableSection.setSpacing(10);

        Label sectionTitle = new Label("主表映射");
        sectionTitle.getStyleClass().add("section-title");

        HBox row1 = new HBox(8);
        row1.setAlignment(Pos.CENTER_LEFT);
        Button loadTablesBtn = new Button("从数据源加载");
        loadTablesBtn.getStyleClass().add("btn-secondary");
        loadTablesBtn.setOnAction(e -> loadTablesForMain());
        Label tableLabel = new Label("主表名:");
        mainTableNameField = new TextField();
        mainTableNameField.setPromptText("MySQL 表名");
        HBox.setHgrow(mainTableNameField, Priority.ALWAYS);
        Label entryLabel = new Label("表单ID:");
        mainEntryIdField = new TextField();
        mainEntryIdField.setPromptText("简道云表单 Entry ID");
        mainEntryIdField.setPrefWidth(200);
        row1.getChildren().addAll(loadTablesBtn, tableLabel, mainTableNameField, entryLabel, mainEntryIdField);

        mainMappingTable = new TableView<>();
        mainMappingTable.getStyleClass().add("data-table");
        setupMainMappingTable();
        VBox.setVgrow(mainMappingTable, Priority.ALWAYS);

        HBox btnBar = new HBox(8);
        btnBar.setAlignment(Pos.CENTER_LEFT);
        btnBar.setPadding(new Insets(5, 0, 0, 0));
        Button loadColumnsBtn = new Button("加载表字段");
        loadColumnsBtn.getStyleClass().add("btn-secondary");
        loadColumnsBtn.setOnAction(e -> loadMainColumns());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        btnBar.getChildren().addAll(loadColumnsBtn, spacer, mainQuickFillBtn);

        mainTableSection.getChildren().addAll(sectionTitle, row1, mainMappingTable, btnBar);
        VBox.setVgrow(mainMappingTable, Priority.ALWAYS);
    }

    private void setupMainMappingTable() {
        TableColumn<ColumnMapping, String> colName = new TableColumn<>("数据库字段");
        colName.setCellValueFactory(cell -> cell.getValue().columnNameProperty());
        colName.setPrefWidth(150);
        colName.setSortable(false);

        TableColumn<ColumnMapping, String> colType = new TableColumn<>("数据类型");
        colType.setCellValueFactory(cell -> cell.getValue().columnTypeProperty());
        colType.setPrefWidth(100);
        colType.setSortable(false);

        TableColumn<ColumnMapping, String> colComment = new TableColumn<>("注释");
        colComment.setCellValueFactory(cell -> cell.getValue().columnCommentProperty());
        colComment.setPrefWidth(150);
        colComment.setSortable(false);

        TableColumn<ColumnMapping, String> colWidget = new TableColumn<>("简道云字段ID");
        colWidget.setCellValueFactory(cell -> cell.getValue().widgetIdProperty());
        colWidget.setPrefWidth(250);
        colWidget.setSortable(false);
        colWidget.setCellFactory(column -> new EditingTextCell<>(cm -> cm.getWidgetId(), (cm, val) -> cm.setWidgetId(val)));

        mainMappingTable.getColumns().addAll(colName, colType, colComment, colWidget);
        mainMappingTable.setEditable(true);
        mainMappingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        mainMappingTable.setMinHeight(180);
    }

    private void setupSubTablesSection() {
        subTablesSection.getStyleClass().add("section-panel");
        subTablesSection.setPadding(new Insets(15));
        subTablesSection.setSpacing(10);

        Label sectionTitle = new Label("子表映射");
        sectionTitle.getStyleClass().add("section-title");

        Button addSubTableBtn = new Button("+ 添加子表");
        addSubTableBtn.getStyleClass().add("btn-secondary");
        addSubTableBtn.setOnAction(e -> addSubTableEditor());

        subTableContainers.setSpacing(15);

        subTablesSection.getChildren().addAll(sectionTitle, addSubTableBtn, subTableContainers);
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

    private void setupListeners() {
        mappingCombo.setOnAction(e -> {
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

        String cacheKey = ds != null ? ds.getId() + "::" + mapping.getMainTableName() : null;
        if (cacheKey != null && mainMappingCache.containsKey(cacheKey)) {
            mainColumnMappings = mainMappingCache.get(cacheKey);
            mainMappingTable.getItems().clear();
            mainMappingTable.getItems().addAll(mainColumnMappings);
            loadExistingMainMapping(mapping);
            showStatus(true, "已从缓存加载主表 " + mainColumnMappings.size() + " 个字段");
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
                        showStatus(true, "已选择主表: " + tableName);
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
            showStatus(true, "已从缓存加载 " + mainColumnMappings.size() + " 个字段");
            return;
        }

        statusLabel.setText("正在加载字段...");
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
                mainColumnMappings = mappings;
                mainMappingCache.put(cacheKey, new ArrayList<>(mappings));
                mainMappingTable.getItems().clear();
                mainMappingTable.getItems().addAll(mappings);
                if (selectedMapping != null) loadExistingMainMapping(selectedMapping);
                showStatus(true, "已加载 " + mappings.size() + " 个字段");
            });
        });
    }

    private void saveMapping() {
        try {
            DataSourceConfig ds = dataSourceCombo.getValue();
            JdyAppConfig app = jdyAppCombo.getValue();
            String mainTable = mainTableNameField.getText().trim();
            String mainEntry = mainEntryIdField.getText().trim();

            if (ds == null) {
                showAlert("保存失败", "请选择数据源");
                return;
            }
            if (app == null) {
                showAlert("保存失败", "请选择简道云应用");
                return;
            }
            if (mainTable.isEmpty()) {
                showAlert("保存失败", "请填写主表名");
                return;
            }
            if (mainEntry.isEmpty()) {
                showAlert("保存失败", "请填写简道云表单ID");
                return;
            }

            mainMappingTable.refresh();
            for (SubTableEditor editor : subTableEditors) {
                editor.commitEdit();
            }

            Map<String, String> mainMapping = new LinkedHashMap<>();
            if (mainColumnMappings != null) {
                for (int i = 0; i < mainColumnMappings.size(); i++) {
                    ColumnMapping cm = mainColumnMappings.get(i);
                    if (cm == null) {
                        System.err.println("DEBUG: mainColumnMappings[" + i + "] is null");
                        continue;
                    }
                    String colName = cm.getColumnName();
                    String wid = cm.getWidgetId();
                    System.out.println("DEBUG: row[" + i + "] colName=" + colName + " widgetId=" + wid);
                    if (colName == null || colName.trim().isEmpty()) {
                        System.err.println("DEBUG: skipping row[" + i + "] - colName is null/empty");
                        continue;
                    }
                    if (wid != null) wid = wid.trim();
                    if (wid == null || wid.isEmpty()) {
                        continue;
                    }
                    mainMapping.put(colName, wid);
                }
            } else {
                System.err.println("DEBUG: mainColumnMappings is null");
            }

            if (mainMapping.isEmpty()) {
                showAlert("保存失败", "主表至少需要映射一个字段，请在表格中填写简道云字段ID");
                return;
            }

            List<SubTableMapping> subMappings = new ArrayList<>();
            if (subTableEditors != null) {
                for (SubTableEditor editor : subTableEditors) {
                    try {
                        SubTableMapping sub = editor.buildSubMapping();
                        if (sub != null) {
                            subMappings.add(sub);
                        }
                    } catch (Exception e) {
                        System.err.println("构建子表映射失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            System.out.println("DEBUG: subMappings size = " + subMappings.size());
            System.out.println("DEBUG: subMappings is null? " + (subMappings == null));

            if (selectedMapping == null) {
                selectedMapping = new FormMappingConfig();
                selectedMapping.setId(UUID.randomUUID().toString());
            }

            selectedMapping.setName(mainTable + " -> " + mainEntry);
            selectedMapping.setDataSourceId(ds.getId());
            selectedMapping.setJdyAppId(app.getId());
            selectedMapping.setMainTableName(mainTable);
            selectedMapping.setMainEntryId(mainEntry);
            selectedMapping.setMainFieldMapping(mainMapping != null ? new LinkedHashMap<>(mainMapping) : new LinkedHashMap<>());
            selectedMapping.setSubTableMappings(subMappings != null ? new ArrayList<>(subMappings) : new ArrayList<>());

            List<FormMappingConfig> mappings = new ArrayList<>(configManager.loadFormMappings());
            boolean found = false;
            for (int i = 0; i < mappings.size(); i++) {
                if (mappings.get(i).getId().equals(selectedMapping.getId())) {
                    mappings.set(i, selectedMapping);
                    found = true;
                    break;
                }
            }
            if (!found) {
                mappings.add(selectedMapping);
            }
            configManager.saveFormMappings(mappings);

            mappingCombo.getItems().clear();
            mappingCombo.getItems().addAll(configManager.loadFormMappings());
            mappingCombo.setValue(selectedMapping);

            String subInfo = subMappings.isEmpty() ? "" : "，含 " + subMappings.size() + " 个子表";
            showStatus(true, "已保存: " + selectedMapping.getName() + " (" + mainMapping.size() + " 个主表字段" + subInfo + ")");
            showAlert("保存成功", "表单映射配置已保存\n\n名称: " + selectedMapping.getName() + "\n主表字段: " + mainMapping.size() + " 个" + (subMappings.isEmpty() ? "" : "\n子表: " + subMappings.size() + " 个"));
        } catch (Exception e) {
            e.printStackTrace();
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            showAlert("保存异常", "错误类型: " + e.getClass().getName() + "\n错误信息: " + e.getMessage() + "\n\n详细堆栈:\n" + sw.toString());
            showStatus(false, "保存失败: " + e.getClass().getName());
        }
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
            showStatus(true, "主表已自动填充 " + filledCount + " 个字段");
        } else {
            showStatus(false, "请先在主表至少填写一个简道云字段ID");
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

    private void clearForm() {
        selectedMapping = null;
        mappingCombo.getSelectionModel().clearSelection();
        dataSourceCombo.getSelectionModel().clearSelection();
        jdyAppCombo.getSelectionModel().clearSelection();
        mainTableNameField.clear();
        mainEntryIdField.clear();
        mainColumnMappings = null;
        mainMappingTable.getItems().clear();
        mainMappingCache.clear();

        subTableContainers.getChildren().clear();
        subTableEditors.clear();

        statusLabel.setText("");
    }

    private void showStatus(boolean success, String message) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-success", "status-error");
        statusLabel.getStyleClass().add(success ? "status-success" : "status-error");
    }

    private static class ColumnDetail {
        String name, type, comment;
        ColumnDetail(String name, String type, String comment) {
            this.name = name;
            this.type = type;
            this.comment = comment;
        }
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
        private final TextField joinFieldField;
        private final TextField subFormWidgetIdField;
        private final TableView<SubColumnMapping> subMappingTable;
        private List<SubColumnMapping> subColumnMappings;
        private final Map<String, List<SubColumnMapping>> subMappingCache = new HashMap<>();
        private final VBox content;
        private final Button subQuickFillBtn;

        SubTableEditor() {
            content = new VBox(10);
            content.setPadding(new Insets(10));
            content.getStyleClass().add("sub-table-card");

            titledPane = new TitledPane();
            titledPane.setText("子表");
            titledPane.setCollapsible(true);
            titledPane.setExpanded(true);

            VBox innerContent = new VBox(8);

            HBox row1 = new HBox(8);
            row1.setAlignment(Pos.CENTER_LEFT);
            Button loadSubTablesBtn = new Button("从数据源加载");
            loadSubTablesBtn.getStyleClass().add("btn-secondary");
            loadSubTablesBtn.setOnAction(e -> loadSubTables());
            Label subTableLabel = new Label("子表名:");
            subTableNameField = new TextField();
            subTableNameField.setPromptText("MySQL 子表名");
            subTableNameField.setPrefWidth(140);
            Label joinLabel = new Label("关联字段:");
            joinFieldField = new TextField();
            joinFieldField.setPromptText("如 main_id");
            joinFieldField.setPrefWidth(100);
            Label widgetLabel = new Label("容器Widget ID:");
            subFormWidgetIdField = new TextField();
            subFormWidgetIdField.setPromptText("_widget_123456");
            subFormWidgetIdField.setPrefWidth(180);
            row1.getChildren().addAll(loadSubTablesBtn, subTableLabel, subTableNameField, joinLabel, joinFieldField, widgetLabel, subFormWidgetIdField);

            subMappingTable = new TableView<>();
            subMappingTable.getStyleClass().add("data-table");
            setupSubMappingTable();
            VBox.setVgrow(subMappingTable, Priority.ALWAYS);

            HBox btnBar = new HBox(8);
            btnBar.setAlignment(Pos.CENTER_LEFT);
            btnBar.setPadding(new Insets(5, 0, 0, 0));
            Button loadSubColumnsBtn = new Button("加载子表字段");
            loadSubColumnsBtn.getStyleClass().add("btn-secondary");
            loadSubColumnsBtn.setOnAction(e -> loadSubColumns());
            subQuickFillBtn = new Button("快速递增填充");
            subQuickFillBtn.getStyleClass().add("btn-secondary");
            subQuickFillBtn.setOnAction(e -> quickFill());
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button removeBtn = new Button("删除此子表");
            removeBtn.getStyleClass().add("btn-danger");
            removeBtn.setOnAction(e -> removeSubTableEditor(SubTableEditor.this));
            btnBar.getChildren().addAll(loadSubColumnsBtn, subQuickFillBtn, spacer, removeBtn);

            innerContent.getChildren().addAll(row1, subMappingTable, btnBar);
            titledPane.setContent(innerContent);

            content.getChildren().add(titledPane);
        }

        private void setupSubMappingTable() {
            TableColumn<SubColumnMapping, String> colName = new TableColumn<>("数据库字段");
            colName.setCellValueFactory(cell -> cell.getValue().columnNameProperty());
            colName.setPrefWidth(130);
            colName.setSortable(false);

            TableColumn<SubColumnMapping, String> colType = new TableColumn<>("数据类型");
            colType.setCellValueFactory(cell -> cell.getValue().columnTypeProperty());
            colType.setPrefWidth(80);
            colType.setSortable(false);

            TableColumn<SubColumnMapping, String> colComment = new TableColumn<>("注释");
            colComment.setCellValueFactory(cell -> cell.getValue().columnCommentProperty());
            colComment.setPrefWidth(100);
            colComment.setSortable(false);

            TableColumn<SubColumnMapping, String> colPrefix = new TableColumn<>("前缀");
            colPrefix.setCellValueFactory(cell -> cell.getValue().widgetPrefixProperty());
            colPrefix.setPrefWidth(100);
            colPrefix.setSortable(false);
            colPrefix.setCellFactory(column -> new EditingTextCell<>(scm -> scm.getWidgetPrefix(), (scm, val) -> scm.setWidgetPrefix(val.isEmpty() ? "_widget_" : val)));

            TableColumn<SubColumnMapping, String> colSuffix = new TableColumn<>("后缀(数字)");
            colSuffix.setCellValueFactory(cell -> cell.getValue().widgetSuffixProperty());
            colSuffix.setPrefWidth(120);
            colSuffix.setSortable(false);
            colSuffix.setCellFactory(column -> new EditingTextCell<>(scm -> scm.getWidgetSuffix(), (scm, val) -> scm.setWidgetSuffix(val)));

            subMappingTable.getColumns().addAll(colName, colType, colComment, colPrefix, colSuffix);
            subMappingTable.setEditable(true);
            subMappingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            subMappingTable.setMinHeight(150);
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
                            showStatus(true, "已选择子表: " + tableName);
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
                showStatus(true, "已从缓存加载 " + subColumnMappings.size() + " 个字段");
                return;
            }

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
                            showStatus(false, "加载子表字段失败: " + e.getMessage()));
                    return;
                }

                List<SubColumnMapping> mappings = new ArrayList<>();
                for (ColumnDetail d : details) {
                    mappings.add(new SubColumnMapping(d.name, d.type, d.comment, "_widget_", ""));
                }

                javafx.application.Platform.runLater(() -> {
                    subColumnMappings = mappings;
                    subMappingCache.put(cacheKey, new ArrayList<>(mappings));
                    subMappingTable.getItems().clear();
                    subMappingTable.getItems().addAll(mappings);
                    showStatus(true, "已加载 " + mappings.size() + " 个字段");
                });
            });
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

            Map<String, String> fieldMapping = new LinkedHashMap<>();
            for (SubColumnMapping scm : subColumnMappings) {
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
            mapping.setJoinFieldName(joinFieldField.getText().trim());
            mapping.setFieldMapping(fieldMapping);
            return mapping;
        }

        void loadSubMapping(SubTableMapping sub) {
            subTableNameField.setText(sub.getSubTableName());
            subFormWidgetIdField.setText(sub.getSubFormWidgetId());
            joinFieldField.setText(sub.getJoinFieldName());

            DataSourceConfig ds = dataSourceCombo.getValue();
            String cacheKey = ds != null ? ds.getId() + "::sub::" + sub.getSubTableName() : null;
            if (cacheKey != null && subMappingCache.containsKey(cacheKey)) {
                subColumnMappings = subMappingCache.get(cacheKey);
                subMappingTable.getItems().clear();
                subMappingTable.getItems().addAll(subColumnMappings);
                if (sub.getFieldMapping() != null) {
                    for (SubColumnMapping scm : subColumnMappings) {
                        String fullWidgetId = sub.getFieldMapping().get(scm.getColumnName());
                        if (fullWidgetId != null && fullWidgetId.startsWith("_widget_")) {
                            scm.setWidgetSuffix(fullWidgetId.substring("_widget_".length()));
                            scm.setWidgetPrefix("_widget_");
                        }
                    }
                }
                subMappingTable.refresh();
            }
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
                showStatus(false, "请先至少填写一个后缀数字");
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
                showStatus(true, "已自动填充 " + fillCount + " 个子表字段");
            } else {
                showStatus(false, "下方没有空白行需要填充");
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
                System.err.println("EditingTextCell updateItem error: " + e.getMessage());
                setGraphic(null);
            }
        }
    }
}
