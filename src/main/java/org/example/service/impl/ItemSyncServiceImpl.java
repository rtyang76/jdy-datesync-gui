package org.example.service.impl;

import org.example.service.ItemSyncService;
import org.example.service.JiandaoyunApiService;
import org.example.service.DataTransformService;
import org.example.service.DataValidationService;
import org.example.service.DatabaseService;
import org.example.dao.ItemDao;
import org.example.model.ItemRecord;
import org.example.config.ConfigManager;
import org.example.config.FieldMappingConfig;
import org.example.util.LogUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 物料同步服务实现类
 * 负责物料数据同步的具体实现
 */
public class ItemSyncServiceImpl implements ItemSyncService {

    private static ItemSyncServiceImpl instance;
    private final JiandaoyunApiService apiService;
    private final DataTransformService transformService;
    private final DataValidationService validationService;
    private final DatabaseService databaseService;
    private final ItemDao itemDao;
    private final ConfigManager configManager;
    private final FieldMappingConfig fieldMappingConfig;

    // 配置常量
    private final String APP_ID;
    private final String ITEM_ENTRY_ID;
    private final int MAX_RETRY;
    private final long RETRY_INTERVAL;
    private final int MAX_BATCH_SIZE;

    private ItemSyncServiceImpl() {
        this.apiService = JiandaoyunApiService.getInstance();
        this.transformService = DataTransformService.getInstance();
        this.validationService = DataValidationServiceImpl.getInstance();
        this.databaseService = DatabaseService.getInstance();
        this.itemDao = ItemDao.getInstance();
        this.configManager = ConfigManager.getInstance();
        this.fieldMappingConfig = FieldMappingConfig.getInstance();

        // 初始化配置
        this.APP_ID = configManager.getProperty("jdy.appId");
        this.ITEM_ENTRY_ID = configManager.getProperty("jdy.itemEntryId", "682bfa4d2dc7a3367d0b2246");
        this.MAX_RETRY = Integer.parseInt(configManager.getProperty("sync.maxRetry", "10"));
        this.RETRY_INTERVAL = Long.parseLong(configManager.getProperty("sync.retryInterval", "5000"));
        this.MAX_BATCH_SIZE = Integer.parseInt(configManager.getProperty("sync.maxBatchSize", "100"));
    }

    public static synchronized ItemSyncServiceImpl getInstance() {
        if (instance == null) {
            instance = new ItemSyncServiceImpl();
        }
        return instance;
    }

