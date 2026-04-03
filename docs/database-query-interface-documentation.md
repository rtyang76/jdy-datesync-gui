# 数据库查询接口文档

## 概述

本文档详细说明简道云数据同步程序中的数据库查询接口，包括SQL查询逻辑、数据获取方式、连接池管理和数据处理流程。系统主要操作SQL Server数据库，涉及订单数据和物料数据的查询与同步状态管理。

## 数据库架构

### 核心数据表

#### 1. oms_order（订单表）
主要存储订单和工单相关信息，包含80+个字段：

**关键字段说明**：
- `id`: 主键ID，用于增量同步
- `sid`: 系统标识
- `order_number`: 订单号
- `job_num`: 工单号
- `job_status`: 工单状态
- `item_number`: 物料号
- `customer_code`: 客户代码
- `work_required_date`: 工作要求日期
- `factory_product_instructions`: 工厂产品说明

#### 2. oms_job_item_info（物料信息表）
存储物料详细信息，包含60+个字段：

**关键字段说明**：
- `id`: 主键ID
- `job_num`: 工单号
- `item_number`: 物料号
- `product_name`: 产品名称
- `product_category`: 产品类别
- `item_capacity`: 物料容量

#### 3. sync_status（同步状态表）
记录同步进度和状态：

**字段说明**：
- `id`: 主键ID
- `last_sync_id`: 最后同步的订单ID
- `item_sync_id`: 最后同步的物料ID
- `sync_date`: 同步日期
- `sync_count`: 同步计数（存储自定义码字符）

### 子表结构
- `requireComponentList`: 物料需求清单
- `testProcessSchemeList`: 测试流程方案
- `waferDcList`: 晶圆DC信息

## 数据库连接管理

### 连接池配置
系统使用HikariCP连接池进行数据库连接管理：

```java
// 连接池参数配置
最大连接数: 10
最小空闲连接: 5
连接超时: 20秒
最大生命周期: 20分钟
```

### 连接获取和释放
```java
// 获取连接
Connection conn = DatabaseConnectionPool.getConnection();

// 使用完毕后释放连接
DatabaseConnectionPool.returnConnection(conn);
```

## 核心查询接口

### 1. 获取同步状态信息

#### getLastSyncId()
**功能**: 获取上次同步的订单ID
**SQL查询**:
```sql
SELECT TOP 1 last_sync_id 
FROM sync_status 
ORDER BY id DESC
```

**返回值**: Integer类型，如果不存在记录则返回null

#### getLastSyncDateAndCount()
**功能**: 获取上次同步日期和计数信息
**SQL查询**:
```sql
SELECT TOP 1 sync_date, sync_count 
FROM sync_status 
ORDER BY id DESC
```

**处理逻辑**:
1. 获取sync_count字符串值
2. 在CUSTOM_CODE_CHARS数组中查找字符索引
3. 如果是当天同步，返回计算出的索引；否则重置为0
4. 默认返回当天日期和计数0

**返回值**: Map包含sync_date和sync_count

### 2. 增量数据查询

#### fetchNewData()
**功能**: 获取新增的订单数据
**参数**:
- `lastSyncId`: 上次同步ID
- `fields`: 需要查询的字段列表

**SQL查询**:
```sql
-- 有同步ID的情况
SELECT TOP 50 {fields} 
FROM oms_order 
WHERE id > ? 
ORDER BY id ASC

-- 首次同步的情况
SELECT TOP 50 {fields} 
FROM oms_order 
ORDER BY id ASC
```

**查询特点**:
- 使用TOP限制每批最大50条记录
- 按ID升序排列确保数据顺序
- 支持增量查询和全量初始化

**数据类型处理**:
```java
// 日期类型转换
if (value instanceof java.sql.Date) {
    value = ((java.sql.Date) value).toLocalDate();
} else if (value instanceof java.sql.Timestamp) {
    value = ((java.sql.Timestamp) value).toLocalDateTime();
}
```

### 3. 子表数据查询

#### querySubTableWithMapping()
**功能**: 查询订单相关的子表数据
**参数**:
- `orderId`: 订单ID
- `tableName`: 子表名称
- `fieldMapping`: 字段映射配置

**SQL查询**:
```sql
SELECT * FROM {tableName} WHERE order_id = ?
```

**数据处理逻辑**:
1. 根据字段映射转换字段名
2. 处理null值为空字符串
3. 数字类型转换为字符串
4. 字符串类型去除前后空格
5. 包装为简道云API格式

### 4. 同步状态更新

#### updateSyncStatus()
**功能**: 更新同步状态信息
**参数**:
- `lastSyncId`: 最新同步ID
- `syncDate`: 同步日期
- `syncCount`: 同步计数

**SQL操作**:
```sql
-- 更新现有记录
UPDATE sync_status 
SET last_sync_id = ?, sync_date = ?, sync_count = ? 
WHERE id = (SELECT MAX(id) FROM sync_status)

-- 如果没有记录则插入
INSERT INTO sync_status (last_sync_id, item_sync_id, sync_date, sync_count) 
VALUES (?, 0, ?, ?)
```

