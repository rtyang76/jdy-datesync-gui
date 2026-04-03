# 简道云API接口调用文档

## 概述

本文档详细说明简道云数据同步程序中使用的API接口，包括请求格式、响应处理和错误码说明。系统通过简道云API v5版本进行数据交互，支持批量创建、查询和更新操作。

## API基础信息

### 基础配置
- **API版本**: v5
- **基础URL**: `https://api.jiandaoyun.com/api/v5`
- **字符编码**: UTF-8
- **请求方式**: POST
- **认证方式**: Bearer Token

### 通用请求头
```http
Authorization: Bearer {api_token}
Content-Type: application/json; charset=UTF-8
Accept-Charset: UTF-8
X-Request-ID: {uuid}
```

### 超时配置
- **连接超时**: 10秒
- **读取超时**: 30秒

## API接口详细说明

### 1. 批量创建数据接口

#### 接口信息
- **URL**: `/app/entry/data/batch_create`
- **方法**: POST
- **用途**: 批量创建表单数据

#### 请求格式
```json
{
  "app_id": "应用ID",
  "entry_id": "表单ID",
  "data_list": [
    {
      "字段ID": {
        "value": "字段值"
      }
    }
  ],
  "is_start_workflow": true
}
```

#### 请求参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| app_id | String | 是 | 简道云应用ID |
| entry_id | String | 是 | 表单ID |
| data_list | Array | 是 | 数据列表，每个元素为一条记录 |
| is_start_workflow | Boolean | 否 | 是否启动工作流，默认true |

#### 数据格式说明
每条数据记录的格式为：
```json
{
  "_widget_1742010071749": {
    "value": "系统标识值"
  },
  "_widget_1742010071750": {
    "value": "订单号值"
  }
}
```

#### 响应格式
成功响应：
```json
{
  "status": "success",
  "data": [
    {
      "dataId": "创建的数据ID"
    }
  ]
}
```

失败响应：
```json
{
  "status": "error",
  "error": {
    "code": "错误码",
    "message": "错误信息"
  }
}
```

### 2. 查询数据接口

#### 接口信息
- **URL**: `/app/entry/data/list`
- **方法**: POST
- **用途**: 根据条件查询表单数据

#### 请求格式
```json
{
  "app_id": "应用ID",
  "entry_id": "表单ID",
  "limit": 10,
  "filter": {
    "rel": "and",
    "cond": [
      {
        "field": "字段ID",
        "type": "text",
        "method": "eq",
        "value": ["查询值"]
      }
    ]
  }
}
```

#### 请求参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| app_id | String | 是 | 简道云应用ID |
| entry_id | String | 是 | 表单ID |
| limit | Integer | 否 | 返回记录数限制，默认10 |
| filter | Object | 否 | 查询过滤条件 |

#### 过滤条件说明
- **rel**: 条件关系，支持"and"、"or"
- **cond**: 条件数组
  - **field**: 字段ID
  - **type**: 字段类型（text、number、date等）
  - **method**: 比较方法（eq、ne、gt、lt等）
  - **value**: 查询值数组

#### 响应格式
```json
{
  "data": [
    {
      "_id": "数据ID",
      "字段ID": "字段值"
    }
  ]
}
```

### 3. 更新数据接口

#### 接口信息
- **URL**: `/app/entry/data/update`
- **方法**: POST
- **用途**: 更新指定数据记录

#### 请求格式
```json
{
  "app_id": "应用ID",
  "entry_id": "表单ID",
  "data_id": "数据ID",
  "data": {
    "字段ID": {
      "value": "新值"
    }
  },
  "is_start_trigger": true
}
```

#### 请求参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| app_id | String | 是 | 简道云应用ID |
| entry_id | String | 是 | 表单ID |
| data_id | String | 是 | 要更新的数据ID |
| data | Object | 是 | 更新的数据内容 |
| is_start_trigger | Boolean | 否 | 是否触发智能助手，默认true |

