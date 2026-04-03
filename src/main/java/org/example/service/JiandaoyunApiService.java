package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.ConfigManager;
import org.example.util.HttpUtil;
import org.example.util.LogUtil;

import java.io.IOException;
import java.util.*;

/**
 * 简道云API服务类
 * 负责与简道云API交互
 */
public class JiandaoyunApiService {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static JiandaoyunApiService instance;

    // 配置信息
    private final String apiUrl;
    private final String queryUrl;
    private final String updateUrl;
    private final String apiToken;

    // 私有构造函数，防止外部实例化
    private JiandaoyunApiService() {
        ConfigManager config = ConfigManager.getInstance();
        this.apiUrl = config.getProperty("jdy.apiUrl");
        this.queryUrl = config.getProperty("jdy.queryUrl", "https://api.jiandaoyun.com/api/v5/app/entry/data/list");
        this.updateUrl = config.getProperty("jdy.updateUrl", "https://api.jiandaoyun.com/api/v5/app/entry/data/update");
        this.apiToken = config.getProperty("jdy.apiToken");
    }

    // 单例模式获取实例
    public static synchronized JiandaoyunApiService getInstance() {
        if (instance == null) {
            instance = new JiandaoyunApiService();
        }
        return instance;
    }

    /**
     * 创建数据
     * 
     * @param appId           应用ID
     * @param entryId         表单ID
     * @param dataList        数据列表
     * @param isStartWorkflow 是否启动工作流
     * @return 是否成功
     * @throws IOException 如果请求失败
     */
    public boolean createData(String appId, String entryId, List<Map<String, Object>> dataList, boolean isStartWorkflow)
            throws IOException {
        // 构建请求体
        Map<String, Object> payload = new HashMap<>();
        payload.put("app_id", appId);
        payload.put("entry_id", entryId);
        payload.put("data_list", dataList);
        payload.put("is_start_workflow", isStartWorkflow);

        String jsonBody = mapper.writeValueAsString(payload);

        String response = HttpUtil.sendPostRequest(apiUrl, jsonBody, apiToken);
        boolean success = isResponseSuccess(response);

        return success;
    }