    @Override
    public boolean syncProcess() {
        try {
            // 获取上次同步ID
            Integer lastSyncId = getLastItemSyncId();

            // 获取新数据
            List<Map<String, Object>> newData = fetchNewItemData(lastSyncId);

            if (newData.isEmpty()) {
                return false;
            }

            // 有数据时才输出详细日志
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("\n=== MSD物料同步开始 " + timestamp + " ===");
            System.out.println("=== 检测到新物料数据，开始处理 ===");

            // 处理数据
            processItemData(newData);
            return true;

        } catch (Exception e) {
            LogUtil.logError("物料同步过程异常: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getCustomCode(LocalDate currentDate, int lastCount) {
        // 物料同步不需要自定义码
        return "";
    }

    @Override
    public Integer getLastItemSyncId() {
        return databaseService.getLastItemSyncId();
    }

    @Override
    public List<Map<String, Object>> fetchNewItemData(Integer lastSyncId) {
        return databaseService.fetchNewItemData(lastSyncId, MAX_BATCH_SIZE);
    }

    @Override
    public Map<String, String> queryExistingItem(String jobNum, String itemNumber, String itemClassification) {
        try {
            // 构建多条件查询：工单号 + 物料号 + 料号所属分
            String jobNumField = configManager.getItemFieldMapping("job_num");
            String itemNumField = configManager.getItemFieldMapping("item_number");
            String itemClassField = configManager.getItemFieldMapping("item_classification");

            if (jobNumField == null || itemNumField == null || itemClassField == null) {
                LogUtil.logError("物料字段映射配置缺失: job_num=" + jobNumField + ", item_number=" + itemNumField + ", item_classification=" + itemClassField);
                return new HashMap<>();
            }

            // 构建查询条件Map
            Map<String, String> conditions = new HashMap<>();
            conditions.put(jobNumField, jobNum);
            conditions.put(itemNumField, itemNumber);
            conditions.put(itemClassField, itemClassification);

            // 使用多条件查询
            Map<String, String> result = apiService.queryDataWithMultipleConditions(APP_ID, ITEM_ENTRY_ID, conditions);

            if (!result.isEmpty()) {
                // 找到已存在物料
            }

            return result;
        } catch (Exception e) {
            LogUtil.logError("查询已存在物料失败 (工单号=" + jobNum + ", 物料号=" + itemNumber + ", 料号所属分=" + itemClassification + "): " + e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public boolean updateItemRecord(String dataId, Map<String, Object> record) {
        try {
            return apiService.updateData(APP_ID, ITEM_ENTRY_ID, dataId, record);
        } catch (Exception e) {
            LogUtil.logError("更新物料记录失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int uploadItemBatchWithRetry(List<Map<String, Object>> batch) {
        int retryCount = 0;
        int successCount = 0;

        while (retryCount < MAX_RETRY && !batch.isEmpty()) {
            try {
                boolean isStartWorkflow = Boolean.parseBoolean(
                        configManager.getProperty("jdy.isStartWorkflow", "false"));
                boolean result = apiService.createData(APP_ID, ITEM_ENTRY_ID, batch, isStartWorkflow);

                if (result) {
                    successCount += batch.size();
                    return successCount;
                } else {
                    // 批量失败，尝试单个上传
                    successCount += uploadSingleItemRecords(batch);
                    batch.clear();
                }
            } catch (Exception e) {
                // 物料批量上传异常
            }

            if (!batch.isEmpty()) {
                retryCount++;
                if (retryCount < MAX_RETRY) {
                    // 准备重试
                    try {
                        Thread.sleep(RETRY_INTERVAL);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        return successCount;
    }

    @Override
    public void updateItemSyncStatus(int lastItemSyncId) {
        databaseService.updateItemSyncStatus(lastItemSyncId);
    }

    /**
     * 处理物料数据
     */
    private void processItemData(List<Map<String, Object>> newData) {
        // 去重处理
        newData = validationService.removeDuplicateItemRecords(newData);

        // 转换数据并区分需要新建的数据和需要更新的数据
        List<Map<String, Object>> newItems = new ArrayList<>();
        List<Map<String, Object>> updateItems = new ArrayList<>();
        Map<String, String> dataIdMap = new HashMap<>(); // key: jobNum|itemNumber, value: dataId
        Map<String, Map<String, Object>> itemKeyToRecordMap = new HashMap<>(); // key: jobNum|itemNumber, value:
                                                                               // converted record

        int totalRecords = 0;
        int validRecords = 0;
        int existingRecords = 0;
        int newRecords = 0;
        int maxId = 0;

        for (Map<String, Object> record : newData) {
            try {
                totalRecords++;

                if (!validationService.isValidItemRecord(record)) {
                    // 跳过缺少必要字段的物料
                    continue;
                }

                validRecords++;
                String jobNum = (String) record.get("job_num");
                String itemNumber = (String) record.get("item_number");
                String itemClassification = (String) record.get("item_classification");
                String itemKey = jobNum + "|" + itemNumber; // 使用工单号+物料号作为唯一标识

                // 转换记录数据
                Map<String, Object> converted = transformService.convertItemData(record);
                if (converted != null) {
                    itemKeyToRecordMap.put(itemKey, converted);

                    // 使用工单号+物料号+料号所属分精确查询物料是否已存在
                    Map<String, String> existingItem = queryExistingItem(jobNum, itemNumber, itemClassification);
                    if (!existingItem.isEmpty() && existingItem.containsKey("data_id")) {
                        String dataId = existingItem.get("data_id");
                        dataIdMap.put(itemKey, dataId);
                        updateItems.add(converted);
                        existingRecords++;
                    } else {
                        newItems.add(converted);
                        newRecords++;
                    }
                } else {
                    LogUtil.logError("物料数据转换失败: " + itemKey);
                }

                // 更新最大ID
                Integer currentId = (Integer) record.get("id");
                if (currentId != null && currentId > maxId) {
                    maxId = currentId;
                }
            } catch (Exception e) {
                // 物料数据处理异常，跳过此记录
            }
        }

        // 输出处理结果汇总
        LogUtil.logInfo(String.format("处理记录汇总：总记录数=%d, 有效记录数=%d, 已存在记录=%d, 新记录=%d",
                totalRecords, validRecords, existingRecords, newRecords));

        // 上传新建数据
        int successCreate = 0;
        if (!newItems.isEmpty()) {
            LogUtil.logInfo("开始创建 " + newItems.size() + " 条新物料");
            successCreate = uploadNewItems(newItems);
        } else if (newRecords == 0 && existingRecords > 0) {
            LogUtil.logInfo("没有需要新建的物料");
        }

        // 更新已存在数据
        int successUpdate = 0;
        if (!updateItems.isEmpty()) {
            LogUtil.logInfo("开始更新 " + updateItems.size() + " 条已存在物料");
            successUpdate = updateExistingItems(updateItems, dataIdMap, itemKeyToRecordMap);
        }

        // 更新同步状态
        if (maxId > 0) {
            updateItemSyncStatus(maxId);
            LogUtil.logInfo(String.format("物料同步完成: 新建成功 %d/%d, 更新成功 %d/%d (最新ID: %d)",
                    successCreate, newItems.size(), successUpdate, updateItems.size(), maxId));
        } else {
            LogUtil.logWarning("本次物料同步未更新状态，无成功数据或最大ID为0");
        }
    }

    /**
     * 上传新物料
     */
    private int uploadNewItems(List<Map<String, Object>> newItems) {
        int batchSize = 100;
        int totalCreate = newItems.size();
        int successCreate = 0;
        List<Map<String, Object>> failedBatches = new ArrayList<>();

        if (!newItems.isEmpty()) {
            for (int i = 0; i < newItems.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, newItems.size());
                List<Map<String, Object>> batch = newItems.subList(i, endIndex);

                int batchSuccess = uploadItemBatchWithRetry(new ArrayList<>(batch));
                successCreate += batchSuccess;

                if (batchSuccess < batch.size()) {
                    failedBatches.addAll(batch);
                }
            }
            if (!failedBatches.isEmpty()) {
                successCreate += uploadItemBatchWithRetry(failedBatches);
            }
        }

        return successCreate;
    }

    /**
     * 更新已存在物料
     */
    private int updateExistingItems(List<Map<String, Object>> updateItems,
            Map<String, String> dataIdMap,
            Map<String, Map<String, Object>> itemKeyToRecordMap) {
        int totalUpdate = updateItems.size();
        int successUpdate = 0;

        if (!updateItems.isEmpty()) {
            LogUtil.logInfo("开始更新 " + totalUpdate + " 条已存在物料");

            int updateIndex = 0;
            for (Map.Entry<String, String> entry : dataIdMap.entrySet()) {
                updateIndex++;
                try {
                    String itemKey = entry.getKey(); // 格式: jobNum|itemNumber
                    String dataId = entry.getValue();
                    Map<String, Object> record = itemKeyToRecordMap.get(itemKey);

                    if (record != null) {
                        if (updateItemRecord(dataId, record)) {
                            successUpdate++;
                        }
                    }
                } catch (Exception e) {
                    // 更新物料异常
                }
            }
        }

        return successUpdate;
    }

    /**
     * 单个物料记录上传
     */
    private int uploadSingleItemRecords(List<Map<String, Object>> batch) {
        int successCount = 0;

        for (Map<String, Object> record : batch) {
            List<Map<String, Object>> singleRecord = Arrays.asList(record);
            String itemIdentifier = extractItemIdentifier(record);

            try {
                boolean isStartWorkflow = Boolean.parseBoolean(
                        configManager.getProperty("jdy.isStartWorkflow", "false"));
                if (apiService.createData(APP_ID, ITEM_ENTRY_ID, singleRecord, isStartWorkflow)) {
                    successCount++;
                }
            } catch (Exception e) {
                // 单条物料上传异常
            }
        }

        return successCount;
    }

    /**
     * 从记录中提取工单号和物料号，用于日志显示
     */
    private String extractItemIdentifier(Map<String, Object> record) {
        StringBuilder identifier = new StringBuilder();

        // 首先尝试从转换后的数据结构中提取
        try {
            String jobNumField = configManager.getItemFieldMapping("job_num");
            String itemNumberField = configManager.getItemFieldMapping("item_number");

            if (jobNumField != null && record.containsKey(jobNumField)) {
                Object fieldValue = record.get(jobNumField);
                if (fieldValue instanceof Map) {
                    Object value = ((Map<?, ?>) fieldValue).get("value");
                    if (value != null) {
                        identifier.append("工单:").append(value.toString());
                    }
                }
            }

            if (itemNumberField != null && record.containsKey(itemNumberField)) {
                Object fieldValue = record.get(itemNumberField);
                if (fieldValue instanceof Map) {
                    Object value = ((Map<?, ?>) fieldValue).get("value");
                    if (value != null) {
                        if (identifier.length() > 0) {
                            identifier.append(", ");
                        }
                        identifier.append("物料:").append(value.toString());
                    }
                }
            }
        } catch (Exception e) {
            // 提取失败，继续尝试其他方法
        }

        // 如果上面方法没有成功提取，尝试直接从记录中提取
        if (identifier.length() == 0) {
            if (record.containsKey("job_num")) {
                Object jobNum = record.get("job_num");
                if (jobNum != null) {
                    identifier.append("工单:").append(jobNum.toString());
                }
            }

            if (record.containsKey("item_number")) {
                Object itemNumber = record.get("item_number");
                if (itemNumber != null) {
                    if (identifier.length() > 0) {
                        identifier.append(", ");
                    }
                    identifier.append("物料:").append(itemNumber.toString());
                }
            }
        }

        // 如果还是没有，返回未知
        if (identifier.length() == 0) {
            return "未知物料";
        }

        return identifier.toString();
    }
}