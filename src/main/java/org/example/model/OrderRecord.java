package org.example.model;


import java.util.Map;

/**
 * 订单记录模型类
 */
public class OrderRecord {
    private Integer id;
    private String sid;
    private String jobNum;
    private String jobVersion;
    private String jobStatus;
    private String itemNumber;
    private Map<String, Object> additionalFields;

    /**
     * 从Map创建订单记录
     */
    public static OrderRecord fromMap(Map<String, Object> data) {
        OrderRecord record = new OrderRecord();
        record.setId((Integer) data.get("id"));
        record.setSid((String) data.get("sid"));
        record.setJobNum((String) data.get("job_num"));
        record.setJobVersion((String) data.get("job_version"));
        record.setJobStatus((String) data.get("job_status"));
        record.setItemNumber((String) data.get("item_number"));
        record.setAdditionalFields(data);
        return record;
    }

    // Getter和Setter方法
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }

    public String getJobNum() { return jobNum; }
    public void setJobNum(String jobNum) { this.jobNum = jobNum; }

    public String getJobVersion() { return jobVersion; }
    public void setJobVersion(String jobVersion) { this.jobVersion = jobVersion; }

    public String getJobStatus() { return jobStatus; }
    public void setJobStatus(String jobStatus) { this.jobStatus = jobStatus; }

    public String getItemNumber() { return itemNumber; }
    public void setItemNumber(String itemNumber) { this.itemNumber = itemNumber; }

    public Map<String, Object> getAdditionalFields() { return additionalFields; }
    public void setAdditionalFields(Map<String, Object> additionalFields) { this.additionalFields = additionalFields; }
}