package org.example.gui.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.gui.model.DataSourceConfig;
import org.example.gui.model.JdyAppConfig;
import org.example.gui.model.SyncProgress;
import org.example.gui.model.SyncTaskConfig;

import org.example.gui.model.FormMappingConfig;
import org.example.gui.model.SubTableMapping;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ConfigManager {

    private static final String CONFIG_DIR = ".jdy-datesync";
    private static final String DATA_SOURCES_FILE = "data_sources.json";
    private static final String JDY_APPS_FILE = "jdy_apps.json";
    private static final String SYNC_TASKS_FILE = "sync_tasks.json";
    private static final String SYNC_PROGRESS_FILE = "sync_progress.json";
    private static final String FORM_MAPPINGS_FILE = "form_mappings.json";

    private final Path configDir;
    private final ObjectMapper mapper;

    public ConfigManager() {
        this.configDir = Paths.get(System.getProperty("user.home"), CONFIG_DIR);
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建配置目录: " + configDir, e);
        }
        migrateLegacyTasks();
    }

    public List<DataSourceConfig> loadDataSources() {
        File file = configDir.resolve(DATA_SOURCES_FILE).toFile();
        if (!file.exists()) return new ArrayList<>();
        try {
            DataSourceConfig[] configs = mapper.readValue(file, DataSourceConfig[].class);
            return List.of(configs);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void saveDataSources(List<DataSourceConfig> configs) {
        File file = configDir.resolve(DATA_SOURCES_FILE).toFile();
        try {
            mapper.writeValue(file, configs);
        } catch (IOException e) {
            throw new RuntimeException("保存数据源配置失败", e);
        }
    }

    public List<JdyAppConfig> loadJdyApps() {
        File file = configDir.resolve(JDY_APPS_FILE).toFile();
        if (!file.exists()) return new ArrayList<>();
        try {
            JdyAppConfig[] configs = mapper.readValue(file, JdyAppConfig[].class);
            return List.of(configs);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void saveJdyApps(List<JdyAppConfig> configs) {
        File file = configDir.resolve(JDY_APPS_FILE).toFile();
        try {
            mapper.writeValue(file, configs);
        } catch (IOException e) {
            throw new RuntimeException("保存简道云应用配置失败", e);
        }
    }

    public JdyAppConfig findJdyAppById(String id) {
        if (id == null) return null;
        return loadJdyApps().stream()
                .filter(a -> id.equals(a.getId()))
                .findFirst().orElse(null);
    }

    public List<SyncTaskConfig> loadSyncTasks() {
        File file = configDir.resolve(SYNC_TASKS_FILE).toFile();
        if (!file.exists()) return new ArrayList<>();
        try {
            SyncTaskConfig[] configs = mapper.readValue(file, SyncTaskConfig[].class);
            return List.of(configs);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void saveSyncTasks(List<SyncTaskConfig> configs) {
        File file = configDir.resolve(SYNC_TASKS_FILE).toFile();
        try {
            mapper.writeValue(file, configs);
        } catch (IOException e) {
            throw new RuntimeException("保存同步任务配置失败", e);
        }
    }

    public SyncProgress loadSyncProgress() {
        File file = configDir.resolve(SYNC_PROGRESS_FILE).toFile();
        if (!file.exists()) return new SyncProgress();
        try {
            return mapper.readValue(file, SyncProgress.class);
        } catch (IOException e) {
            return new SyncProgress();
        }
    }

    public void saveSyncProgress(SyncProgress progress) {
        File file = configDir.resolve(SYNC_PROGRESS_FILE).toFile();
        try {
            mapper.writeValue(file, progress);
        } catch (IOException e) {
            throw new RuntimeException("保存同步进度失败", e);
        }
    }

    public Path getConfigDir() {
        return configDir;
    }

    public List<FormMappingConfig> loadFormMappings() {
        File file = configDir.resolve(FORM_MAPPINGS_FILE).toFile();
        if (!file.exists()) return new ArrayList<>();
        try {
            FormMappingConfig[] configs = mapper.readValue(file, FormMappingConfig[].class);
            return List.of(configs);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void saveFormMappings(List<FormMappingConfig> configs) {
        File file = configDir.resolve(FORM_MAPPINGS_FILE).toFile();
        try {
            if (configs == null) configs = new ArrayList<>();
            mapper.writeValue(file, configs);
        } catch (Exception e) {
            throw new RuntimeException("保存表单映射配置失败: " + e.getMessage(), e);
        }
    }

    public FormMappingConfig findFormMappingById(String id) {
        if (id == null) return null;
        return loadFormMappings().stream()
                .filter(f -> id.equals(f.getId()))
                .findFirst().orElse(null);
    }

    private void migrateLegacyTasks() {
        List<SyncTaskConfig> tasks = loadSyncTasks();
        boolean hasLegacy = false;
        for (SyncTaskConfig task : tasks) {
            if (task.hasLegacyFields() && (task.getFormMappingId() == null || task.getFormMappingId().isEmpty())) {
                hasLegacy = true;
                FormMappingConfig mapping = new FormMappingConfig();
                mapping.setId(UUID.randomUUID().toString());
                mapping.setName(task.getName() + " - 主表映射");
                mapping.setDataSourceId(task.getDataSourceId());
                mapping.setJdyAppId(task.getJdyAppId());
                mapping.setMainTableName(task.getSourceTable());
                mapping.setMainEntryId(task.getEntryId());
                mapping.setMainFieldMapping(task.getFieldMapping() != null ? task.getFieldMapping() : new HashMap<>());
                saveFormMappings(List.of(mapping));

                task.setFormMappingId(mapping.getId());
                task.clearLegacyFields();
            }
        }
        if (hasLegacy) {
            saveSyncTasks(tasks);
        }
    }
}
