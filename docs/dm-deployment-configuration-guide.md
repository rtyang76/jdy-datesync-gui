# DM（客户B）数据同步部署配置指南

## 概述

本文档说明如何配置DM客户的数据库字段映射，以及部署时需要修改的配置项。

## 一、字段映射配置

### 1.1 配置文件位置

**文件名**：`dm_field_mapping.json`（位于项目根目录）

### 1.2 配置文件结构

```json
{
  "main_table": {
    "source_to_local": {
      "客户数据库字段名": "本地数据库字段名"
    }
  },
  "detail_table": {
    "source_to_local": {
      "客户数据库字段名": "本地数据库字段名"
    }
  },
  "status_mapping": {
    "字段名": {
      "客户值": 本地值
    }
  }
}
```

### 1.3 当前DM客户配置

#### 主表字段映射（dm_order）

| 客户数据库字段 | 本地数据库字段 | 说明 |
|--------------|--------------|------|
| ID | source_id | 客户系统的主键ID |
| 单号 | order_no | 工单号 |
| 月结 | month_settlement | 月结信息 |
| 加工厂 | factory | 工厂名称 |
| 负责人 | person_in_charge | 负责人 |
| 币种 | currency | 货币类型 |
| 标记 | mark | 标记信息 |
| 税率 | tax_rate | 税率 |
| 账期 | payment_terms | 付款条件 |
| 备注 | remarks | 备注 |
| 入库仓库 | in_warehouse | 入库仓库 |
| 物料仓库 | material_warehouse | 物料仓库 |
| 原账期 | original_terms | 原始账期 |
| 数量合计 | total_quantity | 总数量 |
| 含税金额合计 | total_tax_amount | 总含税金额 |
| 制单部门 | department | 部门 |
| 制单人 | creator | 创建人 |
| 审核人 | auditor | 审核人 |
| 批准人 | approver | 批准人 |
| FillDate | submit_time | 提交时间 |
| ModifyDate | modify_time | 修改时间 |
| bIsFinish | order_status | 订单状态 |

#### 子表字段映射（dm_order_detail）

| 客户数据库字段 | 本地数据库字段 | 说明 |
|--------------|--------------|------|
| ID | source_line_id | 客户系统的明细ID |
| 序号 | line_no | 行号 |
| 物料编码 | material_code | 物料编码 |
| 物料描述 | material_desc | 物料描述 |
| 数量 | quantity | 数量 |
| 单价 | unit_price | 单价 |
| 含税单价 | tax_unit_price | 含税单价 |
| 含税金额 | tax_amount | 含税金额 |
| 价格本 | price_book | 价格手册 |
| 建议数量 | suggested_quantity | 建议数量 |
| 来源单号 | source_doc_no | 源单号 |
| ModifyDate | modify_time | 修改时间 |

### 1.4 状态映射（status_mapping）

**用途**：将客户数据库的状态值映射为本地数据库的状态值

**示例**：
```json
"status_mapping": {
  "bIsFinish": {
    "-1": -1,
    "0": 0,
    "1": 1
  }
}
```

**说明**：
- `bIsFinish` 是客户数据库的字段名
- 左边的值（如"-1"）是客户数据库中的值
- 右边的值（如-1）是要存储到本地数据库的值
- 在这个例子中，值是直接映射的（-1→-1, 0→0, 1→1）

**你的需求**：
> "这个状态同步数值就可以了，我到简道云再做逻辑判断"

这意味着：
1. 程序直接读取客户数据库的状态值
2. 不做任何转换，原样存储到本地数据库
3. 推送到简道云时也是原样推送
4. 在简道云中通过公式或智能助手做业务逻辑判断

**当前配置已满足需求**：状态值直接映射，无需修改。

## 二、部署配置清单

### 2.1 必须修改的配置文件

#### 1. `dm_db.properties` - 客户数据库连接配置

```properties
# 客户数据库连接信息
db.url=jdbc:sqlserver://客户IP:端口;databaseName=数据库名;trustServerCertificate=true
db.username=数据库用户名
db.password=数据库密码

# 连接池配置（一般不需要修改）
db.poolSize=10
db.minIdle=5
db.idleTimeout=300000
db.connectionTimeout=20000
db.maxLifetime=1200000

# 表名配置（根据客户实际表名修改）
dm.mainTable=dm_order
dm.detailTable=dm_order_detail
```

