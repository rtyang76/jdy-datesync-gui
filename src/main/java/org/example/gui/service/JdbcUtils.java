package org.example.gui.service;

import org.example.gui.model.DataSourceConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcUtils {

    public static class ColumnDetail {
        public final String name;
        public final String type;
        public final String comment;

        public ColumnDetail(String name, String type, String comment) {
            this.name = name != null ? name : "";
            this.type = type != null ? type : "";
            this.comment = comment != null ? comment : "";
        }
    }

    public static Connection getConnection(DataSourceConfig ds) throws SQLException {
        try {
            Class.forName(ds.getDriverClassName());
        } catch (ClassNotFoundException e) {
            throw new SQLException("数据库驱动未找到: " + ds.getDriverClassName(), e);
        }
        return DriverManager.getConnection(ds.getJdbcUrl(), ds.getUsername(), ds.getPassword());
    }

    public static List<ColumnDetail> loadColumnDetails(DataSourceConfig ds, String tableName) throws SQLException {
        try (Connection conn = getConnection(ds)) {
            return loadColumnDetails(conn, ds, tableName);
        }
    }

    public static List<ColumnDetail> loadColumnDetails(Connection conn, DataSourceConfig ds, String tableName) throws SQLException {
        List<ColumnDetail> details = new ArrayList<>();
        String schemaPattern = ds.isSqlServer() ? "dbo" : ds.getDatabase();
        try (ResultSet rs = conn.getMetaData().getColumns(ds.getDatabase(), schemaPattern, tableName, "%")) {
            while (rs.next()) {
                details.add(new ColumnDetail(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getString("REMARKS")
                ));
            }
        }
        return details;
    }
}
