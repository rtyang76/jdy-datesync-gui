package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubTableMapping {
    private String id;
    private String subTableName;
    private String subFormWidgetId;
    private String joinFieldName;
    private Map<String, String> fieldMapping;

    public SubTableMapping() {
        this.fieldMapping = new HashMap<>();
        this.joinFieldName = "main_id";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSubTableName() { return subTableName; }
    public void setSubTableName(String subTableName) { this.subTableName = subTableName; }

    public String getSubFormWidgetId() { return subFormWidgetId; }
    public void setSubFormWidgetId(String subFormWidgetId) { this.subFormWidgetId = subFormWidgetId; }

    public String getJoinFieldName() { return joinFieldName; }
    public void setJoinFieldName(String joinFieldName) { this.joinFieldName = joinFieldName; }

    public Map<String, String> getFieldMapping() { return fieldMapping; }
    public void setFieldMapping(Map<String, String> fieldMapping) { this.fieldMapping = fieldMapping != null ? fieldMapping : new HashMap<>(); }
}
