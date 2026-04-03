package org.example.service.impl;

import org.example.service.DataValidationService;
import org.example.util.LogUtil;
import java.util.*;

/**
 * 数据验证服务实现类
 * 负责数据验证和去重逻辑的具体实现
 */
public class DataValidationServiceImpl implements DataValidationService {

    private static DataValidationServiceImpl instance;

    private DataValidationServiceImpl() {
    }

    public static synchronized DataValidationServiceImpl getInstance() {
        if (instance == null) {
            instance = new DataValidationServiceImpl();
        }
        return instance;
    }

    @Override
    public List<Map<String, Object>> removeDuplicateRecords(List<Map<String, Object>> records) {
        if (records.isEmpty()) {
            return records;
        }

        // 用于存储去重后的记录
        List<Map<String, Object>> uniqueRecords = new ArrayList<>();
        Map<String, List<Map<String, Object>>> contentGroups = new HashMap<>();

        // 按照内容进行分组
        for (Map<String, Object> record : records) {
            String contentFingerprint = generateContentFingerprint(record);
            contentGroups.computeIfAbsent(contentFingerprint, k -> new ArrayList<>()).add(record);
        }

        // 统计重复记录数
        int duplicateCount = 0;

        // 从每组中选择id最大的记录
        for (List<Map<String, Object>> group : contentGroups.values()) {
            if (group.size() > 1) {
                duplicateCount += (group.size() - 1);
                // 找出id最大的记录
                Map<String, Object> maxIdRecord = Collections.max(group,
                        Comparator.comparing(r -> ((Integer) r.get("id"))));
                uniqueRecords.add(maxIdRecord);

                // 记录重复组的详细信息
                logDuplicateGroup(group, maxIdRecord);
            } else {
                uniqueRecords.add(group.get(0));
            }
        }

        // 如果发现重复数据，记录日志
        if (duplicateCount > 0) {
            // 不再输出重复数据统计信息
        }

        return uniqueRecords;
    }

    @Override
    public List<Map<String, Object>> removeDuplicateItemRecords(List<Map<String, Object>> records) {
        if (records.isEmpty()) {
            return records;
        }

        List<Map<String, Object>> uniqueRecords = new ArrayList<>();
        Map<String, List<Map<String, Object>>> contentGroups = new HashMap<>();

        // 按照内容指纹进行分组（包含item_classification等所有字段）
        for (Map<String, Object> record : records) {
            String contentFingerprint = generateItemContentFingerprint(record);
            contentGroups.computeIfAbsent(contentFingerprint, k -> new ArrayList<>()).add(record);
        }

        int duplicateCount = 0;
        int duplicateGroups = 0;

        // 从每组中选择id最大的记录
        for (Map.Entry<String, List<Map<String, Object>>> entry : contentGroups.entrySet()) {
            List<Map<String, Object>> group = entry.getValue();
            if (group.size() > 1) {
                duplicateGroups++;
                duplicateCount += (group.size() - 1);

                // 选择ID最大的记录（最新的记录）
                Map<String, Object> maxIdRecord = Collections.max(group,
                        Comparator.comparing(r -> ((Integer) r.get("id"))));
                uniqueRecords.add(maxIdRecord);

                // 记录重复信息
                String contentFingerprint = entry.getKey();
                // 物料去重信息，不输出详细日志
            } else {
                uniqueRecords.add(group.get(0));
            }
        }

        if (duplicateCount > 0) {
            // 物料数据去重完成，不输出详细统计信息
        }

        return uniqueRecords;
    }

    @Override
    public String generateContentFingerprint(Map<String, Object> record) {
        StringBuilder fingerprint = new StringBuilder();

        // 按字段名排序，确保相同内容的记录生成相同的指纹
        List<String> fieldNames = new ArrayList<>(record.keySet());
        Collections.sort(fieldNames);

        for (String field : fieldNames) {
            // 排除sid、sync_batch、job_version和id字段，以及新增的widget字段
            if (!field.equals("sid") && !field.equals("sync_batch") &&
                    !field.equals("job_version") && !field.equals("id") &&
                    !field.startsWith("_widget_")) {
                Object value = record.get(field);
                String strValue = value == null ? "" : value.toString().trim();
                fingerprint.append(field).append("=").append(strValue).append("|");
            }
        }

        return fingerprint.toString();
    }

    @Override
    public String generateItemContentFingerprint(Map<String, Object> record) {
        StringBuilder fingerprint = new StringBuilder();

        List<String> fieldNames = new ArrayList<>(record.keySet());
        Collections.sort(fieldNames);

        for (String field : fieldNames) {
            // 排除sid、sync_batch、job_version和id字段
            if (!field.equals("sid") && !field.equals("sync_batch") &&
                    !field.equals("job_version") && !field.equals("id")) {
                Object value = record.get(field);
                String strValue = value == null ? "" : value.toString().trim();
                fingerprint.append(field).append("=").append(strValue).append("|");
            }
        }

        return fingerprint.toString();
    }

    @Override
    public boolean isValidRecord(Map<String, Object> record) {
        // 检查必要字段
        String jobNumber = (String) record.get("job_num");
        return jobNumber != null && !jobNumber.trim().isEmpty();
    }

    @Override
    public boolean isValidItemRecord(Map<String, Object> record) {
        // 检查必要字段
        String jobNum = (String) record.get("job_num");
        String itemNumber = (String) record.get("item_number");
        return jobNum != null && !jobNum.trim().isEmpty() &&
                itemNumber != null && !itemNumber.trim().isEmpty();
    }

    /**
     * 记录重复组的详细信息
     */
    private void logDuplicateGroup(List<Map<String, Object>> group, Map<String, Object> maxIdRecord) {
        // 不再输出重复记录组的详细信息日志
        // 只保留去重逻辑，不输出详细日志
    }
}