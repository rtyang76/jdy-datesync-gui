package org.example.service;

import java.util.List;
import java.util.Map;

/**
 * 采购物料通知单同步服务接口
 * 负责采购物料通知单数据的同步逻辑
 */
public interface DeliveryNoticeSyncService extends SyncService {
    
    /**
     * 获取上次同步ID
     * @return 上次同步ID
     */
    Integer getLastDeliverySyncId();
    
    /**
     * 获取新数据
     * @param lastSyncId 上次同步ID
     * @return 新数据列表
     */
    List<Map<String, Object>> fetchNewDeliveryData(Integer lastSyncId);
    
    /**
     * 查询已存在的记录
     * @param asnNum ASN单号
     * @return 查询结果
     */
    Map<String, String> queryExistingDeliveryRecord(String asnNum);
    
    /**
     * 更新记录
     * @param dataId 数据ID
     * @param record 记录数据
     * @return 是否成功
     */
    boolean updateDeliveryRecord(String dataId, Map<String, Object> record);
    
    /**
     * 批量上传数据
     * @param batch 批次数据
     * @return 成功上传的数量
     */
    int uploadDeliveryBatchWithRetry(List<Map<String, Object>> batch);
    
    /**
     * 更新同步状态
     * @param lastDeliverySyncId 最后同步ID
     */
    void updateDeliverySyncStatus(int lastDeliverySyncId);
}