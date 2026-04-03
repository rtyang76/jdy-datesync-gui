# 故障排查指南

## 概述

本文档提供简道云数据同步程序常见问题的诊断和解决方案，帮助用户快速定位和解决部署、配置和运行过程中遇到的问题。

## 快速诊断工具

### 自动诊断脚本
```bash
# 运行自动诊断
./docs/setup-scripts/validate-deployment.sh

# 健康检查
./health-check.sh

# 查看系统状态
./status.sh
```

### 日志分析命令
```bash
# 查看实时日志
tail -f logs/application.log

# 查看错误日志
grep -i "error\|exception\|failed" logs/application.log | tail -20

# 查看今天的日志
grep "$(date +%Y-%m-%d)" logs/application.log

# 统计错误类型
grep -i "error" logs/application.log | awk '{print $4}' | sort | uniq -c | sort -nr
```

## 启动问题

### 1. Java环境问题

#### 问题现象
```
Error: Could not find or load main class org.example.JdySync
```

#### 原因分析
- Java环境未正确安装
- JAVA_HOME环境变量未设置
- Java版本不兼容

#### 解决方案
```bash
# 检查Java版本
java -version
javac -version

# 检查JAVA_HOME
echo $JAVA_HOME

# 设置JAVA_HOME (Linux)
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
echo 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64' >> ~/.bashrc

# 重新安装Java 8
sudo apt-get install openjdk-8-jdk  # Ubuntu
sudo yum install java-1.8.0-openjdk-devel  # CentOS
```

### 2. JAR文件问题

#### 问题现象
```
Error: Unable to access jarfile api_sql-1.0-SNAPSHOT-jar-with-dependencies.jar
```

#### 原因分析
- JAR文件不存在或路径错误
- 文件权限问题
- JAR文件损坏

#### 解决方案
```bash
# 检查文件是否存在
ls -la api_sql-1.0-SNAPSHOT-jar-with-dependencies.jar

# 检查文件权限
chmod 755 api_sql-1.0-SNAPSHOT-jar-with-dependencies.jar

# 验证JAR文件完整性
jar -tf api_sql-1.0-SNAPSHOT-jar-with-dependencies.jar | head -10

# 重新构建JAR文件
mvn clean package
```

### 3. 内存不足问题

#### 问题现象
```
java.lang.OutOfMemoryError: Java heap space
```

#### 原因分析
- JVM堆内存设置过小
- 系统可用内存不足
- 内存泄漏

#### 解决方案
```bash
# 增加JVM内存参数
java -Xms1g -Xmx2g -jar api_sql-1.0-SNAPSHOT-jar-with-dependencies.jar

# 检查系统内存
free -h
top -o %MEM

# 监控内存使用
watch -n 5 'free -h && echo "---" && ps aux --sort=-%mem | head -10'
```

## 配置文件问题

### 1. 配置文件找不到

#### 问题现象
```
[ERROR] 配置文件不存在: application.properties
```

#### 原因分析
- 配置文件路径错误
- 文件名拼写错误
- 权限问题

#### 解决方案
```bash
# 检查配置文件是否存在
ls -la *.properties *.json

# 检查文件权限
chmod 644 application.properties db.properties
chmod 644 field_mapping.json item_field_mapping.json

# 验证文件路径
pwd
ls -la ./config/
```

### 2. JSON格式错误

#### 问题现象
```
[ERROR] JSON解析失败: field_mapping.json
```

#### 原因分析
- JSON语法错误
- 字符编码问题
- 文件损坏

#### 解决方案
```bash
# 验证JSON格式
python -m json.tool field_mapping.json
jq . field_mapping.json

# 检查文件编码
file -bi field_mapping.json

# 转换文件编码
iconv -f GBK -t UTF-8 field_mapping.json > field_mapping_utf8.json

# 在线JSON验证工具
# https://jsonlint.com/
```

### 3. 配置项缺失

#### 问题现象
```
[ERROR] 缺少必需的配置项: jdy.apiToken
```

#### 原因分析
- 配置文件不完整
- 配置项名称错误
- 注释符号问题

#### 解决方案
```bash
# 检查配置项
grep "jdy.apiToken" application.properties

# 查看配置文件模板
cat docs/setup-scripts/application.properties.template

# 验证所有必需配置项
required_configs=(
    "jdy.apiUrl"
    "jdy.appId"
    "jdy.entryId"
    "jdy.apiToken"
    "db.url"
    "db.username"
    "db.password"
)

for config in "${required_configs[@]}"; do
    if grep -q "$config" *.properties; then
        echo "✓ $config"
    else
        echo "✗ $config 缺失"
    fi
done
```

