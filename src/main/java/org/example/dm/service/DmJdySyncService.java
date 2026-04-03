package org.example.dm.service;

import org.example.config.ConfigManager;
import org.example.dm.dao.DmLocalDao;
import org.example.service.JiandaoyunApiService;
import org.example.util.LogUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DM数据推送到简道云服务
 * 负责将本地dm_order数据推送到简道云
 */
public class DmJdySyncService {
    private static DmJdySyncService instance;
    private final JiandaoyunApiService apiService;
    private final DmLocalDao localDao;
    private final ConfigManager configManager;

    // 配置常量
    private final String APP_ID;
    private final String ENTRY_ID;
    private final int MAX_RETRY;
    private final long RETRY_INTERVAL;
    private final int MAX_BATCH_SIZE;

    private DmJdySyncService() {
        this.apiService = JiandaoyunApiService.getInstance();
        this.localDao = DmLocalDao.getInstance();
        this.configManager = ConfigManager.getInstance();

        // 初始化配置
        this.APP_ID = configManager.getProperty("dm.jdy.appId");
        this.ENTRY_ID = configManager.getProperty("dm.jdy.entryId");
        this.MAX_RETRY = Integer.parseInt(configManager.getProperty("sync.maxRetry", "10"));
        this.RETRY_INTERVAL = Long.parseLong(configManager.getProperty("sync.retryInterval", "5000"));
        this.MAX_BATCH_SIZE = Integer.parseInt(configManager.getProperty("sync.maxBatchSize", "50"));
    }

    public static synchronized DmJdySyncService getInstance() {
        if (instance == null) {
            instance = new DmJdySyncService();
        }
        return instance;
    }

