# 简道云API配置文档

## 概述

本文档详细说明简道云数据同步程序中简道云API的配置方法，包括API密钥获取、应用配置、字段映射设置、API调用参数配置等内容。

## 简道云API基础配置

### 1. API密钥配置

#### 获取API Token
1. **登录简道云管理后台**
   - 访问 https://www.jiandaoyun.com
   - 使用管理员账号登录

2. **进入API设置页面**
   - 点击右上角头像 → 个人设置
   - 选择"开放接口"选项卡
   - 或直接访问：https://www.jiandaoyun.com/profile#/open

3. **生成API Token**
   ```
   点击"生成新的API Token"按钮
   复制生成的Token（格式如：U4Sxwm7yhw46yjqJsVGgMcjBWk36nKvt）
   ```

4. **配置API Token**
   ```properties
   # 在application.properties中配置
   jdy.apiToken=Bearer U4Sxwm7yhw46yjqJsVGgMcjBWk36nKvt
   ```

#### API Token安全管理
```properties
# 生产环境建议使用环境变量
jdy.apiToken=Bearer ${JDY_API_TOKEN:default_token}

# 或使用加密配置
jdy.apiToken=Bearer {encrypted}AES:base64encodedtoken
```

### 2. 应用和表单配置

#### 获取应用ID (App ID)
1. **进入应用管理**
   - 登录简道云后台
   - 选择目标应用
   - 点击应用设置

2. **查看应用信息**
   ```
   应用设置 → 应用信息 → 应用ID
   复制应用ID（格式如：672f1dc45d82b890f5231d52）
   ```

3. **配置应用ID**
   ```properties
   jdy.appId=672f1dc45d82b890f5231d52
   ```

#### 获取表单ID (Entry ID)
1. **进入表单设计**
   - 在应用中选择目标表单
   - 点击表单设置

2. **查看表单信息**
   ```
   表单设置 → 基础设置 → 表单ID
   复制表单ID（格式如：67d4f6d78c3252c7117ac665）
   ```

3. **配置表单ID**
   ```properties
   # 订单表单ID
   jdy.entryId=67d4f6d78c3252c7117ac665
   
   # 如果有多个表单，可以配置多个
   jdy.orderEntryId=67d4f6d78c3252c7117ac665
   jdy.itemEntryId=67d4f6d78c3252c7117ac666
   ```

### 3. API端点配置

#### 标准API端点
```properties
# 批量创建数据API
jdy.apiUrl=https://api.jiandaoyun.com/api/v5/app/entry/data/batch_create

# 查询数据API
jdy.queryUrl=https://api.jiandaoyun.com/api/v5/app/entry/data/list

# 更新数据API
jdy.updateUrl=https://api.jiandaoyun.com/api/v5/app/entry/data/update

# 删除数据API（可选）
jdy.deleteUrl=https://api.jiandaoyun.com/api/v5/app/entry/data/delete

# 获取单条数据API（可选）
jdy.retrieveUrl=https://api.jiandaoyun.com/api/v5/app/entry/data/retrieve
```

#### API版本说明
```properties
# 当前使用的是v5版本API
# v5版本特性：
# - 支持批量操作
# - 更好的性能
# - 完整的字段类型支持
# - 子表单支持

# 如果需要使用其他版本
jdy.apiVersion=v5
jdy.baseUrl=https://api.jiandaoyun.com/api/${jdy.apiVersion}
```

## 字段映射配置详解

### 1. 字段ID获取方法

#### 通过表单设计器获取
1. **进入表单设计**
   - 选择目标表单
   - 点击"设计表单"

2. **查看字段属性**
   ```
   选择字段 → 右侧属性面板 → 字段设置 → 字段标识
   字段ID格式：_widget_1742010071749
   ```

3. **批量获取字段ID**
   ```javascript
   // 在浏览器控制台执行以下代码
   var fields = [];
   document.querySelectorAll('[data-widget-id]').forEach(function(el) {
       var id = el.getAttribute('data-widget-id');
       var label = el.querySelector('.widget-label');
       if (label) {
           fields.push({
               id: id,
               label: label.textContent.trim()
           });
       }
   });
   console.table(fields);
   ```

#### 通过API获取表单结构
```bash
# 使用curl获取表单结构
curl -X POST "https://api.jiandaoyun.com/api/v5/app/form/widgets" \
  -H "Authorization: Bearer YOUR_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "app_id": "672f1dc45d82b890f5231d52",
    "entry_id": "67d4f6d78c3252c7117ac665"
  }'
```

### 2. 订单字段映射配置

#### 主表字段映射结构
```json
{
  "main_fields": {
    "数据库字段名": "简道云字段ID",
    "sid": "_widget_1742010071749",
    "order_number": "_widget_1742010071750",
    "job_num": "_widget_1742010071756",
    "customer_code": "_widget_1742010071778"
  }
}
```