**部署时修改**：
- ✅ `db.url` - 改为客户的数据库地址和端口
- ✅ `db.username` - 改为客户的数据库用户名
- ✅ `db.password` - 改为客户的数据库密码
- ⚠️ `dm.mainTable` - 如果客户的主表名不是`dm_order`，需要修改
- ⚠️ `dm.detailTable` - 如果客户的子表名不是`dm_order_detail`，需要修改

#### 2. `dm_field_mapping.json` - 字段映射配置

**部署时修改**：
- ✅ 根据客户实际的数据库字段名修改左边的"客户数据库字段名"
- ⚠️ 右边的"本地数据库字段名"一般不需要修改（除非本地数据库结构变化）

**示例**：如果客户的字段名是"订单号"而不是"单号"
```json
"订单号": "order_no"  // 修改前是 "单号": "order_no"
```

#### 3. `application.properties` - 简道云配置

```properties
# DM简道云配置
dm.jdy.appId=客户的应用ID
dm.jdy.entryId=客户的表单ID
dm.jdy.orderNoWidget=工单号字段的widget_id

# 简道云API配置
jdy.apiUrl=https://api.jiandaoyun.com/api/v5/app/entry/data/create
jdy.queryUrl=https://api.jiandaoyun.com/api/v5/app/entry/data/list
jdy.updateUrl=https://api.jiandaoyun.com/api/v5/app/entry/data/update
jdy.apiToken=简道云API密钥
```

**部署时修改**：
- ✅ `dm.jdy.appId` - 改为客户的简道云应用ID
- ✅ `dm.jdy.entryId` - 改为客户的简道云表单ID
- ✅ `dm.jdy.orderNoWidget` - 改为工单号字段的widget ID
- ✅ `jdy.apiToken` - 改为客户的简道云API密钥

#### 4. `dm_to_jdy_field_mapping.json` - 简道云字段映射

```json
{
  "main_fields": {
    "order_no": "_widget_xxxxxx",
    "factory": "_widget_xxxxxx",
    ...
  },
  "sub_tables": {
    "order_details": {
      "line_no": "_widget_xxxxxx",
      ...
    }
  }
}
```

**部署时修改**：
- ✅ 所有的`_widget_xxxxxx`都需要改为客户简道云表单的实际widget ID
- ⚠️ 左边的字段名（如"order_no"）对应本地数据库字段，一般不需要修改

### 2.2 本地数据库配置

#### `db.properties` - 本地数据库连接

```properties
db.url=jdbc:sqlserver://127.0.0.1:1433;databaseName=LC_EDI;trustServerCertificate=true
db.username=sa
db.password=LC_svr1
```

**部署时修改**：
- ⚠️ 根据实际的本地数据库配置修改

## 三、字段缺失处理

### 3.1 客户数据库缺少某些字段

**场景**：本地数据库有字段，但客户数据库没有对应字段

**处理方式**：
1. **在`dm_field_mapping.json`中删除该字段映射**
2. **程序会自动处理**：
   - 读取时：该字段值为NULL
   - 推送时：该字段值为空字符串""

**示例**：
如果客户没有"标记"字段，删除这一行：
```json
// 删除这一行
"标记": "mark",
```

**结果**：
- 本地数据库的`mark`字段会是NULL
- 推送到简道云时，对应的widget字段值为空字符串

### 3.2 客户数据库有额外字段

**场景**：客户数据库有字段，但本地数据库没有对应字段

**处理方式**：
1. **方案A（推荐）**：在本地数据库添加该字段
   ```sql
   ALTER TABLE dm_order ADD new_field NVARCHAR(100);
   ```

2. **方案B**：忽略该字段
   - 不在`dm_field_mapping.json`中配置
   - 程序会跳过该字段

### 3.3 程序运行时的错误处理

**情况1：字段映射错误**
```
错误：The column name 'xxx' is not valid.
原因：dm_field_mapping.json中配置的客户字段名在客户数据库中不存在
解决：检查并修正字段名
```