## 数据库连接问题

### 1. 连接超时

#### 问题现象
```
[ERROR] 数据库连接超时
java.sql.SQLTimeoutException: Login timeout expired
```

#### 原因分析
- 数据库服务器未启动
- 网络连接问题
- 防火墙阻止连接
- 连接参数错误

#### 解决方案
```bash
# 测试网络连通性
ping your-database-server
telnet your-database-server 1433

# 检查防火墙
sudo ufw status  # Ubuntu
sudo firewall-cmd --list-all  # CentOS

# 测试数据库连接
sqlcmd -S your-server -U your-username -P your-password -Q "SELECT 1"

# 检查SQL Server服务状态
# Windows: services.msc
# Linux: systemctl status mssql-server
```

### 2. 认证失败

#### 问题现象
```
[ERROR] 数据库认证失败
Login failed for user 'username'
```

#### 原因分析
- 用户名或密码错误
- 用户账户被锁定
- 数据库权限不足
- 认证模式问题

#### 解决方案
```sql
-- 检查用户状态
SELECT name, is_disabled, is_policy_checked, is_expiration_checked
FROM sys.sql_logins
WHERE name = 'your-username';

-- 重置密码
ALTER LOGIN [your-username] WITH PASSWORD = 'new-password';

-- 解锁账户
ALTER LOGIN [your-username] WITH CHECK_POLICY = OFF;
ALTER LOGIN [your-username] WITH CHECK_POLICY = ON;

-- 授予权限
USE your-database;
GRANT SELECT ON oms_order TO [your-username];
GRANT SELECT ON oms_job_item_info TO [your-username];
```

### 3. 连接池问题

#### 问题现象
```
[ERROR] 连接池获取连接失败
HikariPool-1 - Connection is not available
```

#### 原因分析
- 连接池配置不当
- 连接泄漏
- 数据库连接数限制

#### 解决方案
```properties
# 调整连接池配置
db.poolSize=5
db.minIdle=2
db.connectionTimeout=30000
db.idleTimeout=600000
db.maxLifetime=1800000

# 启用连接泄漏检测
db.leakDetectionThreshold=60000
```

```sql
-- 检查数据库连接数
SELECT 
    DB_NAME(database_id) as database_name,
    COUNT(*) as connection_count
FROM sys.dm_exec_sessions
WHERE is_user_process = 1
GROUP BY database_id;

-- 查看最大连接数配置
SELECT @@MAX_CONNECTIONS;
```

## 简道云API问题

### 1. API认证失败

#### 问题现象
```
[ERROR] API调用失败: 401 Unauthorized
```

#### 原因分析
- API Token无效或过期
- Token格式错误
- 应用权限不足

#### 解决方案
```bash
# 验证Token格式
echo "Bearer YOUR_TOKEN" | grep "^Bearer "

# 测试API连接
curl -X POST "https://api.jiandaoyun.com/api/v5/app/entry/data/list" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "app_id": "YOUR_APP_ID",
    "entry_id": "YOUR_ENTRY_ID",
    "limit": 1
  }'

# 重新生成API Token
# 1. 登录简道云管理后台
# 2. 个人设置 -> 开放接口
# 3. 生成新的API Token
```

### 2. 应用ID或表单ID错误

#### 问题现象
```
[ERROR] API调用失败: 404 Not Found
```

#### 原因分析
- 应用ID不存在
- 表单ID不存在
- ID配置错误

#### 解决方案
```bash
# 验证应用ID和表单ID
# 1. 登录简道云管理后台
# 2. 应用设置 -> 应用信息 -> 应用ID
# 3. 表单设置 -> 基础设置 -> 表单ID

# 测试ID有效性
curl -X POST "https://api.jiandaoyun.com/api/v5/app/form/widgets" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "app_id": "YOUR_APP_ID",
    "entry_id": "YOUR_ENTRY_ID"
  }'
```

### 3. 请求频率限制

#### 问题现象
```
[ERROR] API调用失败: 429 Too Many Requests
```

#### 原因分析
- API调用频率过高
- 批量大小过大
- 并发请求过多

#### 解决方案
```properties
# 调整批量处理参数
jdy.batchSize=50
jdy.retryInterval=10000
jdy.maxRetries=5

# 添加请求间隔
jdy.requestInterval=1000
```