**自定义码处理**:
```java
// 将计数索引转换为自定义码字符
String syncCodeChar = (syncCount < Constants.CUSTOM_CODE_MAX_COUNT) ? 
    String.valueOf(Constants.CUSTOM_CODE_CHARS[syncCount]) : "Z";
```

## 查询优化策略

### 1. 索引优化
- 在`id`字段上建立聚集索引
- 在`order_id`字段上建立非聚集索引
- 在`sync_date`字段上建立索引

### 2. 分页查询
- 使用TOP限制每次查询记录数
- 基于ID进行增量查询
- 避免大结果集查询

### 3. 连接池优化
- 合理设置连接池大小
- 及时释放数据库连接
- 监控连接池使用情况

## 错误处理和重试机制

### 重试配置
```java
最大重试次数: 10
重试间隔: 5秒
重试策略: 固定间隔
```

### 异常处理
```java
try {
    // 数据库操作
} catch (SQLException e) {
    retryCount++;
    if (retryCount >= Constants.MAX_RETRY) {
        LogUtil.logError("查询数据失败，已重试" + Constants.MAX_RETRY + "次: " + e.getMessage());
        return data;
    }
    LogUtil.logWarning("查询数据失败，准备第" + retryCount + "次重试...");
    Thread.sleep(Constants.RETRY_INTERVAL);
}
```

### 常见异常类型
1. **连接超时**: 网络问题或数据库负载过高
2. **SQL语法错误**: 查询语句错误
3. **权限不足**: 数据库用户权限问题
4. **表不存在**: 数据库结构变更

## 性能监控

### 关键指标
- 查询响应时间
- 连接池使用率
- 查询成功率
- 数据处理量

### 日志记录
```java
// 查询开始
LogUtil.logInfo("开始查询新增数据，lastSyncId: " + lastSyncId);

// 查询结果
LogUtil.logInfo("查询完成，获取到 " + data.size() + " 条记录");

// 异常记录
LogUtil.logError("查询数据失败: " + e.getMessage());
```

## 数据类型映射

### Java到SQL Server类型映射
| Java类型 | SQL Server类型 | 处理方式 |
|----------|----------------|----------|
| String | VARCHAR/NVARCHAR | 直接映射 |
| Integer | INT | 直接映射 |
| LocalDate | DATE | 转换处理 |
| LocalDateTime | DATETIME | 转换处理 |
| BigDecimal | DECIMAL | 转换为字符串 |

### 特殊字段处理
1. **日期字段**: 统一转换为LocalDate或LocalDateTime
2. **时间戳字段**: 处理多种时间格式
3. **数值字段**: 转换为字符串避免精度问题
4. **文本字段**: 去除前后空格

## 配置示例

### db.properties配置
```properties
# 数据库连接配置
db.url=jdbc:sqlserver://localhost:1433;databaseName=your_database
db.username=your_username
db.password=your_password
db.driver=com.microsoft.sqlserver.jdbc.SQLServerDriver

# 连接池配置
db.pool.maxSize=10
db.pool.minIdle=5
db.pool.connectionTimeout=20000
db.pool.maxLifetime=1200000
```

## 使用示例

### 查询新增数据示例
```java
DatabaseService dbService = DatabaseService.getInstance();

// 获取上次同步ID
Integer lastSyncId = dbService.getLastSyncId();

// 构建查询字段
String fields = "id, sid, order_number, job_num, job_status, item_number";

// 查询新增数据
List<Map<String, Object>> newData = dbService.fetchNewData(lastSyncId, fields);

// 处理查询结果
for (Map<String, Object> record : newData) {
    System.out.println("订单ID: " + record.get("id"));
    System.out.println("工单号: " + record.get("job_num"));
}
```

### 子表查询示例
```java
// 查询物料需求清单
Map<String, String> fieldMapping = new HashMap<>();
fieldMapping.put("require_item_number", "_widget_1742269557590");
fieldMapping.put("requirement_quantity", "_widget_1742269557596");

List<Map<String, Object>> components = dbService.querySubTableWithMapping(
    orderId, "requireComponentList", fieldMapping);
```

## 最佳实践

### 1. 查询优化
- 只查询必要的字段
- 使用合适的WHERE条件
- 避免复杂的JOIN操作

### 2. 连接管理
- 及时关闭数据库连接
- 使用try-with-resources语句
- 监控连接池状态

### 3. 异常处理
- 实施合理的重试策略
- 记录详细的错误信息
- 区分可重试和不可重试异常

### 4. 性能监控
- 定期检查查询性能
- 监控数据库负载
- 优化慢查询语句

通过本文档，开发人员可以深入了解数据库查询接口的实现细节，确保数据同步程序的高效稳定运行。