**情况2：字段类型不匹配**
```
错误：Cannot convert value to type xxx
原因：客户数据库字段类型与本地数据库字段类型不兼容
解决：修改本地数据库字段类型或添加数据转换逻辑
```

**情况3：必填字段为空**
```
错误：Cannot insert NULL into column 'xxx'
原因：客户数据库的必填字段值为NULL
解决：在dm_field_mapping.json中添加默认值处理，或修改本地数据库字段允许NULL
```

## 四、部署步骤

### 4.1 准备阶段

1. **获取客户数据库信息**
   - [ ] 数据库地址、端口、用户名、密码
   - [ ] 主表名和子表名
   - [ ] 所有字段名列表
   - [ ] 字段类型和说明

2. **获取简道云信息**
   - [ ] 应用ID（appId）
   - [ ] 表单ID（entryId）
   - [ ] API密钥（apiToken）
   - [ ] 所有字段的widget ID

3. **创建本地数据库表**
   ```sql
   -- 执行 docs/database-scripts/create_dm_tables.sql
   ```

### 4.2 配置阶段

1. **修改`dm_db.properties`**
   - 更新客户数据库连接信息
   - 确认表名配置

2. **修改`dm_field_mapping.json`**
   - 根据客户实际字段名更新映射
   - 删除客户不存在的字段
   - 添加客户特有的字段

3. **修改`application.properties`**
   - 更新简道云配置信息

4. **修改`dm_to_jdy_field_mapping.json`**
   - 更新所有widget ID

### 4.3 测试阶段

1. **测试数据库连接**
   ```bash
   sqlcmd -S 客户IP,端口 -U 用户名 -P 密码 -C -Q "SELECT TOP 1 * FROM 主表名"
   ```

2. **测试数据拉取**
   ```bash
   java -jar target/api_sql-1.0-SNAPSHOT-jar-with-dependencies.jar dm
   ```

3. **测试数据推送**
   ```bash
   java -jar target/api_sql-1.0-SNAPSHOT-jar-with-dependencies.jar dmpush
   ```

4. **验证简道云数据**
   - 登录简道云查看数据是否正确
   - 检查所有字段是否正确映射

### 4.4 部署阶段

1. **备份配置文件**
2. **部署到生产环境**
3. **启动定时任务**
4. **监控运行日志**

## 五、常见问题

### Q1: 如何知道客户数据库的字段名？

**A**: 执行以下SQL查询：
```sql
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = '主表名' 
ORDER BY ORDINAL_POSITION;
```

### Q2: 如何获取简道云的widget ID？

**A**: 
1. 登录简道云
2. 进入表单设计
3. 点击字段，在右侧属性面板查看"字段ID"
4. 或使用简道云API查询表单结构

### Q3: status_mapping什么时候需要配置？

**A**: 
- **需要配置**：当客户数据库的状态值与本地数据库不一致时
  - 例如：客户用"Y"/"N"表示状态，本地用1/0
  - 配置：`"Y": 1, "N": 0`

- **不需要配置**：当状态值一致时（如当前DM客户）
  - 客户和本地都用-1/0/1
  - 配置：`"-1": -1, "0": 0, "1": 1`（直接映射）

### Q4: 如果客户字段名是中文，会有问题吗？

**A**: 不会有问题。程序支持中文字段名，只需在`dm_field_mapping.json`中正确配置即可。

### Q5: 如何处理日期时间格式不一致？

**A**: 程序会自动处理常见的日期时间格式。如果遇到特殊格式，需要在代码中添加转换逻辑。

## 六、配置文件模板

### 完整的dm_field_mapping.json模板

```json
{
  "main_table": {
    "source_to_local": {
      "客户字段1": "本地字段1",
      "客户字段2": "本地字段2"
    }
  },
  "detail_table": {
    "source_to_local": {
      "客户字段1": "本地字段1",
      "客户字段2": "本地字段2"
    }
  },
  "status_mapping": {
    "状态字段名": {
      "客户值1": 本地值1,
      "客户值2": 本地值2
    }
  }
}
```

## 七、技术支持

如有问题，请检查：
1. 日志文件：`logs/sync.log`
2. 数据库连接是否正常
3. 字段映射配置是否正确
4. 简道云API配置是否正确

---

**文档版本**：1.0  
**最后更新**：2026-02-03