#### 常用字段类型映射
```json
{
  "main_fields": {
    // 文本字段
    "order_number": "_widget_1742010071750",
    "customer_code": "_widget_1742010071778",
    
    // 数字字段
    "order_quantity": "_widget_1742010071791",
    "process_price": "_widget_1742010071785",
    
    // 日期字段
    "work_required_date": "_widget_1742010071786",
    "work_start_date": "_widget_1742010071787",
    
    // 选择字段
    "job_status": "_widget_1742010071758",
    "currency": "_widget_1742010071770",
    
    // 布尔字段
    "is_formal_work_type": "_widget_1742010071764",
    "sync_mps": "_widget_1742010071775"
  }
}
```

#### 子表字段映射配置
```json
{
  "sub_tables": {
    "requireComponentList": {
      "require_item_number": "_widget_1742269557590",
      "work_procedure": "_widget_1742269557591",
      "requirement_quantity": "_widget_1742269557596",
      "issued_quantity": "_widget_1742269557597"
    },
    "testProcessSchemeList": {
      "test_item_number": "_widget_1742269557609"
    },
    "waferDcList": {
      "component_item_id": "_widget_1742269557612",
      "lot": "_widget_1742269557613",
      "dc": "_widget_1742269557614",
      "qty": "_widget_1742355416612"
    }
  }
}
```

### 3. 物料字段映射配置

#### 物料表字段映射
```json
{
  "main_fields": {
    "job_num": "_widget_1747712590417",
    "job_version": "_widget_1747712590418",
    "item_number": "_widget_1747712590419",
    "product_name": "_widget_1747712590423",
    "product_type": "_widget_1747712590424",
    "item_capacity": "_widget_1747712590425",
    "customer_pn": "_widget_1747712590434",
    "product_series": "_widget_1747712590435",
    "quality_level": "_widget_1747712590437",
    "packaging_mode": "_widget_1747712590453",
    "item_size": "_widget_1747712590454"
  }
}
```

#### 特殊字段处理
```json
{
  "main_fields": {
    // 自定义码字段（需要特殊处理逻辑）
    "custom_code": "_widget_custom_field",
    
    // 产品类型判断字段
    "product_type_auto": "_widget_product_type",
    
    // 计算字段
    "calculated_field": "_widget_calculated",
    
    // 文件上传字段
    "attachment_field": "_widget_attachment"
  }
}
```

## API调用参数配置

### 1. 批量创建参数配置

#### 基础参数
```properties
# 是否启动工作流
jdy.isStartWorkflow=true

# 批量处理大小
jdy.batchSize=100

# 是否覆盖重复数据
jdy.isOverride=false

# 数据验证模式
jdy.validateMode=strict
```

#### 请求体结构
```json
{
  "app_id": "672f1dc45d82b890f5231d52",
  "entry_id": "67d4f6d78c3252c7117ac665",
  "data_list": [
    {
      "_widget_1742010071749": "SID001",
      "_widget_1742010071750": "ORDER001",
      "_widget_1742269557588": [
        {
          "_widget_1742269557590": "ITEM001",
          "_widget_1742269557596": 100
        }
      ]
    }
  ],
  "is_start_workflow": true
}
```

### 2. 查询参数配置

#### 查询条件配置
```json
{
  "app_id": "672f1dc45d82b890f5231d52",
  "entry_id": "67d4f6d78c3252c7117ac665",
  "filter": {
    "cond": [
      {
        "field": "_widget_1742010071749",
        "method": "eq",
        "value": "SID001"
      }
    ]
  },
  "limit": 100,
  "fields": [
    "_widget_1742010071749",
    "_widget_1742010071750"
  ]
}
```

#### 分页查询配置
```properties
# 每页记录数
jdy.pageSize=100

# 最大查询记录数
jdy.maxRecords=10000

# 查询超时时间（秒）
jdy.queryTimeout=30
```

### 3. 更新参数配置

#### 更新请求结构
```json
{
  "app_id": "672f1dc45d82b890f5231d52",
  "entry_id": "67d4f6d78c3252c7117ac665",
  "data_id": "existing_data_id",
  "data": {
    "_widget_1742010071750": "UPDATED_ORDER001",
    "_widget_1742010071791": 200
  },
  "is_start_workflow": false
}
```

## HTTP请求配置

### 1. 请求头配置

#### 标准请求头
```properties
# Content-Type
jdy.contentType=application/json; charset=UTF-8

# User-Agent
jdy.userAgent=JDYSyncApp/1.0

# Accept
jdy.accept=application/json

# 连接保持
jdy.connection=keep-alive
```

#### 完整请求头示例
```java
// Java代码中的请求头设置
HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "Bearer " + apiToken);
headers.set("Content-Type", "application/json; charset=UTF-8");
headers.set("User-Agent", "JDYSyncApp/1.0");
headers.set("Accept", "application/json");
headers.set("Connection", "keep-alive");
```