```java
// 在代码中添加延迟
Thread.sleep(1000); // 1秒延迟
```

### 4. 字段映射错误

#### 问题现象
```
[ERROR] 字段不存在: _widget_1234567890
```

#### 原因分析
- 字段ID不存在
- 字段已被删除
- 映射配置错误

#### 解决方案
```bash
# 获取表单字段结构
curl -X POST "https://api.jiandaoyun.com/api/v5/app/form/widgets" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "app_id": "YOUR_APP_ID",
    "entry_id": "YOUR_ENTRY_ID"
  }' | jq '.widgets[] | {label: .label, widget_id: .widget_id}'

# 验证字段映射
python3 << 'EOF'
import json

# 读取映射配置
with open('field_mapping.json', 'r') as f:
    mapping = json.load(f)

# 检查字段ID格式
for field, widget_id in mapping['main_fields'].items():
    if not widget_id.startswith('_widget_'):
        print(f"错误的字段ID格式: {field} -> {widget_id}")
    elif len(widget_id) < 15:
        print(f"可能错误的字段ID: {field} -> {widget_id}")
EOF
```

## 数据同步问题

### 1. 数据同步失败

#### 问题现象
```
[ERROR] 数据同步失败，跳过记录: ID=12345
```

#### 原因分析
- 数据格式不匹配
- 必填字段缺失
- 数据验证失败
- 字段长度超限

#### 解决方案
```sql
-- 检查数据完整性
SELECT 
    COUNT(*) as total_records,
    COUNT(CASE WHEN sid IS NULL THEN 1 END) as null_sid,
    COUNT(CASE WHEN order_number IS NULL THEN 1 END) as null_order_number
FROM oms_order
WHERE id > (SELECT last_sync_id FROM sync_status);

-- 查看异常数据
SELECT TOP 10 *
FROM oms_order
WHERE id > (SELECT last_sync_id FROM sync_status)
  AND (sid IS NULL OR order_number IS NULL OR LEN(order_number) > 100);
```

### 2. 重复数据问题

#### 问题现象
```
[WARN] 检测到重复记录: job_num=JOB001
```

#### 原因分析
- 数据库中存在重复记录
- 去重逻辑异常
- 同步状态异常

#### 解决方案
```sql
-- 查找重复记录
SELECT job_num, COUNT(*) as count
FROM oms_order
WHERE id > (SELECT last_sync_id FROM sync_status)
GROUP BY job_num
HAVING COUNT(*) > 1;

-- 清理重复数据（谨慎操作）
WITH DuplicateRecords AS (
    SELECT id, job_num,
           ROW_NUMBER() OVER (PARTITION BY job_num ORDER BY id DESC) as rn
    FROM oms_order
    WHERE id > (SELECT last_sync_id FROM sync_status)
)
SELECT * FROM DuplicateRecords WHERE rn > 1;
-- DELETE FROM oms_order WHERE id IN (SELECT id FROM DuplicateRecords WHERE rn > 1);
```

### 3. 同步状态异常

#### 问题现象
```
[ERROR] 同步状态更新失败
```

#### 原因分析
- sync_status表不存在
- 权限不足
- 数据库连接异常

#### 解决方案
```sql
-- 检查同步状态表
SELECT * FROM sync_status;

-- 重建同步状态表
DROP TABLE IF EXISTS sync_status;
CREATE TABLE sync_status (
    id INT IDENTITY(1,1) PRIMARY KEY,
    last_sync_id BIGINT DEFAULT 0,
    item_sync_id BIGINT DEFAULT 0,
    sync_date DATETIME DEFAULT GETDATE(),
    sync_count INT DEFAULT 0
);

-- 插入初始记录
INSERT INTO sync_status (last_sync_id, item_sync_id, sync_count)
VALUES (0, 0, 0);

-- 重置同步状态
UPDATE sync_status SET 
    last_sync_id = 0,
    item_sync_id = 0,
    sync_date = GETDATE(),
    sync_count = 0;
```

## 性能问题

### 1. 同步速度慢

#### 问题现象
```
[INFO] 订单同步完成，耗时: 300秒，处理记录数: 100
```

#### 原因分析
- 批量大小设置不当
- 数据库查询效率低
- 网络延迟高
- API响应慢

