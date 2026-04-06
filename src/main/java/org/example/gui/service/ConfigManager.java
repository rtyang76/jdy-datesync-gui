package org.example.gui.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.gui.model.DataSourceConfig;
import org.example.gui.model.JdyAppConfig;
import org.example.gui.model.SyncProgress;
import org.example.gui.model.SyncTaskConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private static final String CONFIG_DIR = ".jdy-datesync";
    private static final String DATA_SOURCES_FILE = "data_sources.json";
    private static final String JDY_APPS_FILE = "jdy_apps.json";
    private static final String SYNC_TASKS_FILE = "sync_tasks.json";
    private static final String SYNC_PROGRESS_FILE = "sync_progress.json";

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
}