#### 响应格式
成功响应：
```json
{
  "status": "success",
  "dataId": "更新的数据ID"
}
```

## 响应处理机制

### 成功判断逻辑
系统采用多重判断机制确定API调用是否成功：

1. **状态字段判断**: `status` 字段为 "success"
2. **数据字段判断**: 包含 `data` 字段且不为空
3. **ID字段判断**: 存在 `dataId` 或 `data_id` 字段

```java
// 成功判断示例代码
boolean success = false;
if (resp.containsKey("status") && "success".equals(resp.get("status"))) {
    success = true;
} else if (resp.containsKey("data") && resp.get("data") != null) {
    success = true;
} else if (resp.containsKey("dataId") || resp.containsKey("data_id")) {
    success = true;
}
```

### 错误处理
- **空响应**: 记录错误日志，返回失败状态
- **解析失败**: 记录解析错误，返回失败状态
- **业务失败**: 记录完整响应内容，便于问题排查

## 常见错误码说明

### HTTP状态码
| 状态码 | 说明 | 处理方式 |
|--------|------|----------|
| 200 | 请求成功 | 正常处理响应 |
| 400 | 请求参数错误 | 检查请求格式和参数 |
| 401 | 认证失败 | 检查API Token |
| 403 | 权限不足 | 检查应用权限配置 |
| 429 | 请求频率限制 | 实施重试机制 |
| 500 | 服务器内部错误 | 重试或联系技术支持 |

### 业务错误码
| 错误码 | 错误信息 | 解决方案 |
|--------|----------|----------|
| INVALID_APP_ID | 无效的应用ID | 检查配置文件中的app_id |
| INVALID_ENTRY_ID | 无效的表单ID | 检查配置文件中的entry_id |
| FIELD_NOT_FOUND | 字段不存在 | 检查字段映射配置 |
| DATA_VALIDATION_ERROR | 数据验证失败 | 检查数据格式和必填字段 |
| QUOTA_EXCEEDED | 配额超限 | 联系管理员增加配额 |

## 最佳实践建议

### 1. 批量处理
- 建议每批处理50条记录以内
- 避免单次请求数据量过大
- 合理设置批处理间隔

### 2. 错误重试
- 实施指数退避重试策略
- 最大重试次数不超过10次
- 记录详细的错误日志

### 3. 性能优化
- 复用HTTP连接
- 合理设置超时时间
- 监控API调用频率

### 4. 安全考虑
- 妥善保管API Token
- 使用HTTPS协议
- 定期更新认证信息

## 配置示例

### application.properties配置
```properties
# 简道云API配置
jdy.apiUrl=https://api.jiandaoyun.com/api/v5/app/entry/data/batch_create
jdy.queryUrl=https://api.jiandaoyun.com/api/v5/app/entry/data/list
jdy.updateUrl=https://api.jiandaoyun.com/api/v5/app/entry/data/update
jdy.apiToken=Bearer your_api_token_here

# 应用和表单配置
jdy.appId=your_app_id
jdy.entryId=your_entry_id
jdy.itemEntryId=your_item_entry_id
```

## 调用示例

### Java代码示例
```java
// 创建数据示例
JiandaoyunApiService apiService = JiandaoyunApiService.getInstance();
List<Map<String, Object>> dataList = new ArrayList<>();
Map<String, Object> data = new HashMap<>();
data.put("_widget_1742010071749", Collections.singletonMap("value", "SID001"));
dataList.add(data);

boolean success = apiService.createData("appId", "entryId", dataList, true);
if (success) {
    System.out.println("数据创建成功");
} else {
    System.out.println("数据创建失败");
}
```

## 监控和日志

### 关键监控指标
- API调用成功率
- 平均响应时间
- 错误率统计
- 数据同步量

### 日志记录要点
- 请求和响应的完整内容
- 错误详情和堆栈信息
- 性能指标和时间戳
- 业务关键字段值

通过本文档，开发人员可以全面了解简道云API的使用方法，确保数据同步程序的稳定运行。