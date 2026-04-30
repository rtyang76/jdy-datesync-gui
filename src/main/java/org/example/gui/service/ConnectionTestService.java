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
        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            String dbLabel = config.isSqlServer() ? "SQL Server" : "MySQL";
            return new TestResult(false, "找不到 " + dbLabel + " 驱动");
        }

        try (Connection conn = JdbcUtils.getConnection(config)) {
            String dbProductName = conn.getMetaData().getDatabaseProductName();
            String dbVersion = conn.getMetaData().getDatabaseProductVersion();
            return new TestResult(true, "连接成功！数据库: " + dbProductName + " " + dbVersion);
        } catch (SQLException e) {
            return new TestResult(false, "连接失败: " + e.getMessage());
        }
    }

    public static List<String> fetchTableList(DataSourceConfig config) {
        List<String> tables = new ArrayList<>();
        try (Connection conn = JdbcUtils.getConnection(config)) {
            String[] tableTypes = {"TABLE"};
            String schema = config.isSqlServer() ? "dbo" : config.getDatabase();
            ResultSet rs = conn.getMetaData().getTables(config.getDatabase(), schema, "%", tableTypes);
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
        try {
            List<JdbcUtils.ColumnDetail> details = JdbcUtils.loadColumnDetails(config, tableName);
            for (JdbcUtils.ColumnDetail d : details) {
                columns.add(d.name);
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
