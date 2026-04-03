package org.example.dm.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DM订单模型
 */
public class DmOrder {
    private Integer id;
    private Integer sourceId;
    private String orderNo;
    private String monthSettlement;
    private String factory;
    private String personInCharge;
    private String currency;
    private String mark;
    private BigDecimal taxRate;
    private String paymentTerms;
    private String remarks;
    private String inWarehouse;
    private String materialWarehouse;
    private String originalTerms;
    private BigDecimal totalQuantity;
    private BigDecimal totalTaxAmount;
    private String department;
    private String creator;
    private String auditor;
    private String approver;
    private LocalDateTime submitTime;
    private LocalDateTime modifyTime;
    private Integer orderStatus;
    private Integer syncStatus;
    private Integer syncAttempts;
    private String syncError;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    
    private String iOrd;
    private LocalDateTime fillDate;
    private String priceBook;
    private String responsibleDepartment;
    private LocalDateTime currentPaymentDate;
    private LocalDateTime originalPaymentDate;
    private String documentType;
    
    private List<DmOrderDetail> details = new ArrayList<>();
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public Integer getSourceId() {
        return sourceId;
    }
    
    public void setSourceId(Integer sourceId) {
        this.sourceId = sourceId;
    }
    
    public String getOrderNo() {
        return orderNo;
    }
    
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
    
    public String getMonthSettlement() {
        return monthSettlement;
    }
    
    public void setMonthSettlement(String monthSettlement) {
        this.monthSettlement = monthSettlement;
    }
    
    public String getFactory() {
        return factory;
    }
    
    public void setFactory(String factory) {
        this.factory = factory;
    }
    
    public String getPersonInCharge() {
        return personInCharge;
    }
    
    public void setPersonInCharge(String personInCharge) {
        this.personInCharge = personInCharge;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public String getMark() {
        return mark;
    }
    
    public void setMark(String mark) {
        this.mark = mark;
    }
    
    public BigDecimal getTaxRate() {
        return taxRate;
    }
    
    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }
    
    public String getPaymentTerms() {
        return paymentTerms;
    }
    
    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }
    
    public String getRemarks() {
        return remarks;
    }
    
    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
    
    public String getInWarehouse() {
        return inWarehouse;
    }
    
    public void setInWarehouse(String inWarehouse) {
        this.inWarehouse = inWarehouse;
    }
    
    public String getMaterialWarehouse() {
        return materialWarehouse;
    }
    
    public void setMaterialWarehouse(String materialWarehouse) {
        this.materialWarehouse = materialWarehouse;
    }
    
    public String getOriginalTerms() {
        return originalTerms;
    }
    
    public void setOriginalTerms(String originalTerms) {
        this.originalTerms = originalTerms;
    }
    
    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }
    
    public void setTotalQuantity(BigDecimal totalQuantity) {
        this.totalQuantity = totalQuantity;
    }
    
    public BigDecimal getTotalTaxAmount() {
        return totalTaxAmount;
    }
    
    public void setTotalTaxAmount(BigDecimal totalTaxAmount) {
        this.totalTaxAmount = totalTaxAmount;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    public String getCreator() {
        return creator;
    }
    
    public void setCreator(String creator) {
        this.creator = creator;
    }
    
    public String getAuditor() {
        return auditor;
    }
    
    public void setAuditor(String auditor) {
        this.auditor = auditor;
    }
    
    public String getApprover() {
        return approver;
    }
    
    public void setApprover(String approver) {
        this.approver = approver;
    }
    
    public LocalDateTime getSubmitTime() {
        return submitTime;
    }
    
    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }
    
    public LocalDateTime getModifyTime() {
        return modifyTime;
    }
    
    public void setModifyTime(LocalDateTime modifyTime) {
        this.modifyTime = modifyTime;
    }
    
    public Integer getOrderStatus() {
        return orderStatus;
    }
    
    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }
    
    public Integer getSyncStatus() {
        return syncStatus;
    }
    
    public void setSyncStatus(Integer syncStatus) {
        this.syncStatus = syncStatus;
    }

    public Integer getSyncAttempts() {
        return syncAttempts;
    }
    
    public void setSyncAttempts(Integer syncAttempts) {
        this.syncAttempts = syncAttempts;
    }
    
    public String getSyncError() {
        return syncError;
    }
    
    public void setSyncError(String syncError) {
        this.syncError = syncError;
    }
    
    public LocalDateTime getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
    
    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
    
    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }
    
    public String getIOrd() {
        return iOrd;
    }
    
    public void setIOrd(String iOrd) {
        this.iOrd = iOrd;
    }
    
    public LocalDateTime getFillDate() {
        return fillDate;
    }
    
    public void setFillDate(LocalDateTime fillDate) {
        this.fillDate = fillDate;
    }
    
    public String getPriceBook() {
        return priceBook;
    }
    
    public void setPriceBook(String priceBook) {
        this.priceBook = priceBook;
    }
    
    public String getResponsibleDepartment() {
        return responsibleDepartment;
    }
    
    public void setResponsibleDepartment(String responsibleDepartment) {
        this.responsibleDepartment = responsibleDepartment;
    }
    
    public LocalDateTime getCurrentPaymentDate() {
        return currentPaymentDate;
    }
    
    public void setCurrentPaymentDate(LocalDateTime currentPaymentDate) {
        this.currentPaymentDate = currentPaymentDate;
    }
    
    public LocalDateTime getOriginalPaymentDate() {
        return originalPaymentDate;
    }
    
    public void setOriginalPaymentDate(LocalDateTime originalPaymentDate) {
        this.originalPaymentDate = originalPaymentDate;
    }
    
    public String getDocumentType() {
        return documentType;
    }
    
    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }
    
    public List<DmOrderDetail> getDetails() {
        return details;
    }
    
    public void setDetails(List<DmOrderDetail> details) {
        this.details = details;
    }
}
