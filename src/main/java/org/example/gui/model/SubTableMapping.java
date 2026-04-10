package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubTableMapping {
    private String id;
    private String subTableName;
    private String subFormWidgetId;
    private Map<String, String> fieldMapping;
    private List<SubTableJoinCondition> joinConditions;

    public SubTableMapping() {
        this.fieldMapping = new ConcurrentHashMap<>();
        this.joinConditions = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSubTableName() { return subTableName; }
    public void setSubTableName(String subTableName) { this.subTableName = subTableName; }

    public String getSubFormWidgetId() { return subFormWidgetId; }
    public void setSubFormWidgetId(String subFormWidgetId) { this.subFormWidgetId = subFormWidgetId; }

    public Map<String, String> getFieldMapping() { return fieldMapping != null ? fieldMapping : new ConcurrentHashMap<>(); }
    public void setFieldMapping(Map<String, String> fieldMapping) { this.fieldMapping = fieldMapping != null ? new ConcurrentHashMap<>(fieldMapping) : new ConcurrentHashMap<>(); }

    public List<SubTableJoinCondition> getJoinConditions() { return joinConditions != null ? joinConditions : new ArrayList<>(); }
    public void setJoinConditions(List<SubTableJoinCondition> joinConditions) { this.joinConditions = joinConditions != null ? joinConditions : new ArrayList<>(); }
}
