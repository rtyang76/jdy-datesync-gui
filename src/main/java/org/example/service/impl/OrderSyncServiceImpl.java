package org.example.service.impl;

import org.example.service.OrderSyncService;
import org.example.service.JiandaoyunApiService;
import org.example.service.DataTransformService;
import org.example.service.DataValidationService;
import org.example.service.DatabaseService;
import org.example.dao.OrderDao;
import org.example.model.OrderRecord;
import org.example.config.ConfigManager;
import org.example.config.FieldMappingConfig;
import org.example.util.LogUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 订单同步服务实现类
 * 负责订单数据同步的具体实现
 */
public class OrderSyncServiceImpl implements OrderSyncService {

    private static OrderSyncServiceImpl instance;
    private final JiandaoyunApiService apiService;
    private final DataTransformService transformService;
    private final DataValidationService validationService;
    private final DatabaseService databaseService;
    private final OrderDao orderDao;
    private final ConfigManager configManager;
    private final FieldMappingConfig fieldMappingConfig;

    // 配置常量
    private final String APP_ID;
    private final String ENTRY_ID;
    private final int MAX_RETRY;
    private final long RETRY_INTERVAL;
    private final int MAX_BATCH_SIZE;

    // 自定义码相关常量
    private static final String CUSTOM_CODE_FIELD = "_widget_1748317817210";
    private static final char[] CUSTOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXY0123456789".toCharArray();

    // 更新时需要移除的字段（避免覆盖已有值）
    private static final String DATE_FIELD_TO_REMOVE = "_widget_1748238705999"; // 日期字段

    private OrderSyncServiceImpl() {
        this.apiService = JiandaoyunApiService.getInstance();
        this.transformService = DataTransformService.getInstance();
        this.validationService = DataValidationServiceImpl.getInstance();
        this.databaseService = DatabaseService.getInstance();
        this.orderDao = OrderDao.getInstance();
        this.configManager = ConfigManager.getInstance();
        this.fieldMappingConfig = FieldMappingConfig.getInstance();

        // 初始化配置
        this.APP_ID = configManager.getProperty("jdy.appId");
        this.ENTRY_ID = configManager.getProperty("jdy.entryId");
        this.MAX_RETRY = Integer.parseInt(configManager.getProperty("sync.maxRetry", "10"));
        this.RETRY_INTERVAL = Long.parseLong(configManager.getProperty("sync.retryInterval", "5000"));
        this.MAX_BATCH_SIZE = Integer.parseInt(configManager.getProperty("sync.maxBatchSize", "50"));
    }

    public static synchronized OrderSyncServiceImpl getInstance() {
        if (instance == null) {
            instance = new OrderSyncServiceImpl();
        }
        return instance;
    }