#### 解决方案
```sql
-- 优化数据库查询
CREATE INDEX IX_oms_order_sync ON oms_order(id, job_last_update_date);
CREATE INDEX IX_oms_job_item_info_sync ON oms_job_item_info(id, job_num);

-- 更新统计信息
UPDATE STATISTICS oms_order;
UPDATE STATISTICS oms_job_item_info;
```

```properties
# 调整批量处理参数
jdy.batchSize=100
db.poolSize=15
db.connectionTimeout=30000
```

### 2. 内存使用过高

#### 问题现象
```
[WARN] 内存使用率: 85%
```

#### 原因分析
- JVM堆内存不足
- 内存泄漏
- 批量处理数据过大

#### 解决方案
```bash
# 调整JVM参数
java -Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
     -jar api_sql-1.0-SNAPSHOT-jar-with-dependencies.jar

# 监控内存使用
jstat -gc -t $(pgrep java) 5s

# 生成内存转储
jmap -dump:format=b,file=heap.hprof $(pgrep java)
```

### 3. CPU使用率高

#### 问题现象
```
CPU使用率持续超过80%
```

#### 原因分析
- 数据处理逻辑复杂
- 频繁的GC
- 线程竞争

#### 解决方案
```bash
# 监控CPU使用
top -p $(pgrep java)
htop

# 分析线程状态
jstack $(pgrep java) > thread_dump.txt

# 调整GC参数
java -XX:+UseG1GC -XX:MaxGCPauseMillis=100 \
     -XX:+PrintGC -XX:+PrintGCDetails \
     -jar api_sql-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## 网络问题

### 1. 网络连接超时

#### 问题现象
```
[ERROR] 连接超时: api.jiandaoyun.com
```

#### 原因分析
- 网络不稳定
- DNS解析问题
- 防火墙阻止
- 代理配置问题

#### 解决方案
```bash
# 测试网络连通性
ping api.jiandaoyun.com
curl -I https://api.jiandaoyun.com

# 检查DNS解析
nslookup api.jiandaoyun.com
dig api.jiandaoyun.com

# 测试端口连通性
telnet api.jiandaoyun.com 443
nc -zv api.jiandaoyun.com 443

# 配置代理（如需要）
export http_proxy=http://proxy.company.com:8080
export https_proxy=http://proxy.company.com:8080
```

### 2. SSL证书问题

#### 问题现象
```
[ERROR] SSL证书验证失败
```

#### 原因分析
- 证书过期
- 证书链不完整
- 系统时间错误

#### 解决方案
```bash
# 检查证书
openssl s_client -connect api.jiandaoyun.com:443 -servername api.jiandaoyun.com

# 更新CA证书
sudo apt-get update && sudo apt-get install ca-certificates  # Ubuntu
sudo yum update ca-certificates  # CentOS

# 检查系统时间
date
ntpdate -s time.nist.gov  # 同步时间
```

## 日志分析

### 1. 日志文件过大

#### 问题现象
```
日志文件大小超过1GB
```

#### 解决方案
```bash
# 配置日志轮转
sudo tee /etc/logrotate.d/jdy-sync << 'EOF'
/opt/jdy-sync/logs/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 644 jdysync jdysync
}
EOF

# 手动轮转日志
sudo logrotate -f /etc/logrotate.d/jdy-sync

# 清理旧日志
find /opt/jdy-sync/logs -name "*.log.*" -mtime +7 -delete
```

### 2. 日志分析脚本

```bash
#!/bin/bash
# log-analysis.sh

LOG_FILE="logs/application.log"

echo "=========================================="
echo "日志分析报告"
echo "=========================================="

# 统计日志级别
echo "1. 日志级别统计:"
grep -oE '\[(INFO|WARN|ERROR|DEBUG)\]' "$LOG_FILE" | sort | uniq -c | sort -nr

# 统计错误类型
echo -e "\n2. 错误类型统计:"
grep -i "error\|exception" "$LOG_FILE" | awk '{print $4}' | sort | uniq -c | sort -nr | head -10

# 同步统计
echo -e "\n3. 同步统计:"
echo "订单同步次数: $(grep -c "订单同步完成" "$LOG_FILE")"
echo "物料同步次数: $(grep -c "物料同步完成" "$LOG_FILE")"

# 最近错误
echo -e "\n4. 最近错误 (最近10条):"
grep -i "error" "$LOG_FILE" | tail -10

echo "=========================================="
```

## 监控和告警

### 1. 系统监控脚本

```bash
#!/bin/bash
# monitor.sh

