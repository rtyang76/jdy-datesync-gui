package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataSourceConfig {

    public static final String TYPE_MYSQL = "mysql";
    public static final String TYPE_SQLSERVER = "sqlserver";

    private String id;
    private String name;
    private String dbType;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private boolean enabled;

    @JsonCreator
    public DataSourceConfig() {
        this.dbType = TYPE_MYSQL;
        this.port = 3306;
        this.enabled = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDbType() { return dbType != null ? dbType : TYPE_MYSQL; }
    public void setDbType(String dbType) { this.dbType = dbType; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @JsonIgnore
    public boolean isSqlServer() {
        return TYPE_SQLSERVER.equalsIgnoreCase(getDbType());
    }

    @JsonIgnore
    public String getJdbcUrl() {
        if (isSqlServer()) {
            return String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false;trustServerCertificate=true",
                    host, port, database);
        }
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                host, port, database);
    }

    @JsonIgnore
    public String getDriverClassName() {
        if (isSqlServer()) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        return "com.mysql.cj.jdbc.Driver";
    }

    @JsonIgnore
    public int getDefaultPort() {
        if (isSqlServer()) {
            return 1433;
        }
        return 3306;
    }

    @JsonIgnore
    public String quoteIdentifier(String identifier) {
        if (isSqlServer()) {
            return "[" + identifier + "]";
        }
        return "`" + identifier + "`";
    }

    @JsonIgnore
    public String getLimitClause(int limit) {
        if (isSqlServer()) {
            return "TOP " + limit;
        }
        return "LIMIT " + limit;
    }

    @Override
    public String toString() {
        return name != null ? name : "未命名数据源";
    }
}
