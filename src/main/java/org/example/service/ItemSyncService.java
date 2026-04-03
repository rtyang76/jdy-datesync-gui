package org.example.service;

import java.util.List;
import java.util.Map;

/**
 * 物料同步服务接口
 * 负责物料数据的同步逻辑
 */
public interface ItemSyncService extends SyncService {
    
    /**
     * 获取上次物料同步ID
     * @return 上次同步ID
     */
    Integer getLastItemSyncId();
    
    /**
     * 获取新物料数据
     * @param lastSyncId 上次同步ID
     * @return 新数据列表
     */
    List<Map<String, Object>> fetchNewItemData(Integer lastSyncId);
    
    /**
     * 查询已存在的物料
     * @param jobNum 工单号
     * @param itemNumber 物料号
     * @param itemClassification 料号所属分类
     * @return 查询结果
     */
    Map<String, String> queryExistingItem(String jobNum, String itemNumber, String itemClassification);
    
    /**
     * 更新物料记录
     * @param dataId 数据ID
     * @param record 记录数据
     * @return 是否成功
     */
    boolean updateItemRecord(String dataId, Map<String, Object> record);
    
    /**
     * 批量上传物料数据
     * @param batch 批次数据
     * @return 成功上传的数量
     */
    int uploadItemBatchWithRetry(List<Map<String, Object>> batch);
    
    /**
     * 更新物料同步状态
     * @param lastItemSyncId 最后同步ID
     */
    void updateItemSyncStatus(int lastItemSyncId);
}