### 2. 超时配置

#### 连接超时配置
```properties
# 连接超时时间（毫秒）
jdy.connectTimeout=10000

# 读取超时时间（毫秒）
jdy.readTimeout=30000

# 写入超时时间（毫秒）
jdy.writeTimeout=30000

# 总超时时间（毫秒）
jdy.totalTimeout=60000
```

### 3. 重试配置

#### 重试策略配置
```properties
# 最大重试次数
jdy.maxRetries=3

# 重试间隔（毫秒）
jdy.retryInterval=5000

# 重试退避策略（exponential/linear/fixed）
jdy.retryBackoff=exponential

# 重试退避倍数
jdy.retryMultiplier=2

# 最大重试间隔（毫秒）
jdy.maxRetryInterval=30000
```

#### 重试条件配置
```properties
# 需要重试的HTTP状态码
jdy.retryStatusCodes=500,502,503,504,408,429

# 需要重试的异常类型
jdy.retryExceptions=SocketTimeoutException,ConnectException,UnknownHostException
```

## 错误处理配置

### 1. 错误码处理

#### 常见错误码说明
```properties
# API错误码配置
jdy.error.codes.auth=401,403
jdy.error.codes.notFound=404
jdy.error.codes.rateLimit=429
jdy.error.codes.serverError=500,502,503,504
jdy.error.codes.badRequest=400
```

#### 错误处理策略
```json
{
  "error_handling": {
    "401": "重新获取API Token",
    "403": "检查权限配置",
    "404": "检查应用ID和表单ID",
    "429": "降低请求频率",
    "500": "服务器错误，稍后重试"
  }
}
```

### 2. 数据验证错误处理

#### 字段验证错误
```json
{
  "error": {
    "code": 400,
    "message": "字段验证失败",
    "details": [
      {
        "field": "_widget_1742010071791",
        "message": "数值超出范围",
        "value": "999999999"
      }
    ]
  }
}
```

#### 必填字段错误
```json
{
  "error": {
    "code": 400,
    "message": "必填字段缺失",
    "details": [
      {
        "field": "_widget_1742010071750",
        "message": "订单号不能为空"
      }
    ]
  }
}
```

## 性能优化配置

### 1. 批量处理优化

#### 批量大小配置
```properties
# 根据数据大小调整批量处理大小
# 小数据量（每条记录<1KB）
jdy.batchSize=200

# 中等数据量（每条记录1-10KB）
jdy.batchSize=100

# 大数据量（每条记录>10KB）
jdy.batchSize=50

# 包含子表的数据
jdy.batchSizeWithSubTable=20
```

### 2. 连接池配置

#### HTTP连接池配置
```properties
# 最大连接数
jdy.http.maxConnections=20

# 每个路由的最大连接数
jdy.http.maxConnectionsPerRoute=10

# 连接空闲超时（毫秒）
jdy.http.connectionIdleTimeout=60000

# 连接生存时间（毫秒）
jdy.http.connectionTimeToLive=300000
```

### 3. 缓存配置

#### API响应缓存
```properties
# 启用响应缓存
jdy.cache.enabled=true

# 缓存过期时间（秒）
jdy.cache.expireTime=300

# 缓存最大条目数
jdy.cache.maxEntries=1000

# 缓存类型（memory/redis）
jdy.cache.type=memory
```

## 监控和日志配置

### 1. API调用监控

#### 监控指标配置
```properties
# 启用API调用监控
jdy.monitoring.enabled=true

# 监控指标收集间隔（秒）
jdy.monitoring.interval=60

# 监控数据保留时间（小时）
jdy.monitoring.retention=24
```

#### 监控指标说明
```
- API调用总数
- API调用成功率
- API调用平均响应时间
- API调用错误率
- 数据同步成功率
- 批量处理平均大小
```

### 2. 日志配置

#### API调用日志
```properties
# 启用API调用日志
jdy.logging.api.enabled=true

# 日志级别
jdy.logging.api.level=INFO

# 记录请求体（调试用）
jdy.logging.api.logRequestBody=false

# 记录响应体（调试用）
jdy.logging.api.logResponseBody=false

# 日志文件路径
jdy.logging.api.file=./logs/api-calls.log
```

#### 敏感信息过滤
```properties
# 过滤敏感字段
jdy.logging.sensitiveFields=password,token,secret,key

# 替换字符
jdy.logging.maskChar=*

# 保留字符数
jdy.logging.keepChars=4
```

## 安全配置

### 1. API Token安全

#### Token轮换配置
```properties
# Token自动轮换（如果支持）
jdy.token.autoRotate=false

# Token轮换间隔（天）
jdy.token.rotateInterval=30

# Token过期提醒（天）
jdy.token.expireWarning=7
```

