package org.example.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.example.util.LogUtil;
import org.example.util.Constants;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Properties;

/**
 * 数据库服务类
 * 负责处理数据库操作和连接池管理
 */
public class DatabaseService {
    private static DatabaseService instance;
    private static HikariDataSource dataSource;
    
    // 私有构造函数，防止外部实例化
    private DatabaseService() {
        initializeConnectionPool();
    }
    
    // 单例模式获取实例
    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }
    
    /**
     * 初始化数据库连接池
     */
    private void initializeConnectionPool() {
        try {
            Properties props = new Properties();
            
            // 加载主配置文件（application.properties）
            try (InputStream input = DatabaseService.class.getClassLoader()
                    .getResourceAsStream("application.properties")) {
                if (input == null) {
                    throw new RuntimeException("application.properties 未找到");
                }
                props.load(input);
            }

            // 加载外部数据库配置文件（优先级高于默认配置）
            String dbConfigPath = props.getProperty("external.db.config", "db.properties");
            try (InputStream dbInput = new FileInputStream(dbConfigPath)) {
                props.load(dbInput); // 覆盖同名配置项
            } catch (FileNotFoundException e) {
                LogUtil.logInfo("未找到外部数据库配置，使用默认配置");
            }

            // 配置HikariCP
            HikariConfig config = new HikariConfig();
            
            // 设置基本连接属性
            String url = props.getProperty("db.url");
            String username = props.getProperty("db.username");
            String password = props.getProperty("db.password");
            
            if (url == null || username == null || password == null) {
                throw new RuntimeException("数据库配置不完整，请检查配置文件");
            }
            
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

            // 设置连接池属性
            config.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.poolSize", "10")));
            config.setMinimumIdle(Integer.parseInt(props.getProperty("db.minIdle", "5")));
            config.setIdleTimeout(Long.parseLong(props.getProperty("db.idleTimeout", "300000")));
            config.setConnectionTimeout(Long.parseLong(props.getProperty("db.connectionTimeout", "20000")));
            config.setMaxLifetime(Long.parseLong(props.getProperty("db.maxLifetime", "1200000")));
            
            // 添加连接测试配置
            config.setConnectionTestQuery("SELECT 1");
            config.setValidationTimeout(5000);

            // 初始化连接池
            dataSource = new HikariDataSource(config);
            
            // 测试连接
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(5)) {
                    // 连接池初始化成功
                }
            }
        } catch (Exception e) {
            String errorMsg = "数据库连接池初始化失败: " + e.getMessage();
            LogUtil.logError(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    /**
     * 获取数据库连接
     * @return 数据库连接
     * @throws SQLException SQL异常
     */
    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("数据库连接池未初始化");
        }
        try {
            Connection conn = dataSource.getConnection();
            if (!conn.isValid(1)) {
                throw new SQLException("获取到的连接无效");
            }
            return conn;
        } catch (SQLException e) {
            LogUtil.logError("获取数据库连接失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 关闭连接池
     */
    public void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LogUtil.logInfo("数据库连接池已关闭");
            dataSource = null;
        }
    }
    
    /**
     * 获取连接池状态
     * @return 连接池状态信息
     */
    public String getPoolStats() {
        if (dataSource == null) {
            return "连接池未初始化";
        }
        return String.format("活跃连接: %d, 空闲连接: %d, 等待连接: %d",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
    }
    
    /**
     * 获取上次同步ID
     * @return 上次同步ID，如果不存在则返回null
     */
    public Integer getLastSyncId() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT TOP 1 last_sync_id FROM sync_status ORDER BY id DESC");
            Integer result = rs.next() ? rs.getInt("last_sync_id") : null;
            return result;
        } catch (SQLException e) {
            LogUtil.logError("获取同步ID失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取上次同步日期和计数
     * @return 包含日期和计数的Map，如果不存在则返回当天日期和0
     */
    public Map<String, Object> getLastSyncDateAndCount() {
        Map<String, Object> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        result.put("sync_date", today);
        result.put("sync_count", 0); // 默认计数为0

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT TOP 1 sync_date, sync_count FROM sync_status ORDER BY id DESC");
            if (rs.next()) {
                java.sql.Date syncDate = rs.getDate("sync_date");
                // 获取sync_count作为字符串
                String syncCodeStr = rs.getString("sync_count");

                // 计算字符在CUSTOM_CODE_CHARS中的索引
                int syncCount = 0;
                if (syncCodeStr != null && !syncCodeStr.trim().isEmpty()) {
                    char codeChar = syncCodeStr.trim().charAt(0); // 取第一个字符
                    // 在CUSTOM_CODE_CHARS数组中查找该字符的索引
                    for (int i = 0; i < Constants.CUSTOM_CODE_CHARS.length; i++) {
                        if (Constants.CUSTOM_CODE_CHARS[i] == codeChar) {
                            syncCount = i;
                            break;
                        }
                    }
                }

                if (syncDate != null) {
                    LocalDate lastSyncDate = syncDate.toLocalDate();
                    result.put("sync_date", lastSyncDate);

                    // 如果是同一天，使用计算出的索引；否则重置为0
                    if (lastSyncDate.equals(today)) {
                        result.put("sync_count", syncCount);
                    }
                }
            }
        } catch (SQLException e) {
            LogUtil.logError("获取同步日期和计数失败: " + e.getMessage());
        }

        return result;
    }
    
    /**
     * 更新同步状态
     * @param lastSyncId 最新同步ID
     * @param syncDate 同步日期
     * @param syncCount 同步计数
     */
    public void updateSyncStatus(int lastSyncId, LocalDate syncDate, int syncCount) {
        // 获取当前索引对应的自定义码字符
        String syncCodeChar = (syncCount < Constants.CUSTOM_CODE_MAX_COUNT) ? 
                String.valueOf(Constants.CUSTOM_CODE_CHARS[syncCount]) : "Z";

        String sql = "UPDATE sync_status SET last_sync_id = ?, sync_date = ?, sync_count = ? WHERE id = (SELECT MAX(id) FROM sync_status)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, lastSyncId);
            pstmt.setDate(2, java.sql.Date.valueOf(syncDate));
            // 存储字符而不是数字
            pstmt.setString(3, syncCodeChar);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                insertInitialSyncStatus(conn, lastSyncId, syncDate, syncCount);
            }
            // 同步状态已更新
        } catch (SQLException e) {
            LogUtil.logError("更新同步状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 插入初始同步状态
     * @param conn 数据库连接
     * @param lastSyncId 最新同步ID
     * @param syncDate 同步日期
     * @param syncCount 同步计数
     * @throws SQLException 如果插入失败
     */
    private void insertInitialSyncStatus(Connection conn, int lastSyncId, LocalDate syncDate, int syncCount) throws SQLException {
        // 获取当前索引对应的自定义码字符
        String syncCodeChar = (syncCount < Constants.CUSTOM_CODE_MAX_COUNT) ? 
                String.valueOf(Constants.CUSTOM_CODE_CHARS[syncCount]) : "Z";

        String insertSql = "INSERT INTO sync_status (last_sync_id, item_sync_id, sync_date, sync_count) VALUES (?, 0, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setInt(1, lastSyncId);
            pstmt.setDate(2, java.sql.Date.valueOf(syncDate));
            // 存储字符而不是数字
            pstmt.setString(3, syncCodeChar);
            pstmt.executeUpdate();

            LogUtil.logInfo(String.format("插入初始同步状态: ID=%d", lastSyncId));
        }
    }
    
    /**
     * 获取新增订单数据
     * @param lastSyncId 上次同步ID
     * @param maxBatchSize 最大批次大小
     * @return 新增数据列表
     */
    public List<Map<String, Object>> fetchNewOrderData(Integer lastSyncId, int maxBatchSize) {
        List<Map<String, Object>> data = new ArrayList<>();
        String sql = "SELECT TOP " + maxBatchSize + " " + Constants.ORDER_FIELDS +
                " FROM oms_order " +
                (lastSyncId != null ? "WHERE id > ? ORDER BY id ASC" : "ORDER BY id ASC");

        int retryCount = 0;
        while (retryCount < Constants.MAX_RETRY) {
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                if (lastSyncId != null)
                    pstmt.setInt(1, lastSyncId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    for (String field : Constants.ORDER_FIELDS.split(", ")) {
                        Object value = rs.getObject(field);
                        if (value instanceof java.sql.Date) {
                            value = ((java.sql.Date) value).toLocalDate();
                        } else if (value instanceof java.sql.Timestamp) {
                            value = ((java.sql.Timestamp) value).toLocalDateTime();
                        }
                        record.put(field, value);
                    }
                    data.add(record);
                }
                
                return data;
            } catch (SQLException e) {
                retryCount++;
                if (retryCount >= Constants.MAX_RETRY) {
                    LogUtil.logError("查询订单数据失败，已重试" + Constants.MAX_RETRY + "次: " + e.getMessage());
                    return data;
                }
                LogUtil.logWarning("查询订单数据失败，准备第" + retryCount + "次重试...");
                try {
                    Thread.sleep(Constants.RETRY_INTERVAL);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return data;
    }
    

    
    /**
     * 获取新增物料数据
     * @param lastSyncId 上次同步ID
     * @param maxBatchSize 最大批次大小
     * @return 新增数据列表
     */
    public List<Map<String, Object>> fetchNewItemData(Integer lastSyncId, int maxBatchSize) {
        List<Map<String, Object>> data = new ArrayList<>();
        String sql = "SELECT TOP " + maxBatchSize + " " + Constants.ITEM_FIELDS +
                " FROM oms_job_item_info " +
                (lastSyncId != null ? "WHERE id > ? ORDER BY id ASC" : "ORDER BY id ASC");

        int retryCount = 0;
        while (retryCount < Constants.MAX_RETRY) {
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                if (lastSyncId != null)
                    pstmt.setInt(1, lastSyncId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    for (String field : Constants.ITEM_FIELDS.split(", ")) {
                        Object value = rs.getObject(field);
                        if (value instanceof java.sql.Date) {
                            value = ((java.sql.Date) value).toLocalDate();
                        } else if (value instanceof java.sql.Timestamp) {
                            value = ((java.sql.Timestamp) value).toLocalDateTime();
                        }
                        record.put(field, value);
                    }
                    data.add(record);
                }
                
                return data;
            } catch (SQLException e) {
                retryCount++;
                if (retryCount >= Constants.MAX_RETRY) {
                    LogUtil.logError("查询物料数据失败，已重试" + Constants.MAX_RETRY + "次: " + e.getMessage());
                    return data;
                }
                LogUtil.logWarning("查询物料数据失败，准备第" + retryCount + "次重试...");
                try {
                    Thread.sleep(Constants.RETRY_INTERVAL);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return data;
    }
    
    /**
     * 获取新增物料数据（旧版本兼容）
     * @param lastSyncId 上次同步ID
     * @return 新增数据列表
     */
    public List<Map<String, Object>> fetchNewItemData(Integer lastSyncId) {
        List<Map<String, Object>> data = new ArrayList<>();
        String sql = "SELECT TOP " + Constants.MAX_BATCH_SIZE + " " + Constants.ITEM_FIELDS +
                " FROM oms_job_item_info " +
                (lastSyncId != null ? "WHERE id > ? ORDER BY id ASC" : "ORDER BY id ASC");

        int retryCount = 0;
        while (retryCount < Constants.MAX_RETRY) {
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                if (lastSyncId != null)
                    pstmt.setInt(1, lastSyncId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    for (String field : Constants.ITEM_FIELDS.split(", ")) {
                        Object value = rs.getObject(field);
                        if (value instanceof java.sql.Date) {
                            value = ((java.sql.Date) value).toLocalDate();
                        } else if (value instanceof java.sql.Timestamp) {
                            value = ((java.sql.Timestamp) value).toLocalDateTime();
                        }
                        record.put(field, value);
                    }
                    data.add(record);
                }
                
                return data;
            } catch (SQLException e) {
                retryCount++;
                if (retryCount >= Constants.MAX_RETRY) {
                    LogUtil.logError("查询物料数据失败，已重试" + Constants.MAX_RETRY + "次: " + e.getMessage());
                    return data;
                }
                LogUtil.logWarning("查询物料数据失败，准备第" + retryCount + "次重试...");
                try {
                    Thread.sleep(Constants.RETRY_INTERVAL);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return data;
    }
    
    /**
     * 获取上次物料同步ID
     * @return 上次同步ID，如果不存在则返回null
     */
    public Integer getLastItemSyncId() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT TOP 1 item_sync_id FROM sync_status ORDER BY id DESC");
            Integer result = rs.next() ? rs.getInt("item_sync_id") : null;
            return result;
        } catch (SQLException e) {
            LogUtil.logError("获取物料同步ID失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 更新物料同步状态
     * @param lastItemSyncId 最新物料同步ID
     */
    public void updateItemSyncStatus(int lastItemSyncId) {
        String sql = "UPDATE sync_status SET item_sync_id = ? WHERE id = (SELECT MAX(id) FROM sync_status)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, lastItemSyncId);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                LogUtil.logError("更新物料同步状态失败，不存在记录");
            }
            
            // 物料同步状态已更新
        } catch (SQLException e) {
            LogUtil.logError("更新物料同步状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取上次采购物料通知单同步ID
     * @return 上次同步ID，如果不存在则返回null
     */
    public Integer getLastDeliverySyncId() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT TOP 1 delivery_sync_id FROM sync_status ORDER BY id DESC");
            Integer result = rs.next() ? rs.getInt("delivery_sync_id") : null;
            return result;
        } catch (SQLException e) {
            LogUtil.logError("获取采购物料通知单同步ID失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取新增采购物料通知单数据
     * @param lastSyncId 上次同步ID
     * @return 新增数据列表
     */
    public List<Map<String, Object>> fetchNewDeliveryData(Integer lastSyncId) {
        List<Map<String, Object>> data = new ArrayList<>();
        String sql = "SELECT * FROM po_delivery_notice " +
                (lastSyncId != null ? "WHERE id > ? ORDER BY id ASC" : "ORDER BY id ASC");

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (lastSyncId != null)
                pstmt.setInt(1, lastSyncId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> record = new HashMap<>();
                record.put("id", rs.getInt("id"));
                record.put("sid", rs.getString("sid"));
                record.put("osp_code", rs.getString("osp_code"));
                record.put("tran_date", rs.getString("tran_date"));
                record.put("asn_num", rs.getString("asn_num"));
                record.put("vendor_name", rs.getString("vendor_name"));
                record.put("po_num", rs.getString("po_num"));
                record.put("create_date", rs.getString("create_date"));
                record.put("comments", rs.getString("comments"));
                record.put("create_time", rs.getTimestamp("create_time"));
                data.add(record);
            }
        } catch (SQLException e) {
            LogUtil.logError("获取新增采购物料通知单数据失败: " + e.getMessage());
        }

        return data;
    }

    /**
     * 更新采购物料通知单同步状态
     * @param lastDeliverySyncId 最新采购物料通知单同步ID
     */
    public void updateDeliverySyncStatus(int lastDeliverySyncId) {
        String sql = "UPDATE sync_status SET delivery_sync_id = ? WHERE id = (SELECT MAX(id) FROM sync_status)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, lastDeliverySyncId);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                // 如果没有记录，插入初始记录
                insertInitialDeliverySyncStatus(conn, lastDeliverySyncId);
            }
            
            // 采购物料通知单同步状态已更新
        } catch (SQLException e) {
            LogUtil.logError("更新采购物料通知单同步状态失败: " + e.getMessage());
        }
    }

    /**
     * 插入初始采购物料通知单同步状态
     * @param conn 数据库连接
     * @param lastDeliverySyncId 最新采购物料通知单同步ID
     * @throws SQLException 如果插入失败
     */
    private void insertInitialDeliverySyncStatus(Connection conn, int lastDeliverySyncId) throws SQLException {
        String insertSql = "INSERT INTO sync_status (last_sync_id, item_sync_id, delivery_sync_id, sync_date, sync_count) VALUES (0, 0, ?, ?, 'A')";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setInt(1, lastDeliverySyncId);
            pstmt.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
            pstmt.executeUpdate();

            LogUtil.logInfo(String.format("插入初始采购物料通知单同步状态: ID=%d", lastDeliverySyncId));
        }
    }
    
    /**
     * 查询子表数据
     * @param orderId 订单ID
     * @param tableName 表名
     * @param fieldMapping 字段映射
     * @return 子表数据列表
     */
    public List<Map<String, Object>> querySubTableWithMapping(int orderId, String tableName, Map<String, String> fieldMapping) {
        return querySubTableWithMapping(orderId, tableName, fieldMapping, "order_id");
    }

    /**
     * 查询子表数据（支持自定义外键字段名）
     * @param parentId 父表ID
     * @param tableName 表名
     * @param fieldMapping 字段映射
     * @param foreignKeyField 外键字段名
     * @return 子表数据列表
     */
    public List<Map<String, Object>> querySubTableWithMapping(int parentId, String tableName, Map<String, String> fieldMapping, String foreignKeyField) {
        String sql = "SELECT * FROM " + tableName + " WHERE " + foreignKeyField + " = ?";
        List<Map<String, Object>> result = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, parentId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                    String srcField = entry.getKey();
                    String destField = entry.getValue();
                    Object value = rs.getObject(srcField);
                    if (value == null) {
                        value = "";
                    } else if (value instanceof Number) {
                        value = value.toString();
                    } else if (value instanceof String) {
                        value = ((String) value).trim();
                    }
                    item.put(destField, Collections.singletonMap("value", value));
                }
                result.add(item);
            }
        } catch (SQLException e) {
            LogUtil.logError("查询子表数据失败: " + e.getMessage());
        }
        
        return result;
    }
} 