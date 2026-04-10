package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubTableJoinCondition {
    private String subTableField;
    private String mainTableField;

    public SubTableJoinCondition() {}

    public SubTableJoinCondition(String subTableField, String mainTableField) {
        this.subTableField = subTableField;
        this.mainTableField = mainTableField;
    }

    public String getSubTableField() { return subTableField; }
    public void setSubTableField(String subTableField) { this.subTableField = subTableField; }

    public String getMainTableField() { return mainTableField; }
    public void setMainTableField(String mainTableField) { this.mainTableField = mainTableField; }
}
