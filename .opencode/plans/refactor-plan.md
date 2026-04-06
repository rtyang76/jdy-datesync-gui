# 重构实施计划：多应用支持 + 关联链路可视化

## 目标
1. 支持多个简道云应用（多 App ID + 多 Token）
2. 数据库仍只支持 MySQL
3. 配置链路完整可视化：数据源 → 简道云应用 → 同步任务 → 字段映射
4. 每个环节都能看到上游关联，支持点击跳转

---

## 文件改动清单

### 1. `model/JdyAppConfig.java` - 重写

**改动原因**：当前没有 `id` 和 `name`，无法区分多个应用

**新内容**：
```java
package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JdyAppConfig {
    private String id;
    private String name;
    private String appId;
    private String apiToken;
    private boolean startWorkflow;

    public JdyAppConfig() {
        this.startWorkflow = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
    public boolean isStartWorkflow() { return startWorkflow; }
    public void setStartWorkflow(boolean startWorkflow) { this.startWorkflow = startWorkflow; }

    @Override
    public String toString() {
        return name != null ? name : "未命名应用";
    }
}
```

---

### 2. `model/SyncTaskConfig.java` - 添加 `jdyAppId` 字段

**改动原因**：任务需要关联到具体的简道云应用

**新增字段**（在 `entryId` 之后添加）：
```java
private String jdyAppId;

public String getJdyAppId() { return jdyAppId; }
public void setJdyAppId(String jdyAppId) { this.jdyAppId = jdyAppId; }
```

---

### 3. `service/ConfigManager.java` - 重写 JDY 配置管理

**改动原因**：从单例配置改为多应用列表

**关键变更**：
- 删除 `JDY_CONFIG_FILE = "jdy_config.json"`
- 新增 `JDY_APPS_FILE = "jdy_apps.json"`
- `loadJdyConfig()` → `loadJdyApps()` 返回 `List<JdyAppConfig>`
- `saveJdyConfig(JdyAppConfig)` → `saveJdyApps(List<JdyAppConfig>)`
- 新增便捷方法：`JdyAppConfig findJdyAppById(String id)`

**新增方法**：
```java
public List<JdyAppConfig> loadJdyApps() {
    File file = configDir.resolve(JDY_APPS_FILE).toFile();
    if (!file.exists()) return new ArrayList<>();
    try {
        JdyAppConfig[] configs = mapper.readValue(file, JdyAppConfig[].class);
        return List.of(configs);
    } catch (IOException e) {
        return new ArrayList<>();
    }
}

public void saveJdyApps(List<JdyAppConfig> configs) {
    File file = configDir.resolve(JDY_APPS_FILE).toFile();
    try {
        mapper.writeValue(file, configs);
    } catch (IOException e) {
        throw new RuntimeException("保存简道云应用配置失败", e);
    }
}

public JdyAppConfig findJdyAppById(String id) {
    if (id == null) return null;
    return loadJdyApps().stream()
        .filter(a -> id.equals(a.getId()))
        .findFirst().orElse(null);
}
```

---

### 4. `service/SyncEngine.java` - 修改 JDY 配置获取方式

**改动原因**：不再使用全局单例配置，改为通过任务的 `jdyAppId` 查找

**修改 `executeTask()` 方法中获取 JDY 配置的部分**：

原代码：
```java
JdyAppConfig jdyConfig = configManager.loadJdyConfig();
if (jdyConfig.getApiToken() == null || jdyConfig.getApiToken().trim().isEmpty()) {
    return new SyncResult(false, "简道云 API Token 未配置");
}
```

改为：
```java
JdyAppConfig jdyConfig = configManager.findJdyAppById(task.getJdyAppId());
if (jdyConfig == null) {
    return new SyncResult(false, "简道云应用未配置或已删除");
}
if (jdyConfig.getApiToken() == null || jdyConfig.getApiToken().trim().isEmpty()) {
    return new SyncResult(false, "简道云应用 API Token 未配置: " + jdyConfig.getName());
}
```

---

### 5. `controller/JdyConfigPage.java` - 重写为多应用管理

**布局**：左右分栏（类似 DataSourcePage）
- 左侧：应用列表 `ListView<JdyAppConfig>`
- 右侧：编辑表单 + 操作按钮

**表单字段**：
- 名称（TextField）
- App ID（TextField）
- API Token（TextField）
- 触发工作流（CheckBox）

**按钮**：新建、保存、删除、测试连接

**关键逻辑**：
- 新建时清空表单，selectedApp = null
- 保存时：selectedApp == null 则新建（UUID），否则更新
- 删除时：确认弹窗，从列表移除并保存
- 测试连接：使用当前表单数据构建临时 JdyAppConfig 调用 JdyApiTestService

---

### 6. `controller/SyncTaskPage.java` - 增强关联展示

**表格列变更**：
| 原列 | 保留 | 说明 |
|------|------|------|
| 任务名称 | ✅ | |
| 数据源 | ✅ 新增 | 显示数据源名称（通过 dataSourceId 查找） |
| 简道云应用 | ✅ 新增 | 显示应用名称（通过 jdyAppId 查找） |
| 源表 | ✅ | |
| 表单ID | ✅ | |
| 间隔(分) | ✅ | |
| 状态 | ✅ | |

**表单变更**：
- 新增 `ComboBox<JdyAppConfig> jdyAppCombo`
- 表单顶部添加**关联面包屑** `HBox`，显示：
  ```
  📁 数据源: [名称]  →  ☁️ 简道云应用: [名称]
  ```
  名称使用 Hyperlink 样式，点击可跳转到对应配置页