    @Override
    public boolean syncProcess() {
        try {
            // 获取上次同步ID
            Integer lastSyncId = getLastSyncId();

            // 获取上次同步日期和计数
            Map<String, Object> lastSyncInfo = getLastSyncDateAndCount();
            LocalDate syncDate = (LocalDate) lastSyncInfo.get("sync_date");
            int syncCount = (Integer) lastSyncInfo.get("sync_count");

            // 检查是否需要重置计数（新的一天）
            LocalDate today = LocalDate.now();
            if (!today.equals(syncDate)) {
                syncDate = today;
                syncCount = 0;
            }

            // 获取新数据
            List<Map<String, Object>> newData = fetchNewData(lastSyncId);

            if (newData.isEmpty()) {
                return false;
            }

            // 有数据时才输出详细日志
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("\n=== MSD订单同步开始 " + timestamp + " ===");
            System.out.println("=== 检测到新订单数据，开始处理 ===");

            // 处理数据
            processOrderData(newData, syncDate, syncCount);
            return true;

        } catch (Exception e) {
            LogUtil.logError("同步任务执行失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getCustomCode(LocalDate currentDate, int lastCount) {
        // 实现自定义码生成逻辑
        if (lastCount >= CUSTOM_CODE_CHARS.length) {
            LogUtil.logWarning("自定义码计数超出范围，重置为0");
            lastCount = 0;
        }

        char codeChar = CUSTOM_CODE_CHARS[lastCount];
        return String.valueOf(codeChar); // 只返回字母，不包含日期
    }

    @Override
    public Integer getLastSyncId() {
        return databaseService.getLastSyncId();
    }

    @Override
    public Map<String, Object> getLastSyncDateAndCount() {
        return databaseService.getLastSyncDateAndCount();
    }

    @Override
    public List<Map<String, Object>> fetchNewData(Integer lastSyncId) {
        return databaseService.fetchNewOrderData(lastSyncId, MAX_BATCH_SIZE);
    }

    @Override
    public Map<String, String> queryExistingRecord(String jobNumber) {
        try {
            String jobNumField = configManager.getFieldMapping("job_num");
            return apiService.queryData(APP_ID, ENTRY_ID, jobNumField, jobNumber);
        } catch (Exception e) {
            LogUtil.logError("查询已存在记录失败: " + e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public boolean updateRecord(String dataId, Map<String, Object> record) {
        try {
            return apiService.updateData(APP_ID, ENTRY_ID, dataId, record);
        } catch (Exception e) {
            LogUtil.logError("更新记录失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int uploadBatchWithRetry(List<Map<String, Object>> batch) {
        int retryCount = 0;
        int successCount = 0;

        while (retryCount < MAX_RETRY && !batch.isEmpty()) {
            try {
                boolean isStartWorkflow = Boolean.parseBoolean(
                        configManager.getProperty("jdy.isStartWorkflow", "false"));
                boolean result = apiService.createData(APP_ID, ENTRY_ID, batch, isStartWorkflow);

                if (result) {
                    successCount += batch.size();
                    return successCount;
                } else {
                    // 批量失败，尝试单个上传
                    successCount += uploadSingleRecords(batch);
                    batch.clear();
                }
            } catch (Exception e) {
                // 批量上传异常
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
    public void updateSyncStatus(int lastSyncId, LocalDate syncDate, int syncCount) {
        databaseService.updateSyncStatus(lastSyncId, syncDate, syncCount);
    }

    /**
     * 处理订单数据
     */
    private void processOrderData(List<Map<String, Object>> newData, LocalDate syncDate, int syncCount) {
        // 去重处理
        newData = validationService.removeDuplicateRecords(newData);

        // 转换数据
        List<Map<String, Object>> transformedData = new ArrayList<>();
        List<Map<String, Object>> updateData = new ArrayList<>();
        Map<String, String> dataIdMap = new HashMap<>();
        Map<String, Map<String, Object>> orderRecordMap = new HashMap<>();
        Map<String, Map<String, Object>> updateRecordMap = new HashMap<>();

        int maxId = 0;
        int customCodeCount = 0;
        int totalRecords = 0;
        int validRecords = 0;
        int existingRecords = 0;
        int newRecords = 0;

        for (Map<String, Object> record : newData) {
            try {
                totalRecords++;

                if (!validationService.isValidRecord(record)) {
                    // 跳过无效记录
                    continue;
                }

                validRecords++;
                String jobNumber = (String) record.get("job_num");

                // 检查是否已存在
                Map<String, String> existingRecord = queryExistingRecord(jobNumber);
                Map<String, Object> converted;

                // 转换数据
                converted = transformService.convertData(
                        record,
                        configManager.getFieldMapping(),
                        configManager.getSubTables());

                if (converted != null) {
                    orderRecordMap.put(jobNumber, converted);

                    // 检查是否已存在
                    if (!existingRecord.isEmpty() && existingRecord.containsKey("data_id")) {
                        // 数据已存在，准备更新
                        // 从更新数据中移除日期字段，避免覆盖简道云中已有的值
                        // 其他提取字段仍然更新，以便客户需求变更时能及时同步
                        Map<String, Object> updateConverted = new HashMap<>(converted);
                        updateConverted.remove(DATE_FIELD_TO_REMOVE);

                        dataIdMap.put(jobNumber, existingRecord.get("data_id"));
                        updateData.add(updateConverted);
                        updateRecordMap.put(jobNumber, updateConverted); // 存储处理后的更新数据
                        existingRecords++;
                    } else {
                        // 数据不存在，新建数据

                        // 添加自定义码（如果需要）
                        String jobStatus = (String) record.get("job_status");
                        if (jobStatus != null && "已发放".equals(jobStatus.trim())) {
                            int currentCodeIndex = syncCount + customCodeCount;
                            String customCode = getCustomCode(syncDate, currentCodeIndex);
                            converted.put(CUSTOM_CODE_FIELD,
                                    Collections.singletonMap("value", customCode));
                            customCodeCount++;
                        }

                        transformedData.add(converted);
                        newRecords++;
                    }
                }

                Integer currentId = (Integer) record.get("id");
                if (currentId != null && currentId > maxId) {
                    maxId = currentId;
                }
            } catch (Exception e) {
                // 数据处理异常，跳过此记录
            }
        }

        // 输出处理结果汇总
        LogUtil.logInfo(String.format("处理记录汇总：总记录数=%d, 有效记录数=%d, 已存在记录=%d, 新记录=%d",
                totalRecords, validRecords, existingRecords, newRecords));

        // 上传新建数据
        int successCreate = 0;
        if (!transformedData.isEmpty()) {
            LogUtil.logInfo("开始创建 " + transformedData.size() + " 条新订单");
            successCreate = uploadNewRecords(transformedData);
        }

        // 更新已存在数据
        int successUpdate = 0;
        if (!updateData.isEmpty()) {
            LogUtil.logInfo("开始更新 " + updateData.size() + " 条已存在订单");
            successUpdate = updateExistingRecords(updateData, dataIdMap, updateRecordMap);
        } else if (existingRecords > 0) {
            LogUtil.logInfo("没有需要更新的订单");
        }

        // 更新同步状态
        if (maxId > 0) {
            int newSyncCount = syncCount + customCodeCount;
            updateSyncStatus(maxId, syncDate, newSyncCount);
            LogUtil.logInfo(String.format("订单同步完成: 新建成功 %d/%d, 更新成功 %d/%d (最新ID: %d)",
                    successCreate, transformedData.size(),
                    successUpdate, updateData.size(),
                    maxId));
        }
    }

    /**
     * 上传新记录
     */
    private int uploadNewRecords(List<Map<String, Object>> transformedData) {
        int batchSize = 100;
        int totalCreate = transformedData.size();
        int successCreate = 0;
        List<Map<String, Object>> failedBatches = new ArrayList<>();

        if (!transformedData.isEmpty()) {
            for (int i = 0; i < transformedData.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, transformedData.size());
                List<Map<String, Object>> batch = transformedData.subList(i, endIndex);

                int batchSuccess = uploadBatchWithRetry(new ArrayList<>(batch));
                successCreate += batchSuccess;

                if (batchSuccess < batch.size()) {
                    failedBatches.addAll(batch);
                }
            }
            if (!failedBatches.isEmpty()) {
                successCreate += uploadBatchWithRetry(failedBatches);
            }
        }

        return successCreate;
    }

    /**
     * 更新已存在记录
     */
    private int updateExistingRecords(List<Map<String, Object>> updateData,
            Map<String, String> dataIdMap,
            Map<String, Map<String, Object>> updateRecordMap) {
        int totalUpdate = updateData.size();
        int successUpdate = 0;

        if (!updateData.isEmpty()) {

            int updateIndex = 0;
            for (Map.Entry<String, String> entry : dataIdMap.entrySet()) {
                updateIndex++;
                try {
                    String jobNumber = entry.getKey();
                    String dataId = entry.getValue();
                    Map<String, Object> record = updateRecordMap.get(jobNumber);

                    if (record != null) {
                        if (updateRecord(dataId, record)) {
                            successUpdate++;
                        }
                    }
                } catch (Exception e) {
                    // 更新订单失败
                }
            }

            // 更新结果已统计
        }

        return successUpdate;
    }

    /**
     * 单个记录上传
     */
    private int uploadSingleRecords(List<Map<String, Object>> batch) {
        int successCount = 0;

        for (Map<String, Object> record : batch) {
            List<Map<String, Object>> singleRecord = Arrays.asList(record);
            try {
                boolean isStartWorkflow = Boolean.parseBoolean(
                        configManager.getProperty("jdy.isStartWorkflow", "false"));
                if (apiService.createData(APP_ID, ENTRY_ID, singleRecord, isStartWorkflow)) {
                    successCount++;
                }
            } catch (Exception e) {
                // 单条记录上传失败
            }
        }

        return successCount;
    }
}