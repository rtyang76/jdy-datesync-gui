package org.example.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.util.LogUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 产品信息数据库管理类
 * 负责加载和查询产品信息配置
 */
public class ProductInfoDatabase {
    private static ProductInfoDatabase instance;
    private Map<String, Map<String, String>> productDatabase;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // 型号和容量提取的正则表达式
    // 匹配格式：FOR Lexar S60 32GB 或 FOR Lexar S60 或 S60 32GB
    // 容量必须紧跟GB才有效，避免将序号误识别为容量
    private static final Pattern MODEL_CAPACITY_PATTERN = Pattern.compile(
        "(?:FOR\\s+)?(?:Lexar\\s+)?([A-Z]\\d+[A-Z]?|[A-Z]+\\d+)(?:\\s+(\\d+)\\s*GB)?", 
        Pattern.CASE_INSENSITIVE
    );
    
    private ProductInfoDatabase() {
        loadProductDatabase();
    }
    
    public static synchronized ProductInfoDatabase getInstance() {
        if (instance == null) {
            instance = new ProductInfoDatabase();
        }
        return instance;
    }
    
    /**
     * 加载产品信息数据库
     */
    private void loadProductDatabase() {
        productDatabase = new HashMap<>();
        try {
            File configFile = new File("product_info_database.json");
            if (!configFile.exists()) {
                LogUtil.logError("产品信息数据库文件不存在: product_info_database.json");
                return;
            }
            
            JsonNode rootNode = objectMapper.readTree(configFile);
            JsonNode productsNode = rootNode.get("products");
            
            if (productsNode != null) {
                productsNode.fields().forEachRemaining(entry -> {
                    String model = entry.getKey();
                    JsonNode productInfo = entry.getValue();
                    
                    Map<String, String> info = new HashMap<>();
                    productInfo.fields().forEachRemaining(field -> {
                        info.put(field.getKey(), field.getValue().asText());
                    });
                    
                    productDatabase.put(model.toUpperCase(), info);
                });
            }
            
            LogUtil.logInfo("成功加载产品信息数据库，包含 " + productDatabase.size() + " 个产品型号");
            
        } catch (IOException e) {
            LogUtil.logError("加载产品信息数据库失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据型号和容量获取产品信息
     * @param model 产品型号
     * @param capacityGB 容量(GB)
     * @return 产品信息映射
     */
    public Map<String, String> getProductInfo(String model, Integer capacityGB) {
        if (model == null || model.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        String upperModel = model.toUpperCase().trim();
        Map<String, String> baseInfo = productDatabase.get(upperModel);
        
        if (baseInfo == null) {
            return new HashMap<>();
        }
        
        // 创建返回结果的副本
        Map<String, String> result = new HashMap<>(baseInfo);
        
        // 特殊处理：只有S80和D400需要根据容量判断文件系统
        if ("S80".equals(upperModel) || "D400".equals(upperModel)) {
            if (capacityGB != null && capacityGB >= 512) {
                // S80和D400在≥512GB时使用exFAT
                String specialFileSystem = baseInfo.get("file_system_512gb_plus");
                if (specialFileSystem != null && !specialFileSystem.isEmpty()) {
                    result.put("file_system", specialFileSystem);
                } else {
                    result.put("file_system", "exFAT");
                }
                LogUtil.logInfo("型号 " + model + " 容量 " + capacityGB + "GB 使用文件系统: " + result.get("file_system"));
            } else if (capacityGB == null) {
                // S80和D400识别不到容量时默认FAT32
                result.put("file_system", "FAT32");
                LogUtil.logInfo("型号 " + model + " 未识别到容量，默认使用FAT32");
            }
            // 如果有容量但<512GB，使用数据库中的file_system值（已在baseInfo中）
        }
        // 其他型号直接使用数据库中的file_system值，不需要根据容量判断
        
        return result;
    }
    
    /**
     * 从文本中提取型号和容量信息
     * @param text 文本内容
     * @return 包含model和capacity的Map，如果未找到则返回空Map
     */
    public Map<String, Object> extractModelAndCapacity(String text) {
        Map<String, Object> result = new HashMap<>();
        
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        
        Matcher matcher = MODEL_CAPACITY_PATTERN.matcher(text);
        if (matcher.find()) {
            String model = matcher.group(1);
            String capacityStr = matcher.group(2);
            
            result.put("model", model);
            
            if (capacityStr != null && !capacityStr.trim().isEmpty()) {
                try {
                    Integer capacity = Integer.parseInt(capacityStr);
                    result.put("capacity", capacity);
                    // 从文本中提取到型号和容量信息
                } catch (NumberFormatException e) {
                    LogUtil.logError("解析容量失败: " + capacityStr);
                }
            } else {
                // 只提取到型号，没有容量信息
            }
        }
        
        return result;
    }
    
    /**
     * 检查是否包含产品型号信息
     * @param text 文本内容
     * @return 是否包含型号信息
     */
    public boolean containsModelInfo(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        return MODEL_CAPACITY_PATTERN.matcher(text).find();
    }
    
    /**
     * 获取所有支持的产品型号
     * @return 产品型号列表
     */
    public java.util.Set<String> getSupportedModels() {
        return productDatabase.keySet();
    }
}