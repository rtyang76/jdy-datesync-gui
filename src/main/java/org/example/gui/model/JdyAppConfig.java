package org.example.gui.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JdyAppConfig {
    private String id;
    private String name;
    private String appId;
    private String apiToken;
    private boolean startWorkflow;

    public JdyAppConfig() {
        this.startWorkflow = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }

    public boolean isStartWorkflow() { return startWorkflow; }
    public void setStartWorkflow(boolean startWorkflow) { this.startWorkflow = startWorkflow; }

    @Override
    public String toString() {
        return name != null ? name : "未命名应用";
    }
}