# 监控配置
CPU_THRESHOLD=80
MEMORY_THRESHOLD=80
DISK_THRESHOLD=90
LOG_SIZE_THRESHOLD=100  # MB

# 检查CPU使用率
CPU_USAGE=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | cut -d'%' -f1)
if (( $(echo "$CPU_USAGE > $CPU_THRESHOLD" | bc -l) )); then
    echo "警告: CPU使用率过高 ($CPU_USAGE%)"
fi

# 检查内存使用率
MEMORY_USAGE=$(free | grep Mem | awk '{printf("%.1f"), $3/$2 * 100.0}')
if (( $(echo "$MEMORY_USAGE > $MEMORY_THRESHOLD" | bc -l) )); then
    echo "警告: 内存使用率过高 ($MEMORY_USAGE%)"
fi

# 检查磁盘使用率
DISK_USAGE=$(df -h . | awk 'NR==2 {print $5}' | sed 's/%//')
if [ "$DISK_USAGE" -gt "$DISK_THRESHOLD" ]; then
    echo "警告: 磁盘使用率过高 ($DISK_USAGE%)"
fi

# 检查日志文件大小
if [ -f "logs/application.log" ]; then
    LOG_SIZE=$(du -m logs/application.log | cut -f1)
    if [ "$LOG_SIZE" -gt "$LOG_SIZE_THRESHOLD" ]; then
        echo "警告: 日志文件过大 (${LOG_SIZE}MB)"
    fi
fi

# 检查进程状态
if ! pgrep -f "api_sql-1.0-SNAPSHOT-jar-with-dependencies.jar" > /dev/null; then
    echo "错误: 应用程序未运行"
fi
```

### 2. 告警通知脚本

```bash
#!/bin/bash
# alert.sh

WEBHOOK_URL="YOUR_WEBHOOK_URL"  # 钉钉、企业微信等

send_alert() {
    local message="$1"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # 发送到钉钉
    curl -X POST "$WEBHOOK_URL" \
        -H 'Content-Type: application/json' \
        -d "{
            \"msgtype\": \"text\",
            \"text\": {
                \"content\": \"[$timestamp] 简道云同步程序告警: $message\"
            }
        }"
}

# 使用示例
# send_alert "数据库连接失败"
```

## 恢复和备份

### 1. 配置备份

```bash
#!/bin/bash
# backup-config.sh

BACKUP_DIR="backup/config-$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

# 备份配置文件
cp application.properties "$BACKUP_DIR/"
cp db.properties "$BACKUP_DIR/"
cp field_mapping.json "$BACKUP_DIR/"
cp item_field_mapping.json "$BACKUP_DIR/"

# 备份同步状态
sqlcmd -S your-server -U your-username -P your-password \
    -Q "SELECT * FROM sync_status" -o "$BACKUP_DIR/sync_status.txt"

# 压缩备份
tar -czf "${BACKUP_DIR}.tar.gz" "$BACKUP_DIR"
rm -rf "$BACKUP_DIR"

echo "配置已备份到: ${BACKUP_DIR}.tar.gz"
```

### 2. 数据恢复

```bash
#!/bin/bash
# restore-config.sh

if [ -z "$1" ]; then
    echo "用法: $0 <backup-file.tar.gz>"
    exit 1
fi

BACKUP_FILE="$1"
RESTORE_DIR="restore-$(date +%Y%m%d_%H%M%S)"

# 解压备份
tar -xzf "$BACKUP_FILE" -C "$RESTORE_DIR"

# 恢复配置文件
cp "$RESTORE_DIR"/*.properties .
cp "$RESTORE_DIR"/*.json .

echo "配置已从 $BACKUP_FILE 恢复"
```

## 联系支持

如果以上解决方案无法解决您的问题，请：

1. **收集诊断信息**
   ```bash
   # 运行诊断脚本
   ./docs/setup-scripts/validate-deployment.sh > diagnostic.log 2>&1
   
   # 收集日志
   tar -czf support-logs.tar.gz logs/ *.properties *.json diagnostic.log
   ```

2. **提供问题描述**
   - 问题现象和错误信息
   - 操作步骤和环境信息
   - 相关日志和配置文件

3. **联系方式**
   - 提交GitHub Issue
   - 发送邮件到技术支持
   - 联系系统管理员

---

**注意**: 在生产环境中进行任何修复操作前，请务必备份相关数据和配置文件。