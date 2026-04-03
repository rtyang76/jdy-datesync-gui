package org.example.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 订单同步服务接口
 * 负责订单数据的同步逻辑
 */
public interface OrderSyncService extends SyncService {
    
    /**
     * 获取上次同步ID
     * @return 上次同步ID
     */
    Integer getLastSyncId();
    
    /**
     * 获取上次同步日期和计数
     * @return 包含sync_date和sync_count的Map
     */
    Map<String, Object> getLastSyncDateAndCount();
    
    /**
     * 获取新数据
     * @param lastSyncId 上次同步ID
     * @return 新数据列表
     */
    List<Map<String, Object>> fetchNewData(Integer lastSyncId);
    
    /**
     * 查询已存在的记录
     * @param jobNumber 工单号
     * @return 查询结果
     */
    Map<String, String> queryExistingRecord(String jobNumber);
    
    /**
     * 更新记录
     * @param dataId 数据ID
     * @param record 记录数据
     * @return 是否成功
     */
    boolean updateRecord(String dataId, Map<String, Object> record);
    
    /**
     * 批量上传数据
     * @param batch 批次数据
     * @return 成功上传的数量
     */
    int uploadBatchWithRetry(List<Map<String, Object>> batch);
    
    /**
     * 更新同步状态
     * @param lastSyncId 最后同步ID
     * @param syncDate 同步日期
     * @param syncCount 同步计数
     */
    void updateSyncStatus(int lastSyncId, LocalDate syncDate, int syncCount);
}