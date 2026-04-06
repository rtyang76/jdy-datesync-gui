# jdy-datesync-gui 项目记忆

> 此文件用于 opencode 恢复上下文，包含项目关键信息。每次重大变更后更新此文件。

## 项目基本信息

- **名称**: 简道云数据同步工具 (GUI)
- **路径**: `/Users/yrt/Developer/Work/jdy-datesync-gui`
- **类型**: JavaFX 桌面应用
- **版本**: V1.1
- **构建**: `mvn clean compile`
- **运行**: `mvn javafx:run`
- **主类**: `org.example.gui.MainApp`

## 技术栈

- Java 17, JavaFX 17.0.10, Maven
- mysql-connector-j 8.3.0 (仅 MySQL，已移除 mssql-jdbc)
- jackson-databind 2.16.1 (JSON 序列化)
- 无 HikariCP，使用原生 JDBC DriverManager

## 项目结构 (17 个 Java 文件)

```
src/main/java/org/example/gui/
├── MainApp.java (38行)              # JavaFX Application 入口
├── view/
│   └── MainWindow.java (186行)      # BorderPane 布局，侧边栏导航，页面复用，navigateTo() 联动
├── controller/
│   ├── DataSourcePage.java (368行)  # 数据源 CRUD + 表列表加载，selectItem(id)
│   ├── JdyConfigPage.java (269行)   # 简道云应用 CRUD + 测试连接，selectItem(id)
│   ├── SyncTaskPage.java (626行)    # 任务 CRUD + 面包屑 + 水印编辑 + 调度，refreshData()
│   ├── FieldMappingPage.java (533行) # 任务入口 + 自动加载字段 + 面包屑，refreshData()
│   ├── LogPage.java (123行)         # 日志捕获 + 过滤 + 导出
│   └── ColumnMapping.java (34行)    # JavaFX StringProperty 列映射模型
├── model/
│   ├── DataSourceConfig.java (59行) # id, name, host, port, database, username, password, getJdbcUrl()
│   ├── JdyAppConfig.java (36行)     # id, name, appId, apiToken, startWorkflow
│   ├── SyncTaskConfig.java (75行)   # id, name, dataSourceId, jdyAppId, sourceTable, entryId, fieldMapping(Map), incrementMode, incrementField, syncIntervalMinutes, maxBatchSize, maxRetry, enabled
│   └── SyncProgress.java (25行)     # Map<taskId, lastSyncId>
└── service/
    ├── ConfigManager.java (129行)   # JSON 配置读写 ~/.jdy-datesync/
    ├── SyncEngine.java (320行)      # 核心同步逻辑: 查DB → 字段映射 → 推送简道云 batch_create API
    ├── TaskScheduler.java (115行)   # ScheduledExecutorService 多任务调度
    ├── ConnectionTestService.java (80行)  # MySQL 连接测试 + 表列表
    └── JdyApiTestService.java (78行)      # 简道云 API 测试
```

## 配置存储 (~/.jdy-datesync/)

| 文件 | 内容 |
|------|------|
| `data_sources.json` | List<DataSourceConfig> |
| `jdy_apps.json` | List<JdyAppConfig> |
| `sync_tasks.json` | List<SyncTaskConfig> (含 fieldMapping) |
| `sync_progress.json` | SyncProgress (taskId → lastSyncId) |

## 关联链路设计

```
数据源 (多) → 简道云应用 (多) → 同步任务 (多) → 字段映射 (内置于任务)
```

每个 SyncTaskConfig 持有:
- `dataSourceId` → 关联 DataSourceConfig
- `jdyAppId` → 关联 JdyAppConfig
- `fieldMapping` → Map<数据库字段名, 简道云控件ID>
- 同步水印独立存储在 `sync_progress.json` 中

## 页面导航机制

- MainWindow 持有所有页面实例（不复用创建）
- `showPage(pageId)` 切换显示
- `navigateTo(pageId, selectId)` 切换 + 选中指定项
- 子页面通过 `setNavigator(BiConsumer<String, String>)` 接收导航回调
- 面包屑点击调用 navigator 实现跨页跳转
- SyncTaskPage 和 FieldMappingPage 有 `refreshData()` 方法，showPage 时调用以刷新下拉列表

## 简道云 API

- **批量创建**: `POST https://api.jiandaoyun.com/api/v5/app/entry/data/batch_create`
- **请求头**: `Content-Type: application/json`, `Authorization: Bearer <token>`, `X-Request-ID: <uuid>`
- **请求体**: `{ app_id, entry_id, data_list: [{ widgetId: { value } }], is_start_workflow }`
- **成功判断**: status="success" 或 code=0 或 含 data/data_id 字段

## 同步引擎流程 (SyncEngine.executeTask)

1. 通过 taskId 查找 SyncTaskConfig
2. 通过 task.dataSourceId 查找 DataSourceConfig
3. 通过 task.jdyAppId 查找 JdyAppConfig
4. 从 SyncProgress 获取 lastSyncId
5. 循环查询: `SELECT * FROM table WHERE incrementField > lastSyncId ORDER BY incrementField ASC LIMIT batchSize`
6. 字段映射: 将数据库字段值转为简道云 `{ widgetId: { value } }` 格式
7. 推送简道云 API，失败重试 maxRetry 次
8. 成功后更新 lastSyncId 到 SyncProgress

## 已完成的重构

1. ✅ 删除旧 CLI 项目代码 (SyncApplication, OrderSyncService, DM模块等 35+ 文件)
2. ✅ 删除旧配置文件 (db.properties, application.properties, 旧 JSON 映射文件)
3. ✅ 删除旧文档 (docs/ 目录 11 个文件)
4. ✅ 删除旧测试文件 (src/test/)
5. ✅ 移除 HikariCP 和 mssql-jdbc 依赖
6. ✅ 简道云配置从全局单例改为多应用管理
7. ✅ SyncTaskConfig 添加 jdyAppId 关联
8. ✅ 页面间关联面包屑 + 点击跳转
9. ✅ 字段映射页改为任务入口，自动加载字段
10. ✅ 同步任务水印显示和编辑功能

## 已知问题和待办

- 旧版 `jdy_config.json` 不会自动迁移到 `jdy_apps.json`，用户需重新配置
- 已有同步任务的 `jdyAppId` 为 null，需在 UI 中重新选择应用保存
- 仅支持 MySQL，如需 SQL Server 需重新添加 mssql-jdbc 和 JDBC URL 构建逻辑
- 字段映射缓存基于 `dataSourceId::tableName`，切换任务但同源同表会复用缓存
