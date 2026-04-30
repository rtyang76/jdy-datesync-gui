package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncStatus {

    public static final String STATE_IDLE = "idle";
    public static final String STATE_RUNNING = "running";
    public static final String STATE_FAILED = "failed";
    public static final String STATE_SUCCESS = "success";

    private String taskId;
    private String taskName;
    private String state;
    private String direction;
    private String startTime;
    private String endTime;
    private int totalRecords;
    private int successRecords;
    private int failedRecords;
    private int conflictRecords;
    private String message;
    private String errorDetail;

    public SyncStatus() {
        this.state = STATE_IDLE;
        this.totalRecords = 0;
        this.successRecords = 0;
        this.failedRecords = 0;
        this.conflictRecords = 0;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }

    public int getSuccessRecords() { return successRecords; }
    public void setSuccessRecords(int successRecords) { this.successRecords = successRecords; }

    public int getFailedRecords() { return failedRecords; }
    public void setFailedRecords(int failedRecords) { this.failedRecords = failedRecords; }

    public int getConflictRecords() { return conflictRecords; }
    public void setConflictRecords(int conflictRecords) { this.conflictRecords = conflictRecords; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }

    public void markStart(String direction) {
        this.state = STATE_RUNNING;
        this.direction = direction;
        this.startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.endTime = null;
        this.message = null;
        this.errorDetail = null;
    }

    public void markSuccess(String message) {
        this.state = STATE_SUCCESS;
        this.endTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.message = message;
    }

    public void markFailed(String message, String errorDetail) {
        this.state = STATE_FAILED;
        this.endTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.message = message;
        this.errorDetail = errorDetail;
    }
}
