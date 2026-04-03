package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.io.InputStream;

public class DatabaseConnectionPool {
    private static HikariDataSource dataSource;
    private static final Properties props = new Properties();

    static {
        try {
            // 加载主配置文件（application.properties）
            try (InputStream input = DatabaseConnectionPool.class.getClassLoader()
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
                System.out.println("未找到外部数据库配置，使用默认配置");
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
            System.out.println("正在初始化数据库连接池...");
            System.out.println("数据库URL: " + url);
            dataSource = new HikariDataSource(config);
            
            // 测试连接
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(5)) {
                    System.out.println("数据库连接池初始化成功");
                }
            }
        } catch (Exception e) {
            String errorMsg = "数据库连接池初始化失败: " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * 获取数据库连接
     * @return 数据库连接
     * @throws SQLException SQL异常
     */
    public static Connection getConnection() throws SQLException {
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
            System.err.println("获取数据库连接失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 关闭连接池
     */
    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("数据库连接池已关闭");
            dataSource = null;
        }
    }

    /**
     * 返回连接到连接池（HikariCP会自动管理，此方法为兼容性保留）
     * @param conn 数据库连接
     */
    public static void returnConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close(); // HikariCP会自动将连接返回到池中
            } catch (SQLException e) {
                System.err.println("返回连接失败: " + e.getMessage());
            }
        }
    }

    /**
     * 获取连接池状态
     * @return 连接池状态信息
     */
    public static String getPoolStats() {
        if (dataSource == null) {
            return "连接池未初始化";
        }
        return String.format("活跃连接: %d, 空闲连接: %d, 等待连接: %d",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
    }
}