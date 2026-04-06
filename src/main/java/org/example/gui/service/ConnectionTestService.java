package org.example.gui.service;

import org.example.gui.model.DataSourceConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ConnectionTestService {

    public static TestResult testConnection(DataSourceConfig config) {
        String url = config.getJdbcUrl();
        String username = config.getUsername();
        String password = config.getPassword();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            return new TestResult(false, "找不到 MySQL 驱动");
        }

        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            String dbProductName = conn.getMetaData().getDatabaseProductName();
            String dbVersion = conn.getMetaData().getDatabaseProductVersion();
            return new TestResult(true, "连接成功！数据库: " + dbProductName + " " + dbVersion);
        } catch (SQLException e) {
            return new TestResult(false, "连接失败: " + e.getMessage());
        }
    }

    public static List<String> fetchTableList(DataSourceConfig config) {
        List<String> tables = new ArrayList<>();
        String url = config.getJdbcUrl();
        String username = config.getUsername();
        String password = config.getPassword();

        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            ResultSet rs = conn.getMetaData().getTables(config.getDatabase(), null, "%", new String[]{"TABLE"});
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        } catch (SQLException e) {
            // return whatever we got
        }
        return tables;
    }

    public static List<String> fetchColumnNames(DataSourceConfig config, String tableName) {
        List<String> columns = new ArrayList<>();
        String url = config.getJdbcUrl();
        String username = config.getUsername();
        String password = config.getPassword();

        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            ResultSet rs = conn.getMetaData().getColumns(config.getDatabase(), null, tableName, "%");
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        } catch (SQLException e) {
            // return whatever we got
        }
        return columns;
    }

    public static class TestResult {
        private final boolean success;
        private final String message;

        public TestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