    /**
     * 执行DM数据推送到简道云
     * @return 是否有数据需要同步
     */
    public boolean pushDataToJiandaoyun() {
        try {
            // 1. 查询待同步的订单
            List<org.example.dm.model.DmOrder> pendingOrders = localDao.queryPendingOrders();

            if (pendingOrders.isEmpty()) {
                return false;
            }

            // 有数据时才输出详细日志
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("\n=== DM数据推送简道云开始 " + timestamp + " ===");

            System.out.println("查询到 " + pendingOrders.size() + " 条待同步的DM订单");
            LogUtil.logInfo("查询到 " + pendingOrders.size() + " 条待同步的DM订单");

            // 2. 批量处理所有待同步订单
            int successCount = batchProcessOrders(pendingOrders);

            System.out.println("=== DM数据推送简道云完成 ===");
            LogUtil.logInfo("DM数据推送简道云完成: 成功 " + successCount + " 条");
            return true;

        } catch (Exception e) {
            LogUtil.logError("DM数据推送简道云异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 批量处理订单（自动判断创建或更新）
     */
    private int batchProcessOrders(List<org.example.dm.model.DmOrder> orders) {
        if (orders.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        int createCount = 0;
        int updateCount = 0;
        DmDataTransformService transformService = DmDataTransformService.getInstance();

        // 分批处理，每批最多MAX_BATCH_SIZE条
        for (int i = 0; i < orders.size(); i += MAX_BATCH_SIZE) {
            int endIndex = Math.min(i + MAX_BATCH_SIZE, orders.size());
            List<org.example.dm.model.DmOrder> batch = orders.subList(i, endIndex);

            // 先查询简道云，区分创建和更新
            List<org.example.dm.model.DmOrder> createOrders = new ArrayList<>();
            List<org.example.dm.model.DmOrder> updateOrders = new ArrayList<>();
            Map<String, String> dataIdMap = new java.util.HashMap<>();

            for (org.example.dm.model.DmOrder order : batch) {
                try {
                    // 通过order_no查询简道云判断是否已存在
                    String orderNoWidgetId = configManager.getProperty("dm.jdy.orderNoWidget", "_widget_1770078767290");
                    Map<String, String> queryResult = apiService.queryData(APP_ID, ENTRY_ID, orderNoWidgetId, order.getOrderNo());

                    if (queryResult.containsKey("data_id")) {
                        // 已存在，需要更新
                        updateOrders.add(order);
                        dataIdMap.put(order.getOrderNo(), queryResult.get("data_id"));
                    } else {
                        // 不存在，需要创建
                        createOrders.add(order);
                    }
                } catch (Exception e) {
                    LogUtil.logError("查询简道云失败 (order_no=" + order.getOrderNo() + "): " + e.getMessage());
                    // 查询失败时，默认尝试创建
                    createOrders.add(order);
                }
            }

            System.out.println("批次 " + (i / MAX_BATCH_SIZE + 1) + ": 待创建 " + createOrders.size() + " 条, 待更新 " + updateOrders.size() + " 条");

            // 处理创建
            if (!createOrders.isEmpty()) {
                int batchCreateSuccess = batchCreateOrders(createOrders, transformService);
                createCount += batchCreateSuccess;
                successCount += batchCreateSuccess;
            }

            // 处理更新
            if (!updateOrders.isEmpty()) {
                int batchUpdateSuccess = batchUpdateOrders(updateOrders, dataIdMap, transformService);
                updateCount += batchUpdateSuccess;
                successCount += batchUpdateSuccess;
            }
        }

        System.out.println("创建成功: " + createCount + " 条, 更新成功: " + updateCount + " 条");
        return successCount;
    }

    /**
     * 批量创建订单
     */
    private int batchCreateOrders(List<org.example.dm.model.DmOrder> orders, DmDataTransformService transformService) {
        if (orders.isEmpty()) {
            return 0;
        }

        int successCount = 0;

        List<Map<String, Object>> dataList = new ArrayList<>();
        List<org.example.dm.model.DmOrder> validOrders = new ArrayList<>();

        // 转换数据
        for (org.example.dm.model.DmOrder order : orders) {
            try {
                Map<String, Object> jdyData = transformService.convertToJdyFormat(order);
                if (jdyData != null) {
                    dataList.add(jdyData);
                    validOrders.add(order);
                } else {
                    LogUtil.logError("转换DM订单失败，跳过 (order_id=" + order.getId() + ")");
                    localDao.incrementSyncAttempts(order.getId());
                    localDao.updateSyncError(order.getId(), "数据转换失败");
                }
            } catch (Exception e) {
                LogUtil.logError("转换DM订单异常 (order_id=" + order.getId() + "): " + e.getMessage());
                localDao.incrementSyncAttempts(order.getId());
                localDao.updateSyncError(order.getId(), "数据转换异常: " + e.getMessage());
            }
        }

        if (dataList.isEmpty()) {
            return 0;
        }

        // 批量创建
        int retryCount = 0;
        boolean batchSuccess = false;

        while (retryCount < MAX_RETRY && !batchSuccess) {
            try {
                batchSuccess = apiService.createData(APP_ID, ENTRY_ID, dataList, false);

                if (batchSuccess) {
                    // 批量创建成功，更新所有订单的同步状态
                    for (org.example.dm.model.DmOrder order : validOrders) {
                        localDao.updateSyncStatus(order.getId(), 1);
                        successCount++;
                    }

                    LogUtil.logInfo("批量创建DM订单成功: " + validOrders.size() + " 条");
                } else {
                    retryCount++;
                    if (retryCount < MAX_RETRY) {
                        LogUtil.logWarning("批量创建DM订单失败，准备第 " + retryCount + " 次重试...");
                        Thread.sleep(RETRY_INTERVAL);
                    }
                }
            } catch (Exception e) {
                retryCount++;
                LogUtil.logError("批量创建DM订单异常 (重试 " + retryCount + "/" + MAX_RETRY + "): " + e.getMessage());

                if (retryCount < MAX_RETRY) {
                    try {
                        Thread.sleep(RETRY_INTERVAL);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        // 如果批量创建失败，更新所有订单的重试次数和错误信息
        if (!batchSuccess) {
            for (org.example.dm.model.DmOrder order : validOrders) {
                localDao.incrementSyncAttempts(order.getId());
                localDao.updateSyncError(order.getId(), "批量创建失败，已重试" + MAX_RETRY + "次");
            }
        }

        return successCount;
    }

    /**
     * 批量更新订单
     */
    private int batchUpdateOrders(List<org.example.dm.model.DmOrder> orders, Map<String, String> dataIdMap, DmDataTransformService transformService) {
        if (orders.isEmpty()) {
            return 0;
        }

        int successCount = 0;

        for (org.example.dm.model.DmOrder order : orders) {
            try {
                // 转换数据
                Map<String, Object> jdyData = transformService.convertToJdyFormat(order);
                if (jdyData == null) {
                    LogUtil.logError("转换DM订单失败，跳过 (order_id=" + order.getId() + ")");
                    localDao.incrementSyncAttempts(order.getId());
                    localDao.updateSyncError(order.getId(), "数据转换失败");
                    continue;
                }

                String jdyDataId = dataIdMap.get(order.getOrderNo());
                if (jdyDataId == null) {
                    LogUtil.logError("未找到data_id，跳过更新 (order_no=" + order.getOrderNo() + ")");
                    localDao.incrementSyncAttempts(order.getId());
                    localDao.updateSyncError(order.getId(), "未找到data_id");
                    continue;
                }

                // 执行更新操作
                int retryCount = 0;
                boolean updateSuccess = false;

                while (retryCount < MAX_RETRY && !updateSuccess) {
                    try {
                        updateSuccess = apiService.updateData(APP_ID, ENTRY_ID, jdyDataId, jdyData);

                        if (updateSuccess) {
                            localDao.updateSyncStatus(order.getId(), 1);
                            successCount++;
                            LogUtil.logInfo("更新DM订单成功 (order_id=" + order.getId() + ", jdy_data_id=" + jdyDataId + ")");
                        } else {
                            retryCount++;
                            if (retryCount < MAX_RETRY) {
                                LogUtil.logWarning("更新DM订单失败，准备第 " + retryCount + " 次重试 (order_id=" + order.getId() + ")");
                                Thread.sleep(RETRY_INTERVAL);
                            }
                        }
                    } catch (Exception e) {
                        retryCount++;
                        LogUtil.logError("更新DM订单异常 (order_id=" + order.getId() + ", 重试 " + retryCount + "/" + MAX_RETRY + "): " + e.getMessage());

                        if (retryCount < MAX_RETRY) {
                            try {
                                Thread.sleep(RETRY_INTERVAL);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                }

                // 如果更新失败，更新重试次数和错误信息
                if (!updateSuccess) {
                    localDao.incrementSyncAttempts(order.getId());
                    localDao.updateSyncError(order.getId(), "更新失败，已重试" + MAX_RETRY + "次");
                }

            } catch (Exception e) {
                LogUtil.logError("处理DM订单更新异常 (order_id=" + order.getId() + "): " + e.getMessage());
                localDao.incrementSyncAttempts(order.getId());
                localDao.updateSyncError(order.getId(), "处理异常: " + e.getMessage());
            }
        }

        return successCount;
    }
}
