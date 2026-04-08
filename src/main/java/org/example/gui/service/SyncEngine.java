package org.example.gui.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.gui.model.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

public class SyncEngine {

    private static final Logger logger = Logger.getLogger(SyncEngine.class.getName());
    private static final String CREATE_URL = "https://api.jiandaoyun.com/api/v5/app/entry/data/batch_create";

    private final ConfigManager configManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public SyncEngine(ConfigManager configManager) {
        this.configManager = configManager;
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

        List<DataSourceConfig> dataSources = configManager.loadDataSources();

        FormMappingConfig formMapping;
        if (task.getFormMappingId() != null && !task.getFormMappingId().isEmpty()) {
            formMapping = configManager.findFormMappingById(task.getFormMappingId());
            if (formMapping == null) {
                return new SyncResult(false, "表单映射配置不存在: " + task.getFormMappingId());
            }
        } else if (task.hasLegacyFields()) {
            formMapping = buildLegacyFormMapping(task);
            if (formMapping == null) {
                return new SyncResult(false, "无法从旧任务配置构建表单映射");
            }
        } else {
            return new SyncResult(false, "任务未关联表单映射配置");
        }

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
        String lastSyncIdStr = progress.getLastSyncId(taskId);
        long lastSyncId = parseLongSafe(lastSyncIdStr);

        logger.info("开始同步任务: " + task.getName() + " | 主表: " + formMapping.getMainTableName() + " | 上次同步ID: " + lastSyncId);

        try {
            return syncData(ds, task, formMapping, activeMainMapping, lastSyncId, progress, jdyConfig);
        } catch (Exception e) {
            logger.severe("同步任务异常: " + e.getMessage());
            return new SyncResult(false, "同步异常: " + e.getMessage());
        }
    }

    private FormMappingConfig buildLegacyFormMapping(SyncTaskConfig task) {
        if (task.getSourceTable() == null || task.getEntryId() == null) return null;
        FormMappingConfig fm = new FormMappingConfig();
        fm.setId("_legacy_" + task.getId());
        fm.setDataSourceId(task.getDataSourceId());
        fm.setJdyAppId(task.getJdyAppId());
        fm.setMainTableName(task.getSourceTable());
        fm.setMainEntryId(task.getEntryId());
        fm.setMainFieldMapping(task.getFieldMapping() != null ? task.getFieldMapping() : new HashMap<>());
        fm.setSubTableMappings(new ArrayList<>());
        return fm;
    }

    private SyncResult syncData(DataSourceConfig ds, SyncTaskConfig task,
                                 FormMappingConfig formMapping,
                                 Map<String, String> activeMainMapping, long lastSyncId,
                                 SyncProgress progress, JdyAppConfig jdyConfig) throws Exception {

        String incrementField = task.getIncrementField() != null && !task.getIncrementField().trim().isEmpty()
                ? task.getIncrementField().trim() : "id";

        String url = ds.getJdbcUrl();
        int totalSynced = 0;
        long maxId = lastSyncId;
        boolean hadData = false;
        boolean pushFailed = false;

        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            while (true) {
                String sql = String.format(
                        "SELECT * FROM `%s` WHERE `%s` > %d ORDER BY `%s` ASC LIMIT %d",
                        formMapping.getMainTableName(), incrementField, lastSyncId, incrementField, task.getMaxBatchSize());

                logger.info("执行SQL: " + sql);

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    List<Map<String, Object>> batchData = new ArrayList<>();
                    List<Long> batchIds = new ArrayList<>();

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
                            long id = parseLongSafe(idVal.toString());
                            if (id > maxId) maxId = id;
                            batchIds.add(id);
                        }
                    }

                    if (batchData.isEmpty()) {
                        logger.info("无新数据需要同步");
                        break;
                    }

                    hadData = true;
                    logger.info("查询到 " + batchData.size() + " 条新数据");

                    boolean pushSuccess = pushToJdy(jdyConfig, formMapping, batchData, activeMainMapping, conn, task.getMaxRetry());

                    if (pushSuccess) {
                        totalSynced += batchData.size();
                        lastSyncId = maxId;
                        progress.setLastSyncId(task.getId(), String.valueOf(lastSyncId));
                        configManager.saveSyncProgress(progress);
                        logger.info("成功推送 " + batchData.size() + " 条数据，当前同步ID: " + lastSyncId);
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

        if (pushFailed) {
            return new SyncResult(false, "推送简道云失败，请检查配置和字段映射");
        } else if (hadData) {
            return new SyncResult(true, "同步完成，共推送 " + totalSynced + " 条数据");
        } else {
            return new SyncResult(true, "无新数据需要同步（上次同步ID: " + lastSyncId + "）");
        }
    }

