package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryMatchConfig {
    private String relation;
    private List<QueryCondition> conditions;
    private boolean allowMultipleUpdate;

    public QueryMatchConfig() {
        this.relation = "and";
        this.conditions = new ArrayList<>();
        this.allowMultipleUpdate = false;
    }

    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation != null ? relation : "and"; }

    public List<QueryCondition> getConditions() { return conditions != null ? conditions : new ArrayList<>(); }
    public void setConditions(List<QueryCondition> conditions) { this.conditions = conditions != null ? conditions : new ArrayList<>(); }

    public boolean isAllowMultipleUpdate() { return allowMultipleUpdate; }
    public void setAllowMultipleUpdate(boolean allowMultipleUpdate) { this.allowMultipleUpdate = allowMultipleUpdate; }
}
