# 客户DM数据同步实现方案

## 一、环境信息确认

### 1.1 虚拟机数据库（客户DM模拟环境）
- **连接地址**: `kcm91cgx.88933.vip:11151`
- **数据库名**: `LC_EDI`
- **用户名**: `sa`
- **密码**: `LC_svr1`
- **表结构**: 
  - 主表: `dm_order` (3条测试数据)
  - 子表: `dm_order_detail` (4条测试数据)

### 1.2 本地数据库
- **连接地址**: `127.0.0.1:1433`
- **数据库名**: `LC_EDI`
- **表结构**: 与虚拟机完全一致
  - 主表: `dm_order`
  - 子表: `dm_order_detail`
  - 辅助表: `sync_status` (已添加 `last_sync_time` 字段)

### 1.3 配置文件已创建
- ✅ `dm_db.properties` - DM数据库连接配置
- ✅ `dm_field_mapping.json` - 源字段到本地字段映射
- ✅ `dm_to_jdy_field_mapping.json` - 本地字段到简道云字段映射（待补充）
- ✅ `application.properties` - 已添加DM相关配置

## 二、实现阶段划分

### 阶段1: 虚拟机数据 → 本地数据库 ✅ 当前实现
**目标**: 从虚拟机数据库拉取增量数据，保存到本地数据库

**核心功能**:
1. 连接虚拟机数据库（客户DM）
2. 基于 `modify_time` 时间戳查询增量数据
3. 主表和子表数据同步
4. 数据去重和冲突处理（基于 `source_id` 和 `order_no`）
5. 更新本地时间戳水印

### 阶段2: 本地数据库 → 简道云 ⏸️ 待实现
**目标**: 将本地 dm_order 数据推送到简道云

**前置条件**:
- 需要创建简道云表单
- 需要配置字段映射（`dm_to_jdy_field_mapping.json`）

### 阶段3: 集成到现有同步流程 ⏸️ 待实现
**目标**: 串行执行所有同步任务

**执行顺序**:
1. DM数据拉取（虚拟机 → 本地）
2. DM数据推送（本地 → 简道云）
3. 客户A订单同步
4. 客户A物料同步
5. 客户A采购物料通知单同步

## 三、阶段1详细设计

### 3.1 数据库连接池管理
创建独立的 `DmDatabaseConnectionPool` 类，管理虚拟机数据库连接

### 3.2 核心服务类
- `DmDataPullService` - DM数据拉取服务（虚拟机 → 本地）
- `DmOrderDao` - DM订单数据访问对象
- `DmDataTransformService` - DM数据转换服务

### 3.3 同步逻辑

#### 3.3.1 获取时间戳水印
```sql
SELECT last_sync_time FROM sync_status WHERE id = 1
-- 初始值: 2026-01-26 18:23:05
```

#### 3.3.2 查询虚拟机增量数据（主表）
```sql
SELECT * FROM dm_order 
WHERE modify_time > @last_sync_time 
ORDER BY modify_time ASC
```

#### 3.3.3 处理每条主表记录
```java
for (主表记录) {
    // 1. 检查本地是否存在
    本地记录 = 查询本地(source_id 或 order_no)
    
    if (本地记录存在) {
        // 更新操作
        UPDATE dm_order SET 
            所有字段 = 新值,
            sync_operation = 'U',
            sync_status = 0,
            updated_time = GETDATE()
        WHERE source_id = @source_id
    } else {
        // 插入操作
        INSERT INTO dm_order (...)
        VALUES (..., sync_operation='C', sync_status=0)
    }
    
    // 2. 同步子表数据
    查询虚拟机子表(source_id)
    for (子表记录) {
        处理子表记录(order_id, 子表数据)
    }
}
```

#### 3.3.4 更新时间戳水印
```sql
UPDATE sync_status 
SET last_sync_time = @max_modify_time 
WHERE id = 1
```

### 3.4 数据冲突处理策略

#### 策略1: 基于 source_id 唯一性
- `source_id` 是虚拟机数据库的主键ID
- 本地表有唯一约束: `CONSTRAINT uq_source_id UNIQUE (source_id)`
- **判断逻辑**: 先查询 `source_id` 是否存在

#### 策略2: 基于 order_no 唯一性
- `order_no` 是工单号，业务唯一标识
- 本地表有唯一约束: `CONSTRAINT uq_order_no UNIQUE (order_no)`
- **判断逻辑**: 如果 `source_id` 不存在，再查询 `order_no`

