package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormMappingConfig {
    private String id;
    private String name;
    private String dataSourceId;
    private String jdyAppId;
    private String mainTableName;
    private String mainEntryId;
    private Map<String, String> mainFieldMapping;
    private List<SubTableMapping> subTableMappings;

    public FormMappingConfig() {
        this.mainFieldMapping = new HashMap<>();
        this.subTableMappings = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(String dataSourceId) { this.dataSourceId = dataSourceId; }

    public String getJdyAppId() { return jdyAppId; }
    public void setJdyAppId(String jdyAppId) { this.jdyAppId = jdyAppId; }

    public String getMainTableName() { return mainTableName; }
    public void setMainTableName(String mainTableName) { this.mainTableName = mainTableName; }

    public String getMainEntryId() { return mainEntryId; }
    public void setMainEntryId(String mainEntryId) { this.mainEntryId = mainEntryId; }

    public Map<String, String> getMainFieldMapping() { return mainFieldMapping != null ? mainFieldMapping : new HashMap<>(); }
    public void setMainFieldMapping(Map<String, String> mainFieldMapping) { this.mainFieldMapping = mainFieldMapping != null ? mainFieldMapping : new HashMap<>(); }

    public List<SubTableMapping> getSubTableMappings() { return subTableMappings != null ? subTableMappings : new ArrayList<>(); }
    public void setSubTableMappings(List<SubTableMapping> subTableMappings) { this.subTableMappings = subTableMappings != null ? subTableMappings : new ArrayList<>(); }

    @Override
    public String toString() {
        return name != null ? name : "未命名映射";
    }
}
