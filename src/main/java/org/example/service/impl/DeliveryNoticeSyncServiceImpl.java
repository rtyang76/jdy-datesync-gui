package org.example.service.impl;

import org.example.service.DeliveryNoticeSyncService;
import org.example.service.JiandaoyunApiService;
import org.example.service.DataTransformService;
import org.example.service.DataValidationService;
import org.example.service.DatabaseService;
import org.example.service.impl.DataValidationServiceImpl;
import org.example.config.ConfigManager;
import org.example.util.LogUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 采购物料通知单同步服务实现类
 * 负责采购物料通知单数据同步的具体实现
 */
public class DeliveryNoticeSyncServiceImpl implements DeliveryNoticeSyncService {
    
    private static DeliveryNoticeSyncServiceImpl instance;
    private final JiandaoyunApiService apiService;
    private final DataTransformService transformService;
    private final DataValidationService validationService;
    private final DatabaseService databaseService;
    private final ConfigManager configManager;
    
    // 配置常量
    private final String APP_ID;
    private final String ENTRY_ID = "68ad5719554e544c07b28e6b"; // 采购物料通知单表单ID
    private final int MAX_RETRY;
    private final long RETRY_INTERVAL;
    private final int MAX_BATCH_SIZE;
    
    private DeliveryNoticeSyncServiceImpl() {
        this.apiService = JiandaoyunApiService.getInstance();
        this.transformService = DataTransformService.getInstance();
        this.validationService = DataValidationServiceImpl.getInstance();
        this.databaseService = DatabaseService.getInstance();
        this.configManager = ConfigManager.getInstance();
        
        // 初始化配置
        this.APP_ID = configManager.getProperty("jdy.appId");
        this.MAX_RETRY = Integer.parseInt(configManager.getProperty("sync.maxRetry", "10"));
        this.RETRY_INTERVAL = Long.parseLong(configManager.getProperty("sync.retryInterval", "5000"));
        this.MAX_BATCH_SIZE = Integer.parseInt(configManager.getProperty("sync.maxBatchSize", "50"));
    }
    
    public static synchronized DeliveryNoticeSyncServiceImpl getInstance() {
        if (instance == null) {
            instance = new DeliveryNoticeSyncServiceImpl();
        }
        return instance;
    }
    
