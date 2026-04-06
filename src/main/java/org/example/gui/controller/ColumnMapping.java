package org.example.gui.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ColumnMapping {
    private final StringProperty columnName;
    private final StringProperty columnType;
    private final StringProperty columnComment;
    private final StringProperty widgetId;

    public ColumnMapping(String columnName, String columnType, String columnComment, String widgetId) {
        this.columnName = new SimpleStringProperty(columnName != null ? columnName : "");
        this.columnType = new SimpleStringProperty(columnType != null ? columnType : "");
        this.columnComment = new SimpleStringProperty(columnComment != null ? columnComment : "");
        this.widgetId = new SimpleStringProperty(widgetId != null ? widgetId : "");
    }

    public String getColumnName() { return columnName.get(); }
    public void setColumnName(String v) { columnName.set(v); }
    public StringProperty columnNameProperty() { return columnName; }

    public String getColumnType() { return columnType.get(); }
    public void setColumnType(String v) { columnType.set(v); }
    public StringProperty columnTypeProperty() { return columnType; }

    public String getColumnComment() { return columnComment.get(); }
    public void setColumnComment(String v) { columnComment.set(v); }
    public StringProperty columnCommentProperty() { return columnComment; }

    public String getWidgetId() { return widgetId.get(); }
    public void setWidgetId(String v) { widgetId.set(v); }
    public StringProperty widgetIdProperty() { return widgetId; }
}
