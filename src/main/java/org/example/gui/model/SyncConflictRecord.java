package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncConflictRecord {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RESOLVED = "resolved";
    public static final String STATUS_IGNORED = "ignored";

    public static final String SOURCE_LOCAL = "local";
    public static final String SOURCE_JDY = "jdy";

    private String id;
    private String taskId;
    private String mappingId;
    private String recordKey;
    private String source;
    private String status;
    private String resolution;
    private String resolvedValue;
    private String localValue;
    private String jdyValue;
    private String conflictField;
    private String conflictReason;
    private String createdAt;
    private String resolvedAt;

    public SyncConflictRecord() {
        this.status = STATUS_PENDING;
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getMappingId() { return mappingId; }
    public void setMappingId(String mappingId) { this.mappingId = mappingId; }

    public String getRecordKey() { return recordKey; }
    public void setRecordKey(String recordKey) { this.recordKey = recordKey; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public String getResolvedValue() { return resolvedValue; }
    public void setResolvedValue(String resolvedValue) { this.resolvedValue = resolvedValue; }

    public String getLocalValue() { return localValue; }
    public void setLocalValue(String localValue) { this.localValue = localValue; }

    public String getJdyValue() { return jdyValue; }
    public void setJdyValue(String jdyValue) { this.jdyValue = jdyValue; }

    public String getConflictField() { return conflictField; }
    public void setConflictField(String conflictField) { this.conflictField = conflictField; }

    public String getConflictReason() { return conflictReason; }
    public void setConflictReason(String conflictReason) { this.conflictReason = conflictReason; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }
}