**保存校验新增**：
```java
if (jdyAppCombo.getValue() == null) {
    showStatus(false, "请选择简道云应用");
    return;
}
```

**loadTaskToForm() 新增**：
```java
JdyAppConfig app = dataSources... // 通过 task.getJdyAppId() 查找
jdyAppCombo.setValue(app);
```

**clearForm() 新增**：
```java
jdyAppCombo.getSelectionModel().clearSelection();
```

---

### 7. `controller/FieldMappingPage.java` - 重写为任务入口

**全新交互流程**：

1. **顶部**：任务选择下拉 `ComboBox<SyncTaskConfig>`
2. **关联面包屑条**（选择任务后显示）：
   ```
   数据源: [名称] → 简道云应用: [名称] → 任务: [名称] → 表: [表名]
   ```
   每个名称都是可点击的 Hyperlink
3. **字段表格**：选择任务后自动从关联数据源加载表字段
4. **保存按钮**：直接保存到该任务的 `fieldMapping`，无需弹窗

**面包屑点击联动**：
- 数据源名称 → 调用 `MainWindow.navigateTo("dataSource", dataSourceId)`
- 简道云应用名称 → 调用 `MainWindow.navigateTo("jdyConfig", jdyAppId)`
- 任务名称 → 调用 `MainWindow.navigateTo("syncTask", taskId)`

**需要 MainWindow 暴露导航方法**（见步骤 8）

**加载字段逻辑**：
- 通过任务的 `dataSourceId` 找到数据源
- 通过任务的 `sourceTable` 加载字段
- 已有缓存机制保留

**保存逻辑简化**：
```java
private void saveMapping() {
    if (selectedTask == null) {
        showStatus(false, "请先选择同步任务");
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
    selectedTask.setFieldMapping(mapping);
    List<SyncTaskConfig> tasks = configManager.loadSyncTasks();
    for (int i = 0; i < tasks.size(); i++) {
        if (tasks.get(i).getId().equals(selectedTask.getId())) {
            tasks.set(i, selectedTask);
            break;
        }
    }
    configManager.saveSyncTasks(tasks);
    long count = mapping.size();
    showStatus(true, "已保存 " + count + " 个字段映射");
}
```

---

### 8. `view/MainWindow.java` - 添加页面间导航联动

**新增**：
- 保留各页面实例引用（而非每次 showPage 时新建）
- 新增 `navigateTo(String pageId, String selectId)` 方法
- 各页面暴露 `selectItem(String id)` 方法

**页面实例化**：
```java
private DataSourcePage dataSourcePage;
private JdyConfigPage jdyConfigPage;
private SyncTaskPage syncTaskPage;
private FieldMappingPage fieldMappingPage;
private LogPage logPage;
```

**showPage() 改为复用**：
```java
private void showPage(String pageId) {
    switch (pageId) {
        case "dataSource":
            if (dataSourcePage == null) dataSourcePage = new DataSourcePage(configManager);
            contentArea.getChildren().setAll(dataSourcePage.getContent());
            break;
        // ... 其他页面类似
    }
}
```

**新增导航方法**：
```java
public void navigateTo(String pageId, String selectId) {
    showPage(pageId);
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
```

**各页面需要新增 `selectItem(String id)` 方法**：
- `DataSourcePage.selectItem(id)` - 在列表中查找并选中
- `JdyConfigPage.selectItem(id)` - 在列表中查找并选中
- `SyncTaskPage.selectItem(id)` - 在表格中查找并选中

**MainWindow 需要向子页面传递自身引用**（用于面包屑跳转）：
```java
// 方案：通过构造函数传递 MainWindow 引用
new FieldMappingPage(configManager, mainWindow)
```

或者使用回调方式：
```java
// 在 FieldMappingPage 中设置导航回调
fieldMappingPage.setNavigator((pageId, selectId) -> navigateTo(pageId, selectId));
```

推荐使用回调方式，避免循环依赖。

---

### 9. `pom.xml` - 移除 mssql-jdbc

删除：
```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <version>12.4.2.jre11</version>
</dependency>
```

---

## 实施顺序

1. **Model 层**：JdyAppConfig → SyncTaskConfig（2 个文件，无依赖）
2. **Service 层**：ConfigManager → SyncEngine（依赖 Model）
3. **页面层**：JdyConfigPage → SyncTaskPage → FieldMappingPage（依赖 Service）
4. **导航层**：MainWindow（依赖所有页面）
5. **清理**：pom.xml
6. **编译验证**

---

## 数据迁移

由于 `jdy_apps.json` 是新文件，旧版 `jdy_config.json` 中的数据不会自动迁移。用户需要重新配置简道云应用。

对于已有的同步任务，`jdyAppId` 字段为 null，需要在 UI 中重新选择应用后保存。

---

## 风险点

1. **字段映射页缓存**：旧缓存 key 是 `dataSourceId::tableName`，新方案改为按任务加载，旧缓存自动失效，无影响
2. **TaskScheduler**：不受影响，仍通过 taskId 执行，SyncEngine 内部会查找正确的应用配置
3. **已有用户数据**：`~/.jdy-datesync/` 下的 `jdy_config.json` 不会被删除但不再使用，`sync_tasks.json` 中已有任务缺少 `jdyAppId`
