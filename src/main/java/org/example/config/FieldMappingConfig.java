package org.example.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.util.LogUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 字段映射配置类
 * 用于存储数据库字段与简道云字段的映射关系
 */
public class FieldMappingConfig {
    
    private static FieldMappingConfig instance;
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @JsonProperty("main_fields")
    private Map<String, String> mainFields = new HashMap<>();
    
    @JsonProperty("sub_tables")
    private Map<String, Map<String, String>> subTables = new HashMap<>();
    
    private Map<String, String> itemFields = new HashMap<>();
    
    // 私有构造函数
    private FieldMappingConfig() {
        // 不在构造函数中加载映射，避免循环依赖
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized FieldMappingConfig getInstance() {
        if (instance == null) {
            instance = new FieldMappingConfig();
            instance.loadMappings(); // 在实例创建后再加载映射
        }
        return instance;
    }
    
    /**
     * 加载字段映射配置
     */
    private void loadMappings() {
        try {
            // 直接使用默认路径，避免循环依赖
            String mappingPath = "field_mapping.json";
            File mappingFile = new File(mappingPath);
            
            if (mappingFile.exists()) {
                FieldMappingConfig orderConfig = mapper.readValue(mappingFile, FieldMappingConfig.class);
                this.mainFields = orderConfig.getMainFields();
                this.subTables = orderConfig.getSubTables();
                // 订单字段映射加载成功
            } else {
                LogUtil.logWarning("订单字段映射文件未找到: " + mappingFile.getAbsolutePath());
            }
            
            // 加载物料字段映射
            String itemMappingPath = "item_field_mapping.json";
            File itemMappingFile = new File(itemMappingPath);
            
            if (itemMappingFile.exists()) {
                FieldMappingConfig itemConfig = mapper.readValue(itemMappingFile, FieldMappingConfig.class);
                this.itemFields = itemConfig.getMainFields();
                // 物料字段映射加载成功
            } else {
                LogUtil.logWarning("物料字段映射文件未找到: " + itemMappingFile.getAbsolutePath());
            }
            
        } catch (Exception e) {
            LogUtil.logError("加载字段映射配置失败: " + e.getMessage());
            throw new RuntimeException("字段映射配置加载失败", e);
        }
    }
    
    /**
     * 获取主表字段映射
     * @return 主表字段映射
     */
    public Map<String, String> getMainFields() {
        return mainFields;
    }
    
    /**
     * 设置主表字段映射
     * @param mainFields 主表字段映射
     */
    public void setMainFields(Map<String, String> mainFields) {
        this.mainFields = mainFields;
    }
    
    /**
     * 获取物料字段映射
     * @return 物料字段映射
     */
    public Map<String, String> getItemFields() {
        return itemFields;
    }
    
    /**
     * 设置物料字段映射
     * @param itemFields 物料字段映射
     */
    public void setItemFields(Map<String, String> itemFields) {
        this.itemFields = itemFields;
    }
    
    /**
     * 获取子表字段映射
     * @return 子表字段映射
     */
    public Map<String, Map<String, String>> getSubTables() {
        return subTables;
    }
    
    /**
     * 设置子表字段映射
     * @param subTables 子表字段映射
     */
    public void setSubTables(Map<String, Map<String, String>> subTables) {
        this.subTables = subTables;
    }
    
    /**
     * 获取指定子表的字段映射
     * @param subTableName 子表名称
     * @return 子表字段映射，如果不存在则返回null
     */
    public Map<String, String> getSubTableMapping(String subTableName) {
        return subTables.get(subTableName);
    }
    
    /**
     * 获取数据库字段对应的简道云字段
     * @param dbField 数据库字段名
     * @return 简道云字段名，如果不存在则返回null
     */
    public String getJdyField(String dbField) {
        return mainFields.get(dbField);
    }
    
    /**
     * 获取物料数据库字段对应的简道云字段
     * @param dbField 数据库字段名
     * @return 简道云字段名，如果不存在则返回null
     */
    public String getItemJdyField(String dbField) {
        return itemFields.get(dbField);
    }
} 