package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncTaskConfig {
    private String id;
    private String name;
    private String dataSourceId;
    private String sourceTable;
    private String jdyAppId;
    private String entryId;
    private Map<String, String> fieldMapping;
    private String incrementMode;
    private String incrementField;
    private int syncIntervalMinutes;
    private boolean enabled;
    private int maxBatchSize;
    private int maxRetry;

    public SyncTaskConfig() {
        this.syncIntervalMinutes = 5;
        this.enabled = true;
        this.maxBatchSize = 50;
        this.maxRetry = 3;
        this.incrementMode = "id";
        this.fieldMapping = new HashMap<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(String dataSourceId) { this.dataSourceId = dataSourceId; }

    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }

    public String getJdyAppId() { return jdyAppId; }
    public void setJdyAppId(String jdyAppId) { this.jdyAppId = jdyAppId; }

    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }

    public Map<String, String> getFieldMapping() { return fieldMapping; }
    public void setFieldMapping(Map<String, String> fieldMapping) { this.fieldMapping = fieldMapping; }

    public String getIncrementMode() { return incrementMode; }
    public void setIncrementMode(String incrementMode) { this.incrementMode = incrementMode; }

    public String getIncrementField() { return incrementField; }
    public void setIncrementField(String incrementField) { this.incrementField = incrementField; }

    public int getSyncIntervalMinutes() { return syncIntervalMinutes; }
    public void setSyncIntervalMinutes(int syncIntervalMinutes) { this.syncIntervalMinutes = syncIntervalMinutes; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxBatchSize() { return maxBatchSize; }
    public void setMaxBatchSize(int maxBatchSize) { this.maxBatchSize = maxBatchSize; }

    public int getMaxRetry() { return maxRetry; }
    public void setMaxRetry(int maxRetry) { this.maxRetry = maxRetry; }

    @Override
    public String toString() {
        return name != null ? name : "未命名任务";
    }
}
