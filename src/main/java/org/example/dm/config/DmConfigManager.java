package org.example.dm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.util.LogUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 客户DM配置管理器
 * 负责加载和管理DM相关配置
 */
public class DmConfigManager {
    private static DmConfigManager instance;
    private final Properties properties;
    private Map<String, Object> fieldMapping;
    
    private DmConfigManager() {
        this.properties = new Properties();
        loadProperties();
        loadFieldMapping();
    }
    
    public static synchronized DmConfigManager getInstance() {
        if (instance == null) {
            instance = new DmConfigManager();
        }
        return instance;
    }
    
    /**
     * 加载配置文件
     */
    private void loadProperties() {
        try {
            // 加载DM数据库配置
            String dmConfigPath = "dm_db.properties";
            try (InputStream input = new FileInputStream(dmConfigPath)) {
                properties.load(input);
                LogUtil.logInfo("DM配置文件加载成功");
            }
        } catch (Exception e) {
            LogUtil.logError("加载DM配置文件失败: " + e.getMessage());
            throw new RuntimeException("加载DM配置文件失败", e);
        }
    }
    
    /**
     * 加载字段映射配置
     */
    private void loadFieldMapping() {
        try {
            String mappingPath = properties.getProperty("dm.field.mapping.path", "./dm_field_mapping.json");
            File mappingFile = new File(mappingPath);
            
            if (mappingFile.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                fieldMapping = mapper.readValue(mappingFile, Map.class);
                LogUtil.logInfo("DM字段映射配置加载成功: " + mappingPath);
            } else {
                LogUtil.logWarning("DM字段映射配置文件不存在: " + mappingPath);
                fieldMapping = new HashMap<>();
            }
        } catch (Exception e) {
            LogUtil.logError("加载DM字段映射配置失败: " + e.getMessage());
            fieldMapping = new HashMap<>();
        }
    }
    
    /**
     * 获取配置属性
     * @param key 配置键
     * @return 配置值
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * 获取配置属性（带默认值）
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * 获取主表字段映射
     * @return 主表字段映射
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getMainTableMapping() {
        if (fieldMapping.containsKey("main_table")) {
            Map<String, Object> mainTable = (Map<String, Object>) fieldMapping.get("main_table");
            if (mainTable.containsKey("source_to_local")) {
                return (Map<String, String>) mainTable.get("source_to_local");
            }
        }
        return new HashMap<>();
    }
    
    /**
     * 获取子表字段映射
     * @return 子表字段映射
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getDetailTableMapping() {
        if (fieldMapping.containsKey("detail_table")) {
            Map<String, Object> detailTable = (Map<String, Object>) fieldMapping.get("detail_table");
            if (detailTable.containsKey("source_to_local")) {
                return (Map<String, String>) detailTable.get("source_to_local");
            }
        }
        return new HashMap<>();
    }
    
    /**
     * 获取状态映射
     * @return 状态映射
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStatusMapping() {
        if (fieldMapping.containsKey("status_mapping")) {
            return (Map<String, Object>) fieldMapping.get("status_mapping");
        }
        return new HashMap<>();
    }
    
    /**
     * 获取主表表名
     * @return 主表表名
     */
    public String getMainTableName() {
        return getProperty("dm.table.main", "dm_order");
    }
    
    /**
     * 获取子表表名
     * @return 子表表名
     */
    public String getDetailTableName() {
        return getProperty("dm.table.detail", "dm_order_detail");
    }
    
    /**
     * 获取批次大小
     * @return 批次大小
     */
    public int getBatchSize() {
        return Integer.parseInt(getProperty("dm.sync.batchSize", "50"));
    }
    
    /**
     * 获取最大重试次数
     * @return 最大重试次数
     */
    public int getMaxRetry() {
        return Integer.parseInt(getProperty("dm.sync.maxRetry", "10"));
    }
    
    /**
     * 获取重试间隔（毫秒）
     * @return 重试间隔
     */
    public long getRetryInterval() {
        return Long.parseLong(getProperty("dm.sync.retryInterval", "5000"));
    }
    
    /**
     * 根据本地字段名获取客户数据库字段名（主表）
     * @param localFieldName 本地字段名
     * @return 客户数据库字段名，如果未配置则返回本地字段名
     */
    public String getSourceFieldName(String localFieldName) {
        Map<String, String> mapping = getMainTableMapping();
        // 反向查找：本地字段名 -> 客户字段名
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getValue().equals(localFieldName)) {
                return entry.getKey();
            }
        }
        // 如果没有配置映射，返回本地字段名（兼容性处理）
        return localFieldName;
    }
    
    /**
     * 根据本地字段名获取客户数据库字段名（子表）
     * @param localFieldName 本地字段名
     * @return 客户数据库字段名，如果未配置则返回本地字段名
     */
    public String getSourceDetailFieldName(String localFieldName) {
        Map<String, String> mapping = getDetailTableMapping();
        // 反向查找：本地字段名 -> 客户字段名
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getValue().equals(localFieldName)) {
                return entry.getKey();
            }
        }
        // 如果没有配置映射，返回本地字段名（兼容性处理）
        return localFieldName;
    }
}
