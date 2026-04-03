package org.example.dm;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.example.util.LogUtil;

import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 客户DM数据库连接池
 * 负责管理虚拟机（客户DM）数据库连接
 */
public class DmDatabaseConnectionPool {
    private static DmDatabaseConnectionPool instance;
    private static HikariDataSource dataSource;
    
    private DmDatabaseConnectionPool() {
        initializeConnectionPool();
    }
    
    public static synchronized DmDatabaseConnectionPool getInstance() {
        if (instance == null) {
            instance = new DmDatabaseConnectionPool();
        }
        return instance;
    }
    
    /**
     * 初始化DM数据库连接池
     */
    private void initializeConnectionPool() {
        try {
            Properties props = new Properties();
            
            // 加载DM数据库配置文件
            String dmConfigPath = "dm_db.properties";
            try (InputStream input = new FileInputStream(dmConfigPath)) {
                props.load(input);
                LogUtil.logInfo("DM数据库配置文件加载成功: " + dmConfigPath);
            } catch (Exception e) {
                throw new RuntimeException("无法加载DM数据库配置文件: " + dmConfigPath, e);
            }
            
            // 配置HikariCP
            HikariConfig config = new HikariConfig();
            
            // 设置基本连接属性
            String url = props.getProperty("dm.db.url");
            String username = props.getProperty("dm.db.username");
            String password = props.getProperty("dm.db.password");
            
            if (url == null || username == null || password == null) {
                throw new RuntimeException("DM数据库配置不完整，请检查配置文件");
            }
            
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            
            // 设置连接池属性
            config.setMaximumPoolSize(Integer.parseInt(props.getProperty("dm.db.poolSize", "5")));
            config.setMinimumIdle(Integer.parseInt(props.getProperty("dm.db.minIdle", "2")));
            config.setIdleTimeout(Long.parseLong(props.getProperty("dm.db.idleTimeout", "300000")));
            config.setConnectionTimeout(Long.parseLong(props.getProperty("dm.db.connectionTimeout", "30000")));
            config.setMaxLifetime(Long.parseLong(props.getProperty("dm.db.maxLifetime", "1200000")));
            
            // 添加连接测试配置
            config.setConnectionTestQuery("SELECT 1");
            config.setValidationTimeout(5000);
            
            // 设置连接池名称
            config.setPoolName("DM-HikariPool");
            
            // 初始化连接池
            dataSource = new HikariDataSource(config);
            
            // 测试连接
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(5)) {
                    LogUtil.logInfo("DM数据库连接池初始化成功: " + url);
                }
            }
        } catch (Exception e) {
            String errorMsg = "DM数据库连接池初始化失败: " + e.getMessage();
            LogUtil.logError(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    /**
     * 获取数据库连接
     * @return 数据库连接
     * @throws SQLException SQL异常
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DM数据库连接池未初始化");
        }
        try {
            Connection conn = dataSource.getConnection();
            if (!conn.isValid(1)) {
                throw new SQLException("获取到的DM数据库连接无效");
            }
            return conn;
        } catch (SQLException e) {
            LogUtil.logError("获取DM数据库连接失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 关闭连接池
     */
    public void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LogUtil.logInfo("DM数据库连接池已关闭");
            dataSource = null;
        }
    }
    
    /**
     * 获取连接池状态
     * @return 连接池状态信息
     */
    public String getPoolStats() {
        if (dataSource == null) {
            return "DM连接池未初始化";
        }
        return String.format("DM连接池 - 活跃连接: %d, 空闲连接: %d, 等待连接: %d",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
    }
}