#### 策略3: 更新 vs 插入
```
IF (本地存在 source_id) THEN
    UPDATE (更新所有字段)
ELSE IF (本地存在 order_no) THEN
    UPDATE (更新所有字段，包括 source_id)
ELSE
    INSERT (新增记录)
END IF
```

### 3.5 子表同步策略

#### 方案A: 删除重建（推荐）
```sql
-- 1. 删除旧子表数据
DELETE FROM dm_order_detail WHERE order_id = @order_id

-- 2. 插入新子表数据
INSERT INTO dm_order_detail (...) VALUES (...)
```

**优点**: 逻辑简单，数据一致性高
**缺点**: 删除操作可能影响性能

#### 方案B: 逐条对比更新
```sql
-- 1. 查询虚拟机子表
-- 2. 查询本地子表
-- 3. 对比差异，执行 INSERT/UPDATE/DELETE
```

**优点**: 减少数据库操作
**缺点**: 逻辑复杂，需要对比算法

**当前采用**: 方案A（删除重建）

### 3.6 错误处理

#### 重试机制
- 最大重试次数: 10次
- 重试间隔: 5秒
- 记录失败信息到 `sync_error` 字段

#### 事务管理
- 主表和子表操作在同一事务中
- 失败时回滚，不影响其他记录

## 四、需要补充的信息

### 4.1 虚拟机数据库表名确认 ❓
**问题**: 虚拟机上的表名是什么？
- 当前配置: `dm_order_source` 和 `dm_order_detail_source`
- 实际表名: `dm_order` 和 `dm_order_detail`

**需要确认**: 虚拟机上的实际表名

### 4.2 字段映射确认 ❓
**问题**: 虚拟机数据库的字段名是否与本地完全一致？

**当前假设**: 字段名完全一致（因为表结构相同）

**如果不一致**: 需要更新 `dm_field_mapping.json` 中的映射关系

### 4.3 时间戳字段确认 ✅
- 虚拟机主表: `modify_time` ✅
- 虚拟机子表: `modify_time` ✅
- 本地水印字段: `sync_status.last_sync_time` ✅

### 4.4 数据库名称确认 ✅
- 虚拟机数据库名: `LC_EDI` ✅
- 本地数据库名: `LC_EDI` ✅

## 五、实现步骤

### Step 1: 创建数据库连接池 ✅ 待实现
- 类名: `DmDatabaseConnectionPool`
- 配置来源: `dm_db.properties`

### Step 2: 创建DAO层 ✅ 待实现
- `DmOrderDao` - 本地数据库操作
- `DmRemoteDao` - 虚拟机数据库查询

### Step 3: 创建服务层 ✅ 待实现
- `DmDataPullService` - 数据拉取服务
- `DmDataTransformService` - 数据转换服务

### Step 4: 集成到主程序 ✅ 待实现
- 修改 `SyncApplication.java`
- 添加DM同步任务到定时调度

### Step 5: 测试验证 ✅ 待实现
- 单元测试
- 集成测试
- 数据一致性验证

## 六、测试计划

### 6.1 单元测试
- [ ] 测试虚拟机数据库连接
- [ ] 测试增量数据查询
- [ ] 测试数据转换逻辑
- [ ] 测试冲突处理逻辑

### 6.2 集成测试
- [ ] 测试完整同步流程
- [ ] 测试主子表关联
- [ ] 测试时间戳更新
- [ ] 测试错误重试

### 6.3 数据验证
- [ ] 验证数据完整性
- [ ] 验证数据一致性
- [ ] 验证时间戳准确性

## 七、当前状态总结

### ✅ 已完成
1. 虚拟机数据库环境搭建（3条主表，4条子表）
2. 本地数据库表结构创建
3. 配置文件创建（dm_db.properties, dm_field_mapping.json）
4. sync_status 表添加 last_sync_time 字段
5. 虚拟机数据库连接测试成功

### 🔄 进行中
1. 实现 DmDataPullService 核心逻辑

### ⏸️ 待开始
1. 简道云表单创建
2. 简道云字段映射配置
3. DM数据推送到简道云功能

### ❓ 待确认
1. 虚拟机数据库表名（当前假设与本地一致）
2. 字段映射关系（当前假设完全一致）
