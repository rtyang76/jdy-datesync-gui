# 简道云数据同步工具 (GUI) V1.1

## 项目概述

基于 JavaFX 的图形化数据推送软件，支持将 MySQL 数据库中的数据增量推送到简道云表单系统。支持多数据源、多简道云应用、多同步任务，通过可视化界面完成所有配置。

## 核心特性

- **多数据源**: 支持多个 MySQL 数据库连接
- **多简道云应用**: 支持配置多个简道云应用（App ID + Token）
- **完整关联链路**: 数据源 → 简道云应用 → 同步任务 → 字段映射，每级可见上游关联
- **可视化面包屑**: 同步任务和字段映射页面显示关联面包屑，可点击跳转
- **水印控制**: 同步任务可显示和手动修改同步水印（最大 ID / 时间戳）
- **定时调度**: 多任务独立调度，按需启停
- **增量同步**: 基于自增 ID 或时间戳字段的增量推送
- **批量推送 + 自动重试**
- **运行日志**: 实时日志 + 导出

## 技术栈

- Java 17 / JavaFX 17 / Maven
- mysql-connector-j 8.3.0 / jackson-databind 2.16.1

## 快速开始

```bash
mvn clean package -DskipTests
mvn javafx:run
# 或 java -jar target/jdy-datesync-gui-1.0.0-jar-with-dependencies.jar
```

## 项目结构

```
src/main/java/org/example/gui/
├── MainApp.java                    # JavaFX 入口
├── view/
│   └── MainWindow.java             # 主窗口（侧边栏 + 页面复用 + 导航联动）
├── controller/
│   ├── DataSourcePage.java         # 数据源配置（多数据源列表 + 表单 + 表列表）
│   ├── JdyConfigPage.java          # 简道云应用配置（多应用列表 + 表单 + 测试连接）
│   ├── SyncTaskPage.java           # 同步任务管理（关联面包屑 + 水印编辑 + 调度控制）
│   ├── FieldMappingPage.java       # 字段映射（任务入口 + 自动加载字段 + 关联面包屑）
│   ├── LogPage.java                # 运行日志（实时 + 过滤 + 导出）
│   └── ColumnMapping.java          # 列映射 JavaFX 属性模型
├── model/
│   ├── DataSourceConfig.java       # 数据源（id, name, host, port, database, username, password）
│   ├── JdyAppConfig.java           # 简道云应用（id, name, appId, apiToken, startWorkflow）
│   ├── SyncTaskConfig.java         # 同步任务（id, name, dataSourceId, jdyAppId, sourceTable, entryId, fieldMapping, incrementMode, incrementField, syncIntervalMinutes, maxBatchSize, maxRetry, enabled）
│   └── SyncProgress.java           # 同步进度（taskId → lastSyncId 映射）
└── service/
    ├── ConfigManager.java          # 配置管理（~/.jdy-datesync/ 下的 JSON 文件）
    ├── SyncEngine.java             # 同步引擎（查 DB → 映射字段 → 推送简道云 API）
    ├── TaskScheduler.java          # 定时调度（ScheduledExecutorService）
    ├── ConnectionTestService.java  # 数据库连接测试
    └── JdyApiTestService.java      # 简道云 API 连接测试
```

## 配置存储

所有用户配置存储在 `~/.jdy-datesync/` 目录下：

| 文件 | 内容 |
|------|------|
| `data_sources.json` | 数据源列表 |
| `jdy_apps.json` | 简道云应用列表 |
| `sync_tasks.json` | 同步任务列表（含字段映射） |
| `sync_progress.json` | 各任务同步水印（lastSyncId） |

## 使用说明

1. **数据源配置**: 添加 MySQL 数据库连接，可加载表列表
2. **简道云配置**: 添加简道云应用（名称、App ID、API Token），可测试连接
3. **同步任务**: 创建任务，选择数据源和简道云应用，设置源表、表单 ID、增量方式、调度间隔
4. **字段映射**: 选择同步任务后自动加载表字段，填写简道云字段 ID，保存到任务
5. **水印控制**: 在同步任务页面可查看和修改任务的同步水印
6. **运行任务**: 手动执行或启用定时调度

## 关联链路

```
数据源 (DataSourceConfig)
  └── 简道云应用 (JdyAppConfig)
        └── 同步任务 (SyncTaskConfig)
              ├── 字段映射 (fieldMapping Map)
              └── 同步水印 (SyncProgress)
```

每个页面顶部面包屑显示上游关联，点击可跳转到对应配置页。
