package org.example.gui.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.example.gui.model.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class SyncEngine {

    private static final Logger logger = Logger.getLogger(SyncEngine.class.getName());
    private static final String CREATE_URL = "https://api.jiandaoyun.com/api/v5/app/entry/data/batch_create";
    private static final String QUERY_URL = "https://api.jiandaoyun.com/api/v5/app/entry/data/list";
    private static final String UPDATE_URL = "https://api.jiandaoyun.com/api/v5/app/entry/data/update";
    private static final long UPDATE_DELAY_MS = 2000;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final ConfigManager configManager;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient httpClient;

    public SyncEngine(ConfigManager configManager) {
        this.configManager = configManager;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public SyncResult executeTask(String taskId) {
        List<SyncTaskConfig> tasks = configManager.loadSyncTasks();
        SyncTaskConfig task = tasks.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst().orElse(null);

        if (task == null) {
            return new SyncResult(false, "任务不存在: " + taskId);
        }

        if (!task.isEnabled()) {
            return new SyncResult(false, "任务已禁用: " + task.getName());
        }

        List<String> mappingIds = task.getFormMappingIds();
        if (mappingIds == null || mappingIds.isEmpty()) {
            return new SyncResult(false, "任务未关联任何表单映射配置");
        }

        StringBuilder resultMsg = new StringBuilder();
        int successCount = 0;
        int failCount = 0;

        for (String mappingId : mappingIds) {
            FormMappingConfig formMapping = configManager.findFormMappingById(mappingId);
            if (formMapping == null) {
                failCount++;
                resultMsg.append("表单映射配置不存在: ").append(mappingId).append("; ");
                continue;
            }

            SyncResult result = executeSingleMapping(task, formMapping);
            if (result.isSuccess()) {
                successCount++;
            } else {
                failCount++;
                resultMsg.append(formMapping.getName()).append(": ").append(result.getMessage()).append("; ");
            }
        }

        if (failCount == 0) {
            return new SyncResult(true, "成功推送 " + successCount + " 个表单映射");
        } else if (successCount == 0) {
            return new SyncResult(false, "全部失败: " + resultMsg.toString());
        } else {
            return new SyncResult(true, "部分成功: 成功 " + successCount + " 个，失败 " + failCount + " 个");
        }
    }

    private SyncResult executeSingleMapping(SyncTaskConfig task, FormMappingConfig formMapping) {
        List<DataSourceConfig> dataSources = configManager.loadDataSources();

        DataSourceConfig ds = dataSources.stream()
                .filter(d -> d.getId().equals(formMapping.getDataSourceId()))
                .findFirst().orElse(null);

        if (ds == null) {
            return new SyncResult(false, "数据源不存在: " + formMapping.getDataSourceId());
        }

        JdyAppConfig jdyConfig = configManager.findJdyAppById(formMapping.getJdyAppId());
        if (jdyConfig == null) {
            return new SyncResult(false, "简道云应用未配置或已删除");
        }
        if (jdyConfig.getApiToken() == null || jdyConfig.getApiToken().trim().isEmpty()) {
            return new SyncResult(false, "简道云应用 API Token 未配置: " + jdyConfig.getName());
        }

        Map<String, String> mainMapping = formMapping.getMainFieldMapping();
        if (mainMapping == null || mainMapping.isEmpty()) {
            return new SyncResult(false, "主表字段映射未配置");
        }

        Map<String, String> activeMainMapping = new LinkedHashMap<>();
        mainMapping.forEach((k, v) -> {
            if (v != null && !v.trim().isEmpty()) {
                activeMainMapping.put(k, v.trim());
            }
        });

        if (activeMainMapping.isEmpty()) {
            return new SyncResult(false, "没有配置任何有效的主表字段映射");
        }

        SyncProgress progress = configManager.loadSyncProgress();
        String lastSyncIdStr = progress.getLastSyncId(formMapping.getId());

        String incrementField = formMapping.getIncrementField() != null && !formMapping.getIncrementField().trim().isEmpty()
                ? formMapping.getIncrementField().trim() : "id";
        String incrementMode = formMapping.getIncrementMode() != null ? formMapping.getIncrementMode() : "id";

        if (lastSyncIdStr == null || lastSyncIdStr.isEmpty()) {
            if ("timestamp".equals(incrementMode)) {
                lastSyncIdStr = LocalDate.now().atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                lastSyncIdStr = "0";
            }
        }

        logger.info("========== 开始同步 ==========");
        logger.info("任务名称: " + task.getName());
        logger.info("表单配置: " + formMapping.getName());
        logger.info("主表: " + formMapping.getMainTableName());
        logger.info("增量方式: " + ("id".equals(incrementMode) ? "自增ID" : "时间戳"));
        logger.info("增量字段: " + incrementField);
        logger.info("当前水印: " + lastSyncIdStr);

        try {
            return syncData(ds, task, formMapping, activeMainMapping, lastSyncIdStr, progress, jdyConfig, incrementField, incrementMode);
        } catch (Exception e) {
            logger.severe("同步任务异常: " + e.getMessage());
            return new SyncResult(false, "同步异常: " + e.getMessage());
        }
    }

    private SyncResult syncData(DataSourceConfig ds, SyncTaskConfig task,
                                 FormMappingConfig formMapping,
                                 Map<String, String> activeMainMapping, String lastSyncIdStr,
                                 SyncProgress progress, JdyAppConfig jdyConfig,
                                 String incrementField, String incrementMode) throws Exception {

        String primaryKeyField = detectPrimaryKeyField(activeMainMapping, incrementField);

        validateSqlIdentifier(formMapping.getMainTableName(), "主表名");
        validateSqlIdentifier(incrementField, "增量字段");

        String url = ds.getJdbcUrl();
        int totalCreated = 0;
        int totalUpdated = 0;
        String maxId = lastSyncIdStr;
        boolean hadData = false;
        boolean pushFailed = false;

        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            while (true) {
                String sql = "SELECT * FROM `" + formMapping.getMainTableName()
                        + "` WHERE `" + incrementField + "` > ? ORDER BY `" + incrementField + "` ASC LIMIT ?";

                logger.info("查询本地数据库SQL: " + sql + " [参数: " + lastSyncIdStr + ", " + task.getMaxBatchSize() + "]");

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    if ("timestamp".equals(incrementMode)) {
                        pstmt.setString(1, lastSyncIdStr);
                    } else {
                        pstmt.setLong(1, parseLongSafe(lastSyncIdStr));
                    }
                    pstmt.setInt(2, task.getMaxBatchSize());

                    try (ResultSet rs = pstmt.executeQuery()) {

                        List<Map<String, Object>> batchData = new ArrayList<>();

                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                row.put(metaData.getColumnName(i), rs.getObject(i));
                            }
                            batchData.add(row);

                            Object idVal = row.get(incrementField);
                            if (idVal != null) {
                                String idStr = idVal.toString();
                                if ("timestamp".equals(incrementMode)) {
                                    if (idStr.compareTo(maxId) > 0) {
                                        maxId = idStr;
                                    }
                                } else {
                                    long id = parseLongSafe(idStr);
                                    if (id > parseLongSafe(maxId)) {
                                        maxId = String.valueOf(id);
                                    }
                                }
                            }
                        }

                        if (batchData.isEmpty()) {
                            logger.info("查询结果: 无新数据需要同步");
                            break;
                        }

                        hadData = true;
                        logger.info("查询结果: 发现 " + batchData.size() + " 条新数据");

                        QueryMatchConfig queryConfig = formMapping.getQueryMatchConfig();
                        boolean hasQueryConditions = queryConfig != null && queryConfig.getConditions() != null && !queryConfig.getConditions().isEmpty();

                        if (hasQueryConditions) {
                            logger.info("匹配条件: " + queryConfig.getRelation().toUpperCase() + " 模式，共 " + queryConfig.getConditions().size() + " 个条件");
                        } else {
                            logger.info("匹配条件: 无，将全部走新建接口");
                        }

                        int[] result = pushToJdy(jdyConfig, formMapping, batchData, activeMainMapping, conn, task.getMaxRetry(), primaryKeyField, incrementMode, hasQueryConditions, queryConfig);
                        int created = result[0];
                        int updated = result[1];

                        if (created > 0 || updated > 0 || !hasQueryConditions) {
                            totalCreated += created;
                            totalUpdated += updated;
                            progress.setLastSyncId(formMapping.getId(), maxId);
                            configManager.saveSyncProgress(progress);
                            logger.info("推送结果: 新建 " + created + " 条，更新 " + updated + " 条");
                            logger.info("水印更新: " + maxId);
                        } else {
                            pushFailed = true;
                            logger.warning("推送失败，本批次数据将下次重试");
                            break;
                        }

                        if (batchData.size() < task.getMaxBatchSize()) {
                            break;
                        }
                    }
                }
            }
        }

        logger.info("========== 同步结束 ==========");
        if (pushFailed) {
            return new SyncResult(false, "推送简道云失败，请检查配置和字段映射");
        } else if (hadData) {
            return new SyncResult(true, "同步完成，新建 " + totalCreated + " 条，更新 " + totalUpdated + " 条");
        } else {
            return new SyncResult(true, "无新数据需要同步（水印: " + lastSyncIdStr + "）");
        }
    }

    private String detectPrimaryKeyField(Map<String, String> mainFieldMapping, String incrementField) {
        return "id";
    }

    private void validateSqlIdentifier(String identifier, String label) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException(label + "包含非法字符: " + identifier + "，仅允许字母、数字和下划线");
        }
    }

    private Request buildJdyRequest(String url, JdyAppConfig jdyConfig, String jsonBody) {
        String token = jdyConfig.getApiToken().trim();
        if (!token.startsWith("Bearer ")) {
            token = "Bearer " + token;
        }
        RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);
        return new Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .header("Authorization", token)
                .header("X-Request-ID", UUID.randomUUID().toString())
                .build();
    }

    private String executeRequest(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String responseStr = responseBody != null ? responseBody.string() : "";
            logger.info("简道云API响应 [HTTP " + response.code() + "]: " + responseStr);
            if (response.code() == 200) {
                return responseStr;
            }
            throw new IOException("HTTP " + response.code() + ": " + responseStr);
        }
    }

    private int[] pushToJdy(JdyAppConfig jdyConfig, FormMappingConfig formMapping,
                               List<Map<String, Object>> batchData,
                               Map<String, String> activeMainMapping,
                               Connection conn, int maxRetry, String primaryKeyField,
                               String incrementMode, boolean hasQueryConditions,
                               QueryMatchConfig queryConfig) throws Exception {

        int createdCount = 0;
        int updatedCount = 0;

        List<Map<String, Object>> jdyPayload = buildJdyPayload(batchData, activeMainMapping, formMapping, conn, primaryKeyField);

        if (!hasQueryConditions) {
            logger.info("无匹配条件，全部走批量新建接口");
            boolean success = createJdyDataBatch(jdyConfig, formMapping, jdyPayload, maxRetry);
            if (success) {
                createdCount = batchData.size();
            }
            return new int[]{createdCount, updatedCount};
        }

        logger.info("开始按匹配条件分组处理...");

        Map<String, List<Integer>> matchKeyGroups = new LinkedHashMap<>();
        for (int i = 0; i < batchData.size(); i++) {
            String matchKey = buildMatchKey(batchData.get(i), queryConfig);
            matchKeyGroups.computeIfAbsent(matchKey, k -> new ArrayList<>()).add(i);
        }

        logger.info("批次数据按匹配条件分组: " + matchKeyGroups.size() + " 个组");

        for (Map.Entry<String, List<Integer>> entry : matchKeyGroups.entrySet()) {
            List<Integer> indices = entry.getValue();

            indices.sort((a, b) -> {
                Object idA = batchData.get(a).get(primaryKeyField);
                Object idB = batchData.get(b).get(primaryKeyField);
                if ("timestamp".equals(incrementMode)) {
                    String tsA = idA != null ? idA.toString() : "";
                    String tsB = idB != null ? idB.toString() : "";
                    return tsA.compareTo(tsB);
                } else {
                    long longA = parseLongSafe(idA != null ? idA.toString() : "0");
                    long longB = parseLongSafe(idB != null ? idB.toString() : "0");
                    return Long.compare(longA, longB);
                }
            });

            int firstIdx = indices.get(0);
            Map<String, Object> firstRow = batchData.get(firstIdx);
            Map<String, Object> firstJdyRow = jdyPayload.get(firstIdx);

            logger.info("处理分组 [" + entry.getKey() + "]，共 " + indices.size() + " 条数据");

            List<Map<String, Object>> existingData = queryJdyData(jdyConfig, formMapping, firstRow, queryConfig);

            String dataId = null;

            if (!existingData.isEmpty()) {
                if (queryConfig.isAllowMultipleUpdate() || existingData.size() == 1) {
                    dataId = (String) existingData.get(0).get("_id");
                    if (dataId != null) {
                        logger.info("简道云已存在该数据(dataId=" + dataId + ")，第一条走更新");
                        boolean updateSuccess = updateJdyData(jdyConfig, formMapping, dataId, firstJdyRow, maxRetry);
                        if (updateSuccess) {
                            updatedCount++;
                        }
                    }
                } else {
                    logger.warning("查询到 " + existingData.size() + " 条匹配数据但未启用多选更新，跳过此组");
                    continue;
                }
            } else {
                logger.info("简道云不存在该数据，第一条走新建");
                String newId = createJdyDataSingle(jdyConfig, formMapping, firstJdyRow, maxRetry);
                if (newId != null) {
                    dataId = newId;
                    createdCount++;
                    logger.info("新建成功，dataId=" + dataId);
                } else {
                    logger.info("新建接口未返回dataId，通过查询获取");
                    Thread.sleep(1000);
                    List<Map<String, Object>> newQueryResult = queryJdyData(jdyConfig, formMapping, firstRow, queryConfig);
                    if (!newQueryResult.isEmpty()) {
                        dataId = (String) newQueryResult.get(0).get("_id");
                        logger.info("查询到新建数据的dataId=" + dataId);
                    }
                }
            }

            if (dataId != null && indices.size() > 1) {
                logger.info("后续 " + (indices.size() - 1) + " 条数据依次走更新接口");
                for (int i = 1; i < indices.size(); i++) {
                    int idx = indices.get(i);
                    Map<String, Object> jdyRow = jdyPayload.get(idx);
                    Thread.sleep(UPDATE_DELAY_MS);
                    boolean updateSuccess = updateJdyData(jdyConfig, formMapping, dataId, jdyRow, maxRetry);
                    if (updateSuccess) {
                        updatedCount++;
                    }
                }
            }
        }

        return new int[]{createdCount, updatedCount};
    }

    private List<Map<String, Object>> buildJdyPayload(List<Map<String, Object>> batchData,
                                                        Map<String, String> activeMainMapping,
                                                        FormMappingConfig formMapping,
                                                        Connection conn, String primaryKeyField) throws Exception {
        List<Map<String, Object>> jdyPayload = new ArrayList<>();

        Map<String, Map<String, List<Map<String, Object>>>> subTableCache = new LinkedHashMap<>();
        if (formMapping.getSubTableMappings() != null && !formMapping.getSubTableMappings().isEmpty()) {
            logger.info("开始批量预加载子表数据，共 " + formMapping.getSubTableMappings().size() + " 个子表");
            for (SubTableMapping subMapping : formMapping.getSubTableMappings()) {
                if (subMapping.getSubTableName() == null || subMapping.getSubTableName().trim().isEmpty()) {
                    continue;
                }
                if (subMapping.getFieldMapping() == null || subMapping.getFieldMapping().isEmpty()) {
                    continue;
                }
                if (subMapping.getSubFormWidgetId() == null || subMapping.getSubFormWidgetId().trim().isEmpty()) {
                    continue;
                }
                List<SubTableJoinCondition> joinConditions = subMapping.getJoinConditions();
                if (joinConditions == null || joinConditions.isEmpty()) {
                    continue;
                }
                Map<String, List<Map<String, Object>>> grouped = batchQuerySubTable(
                        conn, subMapping.getSubTableName(), joinConditions, batchData);
                subTableCache.put(subMapping.getSubTableName(), grouped);
                logger.info("子表 " + subMapping.getSubTableName() + " 预加载完成，共 " + grouped.size() + " 个分组");
            }
        }

        for (Map<String, Object> row : batchData) {
            Map<String, Object> jdyRow = new LinkedHashMap<>();

            for (Map.Entry<String, String> mapping : activeMainMapping.entrySet()) {
                String dbField = mapping.getKey();
                String widgetId = mapping.getValue();
                Object value = row.get(dbField);
                jdyRow.put(widgetId, wrapValue(value));
            }

            if (formMapping.getSubTableMappings() != null && !formMapping.getSubTableMappings().isEmpty()) {
                for (SubTableMapping subMapping : formMapping.getSubTableMappings()) {
                    if (subMapping.getSubTableName() == null || subMapping.getSubTableName().trim().isEmpty()) {
                        continue;
                    }
                    if (subMapping.getFieldMapping() == null || subMapping.getFieldMapping().isEmpty()) {
                        continue;
                    }
                    if (subMapping.getSubFormWidgetId() == null || subMapping.getSubFormWidgetId().trim().isEmpty()) {
                        continue;
                    }
                    List<SubTableJoinCondition> joinConditions = subMapping.getJoinConditions();
                    if (joinConditions == null || joinConditions.isEmpty()) {
                        continue;
                    }

                    String joinKey = buildJoinKey(row, joinConditions);
                    Map<String, List<Map<String, Object>>> grouped = subTableCache.get(subMapping.getSubTableName());
                    List<Map<String, Object>> subRows = (grouped != null) ? grouped.getOrDefault(joinKey, Collections.emptyList()) : Collections.emptyList();

                    List<Map<String, Object>> jdySubRows = new ArrayList<>();
                    for (Map<String, Object> subRow : subRows) {
                        Map<String, Object> jdySubRow = new LinkedHashMap<>();
                        for (Map.Entry<String, String> subFieldMap : subMapping.getFieldMapping().entrySet()) {
                            String dbField = subFieldMap.getKey();
                            String widgetId = subFieldMap.getValue();
                            Object value = subRow.get(dbField);
                            jdySubRow.put(widgetId, wrapValue(value));
                        }
                        if (!jdySubRow.isEmpty()) {
                            jdySubRows.add(jdySubRow);
                        }
                    }

                    if (!jdySubRows.isEmpty()) {
                        Map<String, Object> subTableValue = new LinkedHashMap<>();
                        subTableValue.put("value", jdySubRows);
                        jdyRow.put(subMapping.getSubFormWidgetId(), subTableValue);
                    }
                }
            }

            jdyPayload.add(jdyRow);
        }

        return jdyPayload;
    }

    private String buildMatchKey(Map<String, Object> row, QueryMatchConfig queryConfig) {
        if (queryConfig == null || queryConfig.getConditions() == null || queryConfig.getConditions().isEmpty()) {
            return UUID.randomUUID().toString();
        }

        StringBuilder keyBuilder = new StringBuilder();
        for (QueryCondition cond : queryConfig.getConditions()) {
            if (cond.getField() != null) {
                Object value = row.get(cond.getField());
                keyBuilder.append(cond.getField()).append("=");
                if (value != null) {
                    keyBuilder.append(value.toString());
                }
                keyBuilder.append("|");
            }
        }
        return keyBuilder.toString();
    }

    private String createJdyDataSingle(JdyAppConfig jdyConfig, FormMappingConfig formMapping,
                                        Map<String, Object> jdyRow, int maxRetry) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("app_id", jdyConfig.getAppId());
            body.put("entry_id", formMapping.getMainEntryId());
            List<Map<String, Object>> dataList = new ArrayList<>();
            dataList.add(jdyRow);
            body.put("data_list", dataList);
            body.put("is_start_workflow", jdyConfig.isStartWorkflow());

            String jsonBody = mapper.writeValueAsString(body);
            logger.info("新建请求体: " + jsonBody.substring(0, Math.min(jsonBody.length(), 500)));

            for (int attempt = 1; attempt <= maxRetry; attempt++) {
                try {
                    Request request = buildJdyRequest(CREATE_URL, jdyConfig, jsonBody);
                    String response = executeRequest(request);

                    Map<String, Object> resp = mapper.readValue(response, Map.class);

                    if ("success".equals(resp.get("status"))) {
                        Object successIdsObj = resp.get("success_ids");
                        if (successIdsObj instanceof List) {
                            List<String> successIds = (List<String>) successIdsObj;
                            if (!successIds.isEmpty()) {
                                return successIds.get(0);
                            }
                        }

                        Object dataObj = resp.get("data");
                        if (dataObj instanceof List) {
                            List<Map<String, Object>> dataList2 = (List<Map<String, Object>>) dataObj;
                            if (!dataList2.isEmpty()) {
                                String newId = (String) dataList2.get(0).get("_id");
                                if (newId != null) {
                                    return newId;
                                }
                            }
                        }

                        Object dataIdObj = resp.get("data_id");
                        if (dataIdObj instanceof String) {
                            return (String) dataIdObj;
                        }

                        logger.info("新建成功但响应中未找到dataId，status=" + resp.get("status") + ", success_count=" + resp.get("success_count"));
                        return null;
                    }

                    if (attempt < maxRetry) {
                        logger.warning("新建失败，第 " + attempt + " 次重试...");
                        Thread.sleep(2000);
                    }
                } catch (IOException e) {
                    logger.warning("新建异常 (第 " + attempt + " 次): " + e.getMessage());
                    if (attempt < maxRetry) {
                        Thread.sleep(2000);
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("新建简道云数据失败: " + e.getMessage());
        }
        return null;
    }

    private boolean createJdyDataBatch(JdyAppConfig jdyConfig, FormMappingConfig formMapping,
                                        List<Map<String, Object>> jdyPayload, int maxRetry) {
        if (jdyPayload == null || jdyPayload.isEmpty()) {
            return true;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("app_id", jdyConfig.getAppId());
            body.put("entry_id", formMapping.getMainEntryId());
            body.put("data_list", jdyPayload);
            body.put("is_start_workflow", jdyConfig.isStartWorkflow());

            String jsonBody = mapper.writeValueAsString(body);

            for (int attempt = 1; attempt <= maxRetry; attempt++) {
                try {
                    Request request = buildJdyRequest(CREATE_URL, jdyConfig, jsonBody);
                    String response = executeRequest(request);

                    if (isResponseSuccess(response)) {
                        return true;
                    }

                    if (attempt < maxRetry) {
                        logger.warning("批量新建失败，第 " + attempt + " 次重试...");
                        Thread.sleep(2000);
                    }
                } catch (IOException e) {
                    logger.warning("批量新建异常 (第 " + attempt + " 次): " + e.getMessage());
                    if (attempt < maxRetry) {
                        Thread.sleep(2000);
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("批量新建简道云数据失败: " + e.getMessage());
        }

        return false;
    }

    private boolean updateJdyData(JdyAppConfig jdyConfig, FormMappingConfig formMapping,
                                   String dataId, Map<String, Object> jdyRow, int maxRetry) {
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("app_id", jdyConfig.getAppId());
                body.put("entry_id", formMapping.getMainEntryId());
                body.put("data_id", dataId);
                body.put("data", jdyRow);
                body.put("is_start_workflow", jdyConfig.isStartWorkflow());

                String jsonBody = mapper.writeValueAsString(body);
                logger.info("更新请求体: " + jsonBody.substring(0, Math.min(jsonBody.length(), 500)));

                Request request = buildJdyRequest(UPDATE_URL, jdyConfig, jsonBody);
                String response = executeRequest(request);

                if (isResponseSuccess(response)) {
                    return true;
                }

                if (attempt < maxRetry) {
                    logger.warning("更新失败，第 " + attempt + " 次重试... dataId=" + dataId);
                    Thread.sleep(2000);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            } catch (IOException e) {
                logger.warning("更新异常 (第 " + attempt + " 次): " + e.getMessage());
                try {
                    if (attempt < maxRetry) {
                        Thread.sleep(2000);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private List<Map<String, Object>> queryJdyData(JdyAppConfig jdyConfig, FormMappingConfig formMapping,
                                                     Map<String, Object> row, QueryMatchConfig queryConfig) throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();

        if (queryConfig == null || queryConfig.getConditions() == null || queryConfig.getConditions().isEmpty()) {
            return results;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app_id", jdyConfig.getAppId());
        body.put("entry_id", formMapping.getMainEntryId());
        body.put("limit", 100);

        List<String> fields = new ArrayList<>();
        if (formMapping.getMainFieldMapping() != null) {
            fields.addAll(formMapping.getMainFieldMapping().values());
        }
        body.put("fields", fields);

        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("rel", queryConfig.getRelation() != null ? queryConfig.getRelation() : "and");

        List<Map<String, Object>> conditions = new ArrayList<>();
        for (QueryCondition cond : queryConfig.getConditions()) {
            if (cond.getWidgetId() == null || cond.getWidgetId().trim().isEmpty()) {
                continue;
            }

            Map<String, Object> condMap = new LinkedHashMap<>();
            condMap.put("field", cond.getWidgetId());
            condMap.put("type", cond.getType() != null ? cond.getType() : "text");
            condMap.put("method", cond.getMethod() != null ? cond.getMethod() : "eq");

            Object fieldValue = row.get(cond.getField());
            if ("empty".equals(cond.getMethod()) || "not_empty".equals(cond.getMethod())) {
                // no value needed
            } else if ("verified".equals(cond.getMethod()) || "unverified".equals(cond.getMethod())) {
                // no value needed
            } else {
                List<Object> valueList = new ArrayList<>();
                if (fieldValue != null) {
                    valueList.add(fieldValue.toString());
                }
                condMap.put("value", valueList);
            }

            conditions.add(condMap);
        }

        filter.put("cond", conditions);
        body.put("filter", filter);

        String jsonBody = mapper.writeValueAsString(body);
        logger.fine("查询简道云数据: " + jsonBody);

        try {
            Request request = buildJdyRequest(QUERY_URL, jdyConfig, jsonBody);
            String response = executeRequest(request);

            Map<String, Object> resp = mapper.readValue(response, Map.class);
            if ("success".equals(resp.get("status")) || resp.containsKey("data")) {
                Object dataObj = resp.get("data");
                if (dataObj instanceof List) {
                    results = (List<Map<String, Object>>) dataObj;
                }
            }
        } catch (IOException e) {
            logger.warning("查询简道云数据失败: " + e.getMessage());
        }

        return results;
    }

    private Map<String, List<Map<String, Object>>> batchQuerySubTable(
            Connection conn, String subTableName,
            List<SubTableJoinCondition> joinConditions,
            List<Map<String, Object>> batchData) throws Exception {

        validateSqlIdentifier(subTableName, "子表名");
        for (SubTableJoinCondition cond : joinConditions) {
            validateSqlIdentifier(cond.getSubTableField(), "子表关联字段");
        }

        Set<Object> uniqueValues = new LinkedHashSet<>();
        String firstSubField = joinConditions.get(0).getSubTableField();
        String firstMainField = joinConditions.get(0).getMainTableField();
        for (Map<String, Object> row : batchData) {
            Object val = row.get(firstMainField);
            if (val != null) {
                uniqueValues.add(val);
            }
        }

        if (uniqueValues.isEmpty()) {
            return new LinkedHashMap<>();
        }

        StringBuilder inClause = new StringBuilder();
        List<Object> params = new ArrayList<>();
        int idx = 0;
        for (Object val : uniqueValues) {
            if (idx > 0) inClause.append(", ");
            inClause.append("?");
            params.add(val);
            idx++;
        }

        StringBuilder whereClause = new StringBuilder();
        whereClause.append("`").append(firstSubField).append("` IN (").append(inClause).append(")");

        for (int i = 1; i < joinConditions.size(); i++) {
            SubTableJoinCondition cond = joinConditions.get(i);
            whereClause.append(" AND `").append(cond.getSubTableField()).append("` IS NOT NULL");
        }

        String sql = "SELECT * FROM `" + subTableName + "` WHERE " + whereClause;
        logger.info("子表批量查询SQL: " + sql + ", 参数数量: " + params.size());

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= columnCount; c++) {
                        row.put(metaData.getColumnName(c), rs.getObject(c));
                    }

                    String key = buildJoinKeyFromSubRow(row, joinConditions);
                    grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
                }
            }
        }

        return grouped;
    }

    private String buildJoinKey(Map<String, Object> mainRow, List<SubTableJoinCondition> joinConditions) {
        StringBuilder key = new StringBuilder();
        for (SubTableJoinCondition cond : joinConditions) {
            Object val = mainRow.get(cond.getMainTableField());
            key.append(val != null ? val.toString() : "null").append("|");
        }
        return key.toString();
    }

    private String buildJoinKeyFromSubRow(Map<String, Object> subRow, List<SubTableJoinCondition> joinConditions) {
        StringBuilder key = new StringBuilder();
        for (SubTableJoinCondition cond : joinConditions) {
            Object val = subRow.get(cond.getSubTableField());
            key.append(val != null ? val.toString() : "null").append("|");
        }
        return key.toString();
    }

    private Map<String, Object> wrapValue(Object value) {
        if (value == null) {
            return Collections.singletonMap("value", "");
        }

        if (value instanceof LocalDate) {
            return Collections.singletonMap("value", ((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        if (value instanceof LocalDateTime) {
            return Collections.singletonMap("value", ((LocalDateTime) value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        if (value instanceof java.sql.Date) {
            return Collections.singletonMap("value", value.toString());
        }

        if (value instanceof java.sql.Timestamp) {
            java.sql.Timestamp ts = (java.sql.Timestamp) value;
            return Collections.singletonMap("value", ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        if (value instanceof java.util.Date) {
            return Collections.singletonMap("value", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value));
        }

        return Collections.singletonMap("value", value.toString().trim());
    }

    private boolean isResponseSuccess(String response) {
        try {
            Map<String, Object> resp = mapper.readValue(response, Map.class);
            if ("success".equals(resp.get("status"))) return true;
            if (resp.containsKey("code") && Integer.valueOf(0).equals(resp.get("code"))) return true;
            if (resp.containsKey("data") && resp.get("data") != null) return true;
            if (resp.containsKey("data_id") || resp.containsKey("dataId")) return true;
            if (resp.containsKey("success_ids")) return true;
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private long parseLongSafe(String str) {
        try {
            return Long.parseLong(str);
        } catch (Exception e) {
            return 0;
        }
    }

    public static class SyncResult {
        private final boolean success;
        private final String message;

        public SyncResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
