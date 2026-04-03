package org.example.dm.service;

import org.example.dm.config.DmConfigManager;
import org.example.dm.dao.DmLocalDao;
import org.example.dm.dao.DmRemoteDao;
import org.example.dm.model.DmOrder;
import org.example.dm.model.DmOrderDetail;
import org.example.util.LogUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * DM数据拉取服务
 * 负责从虚拟机（客户DM）数据库拉取数据到本地数据库
 */
public class DmDataPullService {
    private static DmDataPullService instance;
    private final DmRemoteDao remoteDao;
    private final DmLocalDao localDao;
    private final DmConfigManager configManager;
    
    private DmDataPullService() {
        this.remoteDao = DmRemoteDao.getInstance();
        this.localDao = DmLocalDao.getInstance();
        this.configManager = DmConfigManager.getInstance();
    }
    
    public static synchronized DmDataPullService getInstance() {
        if (instance == null) {
            instance = new DmDataPullService();
        }
        return instance;
    }
    
    /**
     * 执行数据拉取同步
     */
    public void pullDataFromRemote() {
        try {
            // 1. 获取上次同步时间戳
            LocalDateTime lastSyncTime = localDao.getLastSyncTime();
            
            // 2. 从远程数据库查询增量数据
            int batchSize = configManager.getBatchSize();
            List<DmOrder> remoteOrders = remoteDao.fetchIncrementalOrders(lastSyncTime, batchSize);
            
            if (remoteOrders.isEmpty()) {
                LogUtil.logInfo("[DM拉取] 无新数据");
                return;
            }
            
            // 有数据时才输出详细日志
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("\n=== DM数据拉取开始 " + timestamp + " ===");
            LogUtil.logInfo("DM上次同步时间: " + lastSyncTime);
            
            System.out.println("=== 检测到 " + remoteOrders.size() + " 条DM订单数据，开始处理 ===");
            
            // 3. 处理每条订单
            int totalProcessed = 0;
            int totalInserted = 0;
            int totalUpdated = 0;
            int totalFailed = 0;
            LocalDateTime maxModifyTime = lastSyncTime;
            
            for (DmOrder remoteOrder : remoteOrders) {
                try {
                    boolean success = processOrder(remoteOrder);
                    
                    if (success) {
                        totalProcessed++;
                        
                        // 判断是新增还是更新
                        Integer existingId = localDao.checkOrderExistsBySourceId(remoteOrder.getSourceId());
                        if (existingId == null) {
                            existingId = localDao.checkOrderExistsByOrderNo(remoteOrder.getOrderNo());
                        }
                        
                        if (existingId != null) {
                            totalUpdated++;
                        } else {
                            totalInserted++;
                        }
                        
                        // 更新最大修改时间
                        if (remoteOrder.getModifyTime().isAfter(maxModifyTime)) {
                            maxModifyTime = remoteOrder.getModifyTime();
                        }
                    } else {
                        totalFailed++;
                    }
                } catch (Exception e) {
                    totalFailed++;
                    LogUtil.logError("处理DM订单失败 (order_no=" + remoteOrder.getOrderNo() + "): " + e.getMessage());
                }
            }
            
            // 4. 更新同步时间戳
            if (totalProcessed > 0) {
                localDao.updateLastSyncTime(maxModifyTime);
            }
            
            // 5. 输出统计信息
            System.out.println("=== DM数据拉取完成 ===");
            System.out.println("总记录数: " + remoteOrders.size());
            System.out.println("成功处理: " + totalProcessed);
            System.out.println("新增: " + totalInserted);
            System.out.println("更新: " + totalUpdated);
            System.out.println("失败: " + totalFailed);
            System.out.println("最新时间戳: " + maxModifyTime);
            
            LogUtil.logInfo(String.format("DM数据拉取完成: 总数=%d, 成功=%d, 新增=%d, 更新=%d, 失败=%d",
                    remoteOrders.size(), totalProcessed, totalInserted, totalUpdated, totalFailed));
            
        } catch (Exception e) {
            LogUtil.logError("DM数据拉取过程异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 处理单个订单（主表+子表）
     * @param remoteOrder 远程订单数据
     * @return 是否成功
     */
    private boolean processOrder(DmOrder remoteOrder) {
        try {
            // 1. 查询远程子表数据
            // 主子表关联：子表order_no格式为"主表order_no-序号"，通过LIKE匹配关联
            List<DmOrderDetail> remoteDetails = remoteDao.fetchOrderDetails(remoteOrder.getOrderNo());
            remoteOrder.setDetails(remoteDetails);
            
            // 2. 检查本地是否存在（优先通过source_id，其次通过order_no）
            Integer localOrderId = localDao.checkOrderExistsBySourceId(remoteOrder.getSourceId());
            
            if (localOrderId == null) {
                // 通过order_no再次检查
                localOrderId = localDao.checkOrderExistsByOrderNo(remoteOrder.getOrderNo());
            }
            
            if (localOrderId != null) {
                // 订单已存在，执行更新
                return updateExistingOrder(localOrderId, remoteOrder);
            } else {
                // 订单不存在，执行插入
                return insertNewOrder(remoteOrder);
            }
            
        } catch (Exception e) {
            LogUtil.logError("处理DM订单异常 (order_no=" + remoteOrder.getOrderNo() + "): " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 插入新订单
     * @param order 订单对象
     * @return 是否成功
     */
    private boolean insertNewOrder(DmOrder order) {
        try {
            // 1. 插入主表
            Integer orderId = localDao.insertOrder(order);
            
            if (orderId == null) {
                LogUtil.logError("插入DM订单主表失败 (order_no=" + order.getOrderNo() + ")");
                return false;
            }
            
            // 2. 插入子表
            if (!order.getDetails().isEmpty()) {
                int detailCount = localDao.batchInsertOrderDetails(orderId, order.getDetails());
                LogUtil.logInfo("新增DM订单: " + order.getOrderNo() + " (主表ID=" + orderId + ", 子表=" + detailCount + "条)");
            } else {
                LogUtil.logInfo("新增DM订单: " + order.getOrderNo() + " (主表ID=" + orderId + ", 无子表数据)");
            }
            
            return true;
            
        } catch (Exception e) {
            LogUtil.logError("插入DM订单失败 (order_no=" + order.getOrderNo() + "): " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 更新已存在的订单
     * @param localOrderId 本地订单ID
     * @param order 订单对象
     * @return 是否成功
     */
    private boolean updateExistingOrder(Integer localOrderId, DmOrder order) {
        try {
            // 1. 更新主表
            boolean mainSuccess = localDao.updateOrder(localOrderId, order);
            
            if (!mainSuccess) {
                LogUtil.logError("更新DM订单主表失败 (order_no=" + order.getOrderNo() + ")");
                return false;
            }
            
            // 2. 删除旧子表数据
            localDao.deleteOrderDetails(localOrderId);
            
            // 3. 插入新子表数据
            if (!order.getDetails().isEmpty()) {
                int detailCount = localDao.batchInsertOrderDetails(localOrderId, order.getDetails());
                LogUtil.logInfo("更新DM订单: " + order.getOrderNo() + " (主表ID=" + localOrderId + ", 子表=" + detailCount + "条)");
            } else {
                LogUtil.logInfo("更新DM订单: " + order.getOrderNo() + " (主表ID=" + localOrderId + ", 无子表数据)");
            }
            
            return true;
            
        } catch (Exception e) {
            LogUtil.logError("更新DM订单失败 (order_no=" + order.getOrderNo() + "): " + e.getMessage());
            return false;
        }
    }
}