#### Token存储安全
```properties
# 使用加密存储
jdy.token.encrypted=true

# 加密算法
jdy.token.encryptAlgorithm=AES-256-GCM

# 密钥存储位置
jdy.token.keyStore=./config/keystore.jks
```

### 2. 网络安全

#### HTTPS配置
```properties
# 强制使用HTTPS
jdy.https.enforced=true

# SSL证书验证
jdy.ssl.verifyHostname=true
jdy.ssl.verifyPeer=true

# 信任的证书存储
jdy.ssl.trustStore=./config/truststore.jks
jdy.ssl.trustStorePassword=password
```

#### 代理配置
```properties
# HTTP代理配置
jdy.proxy.enabled=false
jdy.proxy.host=proxy.company.com
jdy.proxy.port=8080
jdy.proxy.username=proxyuser
jdy.proxy.password=proxypass

# 代理认证类型
jdy.proxy.authType=basic
```

## 测试和验证

### 1. API连接测试

#### 连接测试脚本
```bash
#!/bin/bash
# API连接测试脚本

API_TOKEN="Bearer YOUR_API_TOKEN"
APP_ID="672f1dc45d82b890f5231d52"
ENTRY_ID="67d4f6d78c3252c7117ac665"

# 测试API连接
curl -X POST "https://api.jiandaoyun.com/api/v5/app/entry/data/list" \
  -H "Authorization: $API_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"app_id\": \"$APP_ID\",
    \"entry_id\": \"$ENTRY_ID\",
    \"limit\": 1
  }"
```

### 2. 字段映射验证

#### 映射验证工具
```java
// Java代码示例：验证字段映射
public boolean validateFieldMapping(String mappingFile) {
    try {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode mapping = mapper.readTree(new File(mappingFile));
        
        // 验证主表字段
        JsonNode mainFields = mapping.get("main_fields");
        for (Iterator<String> it = mainFields.fieldNames(); it.hasNext(); ) {
            String dbField = it.next();
            String jdyField = mainFields.get(dbField).asText();
            
            // 验证字段ID格式
            if (!jdyField.matches("_widget_\\d+")) {
                System.err.println("Invalid field ID: " + jdyField);
                return false;
            }
        }
        
        return true;
    } catch (Exception e) {
        System.err.println("Validation failed: " + e.getMessage());
        return false;
    }
}
```

### 3. 数据同步测试

#### 测试数据准备
```sql
-- 创建测试数据
INSERT INTO oms_order (
    sid, order_number, job_num, customer_code, order_quantity
) VALUES (
    'TEST001', 'ORDER_TEST_001', 'JOB_TEST_001', 'CUST001', 100
);

-- 验证测试数据
SELECT * FROM oms_order WHERE sid = 'TEST001';
```

#### 同步结果验证
```bash
# 检查同步日志
tail -f ./logs/sync.log | grep "TEST001"

# 验证简道云数据
# 通过简道云后台查看数据是否正确同步
```

## 故障排查

### 1. 常见API错误

#### 401 未授权错误
```
原因：API Token无效或过期
解决：重新生成API Token并更新配置
检查：确认Token格式正确（Bearer + 空格 + Token）
```

#### 403 权限不足错误
```
原因：API Token没有足够权限
解决：检查Token对应用户的权限设置
检查：确认应用ID和表单ID正确
```

#### 404 资源不存在错误
```
原因：应用ID或表单ID不存在
解决：重新确认ID的正确性
检查：登录简道云后台验证ID
```

#### 429 请求频率限制
```
原因：API调用频率过高
解决：降低调用频率，增加请求间隔
配置：调整批量大小和重试间隔
```

### 2. 字段映射错误

#### 字段ID不存在
```
错误信息：Field not found: _widget_1742010071749
解决方法：重新获取正确的字段ID
检查步骤：
1. 登录简道云后台
2. 进入表单设计
3. 确认字段是否存在
4. 重新获取字段ID
```

#### 字段类型不匹配
```
错误信息：Invalid field type for widget_id
解决方法：检查数据类型匹配
常见问题：
- 数字字段传入文本
- 日期字段格式不正确
- 选择字段值不在选项中
```

### 3. 网络连接问题

#### 连接超时
```bash
# 测试网络连通性
ping api.jiandaoyun.com

# 测试HTTPS连接
curl -I https://api.jiandaoyun.com

# 检查防火墙设置
telnet api.jiandaoyun.com 443
```

#### DNS解析问题
```bash
# 检查DNS解析
nslookup api.jiandaoyun.com

# 使用备用DNS
echo "nameserver 8.8.8.8" >> /etc/resolv.conf
```

通过以上详细的简道云API配置文档，您可以正确配置和使用简道云API进行数据同步，确保系统与简道云平台的稳定连接和数据传输。