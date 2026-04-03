package org.example.dm.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DM订单明细模型
 */
public class DmOrderDetail {
    private Integer id;
    private Integer orderId;
    private String orderNo;
    private Integer lineNo;
    private String materialCode;
    private String materialDesc;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal taxUnitPrice;
    private BigDecimal taxAmount;
    private String priceBook;
    private BigDecimal suggestedQuantity;
    private String sourceDocNo;
    private LocalDateTime modifyTime;
    private Integer syncStatus;
    private LocalDateTime lastSyncTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    
    private Integer sourceId;
    private String iOrd;
    private String sameAuxiliary;
    private String updateMark;
    private String expandMark;
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public Integer getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }
    
    public String getOrderNo() {
        return orderNo;
    }
    
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
    
    public Integer getLineNo() {
        return lineNo;
    }
    
    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }
    
    public String getMaterialCode() {
        return materialCode;
    }
    
    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }
    
    public String getMaterialDesc() {
        return materialDesc;
    }
    
    public void setMaterialDesc(String materialDesc) {
        this.materialDesc = materialDesc;
    }
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
    
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
    
    public BigDecimal getTaxUnitPrice() {
        return taxUnitPrice;
    }
    
    public void setTaxUnitPrice(BigDecimal taxUnitPrice) {
        this.taxUnitPrice = taxUnitPrice;
    }
    
    public BigDecimal getTaxAmount() {
        return taxAmount;
    }
    
    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }
    
    public String getPriceBook() {
        return priceBook;
    }
    
    public void setPriceBook(String priceBook) {
        this.priceBook = priceBook;
    }
    
    public BigDecimal getSuggestedQuantity() {
        return suggestedQuantity;
    }
    
    public void setSuggestedQuantity(BigDecimal suggestedQuantity) {
        this.suggestedQuantity = suggestedQuantity;
    }
    
    public String getSourceDocNo() {
        return sourceDocNo;
    }
    
    public void setSourceDocNo(String sourceDocNo) {
        this.sourceDocNo = sourceDocNo;
    }
    
    public LocalDateTime getModifyTime() {
        return modifyTime;
    }
    
    public void setModifyTime(LocalDateTime modifyTime) {
        this.modifyTime = modifyTime;
    }
    
    public Integer getSyncStatus() {
        return syncStatus;
    }
    
    public void setSyncStatus(Integer syncStatus) {
        this.syncStatus = syncStatus;
    }
    
    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }
    
    public void setLastSyncTime(LocalDateTime lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
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
    
    public Integer getSourceId() {
        return sourceId;
    }
    
    public void setSourceId(Integer sourceId) {
        this.sourceId = sourceId;
    }
    
    public String getIOrd() {
        return iOrd;
    }
    
    public void setIOrd(String iOrd) {
        this.iOrd = iOrd;
    }
    
    public String getSameAuxiliary() {
        return sameAuxiliary;
    }
    
    public void setSameAuxiliary(String sameAuxiliary) {
        this.sameAuxiliary = sameAuxiliary;
    }
    
    public String getUpdateMark() {
        return updateMark;
    }
    
    public void setUpdateMark(String updateMark) {
        this.updateMark = updateMark;
    }
    
    public String getExpandMark() {
        return expandMark;
    }
    
    public void setExpandMark(String expandMark) {
        this.expandMark = expandMark;
    }
}
