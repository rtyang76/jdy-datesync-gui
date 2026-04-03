# DM业务数据同步指南

## 概述

DM业务数据同步是指将客户B（DM）的远程SQL Server数据库中的订单数据，经过本地中转，最终同步到简道云表单的完整流程。

该流程分为两个阶段：
1. **阶段1 (DM Pull)**: 从远程/虚拟机SQL Server拉取增量数据到本地SQL Server。
2. **阶段2 (DM Push)**: 将本地SQL Server中的数据推送到简道云。

## 系统架构

```
远程数据库 (DM客户)
    ↓ (阶段1: DmDataPullService)
本地数据库 (dm_order + dm_order_detail)
    ↓ (阶段2: DmJdySyncService)
简道云表单
```

---

## 阶段1：远程到本地 (DM Pull)

此阶段负责连接客户的SQL Server数据库，读取增量数据（基于 `modify_time`），并保存到本地数据库的 `dm_order` 和 `dm_order_detail` 表中。

### 1.1 数据库连接配置 (`dm_db.properties`)

位于项目根目录下，用于配置远程数据库连接。

```properties
# 客户数据库连接信息
db.url=jdbc:sqlserver://客户IP:端口;databaseName=数据库名;trustServerCertificate=true
db.username=数据库用户名
db.password=数据库密码

# 表名配置（根据客户实际表名修改）
dm.mainTable=dm_order
dm.detailTable=dm_order_detail
```

### 1.2 字段映射配置 (`dm_field_mapping.json`)

用于定义**远程数据库字段**到**本地数据库字段**的映射关系。

```json
{
  "main_table": {
    "source_to_local": {
      "客户字段名": "本地字段名",
      "单号": "order_no",
      "ModifyDate": "modify_time"
    }
  },
  "detail_table": {
    "source_to_local": {
      "客户明细字段名": "本地明细字段名"
    }
  }
}
```
**注意**：如果不配置某个字段，该字段将不会被同步到本地。

---

## 阶段2：本地到简道云 (DM Push)

此阶段读取本地数据库中 `sync_status = 0` (待同步) 的记录，推送到简道云。

### 2.1 核心逻辑
- **自动判断新增/更新**: 根据 `sync_operation` 字段 ('C'=Create, 'U'=Update)。
- **批量处理**: 新增记录采用批量API接口，提高效率。
- **断点续传**: 记录简道云返回的 `data_id` 到本地 `jdy_data_id` 字段，用于后续更新。

### 2.2 简道云配置 (`application.properties`)

```properties
# DM简道云配置
dm.jdy.appId=你的应用ID
dm.jdy.entryId=你的表单ID
dm.jdy.orderNoWidget=_widget_工单号ID
```

### 2.3 简道云字段映射 (`dm_to_jdy_field_mapping.json`)

用于定义**本地数据库字段**到**简道云Widget ID**的映射。

```json
{
  "main_fields": {
    "order_no": "_widget_123456",
    "factory": "_widget_789012"
  },
  "sub_tables": {
    "order_details": {
      "material_code": "_widget_abcdef"
    }
  }
}
```

---

## 数据库表结构 (本地)

### dm_order (主表)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | INT | 本地主键 |
| source_id | INT | 源系统ID |
| order_no | NVARCHAR(50) | 工单号 |
| sync_status | TINYINT | 0=待同步, 1=已同步, 9=失败 |
| sync_operation | CHAR(1) | C=创建, U=更新 |
| jdy_data_id | NVARCHAR(50) | 简道云数据ID |
| sync_error | NVARCHAR(500) | 错误信息 |

### dm_order_detail (子表)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | INT | 本地主键 |
| order_id | INT | 关联主表ID |
| line_no | INT | 行号 |
| material_code | NVARCHAR(50) | 物料编码 |

---

## 故障排查

### 常见问题

1. **字段未同步**
   - 检查 `dm_field_mapping.json` (远程->本地) 是否配置了该字段。
   - 检查 `dm_to_jdy_field_mapping.json` (本地->简道云) 是否配置了Widget ID。

2. **同步失败 (sync_status=9)**
   - 检查数据库 `sync_error` 字段查看具体错误信息。
   - 常见原因：API限流、必填字段为空、字段类型不匹配。

3. **如何重置失败记录？**
   ```sql
   UPDATE dm_order SET sync_attempts = 0, sync_status = 0 WHERE sync_status = 9;
   ```

4. **如何手动运行？**
   ```bash
   # 仅拉取
   java -jar api_sql-1.0-SNAPSHOT.jar dm
   # 仅推送
   java -jar api_sql-1.0-SNAPSHOT.jar dmpush
   ```