    private boolean pushToJdy(JdyAppConfig jdyConfig, FormMappingConfig formMapping,
                               List<Map<String, Object>> batchData,
                               Map<String, String> activeMainMapping,
                               Connection conn, int maxRetry) throws Exception {

        List<Map<String, Object>> jdyPayload = new ArrayList<>();

        for (Map<String, Object> row : batchData) {
            Map<String, Object> jdyRow = new LinkedHashMap<>();

            for (Map.Entry<String, String> mapping : activeMainMapping.entrySet()) {
                String dbField = mapping.getKey();
                String widgetId = mapping.getValue();
                Object value = row.get(dbField);
                jdyRow.put(widgetId, wrapValue(value));
            }

            if (formMapping.getSubTableMappings() != null) {
                for (SubTableMapping subMapping : formMapping.getSubTableMappings()) {
                    if (subMapping.getSubTableName() == null || subMapping.getSubTableName().trim().isEmpty()) continue;
                    if (subMapping.getFieldMapping() == null || subMapping.getFieldMapping().isEmpty()) continue;

                    String joinField = subMapping.getJoinFieldName() != null && !subMapping.getJoinFieldName().trim().isEmpty()
                            ? subMapping.getJoinFieldName() : "main_id";

                    Object mainId = row.get("id");
                    if (mainId == null) continue;

                    List<Map<String, Object>> subRows = querySubTable(conn, subMapping.getSubTableName(), joinField, mainId.toString());

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
                        jdyRow.put(subMapping.getSubFormWidgetId(), jdySubRows);
                    }
                }
            }

            jdyPayload.add(jdyRow);
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("app_id", jdyConfig.getAppId());
            body.put("entry_id", formMapping.getMainEntryId());
            body.put("data_list", jdyPayload);
            body.put("is_start_workflow", jdyConfig.isStartWorkflow());

            String jsonBody = mapper.writeValueAsString(body);
            logger.fine("简道云请求体: " + jsonBody);

            for (int attempt = 1; attempt <= maxRetry; attempt++) {
                try {
                    URL apiUrl = new URL(CREATE_URL);
                    HttpURLConnection httpConn = (HttpURLConnection) apiUrl.openConnection();
                    httpConn.setRequestMethod("POST");
                    httpConn.setRequestProperty("Content-Type", "application/json");
                    String token = jdyConfig.getApiToken().trim();
                    if (!token.startsWith("Bearer ")) {
                        token = "Bearer " + token;
                    }
                    httpConn.setRequestProperty("Authorization", token);
                    httpConn.setRequestProperty("X-Request-ID", UUID.randomUUID().toString());
                    httpConn.setConnectTimeout(15000);
                    httpConn.setReadTimeout(30000);
                    httpConn.setDoOutput(true);

                    httpConn.getOutputStream().write(jsonBody.getBytes("UTF-8"));
                    httpConn.getOutputStream().flush();
                    httpConn.getOutputStream().close();

                    int responseCode = httpConn.getResponseCode();
                    InputStream is = responseCode == 200 ? httpConn.getInputStream() : httpConn.getErrorStream();
                    String response = readStream(is);

                    logger.info("简道云响应 [HTTP " + responseCode + "]: " + response);

                    if (responseCode == 200 && isResponseSuccess(response)) {
                        return true;
                    }

                    if (attempt < maxRetry) {
                        logger.warning("推送失败，第 " + attempt + " 次重试...");
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    logger.warning("推送异常 (第 " + attempt + " 次): " + e.getMessage());
                    if (attempt < maxRetry) {
                        Thread.sleep(2000);
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("推送简道云失败: " + e.getMessage());
        }

        return false;
    }

    private List<Map<String, Object>> querySubTable(Connection conn, String subTableName, String joinField, String mainId) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = String.format("SELECT * FROM `%s` WHERE `%s` = ?", subTableName, joinField);

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, mainId);
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(metaData.getColumnName(i), rs.getObject(i));
                    }
                    result.add(row);
                }
            }
        }

        return result;
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
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
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
