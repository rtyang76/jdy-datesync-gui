package org.example.service;

import org.example.util.LogUtil;

import java.util.*;

/**
 * 数据处理服务
 * 负责数据去重、分组等处理逻辑
 */
public class DataProcessingService {
    private static DataProcessingService instance;

    private DataProcessingService() {
    }

    public static synchronized DataProcessingService getInstance() {
        if (instance == null) {
            instance = new DataProcessingService();
        }
        return instance;
    }

    /**
     * 检测并移除重复订单数据
     * 
     * @param records 原始记录列表
     * @return 去重后的记录列表
     */
    public List<Map<String, Object>> removeDuplicateOrderRecords(List<Map<String, Object>> records) {
        if (records.isEmpty()) {
            return records;
        }

        List<Map<String, Object>> uniqueRecords = new ArrayList<>();
        Map<String, List<Map<String, Object>>> contentGroups = new HashMap<>();

        // 按照内容进行分组
        for (Map<String, Object> record : records) {
            String contentFingerprint = generateOrderContentFingerprint(record);
            contentGroups.computeIfAbsent(contentFingerprint, k -> new ArrayList<>()).add(record);
        }

        // 统计重复记录数
        int duplicateCount = 0;
        int totalGroups = 0;

        // 从每组中选择id最大的记录
        for (List<Map<String, Object>> group : contentGroups.values()) {
            totalGroups++;
            if (group.size() > 1) {
                duplicateCount += (group.size() - 1);
                Map<String, Object> maxIdRecord = Collections.max(group,
                        Comparator.comparing(r -> ((Integer) r.get("id"))));
                uniqueRecords.add(maxIdRecord);

                logDuplicateGroup(group, maxIdRecord);
            } else {
                uniqueRecords.add(group.get(0));
            }
        }

        if (duplicateCount > 0) {
            // 不再输出重复记录统计信息
        }

        return uniqueRecords;
    }

    /**
     * 检测并移除重复物料数据
     * 
     * @param records 原始记录列表
     * @return 去重后的记录列表
     */
    public List<Map<String, Object>> removeDuplicateItemRecords(List<Map<String, Object>> records) {
        if (records.isEmpty()) {
            return records;
        }

        List<Map<String, Object>> uniqueRecords = new ArrayList<>();
        Map<String, List<Map<String, Object>>> contentGroups = new HashMap<>();

        // 按照内容进行分组
        for (Map<String, Object> record : records) {
            String contentFingerprint = generateItemContentFingerprint(record);
            contentGroups.computeIfAbsent(contentFingerprint, k -> new ArrayList<>()).add(record);
        }

        // 统计重复记录数
        int duplicateCount = 0;
        int totalGroups = 0;

        // 从每组中选择id最大的记录
        for (List<Map<String, Object>> group : contentGroups.values()) {
            totalGroups++;
            if (group.size() > 1) {
                duplicateCount += (group.size() - 1);
                Map<String, Object> maxIdRecord = Collections.max(group,
                        Comparator.comparing(r -> ((Integer) r.get("id"))));
                uniqueRecords.add(maxIdRecord);
            } else {
                uniqueRecords.add(group.get(0));
            }
        }

        if (duplicateCount > 0) {
            // 不再输出重复记录统计信息
        }

        return uniqueRecords;
    }

    /**
     * 生成订单记录内容的指纹
     * 
     * @param record 记录
     * @return 内容指纹
     */
    private String generateOrderContentFingerprint(Map<String, Object> record) {
        StringBuilder fingerprint = new StringBuilder();
        List<String> fieldNames = new ArrayList<>(record.keySet());
        Collections.sort(fieldNames);

        for (String field : fieldNames) {
            // 排除特定字段
            if (!field.equals("sid") && !field.equals("sync_batch") && !field.equals("id")
                    && !field.equals("job_version") && !field.startsWith("_widget_")) {
                Object value = record.get(field);
                String strValue = value == null ? "" : value.toString().trim();
                fingerprint.append(field).append("=").append(strValue).append("|");
            }
        }

        return fingerprint.toString();
    }

    /**
     * 生成物料记录内容的指纹
     * 
     * @param record 记录
     * @return 内容指纹
     */
    private String generateItemContentFingerprint(Map<String, Object> record) {
        StringBuilder fingerprint = new StringBuilder();
        List<String> fieldNames = new ArrayList<>(record.keySet());
        Collections.sort(fieldNames);

        for (String field : fieldNames) {
            // 排除特定字段
            if (!field.equals("sid") && !field.equals("sync_batch") && !field.equals("id")
                    && !field.equals("job_version")) {
                Object value = record.get(field);
                String strValue = value == null ? "" : value.toString().trim();
                fingerprint.append(field).append("=").append(strValue).append("|");
            }
        }

        return fingerprint.toString();
    }

    /**
     * 记录重复组的详细信息
     * 
     * @param group       重复记录组
     * @param maxIdRecord 保留的记录
     */
    private void logDuplicateGroup(List<Map<String, Object>> group, Map<String, Object> maxIdRecord) {
        // 不再输出重复记录组的详细信息日志
        // 只保留去重逻辑，不输出详细日志
    }

    /**
     * 按工单号分组处理相同工单号的记录
     * 
     * @param records 记录列表
     * @return 处理结果，包含新建记录和延迟更新记录
     */
    public Map<String, List<Map<String, Object>>> groupByJobNumber(List<Map<String, Object>> records) {
        Map<String, List<Map<String, Object>>> jobNumberGroups = new HashMap<>();
        List<Map<String, Object>> resultRecords = new ArrayList<>();
        List<Map<String, Object>> delayedUpdateRecords = new ArrayList<>();

        // 按job_number对数据进行分组
        for (Map<String, Object> record : records) {
            String jobNumber = (String) record.get("job_num");
            if (jobNumber != null && !jobNumber.trim().isEmpty()) {
                jobNumberGroups.computeIfAbsent(jobNumber, k -> new ArrayList<>()).add(record);
            } else {
                // 无工单号的记录直接加入结果
                resultRecords.add(record);
            }
        }

        // 处理相同job_number的记录组
        for (Map.Entry<String, List<Map<String, Object>>> entry : jobNumberGroups.entrySet()) {
            String jobNumber = entry.getKey();
            List<Map<String, Object>> group = entry.getValue();

            if (group.size() > 1) {
                // 按ID排序，保留最小ID的记录作为新建，其余作为延迟更新
                group.sort(Comparator.comparing(r -> ((Integer) r.get("id"))));

                // 第一条记录（ID最小）作为新建
                resultRecords.add(group.get(0));

                // 其余记录标记为延迟更新
                for (int i = 1; i < group.size(); i++) {
                    Map<String, Object> record = group.get(i);
                    record.put("_delayed_update", true);
                    delayedUpdateRecords.add(record);
                }

                LogUtil.logInfo(String.format("工单号 %s 有 %d 条记录，保留ID最小的作为新建，其余 %d 条作为延迟更新",
                        jobNumber, group.size(), group.size() - 1));
            } else {
                // 只有一条记录，直接加入结果
                resultRecords.add(group.get(0));
            }
        }

        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        result.put("newRecords", resultRecords);
        result.put("delayedUpdateRecords", delayedUpdateRecords);

        return result;
    }
}