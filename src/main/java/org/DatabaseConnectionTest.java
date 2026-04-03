package org;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class DatabaseConnectionTest {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        log("开始测试数据库连接...");

        String url = "jdbc:sqlserver://127.0.0.1:1433;databaseName=LC_EDI;encrypt=true;trustServerCertificate=true;loginTimeout=30";
        String user = "sa";
        String password = "LC_svr1";

        try {
            // 加载驱动
            log("正在加载数据库驱动...");
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            log("数据库驱动加载成功");

            // 测试连接
            log("正在尝试连接数据库...");
            log("连接URL: " + url);
            Connection conn = DriverManager.getConnection(url, user, password);
            log("数据库连接成功");

            // 测试查询
            log("执行测试查询...");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT 1 as test");
            if (rs.next()) {
                log("测试查询成功: " + rs.getInt("test"));
            }

            // 测试数据库元数据
            log("数据库产品名称: " + conn.getMetaData().getDatabaseProductName());
            log("数据库版本: " + conn.getMetaData().getDatabaseProductVersion());

            // 关闭资源
            rs.close();
            stmt.close();
            conn.close();
            log("数据库连接测试完成");

        } catch (ClassNotFoundException e) {
            logError("驱动加载失败", e);
        } catch (Exception e) {
            logError("数据库连接测试失败", e);
        }
    }

    private static void log(String message) {
        System.out.println(String.format("[%s] [INFO] %s",
                LocalDateTime.now().format(formatter), message));
    }

    private static void logError(String message, Exception e) {
        System.err.println(String.format("[%s] [ERROR] %s",
                LocalDateTime.now().format(formatter), message));
        System.err.println("错误详情: " + e.getMessage());
        e.printStackTrace();
    }
}