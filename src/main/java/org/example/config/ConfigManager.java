package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 配置管理类
 * 负责加载和管理应用程序配置
 */
public class ConfigManager {
    private static final Logger logger = Logger.getLogger(ConfigManager.class.getName());
    private static final Properties props = new Properties();
    private static ConfigManager instance;
    private static final ObjectMapper mapper = new ObjectMapper();
    
    // 字段映射
    private Map<String, String> fieldMapping;
    private Map<String, Map<String, String>> subTables;
    private Map<String, String> itemFieldMapping;

    // 私有构造函数，防止外部实例化
    private ConfigManager() {
        loadProperties();
        loadFieldMappings();
    }

    // 单例模式获取实例
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    // 加载配置文件
    private void loadProperties() {
        try {
            // 加载配置文件
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
                if (input == null) {
                    throw new RuntimeException("配置文件 application.properties 未找到");
                }
                props.load(input);
                logger.info("配置文件加载成功");
            }
        } catch (Exception e) {
            logger.severe("配置文件加载失败: " + e.getMessage());
            throw new RuntimeException("配置文件加载失败", e);
        }
    }

    // 获取配置属性
    public String getProperty(String key) {
        return props.getProperty(key);
    }

    // 获取配置属性，如果不存在则返回默认值
    public String getProperty(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    // 获取整型属性
    public int getIntProperty(String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warning("无法将属性 " + key + " 的值 '" + value + "' 转换为整数，使用默认值 " + defaultValue);
            return defaultValue;
        }
    }

    // 获取布尔型属性
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    // 获取所有属性
    public Properties getAllProperties() {
        return props;
    }

    // 检查文件是否存在
    public boolean fileExists(String path) {
        File file = new File(path);
        return file.exists();
    }
    
    // 加载字段映射配置
    private void loadFieldMappings() {
        try {
            // 加载订单字段映射
            String mappingPath = props.getProperty("field.mapping.path", "field_mapping.json");
            File mappingFile = new File(mappingPath);
            
            if (mappingFile.exists()) {
                FieldMappingConfig config = mapper.readValue(mappingFile, FieldMappingConfig.class);
                this.fieldMapping = config.getMainFields();
                this.subTables = config.getSubTables();
                logger.info("订单字段映射加载成功，主表字段数量: " + fieldMapping.size());
            } else {
                logger.warning("订单字段映射文件未找到: " + mappingFile.getAbsolutePath());
            }
            
            // 加载物料字段映射
            String itemMappingPath = props.getProperty("item.field.mapping.path", "item_field_mapping.json");
            File itemMappingFile = new File(itemMappingPath);
            
            if (itemMappingFile.exists()) {
                FieldMappingConfig itemConfig = mapper.readValue(itemMappingFile, FieldMappingConfig.class);
                this.itemFieldMapping = itemConfig.getMainFields();
                logger.info("物料字段映射加载成功，字段数量: " + itemFieldMapping.size());
            } else {
                logger.warning("物料字段映射文件未找到: " + itemMappingFile.getAbsolutePath());
            }
            
        } catch (Exception e) {
            logger.severe("字段映射配置加载失败: " + e.getMessage());
            throw new RuntimeException("字段映射配置加载失败", e);
        }
    }
    
    // 获取订单字段映射
    public Map<String, String> getFieldMapping() {
        return fieldMapping;
    }
    
    // 获取订单字段映射中的特定字段
    public String getFieldMapping(String key) {
        return fieldMapping != null ? fieldMapping.get(key) : null;
    }
    
    // 获取子表映射
    public Map<String, Map<String, String>> getSubTables() {
        return subTables;
    }
    
    // 获取物料字段映射
    public Map<String, String> getItemFieldMapping() {
        return itemFieldMapping;
    }
    
    // 获取物料字段映射中的特定字段
    public String getItemFieldMapping(String key) {
        return itemFieldMapping != null ? itemFieldMapping.get(key) : null;
    }
} 