    @Override
    public boolean syncProcess() {
        try {
            // 获取上次同步ID
            Integer lastSyncId = getLastDeliverySyncId();

            // 获取新数据
            List<Map<String, Object>> newData = fetchNewDeliveryData(lastSyncId);

            if (newData.isEmpty()) {
                return false;
            }

            // 有数据时才输出详细日志
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("\n=== MSD采购物料通知单同步开始 " + timestamp + " ===");
            System.out.println("获取到 " + newData.size() + " 条新数据");

            int totalProcessed = 0;
            int totalUploaded = 0;
            int maxId = lastSyncId != null ? lastSyncId : 0;

            // 分批处理数据
            for (int i = 0; i < newData.size(); i += MAX_BATCH_SIZE) {
                int endIndex = Math.min(i + MAX_BATCH_SIZE, newData.size());
                List<Map<String, Object>> batch = newData.subList(i, endIndex);
                
                List<Map<String, Object>> processedBatch = new ArrayList<>();
                
                for (Map<String, Object> record : batch) {
                    try {
                        // 更新最大ID
                        Integer recordId = (Integer) record.get("id");
                        if (recordId != null && recordId > maxId) {
                            maxId = recordId;
                        }

                        // 转换数据
                        Map<String, Object> convertedData = convertDeliveryData(record);
                        if (convertedData == null) {
                            continue;
                        }

                        // 数据验证 - 检查ASN单号是否有效
                        String asnNum = (String) record.get("asn_num");
                        if (asnNum == null || asnNum.trim().isEmpty()) {
                            LogUtil.logError("ASN单号为空，跳过记录");
                            continue;
                        }

                        // 查询是否已存在
                        Map<String, String> existingRecord = queryExistingDeliveryRecord(asnNum.trim());
                        
                        if (existingRecord != null && !existingRecord.isEmpty()) {
                            // 更新现有记录
                            String dataId = existingRecord.get("_id");
                            if (updateDeliveryRecord(dataId, convertedData)) {
                                totalProcessed++;
                                System.out.println("更新记录: " + asnNum);
                            }
                        } else {
                            // 新增记录
                            processedBatch.add(convertedData);
                        }

                    } catch (Exception e) {
                        LogUtil.logError("处理记录异常: " + e.getMessage());
                        continue;
                    }
                }

                // 批量上传新记录
                if (!processedBatch.isEmpty()) {
                    int uploaded = uploadDeliveryBatchWithRetry(processedBatch);
                    totalUploaded += uploaded;
                    totalProcessed += uploaded;
                }
            }

            // 更新同步状态
            if (maxId > (lastSyncId != null ? lastSyncId : 0)) {
                updateDeliverySyncStatus(maxId);
            }

            System.out.println("=== 采购物料通知单同步完成，处理 " + totalProcessed + " 条数据 ===");
            return true;

        } catch (Exception e) {
            LogUtil.logError("采购物料通知单同步异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Integer getLastDeliverySyncId() {
        try {
            return databaseService.getLastDeliverySyncId();
        } catch (Exception e) {
            LogUtil.logError("获取上次同步ID失败: " + e.getMessage());
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> fetchNewDeliveryData(Integer lastSyncId) {
        try {
            return databaseService.fetchNewDeliveryData(lastSyncId);
        } catch (Exception e) {
            LogUtil.logError("获取新数据失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, String> queryExistingDeliveryRecord(String asnNum) {
        try {
            return apiService.queryExistingDeliveryRecord(APP_ID, ENTRY_ID, asnNum);
        } catch (Exception e) {
            LogUtil.logError("查询已存在记录失败: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean updateDeliveryRecord(String dataId, Map<String, Object> record) {
        try {
            return apiService.updateDeliveryRecord(APP_ID, ENTRY_ID, dataId, record);
        } catch (Exception e) {
            LogUtil.logError("更新记录失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int uploadDeliveryBatchWithRetry(List<Map<String, Object>> batch) {
        int retryCount = 0;
        while (retryCount < MAX_RETRY) {
            try {
                return apiService.uploadDeliveryBatch(APP_ID, ENTRY_ID, batch);
            } catch (Exception e) {
                retryCount++;
                LogUtil.logError("批量上传失败，重试 " + retryCount + "/" + MAX_RETRY + ": " + e.getMessage());
                
                if (retryCount < MAX_RETRY) {
                    try {
                        Thread.sleep(RETRY_INTERVAL);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return 0;
    }

    @Override
    public void updateDeliverySyncStatus(int lastDeliverySyncId) {
        try {
            databaseService.updateDeliverySyncStatus(lastDeliverySyncId);
        } catch (Exception e) {
            LogUtil.logError("更新同步状态失败: " + e.getMessage());
        }
    }

    @Override
    public String getCustomCode(LocalDate currentDate, int lastCount) {
        // 采购物料通知单不需要自定义码，返回空字符串
        return "";
    }

    /**
     * 转换采购物料通知单数据
     */
    private Map<String, Object> convertDeliveryData(Map<String, Object> record) {
        try {
            // 加载字段映射配置
            Map<String, Object> mappingConfig = loadDeliveryFieldMapping();
            if (mappingConfig == null) {
                LogUtil.logError("无法加载采购物料通知单字段映射配置");
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> mainFields = (Map<String, String>) mappingConfig.get("main_fields");
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> subTables = (Map<String, Map<String, String>>) mappingConfig.get("sub_tables");

            // 使用DataTransformService进行数据转换
            return transformService.convertDeliveryData(record, mainFields, subTables);
            
        } catch (Exception e) {
            LogUtil.logError("转换采购物料通知单数据异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 加载采购物料通知单字段映射配置
     * 优先使用外置配置文件，如果不存在则使用硬编码配置作为备用
     */
    private Map<String, Object> loadDeliveryFieldMapping() {
        try {
            // 首先尝试加载外置配置文件
            String mappingPath = "po_delivery_notice_field_mapping.json";
            File mappingFile = new File(mappingPath);
            
            if (mappingFile.exists()) {
                // 使用外置配置文件
                ObjectMapper mapper = new ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> config = mapper.readValue(mappingFile, Map.class);
                LogUtil.logInfo("采购物料通知单字段映射配置文件加载成功: " + mappingPath);
                return config;
            } else {
                LogUtil.logInfo("外置配置文件不存在，使用硬编码配置: " + mappingPath);
            }
            
            // 备用硬编码配置
            Map<String, Object> config = new HashMap<>();
            
            Map<String, String> mainFields = new HashMap<>();
            mainFields.put("sid", "_widget_1756190493399");
            mainFields.put("osp_code", "_widget_1756190493400");
            mainFields.put("tran_date", "_widget_1756190493401");
            mainFields.put("asn_num", "_widget_1756190493402");
            mainFields.put("vendor_name", "_widget_1756190493403");
            mainFields.put("po_num", "_widget_1756190493404");
            mainFields.put("comments", "_widget_1756190493405");
            mainFields.put("delivery_details", "_widget_1756190493406");
            
            Map<String, String> deliveryDetails = new HashMap<>();
            deliveryDetails.put("line_num", "_widget_1756190493408");
            deliveryDetails.put("item_num", "_widget_1756190493409");
            deliveryDetails.put("lot_flag", "_widget_1756190493410");
            deliveryDetails.put("item_pn", "_widget_1756190493411");
            deliveryDetails.put("item_desc", "_widget_1756190493412");
            deliveryDetails.put("qty", "_widget_1756190493413");
            deliveryDetails.put("delivery_date", "_widget_1756190493414");
            deliveryDetails.put("asn_line", "_widget_1756190493415");
            deliveryDetails.put("plant_code", "_widget_1756190493416");
            deliveryDetails.put("warehouse_code", "_widget_1756190493417");
            deliveryDetails.put("product_line", "_widget_1756190493418");
            deliveryDetails.put("comments", "_widget_1756190493419");
            deliveryDetails.put("attribute1", "_widget_1756190493420");
            deliveryDetails.put("attribute2", "_widget_1756190493421");
            deliveryDetails.put("attribute3", "_widget_1756190493422");
            
            Map<String, Map<String, String>> subTables = new HashMap<>();
            subTables.put("delivery_details", deliveryDetails);
            
            config.put("main_fields", mainFields);
            config.put("sub_tables", subTables);
            
            return config;
            
        } catch (Exception e) {
            LogUtil.logError("加载采购物料通知单字段映射配置失败: " + e.getMessage());
            return null;
        }
    }
}