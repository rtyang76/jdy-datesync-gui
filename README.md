# 简道云数据同步工具

MySQL 数据库与简道云表单之间的数据同步桌面工具，支持增量同步、子表关联、定时任务和系统托盘后台运行。

## 功能特性

- **数据源管理** — 配置多个 MySQL 数据库连接，支持连接测试
- **简道云应用管理** — 支持多个简道云应用（多 App ID + 多 API Token），可触发工作流
- **表单映射** — 可视化配置数据库表与简道云表单的字段映射关系，支持主表和子表
- **同步任务** — 定时增量同步，支持自增 ID 和时间戳两种增量模式
- **数据匹配更新** — 通过查询条件判断简道云是否已有数据，自动选择新建或更新
- **子表关联** — 支持主表与子表的 JOIN 关联，将子表数据同步为简道云子表单
- **运行日志** — 实时查看同步执行日志
- **系统托盘** — 关闭窗口后最小化到系统托盘后台运行，支持 macOS Dock 事件

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java 21 |
| UI 框架 | JavaFX 21 |
| 构建工具 | Maven |
| HTTP 客户端 | OkHttp 4.12 |
| JSON 处理 | Jackson 2.16 |
| 数据库驱动 | MySQL Connector/J 8.3 |

## 项目结构

```
src/main/java/org/example/gui/
├── MainApp.java                    # 应用入口，JavaFX Application
├── controller/
│   ├── ColumnMapping.java          # 字段映射模型（DB列 ↔ 简道云控件）
│   ├── DataSourcePage.java         # 数据源配置页面
│   ├── FormMappingPage.java        # 表单映射配置页面
│   ├── JdyConfigPage.java          # 简道云应用配置页面
│   ├── LogPage.java                # 运行日志页面
│   └── SyncTaskPage.java           # 同步任务管理页面
├── model/
│   ├── DataSourceConfig.java       # 数据源配置模型
│   ├── FormMappingConfig.java      # 表单映射配置模型
│   ├── JdyAppConfig.java           # 简道云应用配置模型
│   ├── QueryCondition.java         # 查询匹配条件
│   ├── QueryMatchConfig.java       # 查询匹配配置
│   ├── SubTableJoinCondition.java  # 子表关联条件
│   ├── SubTableMapping.java        # 子表映射配置
│   ├── SyncProgress.java           # 同步进度（水印）
│   └── SyncTaskConfig.java         # 同步任务配置
├── service/
│   ├── ConfigManager.java          # 配置文件读写管理
│   ├── ConnectionTestService.java  # 数据库连接测试
│   ├── JdbcUtils.java              # JDBC 工具类
│   ├── JdyApiTestService.java      # 简道云 API 连接测试
│   ├── SyncEngine.java             # 同步引擎核心逻辑
│   ├── SystemTrayManager.java      # 系统托盘管理
│   └── TaskScheduler.java          # 定时任务调度器
└── view/
    └── MainWindow.java             # 主窗口布局与导航
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 数据库

### 编译运行

```bash
# 编译
mvn clean compile

# 运行
mvn javafx:run
```

### 打包

```bash
# 打包为包含依赖的 JAR
mvn clean package

# 运行打包后的 JAR
java --add-exports java.desktop/com.apple.eawt=ALL-UNNAMED \
     --add-exports java.desktop/com.apple.eawt.event=ALL-UNNAMED \
     -jar target/jdy-datesync-gui-1.0.0-jar-with-dependencies.jar
```

## 使用说明

### 1. 配置数据源

在「数据源配置」页面添加 MySQL 数据库连接信息（主机、端口、数据库名、用户名、密码），点击「测试连接」验证。

### 2. 配置简道云应用

在「简道云配置」页面添加简道云应用的 App ID 和 API Token。API Token 可在简道云应用的「API 密钥」中获取。

### 3. 配置表单映射

在「表单映射」页面创建映射配置：
- 选择关联的数据源和简道云应用
- 填写主表名和简道云表单 Entry ID
- 配置主表字段与简道云控件的映射关系
- 可选配置子表映射和查询匹配条件
- 选择增量模式（自增 ID / 时间戳）及增量字段

### 4. 创建同步任务

在「同步任务」页面创建同步任务：
- 关联一个或多个表单映射配置
- 设置同步间隔（分钟）
- 启用/禁用任务
- 配置批量大小和重试次数

### 5. 查看日志

在「运行日志」页面查看同步执行的详细日志。

## 同步原理

1. **增量同步**：基于水印（Watermark）机制，记录上次同步的最大 ID 或时间戳，每次只同步新增数据
2. **匹配更新**：通过查询条件在简道云中查找已有数据，存在则更新，不存在则新建
3. **子表关联**：通过 JOIN 条件关联主表和子表，将子表数据作为简道云子表单推送
4. **断点续传**：同步进度实时持久化，推送成功后更新水印，失败则下次重试

## 配置文件

所有配置文件存储在用户主目录下的 `~/.jdy-datesync/` 目录：

| 文件 | 说明 |
|------|------|
| `data_sources.json` | 数据源配置列表 |
| `jdy_apps.json` | 简道云应用配置列表 |
| `form_mappings.json` | 表单映射配置列表 |
| `sync_tasks.json` | 同步任务配置列表 |
| `sync_progress.json` | 同步进度（水印） |

## 简道云 API

本工具使用简道云 V5 API：

- 批量新建：`POST /api/v5/app/entry/data/batch_create`
- 查询数据：`POST /api/v5/app/entry/data/list`
- 更新数据：`POST /api/v5/app/entry/data/update`

API 文档参考：[简道云开放平台](https://open.jiandaoyun.com/)

## 许可证

本项目仅供内部使用。
