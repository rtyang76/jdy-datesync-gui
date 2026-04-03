package org.example.service;

import java.util.List;
import java.util.Map;

/**
 * 数据验证服务接口
 * 负责数据验证和去重逻辑
 */
public interface DataValidationService {
    
    /**
     * 移除重复的订单记录
     * @param records 原始记录列表
     * @return 去重后的记录列表
     */
    List<Map<String, Object>> removeDuplicateRecords(List<Map<String, Object>> records);
    
    /**
     * 移除重复的物料记录
     * @param records 原始记录列表
     * @return 去重后的记录列表
     */
    List<Map<String, Object>> removeDuplicateItemRecords(List<Map<String, Object>> records);
    
    /**
     * 生成记录内容指纹
     * @param record 记录
     * @return 内容指纹
     */
    String generateContentFingerprint(Map<String, Object> record);
    
    /**
     * 生成物料记录内容指纹
     * @param record 记录
     * @return 内容指纹
     */
    String generateItemContentFingerprint(Map<String, Object> record);
    
    /**
     * 验证记录是否有效
     * @param record 记录
     * @return 是否有效
     */
    boolean isValidRecord(Map<String, Object> record);
    
    /**
     * 验证物料记录是否有效
     * @param record 记录
     * @return 是否有效
     */
    boolean isValidItemRecord(Map<String, Object> record);
}