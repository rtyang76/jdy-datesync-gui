package org.example.model;

import java.util.Map;

/**
 * 物料记录模型类
 */
public class ItemRecord {
    private Integer id;
    private String sid;
    private String jobNum;
    private String jobVersion;
    private String itemNumber;
    private String productCategory;
    private Map<String, Object> additionalFields;

    /**
     * 从Map创建物料记录
     */
    public static ItemRecord fromMap(Map<String, Object> data) {
        ItemRecord record = new ItemRecord();
        record.setId((Integer) data.get("id"));
        record.setSid((String) data.get("sid"));
        record.setJobNum((String) data.get("job_num"));
        record.setJobVersion((String) data.get("job_version"));
        record.setItemNumber((String) data.get("item_number"));
        record.setProductCategory((String) data.get("product_category"));
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

    public String getItemNumber() { return itemNumber; }
    public void setItemNumber(String itemNumber) { this.itemNumber = itemNumber; }

    public String getProductCategory() { return productCategory; }
    public void setProductCategory(String productCategory) { this.productCategory = productCategory; }

    public Map<String, Object> getAdditionalFields() { return additionalFields; }
    public void setAdditionalFields(Map<String, Object> additionalFields) { this.additionalFields = additionalFields; }
}