package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncTaskConfig {
    private String id;
    private String name;
    private List<String> formMappingIds;
    private int syncIntervalMinutes;
    private boolean enabled;
    private int maxBatchSize;
    private int maxRetry;

    public SyncTaskConfig() {
        this.formMappingIds = new ArrayList<>();
        this.syncIntervalMinutes = 5;
        this.enabled = true;
        this.maxBatchSize = 50;
        this.maxRetry = 3;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getFormMappingIds() { return formMappingIds != null ? formMappingIds : new ArrayList<>(); }
    public void setFormMappingIds(List<String> formMappingIds) { this.formMappingIds = formMappingIds != null ? formMappingIds : new ArrayList<>(); }

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
