package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncProgress {
    private Map<String, String> taskProgress;

    public SyncProgress() {
        this.taskProgress = new ConcurrentHashMap<>();
    }

    public Map<String, String> getTaskProgress() { return taskProgress; }
    public void setTaskProgress(Map<String, String> taskProgress) {
        this.taskProgress = taskProgress != null ? new ConcurrentHashMap<>(taskProgress) : new ConcurrentHashMap<>();
    }

    public String getLastSyncId(String taskId) {
        return taskProgress.getOrDefault(taskId, "0");
    }

    public void setLastSyncId(String taskId, String lastSyncId) {
        taskProgress.put(taskId, lastSyncId);
    }
}
