package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncProgress {
    private Map<String, String> taskProgress;
    private Map<String, String> pullProgress;

    public SyncProgress() {
        this.taskProgress = new ConcurrentHashMap<>();
        this.pullProgress = new ConcurrentHashMap<>();
    }

    public Map<String, String> getTaskProgress() { return taskProgress; }
    public void setTaskProgress(Map<String, String> taskProgress) {
        this.taskProgress = taskProgress != null ? new ConcurrentHashMap<>(taskProgress) : new ConcurrentHashMap<>();
    }

    public Map<String, String> getPullProgress() { return pullProgress != null ? pullProgress : new ConcurrentHashMap<>(); }
    public void setPullProgress(Map<String, String> pullProgress) {
        this.pullProgress = pullProgress != null ? new ConcurrentHashMap<>(pullProgress) : new ConcurrentHashMap<>();
    }

    public String getLastSyncId(String taskId) {
        return taskProgress.getOrDefault(taskId, "0");
    }

    public void setLastSyncId(String taskId, String lastSyncId) {
        taskProgress.put(taskId, lastSyncId);
    }

    public String getLastPullId(String mappingId) {
        return pullProgress.getOrDefault(mappingId, "");
    }

    public void setLastPullId(String mappingId, String lastPullId) {
        if (pullProgress == null) {
            pullProgress = new ConcurrentHashMap<>();
        }
        pullProgress.put(mappingId, lastPullId);
    }
}
