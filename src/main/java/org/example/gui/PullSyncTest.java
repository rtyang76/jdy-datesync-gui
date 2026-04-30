package org.example.gui;

import org.example.gui.model.*;
import org.example.gui.service.ConfigManager;
import org.example.gui.service.SyncEngine;

import java.util.*;

public class PullSyncTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  简道云双向同步测试");
        System.out.println("========================================\n");

        ConfigManager configManager = new ConfigManager();
        SyncEngine syncEngine = new SyncEngine(configManager);

        List<DataSourceConfig> dataSources = configManager.loadDataSources();
        List<JdyAppConfig> jdyApps = configManager.loadJdyApps();
        List<FormMappingConfig> formMappings = configManager.loadFormMappings();
        List<SyncTaskConfig> syncTasks = configManager.loadSyncTasks();

        System.out.println("--- 已配置数据源 ---");
        for (DataSourceConfig ds : dataSources) {
            System.out.println("  [" + ds.getId() + "] " + ds.getName() + " (" + ds.getDbType() + ") " + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabase());
        }

        System.out.println("\n--- 已配置简道云应用 ---");
        for (JdyAppConfig app : jdyApps) {
            System.out.println("  [" + app.getId() + "] " + app.getName() + " (AppId: " + app.getAppId() + ")");
        }

        System.out.println("\n--- 已配置表单映射 ---");
        for (FormMappingConfig fm : formMappings) {
            System.out.println("  [" + fm.getId() + "] " + fm.getName());
            System.out.println("    主表: " + fm.getMainTableName() + ", EntryID: " + fm.getMainEntryId());
            System.out.println("    推送映射: " + fm.getMainFieldMapping().size() + " 个字段");
            System.out.println("    拉取映射: " + fm.getPullFieldMapping().size() + " 个字段");
            System.out.println("    拉取匹配字段: " + (fm.getPullMatchField() != null ? fm.getPullMatchField() : "未配置"));
        }

        System.out.println("\n--- 已配置同步任务 ---");
        for (SyncTaskConfig task : syncTasks) {
            System.out.println("  [" + task.getId() + "] " + task.getName() + " (方向: " + task.getSyncDirection() + ", 启用: " + task.isEnabled() + ")");
        }

        if (formMappings.isEmpty()) {
            System.out.println("\n❌ 没有找到任何表单映射配置，请先在 UI 中配置。");
            return;
        }

        FormMappingConfig testMapping = formMappings.get(0);
        System.out.println("\n--- 使用第一个表单映射进行测试 ---");
        System.out.println("  映射名称: " + testMapping.getName());
        System.out.println("  主表: " + testMapping.getMainTableName());

        if (testMapping.getPullFieldMapping().isEmpty()) {
            System.out.println("\n⚠️  拉取映射未配置，自动从推送映射生成...");
            Map<String, String> pullMapping = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : testMapping.getMainFieldMapping().entrySet()) {
                pullMapping.put(entry.getValue(), entry.getKey());
            }
            testMapping.setPullFieldMapping(pullMapping);
            System.out.println("  生成拉取映射: " + pullMapping.size() + " 个字段");

            List<FormMappingConfig> mappings = new ArrayList<>(configManager.loadFormMappings());
            for (int i = 0; i < mappings.size(); i++) {
                if (mappings.get(i).getId().equals(testMapping.getId())) {
                    mappings.set(i, testMapping);
                    break;
                }
            }
            configManager.saveFormMappings(mappings);
            System.out.println("  已自动保存拉取映射到配置文件");
        }

        if (testMapping.getPullMatchField() == null || testMapping.getPullMatchField().trim().isEmpty()) {
            String matchField = "id";
            testMapping.setPullMatchField(matchField);
            System.out.println("\n⚠️  拉取匹配字段未配置，默认使用: " + matchField);

            List<FormMappingConfig> mappings = new ArrayList<>(configManager.loadFormMappings());
            for (int i = 0; i < mappings.size(); i++) {
                if (mappings.get(i).getId().equals(testMapping.getId())) {
                    mappings.set(i, testMapping);
                    break;
                }
            }
            configManager.saveFormMappings(mappings);
            System.out.println("  已自动保存匹配字段到配置文件");
        }

        // 测试1: 拉取同步
        testPullSync(configManager, syncEngine, testMapping);

        // 测试2: 双向同步
        testBidirectionalSync(configManager, syncEngine, testMapping);

        // 测试3: 数据一致性校验
        testConsistencyCheck(configManager, syncEngine, testMapping);

        // 测试4: 查看同步状态
        testSyncStatus(configManager);

        System.out.println("\n========================================");
        System.out.println("  所有测试完成");
        System.out.println("========================================");
    }

    private static void testPullSync(ConfigManager configManager, SyncEngine syncEngine, FormMappingConfig testMapping) {
        System.out.println("\n========== 测试1: 拉取同步 ==========");

        SyncTaskConfig testTask = new SyncTaskConfig();
        testTask.setId("test-pull-" + System.currentTimeMillis());
        testTask.setName("拉取测试任务");
        testTask.setFormMappingIds(List.of(testMapping.getId()));
        testTask.setSyncDirection(SyncTaskConfig.DIRECTION_PULL);
        testTask.setMaxBatchSize(50);
        testTask.setMaxRetry(3);
        testTask.setEnabled(true);

        executeAndCleanup(configManager, syncEngine, testTask, "拉取");
    }

    private static void testBidirectionalSync(ConfigManager configManager, SyncEngine syncEngine, FormMappingConfig testMapping) {
        System.out.println("\n========== 测试2: 双向同步 ==========");

        SyncTaskConfig testTask = new SyncTaskConfig();
        testTask.setId("test-both-" + System.currentTimeMillis());
        testTask.setName("双向同步测试任务");
        testTask.setFormMappingIds(List.of(testMapping.getId()));
        testTask.setSyncDirection(SyncTaskConfig.DIRECTION_BOTH);
        testTask.setMaxBatchSize(50);
        testTask.setMaxRetry(3);
        testTask.setEnabled(true);

        executeAndCleanup(configManager, syncEngine, testTask, "双向");
    }

    private static void testConsistencyCheck(ConfigManager configManager, SyncEngine syncEngine, FormMappingConfig testMapping) {
        System.out.println("\n========== 测试3: 数据一致性校验 ==========");

        SyncTaskConfig testTask = new SyncTaskConfig();
        testTask.setId("test-check-" + System.currentTimeMillis());
        testTask.setName("一致性校验任务");
        testTask.setFormMappingIds(List.of(testMapping.getId()));
        testTask.setSyncDirection(SyncTaskConfig.DIRECTION_PULL);
        testTask.setEnabled(true);

        List<SyncTaskConfig> existingTasks = new ArrayList<>(configManager.loadSyncTasks());
        existingTasks.add(testTask);
        configManager.saveSyncTasks(existingTasks);

        try {
            SyncEngine.SyncResult result = syncEngine.verifyDataConsistency(testTask.getId());
            System.out.println("  校验结果: " + (result.isSuccess() ? "✅ 通过" : "⚠️  发现问题"));
            System.out.println("  消息: " + result.getMessage());
        } catch (Exception e) {
            System.out.println("  ❌ 校验异常: " + e.getMessage());
        }

        existingTasks = new ArrayList<>(configManager.loadSyncTasks());
        existingTasks.removeIf(t -> t.getId().equals(testTask.getId()));
        configManager.saveSyncTasks(existingTasks);
    }

    private static void testSyncStatus(ConfigManager configManager) {
        System.out.println("\n========== 测试4: 同步状态查询 ==========");

        List<SyncStatus> statuses = configManager.loadSyncStatus();
        if (statuses.isEmpty()) {
            System.out.println("  暂无同步状态记录");
        } else {
            for (SyncStatus status : statuses) {
                System.out.println("  任务: " + status.getTaskName());
                System.out.println("    状态: " + status.getState());
                System.out.println("    方向: " + status.getDirection());
                System.out.println("    开始: " + status.getStartTime());
                System.out.println("    结束: " + status.getEndTime());
                System.out.println("    消息: " + status.getMessage());
            }
        }

        List<SyncConflictRecord> conflicts = configManager.loadSyncConflicts();
        System.out.println("\n  冲突记录: " + conflicts.size() + " 条");
        for (SyncConflictRecord conflict : conflicts) {
            System.out.println("    [" + conflict.getStatus() + "] " + conflict.getConflictField()
                    + " 本地=" + conflict.getLocalValue() + " 简道云=" + conflict.getJdyValue());
        }
    }

    private static void executeAndCleanup(ConfigManager configManager, SyncEngine syncEngine,
                                          SyncTaskConfig testTask, String label) {
        List<SyncTaskConfig> existingTasks = new ArrayList<>(configManager.loadSyncTasks());
        existingTasks.add(testTask);
        configManager.saveSyncTasks(existingTasks);

        try {
            SyncEngine.SyncResult result = syncEngine.executeTask(testTask.getId());
            System.out.println("  结果: " + (result.isSuccess() ? "✅ 成功" : "❌ 失败"));
            System.out.println("  消息: " + result.getMessage());
        } catch (Exception e) {
            System.out.println("  ❌ " + label + "异常: " + e.getMessage());
            e.printStackTrace();
        }

        existingTasks = new ArrayList<>(configManager.loadSyncTasks());
        existingTasks.removeIf(t -> t.getId().equals(testTask.getId()));
        configManager.saveSyncTasks(existingTasks);
    }
}
