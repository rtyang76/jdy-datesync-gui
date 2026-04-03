package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.util.LogUtil;
import org.example.util.Constants;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 字段映射加载器
 * 负责加载字段映射配置文件
 */
public class FieldMappingLoader {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static FieldMappingLoader instance;
    
    // 字段映射缓存
    private final Map<String, FieldMappingConfig> mappingCache = new HashMap<>();
    
    // 私有构造函数，防止外部实例化
    private FieldMappingLoader() {
    }
    
    // 单例模式获取实例
    public static synchronized FieldMappingLoader getInstance() {
        if (instance == null) {
            instance = new FieldMappingLoader();
        }
        return instance;
    }
    
    /**
     * 加载字段映射配置
     * @param mappingPath 映射文件路径
     * @return 字段映射配置对象
     * @throws IOException 如果加载失败
     */
    public FieldMappingConfig loadMapping(String mappingPath) throws IOException {
        // 如果缓存中已存在，则直接返回
        if (mappingCache.containsKey(mappingPath)) {
            return mappingCache.get(mappingPath);
        }
        
        // 获取配置文件路径
        String path = mappingPath;
        if (path == null || path.isEmpty()) {
            path = Constants.DEFAULT_FIELD_MAPPING_PATH;
        }
        
        File mappingFile = new File(path);
        
        // 检查文件是否存在
        if (!mappingFile.exists()) {
            throw new IOException("字段映射配置文件未找到，请检查路径: " + mappingFile.getAbsolutePath());
        }
        
        try {
            // 加载外部配置文件
            FieldMappingConfig config = mapper.readValue(mappingFile, FieldMappingConfig.class);
            
            // 输出字段映射信息
            LogUtil.logInfo("===== 字段映射信息 =====");
            LogUtil.logInfo("主表字段映射数量: " + config.getMainFields().size());
            
            // 检查订单号字段映射
            String orderNumberField = config.getMainFields().get("order_number");
            if (orderNumberField != null) {
                LogUtil.logInfo("订单号字段映射: order_number -> " + orderNumberField);
            }
            
            LogUtil.logInfo("子表映射数量: " + config.getSubTables().size());
            LogUtil.logInfo("======================");
            
            // 缓存配置
            mappingCache.put(mappingPath, config);
            
            return config;
        } catch (Exception e) {
            throw new IOException("字段映射配置加载失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加载默认字段映射配置
     * @return 字段映射配置对象
     * @throws IOException 如果加载失败
     */
    public FieldMappingConfig loadDefaultMapping() throws IOException {
        return loadMapping(Constants.DEFAULT_FIELD_MAPPING_PATH);
    }
    
    /**
     * 加载物料字段映射配置
     * @return 字段映射配置对象
     * @throws IOException 如果加载失败
     */
    public FieldMappingConfig loadItemMapping() throws IOException {
        return loadMapping(Constants.DEFAULT_ITEM_FIELD_MAPPING_PATH);
    }
} 