    /**
     * 查询数据（返回完整记录）
     * 
     * @param appId      应用ID
     * @param entryId    表单ID
     * @param fieldName  字段名
     * @param fieldValue 字段值
     * @return 查询结果（包含完整数据）
     * @throws IOException 如果请求失败
     */
    public Map<String, Object> queryFullData(String appId, String entryId, String fieldName, String fieldValue)
            throws IOException {
        Map<String, Object> result = new HashMap<>();

        // 构建查询请求体
        Map<String, Object> payload = new HashMap<>();
        payload.put("app_id", appId);
        payload.put("entry_id", entryId);
        payload.put("limit", 10); // 只需要一条记录，但设置大一点避免限制

        // 构建过滤条件，根据指定字段过滤
        Map<String, Object> filter = new HashMap<>();
        filter.put("rel", "and");

        List<Map<String, Object>> conditions = new ArrayList<>();
        Map<String, Object> condition = new HashMap<>();
        condition.put("field", fieldName);
        condition.put("type", "text");
        condition.put("method", "eq");
        condition.put("value", new String[] { fieldValue });
        conditions.add(condition);

        filter.put("cond", conditions);
        payload.put("filter", filter);

        // 发送请求
        String jsonBody = mapper.writeValueAsString(payload);
        String response = HttpUtil.sendPostRequest(queryUrl, jsonBody, apiToken);

        // 解析响应
        try {
            Map<String, Object> responseMap = mapper.readValue(
                    response,
                    mapper.getTypeFactory().constructMapType(
                            HashMap.class,
                            String.class,
                            Object.class));

            // 检查响应中是否包含数据
            if (responseMap.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
                if (data != null && !data.isEmpty()) {
                    // 遍历结果，查找匹配的记录
                    for (Map<String, Object> record : data) {
                        // 获取记录中的字段值
                        String widgetValue = (String) record.get(fieldName);
                        String dataId = (String) record.get("_id");

                        // 精确匹配字段值
                        if (fieldValue.equals(widgetValue) && dataId != null) {
                            result.put("data_id", dataId);
                            result.putAll(record); // 包含完整数据
                            return result;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.logError("解析查询响应失败: " + e.getMessage());
            throw new IOException("解析查询响应失败", e);
        }

        return result;
    }

    /**
     * 查询数据（单条件）
     * 
     * @param appId      应用ID
     * @param entryId    表单ID
     * @param fieldName  字段名
     * @param fieldValue 字段值
     * @return 查询结果
     * @throws IOException 如果请求失败
     */
    public Map<String, String> queryData(String appId, String entryId, String fieldName, String fieldValue)
            throws IOException {
        Map<String, String> result = new HashMap<>();

        // 构建查询请求体
        Map<String, Object> payload = new HashMap<>();
        payload.put("app_id", appId);
        payload.put("entry_id", entryId);
        payload.put("limit", 10); // 只需要一条记录，但设置大一点避免限制

        // 构建过滤条件，根据指定字段过滤
        Map<String, Object> filter = new HashMap<>();
        filter.put("rel", "and");

        List<Map<String, Object>> conditions = new ArrayList<>();
        Map<String, Object> condition = new HashMap<>();
        condition.put("field", fieldName);
        condition.put("type", "text");
        condition.put("method", "eq");
        condition.put("value", new String[] { fieldValue });
        conditions.add(condition);

        filter.put("cond", conditions);
        payload.put("filter", filter);

        // 发送请求
        String jsonBody = mapper.writeValueAsString(payload);
        String response = HttpUtil.sendPostRequest(queryUrl, jsonBody, apiToken);

        // 解析响应
        try {
            Map<String, Object> responseMap = mapper.readValue(
                    response,
                    mapper.getTypeFactory().constructMapType(
                            HashMap.class,
                            String.class,
                            Object.class));

            // 检查响应中是否包含数据
            if (responseMap.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
                if (data != null && !data.isEmpty()) {
                    // 遍历结果，查找匹配的记录
                    for (Map<String, Object> record : data) {
                        // 获取记录中的字段值
                        String widgetValue = (String) record.get(fieldName);
                        String dataId = (String) record.get("_id");

                        // 精确匹配字段值
                        if (fieldValue.equals(widgetValue) && dataId != null) {
                            result.put("data_id", dataId);
                            return result;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.logError("解析查询响应失败: " + e.getMessage());
            throw new IOException("解析查询响应失败", e);
        }

        return result;
    }

    /**
     * 查询数据（多条件）
     * 
     * @param appId       应用ID
     * @param entryId     表单ID
     * @param conditions  查询条件Map，key为字段名，value为字段值
     * @return 查询结果
     * @throws IOException 如果请求失败
     */
    public Map<String, String> queryDataWithMultipleConditions(String appId, String entryId, 
            Map<String, String> conditions) throws IOException {
        Map<String, String> result = new HashMap<>();

        if (conditions == null || conditions.isEmpty()) {
            return result;
        }

        // 构建查询请求体
        Map<String, Object> payload = new HashMap<>();
        payload.put("app_id", appId);
        payload.put("entry_id", entryId);
        payload.put("limit", 10); // 只需要一条记录，但设置大一点避免限制

        // 构建过滤条件，使用AND关系
        Map<String, Object> filter = new HashMap<>();
        filter.put("rel", "and");

        List<Map<String, Object>> conditionList = new ArrayList<>();
        for (Map.Entry<String, String> entry : conditions.entrySet()) {
            Map<String, Object> condition = new HashMap<>();
            condition.put("field", entry.getKey());
            condition.put("type", "text");
            condition.put("method", "eq");
            condition.put("value", new String[] { entry.getValue() });
            conditionList.add(condition);
        }

        filter.put("cond", conditionList);
        payload.put("filter", filter);

        // 发送请求
        String jsonBody = mapper.writeValueAsString(payload);
        String response = HttpUtil.sendPostRequest(queryUrl, jsonBody, apiToken);

        // 解析响应
        try {
            Map<String, Object> responseMap = mapper.readValue(
                    response,
                    mapper.getTypeFactory().constructMapType(
                            HashMap.class,
                            String.class,
                            Object.class));

            // 检查响应中是否包含数据
            if (responseMap.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
                if (data != null && !data.isEmpty()) {
                    // 遍历结果，查找匹配的记录
                    for (Map<String, Object> record : data) {
                        String dataId = (String) record.get("_id");
                        
                        // 验证所有条件是否匹配
                        boolean allMatch = true;
                        for (Map.Entry<String, String> entry : conditions.entrySet()) {
                            String fieldName = entry.getKey();
                            String expectedValue = entry.getValue();
                            String actualValue = (String) record.get(fieldName);
                            
                            if (!expectedValue.equals(actualValue)) {
                                allMatch = false;
                                break;
                            }
                        }
                        
                        // 如果所有条件都匹配，返回结果
                        if (allMatch && dataId != null) {
                            result.put("data_id", dataId);
                            // 可以添加其他需要的字段
                            for (Map.Entry<String, String> entry : conditions.entrySet()) {
                                String fieldValue = (String) record.get(entry.getKey());
                                if (fieldValue != null) {
                                    result.put(entry.getKey(), fieldValue);
                                }
                            }
                            return result;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.logError("解析多条件查询响应失败: " + e.getMessage());
            throw new IOException("解析多条件查询响应失败", e);
        }

        return result;
    }

    /**
     * 更新数据
     * 
     * @param appId   应用ID
     * @param entryId 表单ID
     * @param dataId  数据ID
     * @param data    数据
     * @return 是否成功
     * @throws IOException 如果请求失败
     */
    public boolean updateData(String appId, String entryId, String dataId, Map<String, Object> data)
            throws IOException {
        // 构建更新请求体
        Map<String, Object> payload = new HashMap<>();
        payload.put("app_id", appId);
        payload.put("entry_id", entryId);
        payload.put("data_id", dataId);
        payload.put("data", data);
        payload.put("is_start_trigger", true); // 添加触发智能助手参数

        // 发送请求
        String jsonBody = mapper.writeValueAsString(payload);
        

        
        String response = HttpUtil.sendPostRequest(updateUrl, jsonBody, apiToken);
        
        boolean success = isResponseSuccess(response);

        return success;
    }

    /**
     * 查询已存在的采购物料通知单记录
     * 
     * @param appId   应用ID
     * @param entryId 表单ID
     * @param asnNum  ASN单号
     * @return 查询结果
     * @throws IOException 如果请求失败
     */
    public Map<String, String> queryExistingDeliveryRecord(String appId, String entryId, String asnNum) throws IOException {
        Map<String, String> result = new HashMap<>();

        // 构建查询请求体
        Map<String, Object> payload = new HashMap<>();
        payload.put("app_id", appId);
        payload.put("entry_id", entryId);
        payload.put("limit", 10);

        // 构建过滤条件，根据ASN单号过滤
        Map<String, Object> filter = new HashMap<>();
        filter.put("rel", "and");

        List<Map<String, Object>> conditions = new ArrayList<>();
        Map<String, Object> condition = new HashMap<>();
        condition.put("field", "_widget_1756190493402"); // ASN单号字段
        condition.put("type", "text");
        condition.put("method", "eq");
        condition.put("value", asnNum);
        conditions.add(condition);

        filter.put("cond", conditions);
        payload.put("filter", filter);

        String jsonBody = mapper.writeValueAsString(payload);
        String response = HttpUtil.sendPostRequest(queryUrl, jsonBody, apiToken);

        try {
            Map<String, Object> resp = mapper.readValue(response, 
                mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));

            if (resp != null && resp.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
                if (data != null && !data.isEmpty()) {
                    for (Map<String, Object> record : data) {
                        String widgetValue = (String) record.get("_widget_1756190493402");
                        String dataId = (String) record.get("_id");

                        if (asnNum.equals(widgetValue) && dataId != null) {
                            result.put("_id", dataId);
                            return result;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.logError("解析采购物料通知单查询响应失败: " + e.getMessage());
            throw new IOException("解析采购物料通知单查询响应失败", e);
        }

        return result;
    }

    /**
     * 更新采购物料通知单记录
     * 
     * @param appId   应用ID
     * @param entryId 表单ID
     * @param dataId  数据ID
     * @param record  记录数据
     * @return 是否成功
     * @throws IOException 如果请求失败
     */
    public boolean updateDeliveryRecord(String appId, String entryId, String dataId, Map<String, Object> record) throws IOException {
        // 构建更新请求体
        Map<String, Object> payload = new HashMap<>();
        payload.put("app_id", appId);
        payload.put("entry_id", entryId);
        payload.put("data_id", dataId);
        payload.put("data", record);
        payload.put("is_start_trigger", true);

        String jsonBody = mapper.writeValueAsString(payload);
        String response = HttpUtil.sendPostRequest(updateUrl, jsonBody, apiToken);
        
        return isResponseSuccess(response);
    }

    /**
     * 批量上传采购物料通知单数据
     * 
     * @param appId    应用ID
     * @param entryId  表单ID
     * @param batch    批次数据
     * @return 成功上传的数量
     * @throws IOException 如果请求失败
     */
    public int uploadDeliveryBatch(String appId, String entryId, List<Map<String, Object>> batch) throws IOException {
        if (batch.isEmpty()) {
            return 0;
        }

        boolean success = createData(appId, entryId, batch, false);
        return success ? batch.size() : 0;
    }

    /**
     * 判断响应是否成功
     * 
     * @param response 响应内容
     * @return 是否成功
     */
    private boolean isResponseSuccess(String response) {
        try {
            if (response.length() == 0) {
                LogUtil.logError("简道云返回空响应");
                return false;
            }

            Map<String, Object> resp = mapper.readValue(
                    response,
                    mapper.getTypeFactory().constructMapType(
                            HashMap.class,
                            String.class,
                            Object.class));

            if (resp == null) {
                LogUtil.logError("简道云响应解析失败");
                return false;
            }

            // 增强响应成功判断逻辑：
            // 1. status为success
            // 2. 或者包含data字段且不为空
            // 3. 或者存在dataId字段
            boolean success = false;

            if (resp.containsKey("status") && "success".equals(resp.get("status"))) {
                success = true;
            } else if (resp.containsKey("data") && resp.get("data") != null) {
                success = true;
            } else if (resp.containsKey("dataId") || resp.containsKey("data_id")) {
                success = true;
            }

            if (!success) {
                LogUtil.logError("简道云返回失败状态: " + response);
            }

            return success;
        } catch (Exception e) {
            LogUtil.logError("解析简道云响应失败: " + e.getMessage());
            return false;
        }
    }
}