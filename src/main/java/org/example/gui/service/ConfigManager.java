package org.example.gui.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.gui.model.DataSourceConfig;
import org.example.gui.model.JdyAppConfig;
import org.example.gui.model.SyncConflictRecord;
import org.example.gui.model.SyncProgress;
import org.example.gui.model.SyncStatus;
import org.example.gui.model.SyncTaskConfig;

import org.example.gui.model.FormMappingConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public class ConfigManager {

    private static final Logger logger = Logger.getLogger(ConfigManager.class.getName());
    private static final String CONFIG_DIR = ".jdy-datesync";
    private static final String DATA_SOURCES_FILE = "data_sources.json";
    private static final String JDY_APPS_FILE = "jdy_apps.json";
    private static final String SYNC_TASKS_FILE = "sync_tasks.json";
    private static final String SYNC_PROGRESS_FILE = "sync_progress.json";
    private static final String FORM_MAPPINGS_FILE = "form_mappings.json";
    private static final String SYNC_CONFLICTS_FILE = "sync_conflicts.json";
    private static final String SYNC_STATUS_FILE = "sync_status.json";

    private final Path configDir;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ConfigManager() {
        this.configDir = Paths.get(System.getProperty("user.home"), CONFIG_DIR);
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建配置目录: " + configDir, e);
        }
    }

    public List<DataSourceConfig> loadDataSources() {
        return loadConfig(DATA_SOURCES_FILE, DataSourceConfig[].class);
    }

    public void saveDataSources(List<DataSourceConfig> configs) {
        saveConfig(DATA_SOURCES_FILE, configs, "数据源配置");
    }

    public List<JdyAppConfig> loadJdyApps() {
        return loadConfig(JDY_APPS_FILE, JdyAppConfig[].class);
    }

    public void saveJdyApps(List<JdyAppConfig> configs) {
        saveConfig(JDY_APPS_FILE, configs, "简道云应用配置");
    }

    public JdyAppConfig findJdyAppById(String id) {
        if (id == null) return null;
        return loadJdyApps().stream()
                .filter(a -> id.equals(a.getId()))
                .findFirst().orElse(null);
    }

    public List<SyncTaskConfig> loadSyncTasks() {
        return loadConfig(SYNC_TASKS_FILE, SyncTaskConfig[].class);
    }

    public void saveSyncTasks(List<SyncTaskConfig> configs) {
        saveConfig(SYNC_TASKS_FILE, configs, "同步任务配置");
    }

    public SyncProgress loadSyncProgress() {
        lock.readLock().lock();
        try {
            File file = configDir.resolve(SYNC_PROGRESS_FILE).toFile();
            if (!file.exists()) return new SyncProgress();
            try {
                return mapper.readValue(file, SyncProgress.class);
            } catch (IOException e) {
                logger.warning("加载同步进度失败: " + e.getMessage());
                return new SyncProgress();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveSyncProgress(SyncProgress progress) {
        lock.writeLock().lock();
        try {
            File file = configDir.resolve(SYNC_PROGRESS_FILE).toFile();
            try {
                mapper.writeValue(file, progress);
            } catch (IOException e) {
                throw new RuntimeException("保存同步进度失败", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Path getConfigDir() {
        return configDir;
    }

    public List<FormMappingConfig> loadFormMappings() {
        return loadConfig(FORM_MAPPINGS_FILE, FormMappingConfig[].class);
    }

    public void saveFormMappings(List<FormMappingConfig> configs) {
        if (configs == null) configs = new ArrayList<>();
        saveConfig(FORM_MAPPINGS_FILE, configs, "表单映射配置");
    }

    public FormMappingConfig findFormMappingById(String id) {
        if (id == null) return null;
        return loadFormMappings().stream()
                .filter(f -> id.equals(f.getId()))
                .findFirst().orElse(null);
    }

    public List<SyncConflictRecord> loadSyncConflicts() {
        return loadConfig(SYNC_CONFLICTS_FILE, SyncConflictRecord[].class);
    }

    public void saveSyncConflicts(List<SyncConflictRecord> conflicts) {
        saveConfig(SYNC_CONFLICTS_FILE, conflicts, "同步冲突记录");
    }

    public void addSyncConflict(SyncConflictRecord conflict) {
        List<SyncConflictRecord> conflicts = new ArrayList<>(loadSyncConflicts());
        conflicts.add(conflict);
        saveSyncConflicts(conflicts);
    }

    public List<SyncStatus> loadSyncStatus() {
        return loadConfig(SYNC_STATUS_FILE, SyncStatus[].class);
    }

    public void saveSyncStatus(List<SyncStatus> statuses) {
        saveConfig(SYNC_STATUS_FILE, statuses, "同步状态");
    }

    public void updateSyncStatus(SyncStatus status) {
        List<SyncStatus> statuses = new ArrayList<>(loadSyncStatus());
        statuses.removeIf(s -> s.getTaskId() != null && s.getTaskId().equals(status.getTaskId()));
        statuses.add(status);
        saveSyncStatus(statuses);
    }

    public SyncStatus findSyncStatusByTaskId(String taskId) {
        return loadSyncStatus().stream()
                .filter(s -> s.getTaskId() != null && s.getTaskId().equals(taskId))
                .findFirst().orElse(null);
    }

    private <T> List<T> loadConfig(String fileName, Class<T[]> arrayClass) {
        lock.readLock().lock();
        try {
            File file = configDir.resolve(fileName).toFile();
            if (!file.exists()) return new ArrayList<>();
            try {
                T[] configs = mapper.readValue(file, arrayClass);
                return List.of(configs);
            } catch (IOException e) {
                logger.warning("加载配置失败 [" + fileName + "]: " + e.getMessage());
                return new ArrayList<>();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private <T> void saveConfig(String fileName, List<T> configs, String label) {
        lock.writeLock().lock();
        try {
            Path filePath = configDir.resolve(fileName);
            try {
                byte[] data = mapper.writeValueAsBytes(configs);
                Files.write(filePath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("保存" + label + "失败", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
