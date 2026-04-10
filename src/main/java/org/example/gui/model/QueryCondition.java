package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryCondition {
    private String field;
    private String widgetId;
    private String method;
    private String type;

    public QueryCondition() {
        this.method = "eq";
    }

    public QueryCondition(String field, String widgetId, String method, String type) {
        this.field = field;
        this.widgetId = widgetId;
        this.method = method != null ? method : "eq";
        this.type = type;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getWidgetId() { return widgetId; }
    public void setWidgetId(String widgetId) { this.widgetId = widgetId; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
