package org.example.dm.dao;

import org.example.dm.DmDatabaseConnectionPool;
import org.example.dm.config.DmConfigManager;
import org.example.dm.model.DmOrder;
import org.example.dm.model.DmOrderDetail;
import org.example.util.LogUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DM远程数据访问对象
 * 负责从虚拟机（客户DM）数据库查询数据
 */
public class DmRemoteDao {
    private static DmRemoteDao instance;
    private final DmDatabaseConnectionPool connectionPool;
    private final DmConfigManager configManager;
    
    private DmRemoteDao() {
        this.connectionPool = DmDatabaseConnectionPool.getInstance();
        this.configManager = DmConfigManager.getInstance();
    }
    
    public static synchronized DmRemoteDao getInstance() {
        if (instance == null) {
            instance = new DmRemoteDao();
        }
        return instance;
    }
    
    /**
     * 查询增量主表数据
     * @param lastSyncTime 上次同步时间
     * @param batchSize 批次大小
     * @return 订单列表
     */
    public List<DmOrder> fetchIncrementalOrders(LocalDateTime lastSyncTime, int batchSize) {
        List<DmOrder> orders = new ArrayList<>();
        String tableName = configManager.getMainTableName();
        String modifyTimeField = configManager.getSourceFieldName("modify_time");
        
        String sql = "SELECT TOP " + batchSize + " * FROM " + tableName + " " +
                     (lastSyncTime != null ? "WHERE " + modifyTimeField + " > ? " : "") +
                     "ORDER BY " + modifyTimeField + " ASC";
        
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            if (lastSyncTime != null) {
                pstmt.setTimestamp(1, Timestamp.valueOf(lastSyncTime));
            }
            
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                DmOrder order = mapResultSetToOrder(rs);
                orders.add(order);
            }
            
            LogUtil.logInfo("从DM远程数据库获取到 " + orders.size() + " 条订单记录");
            
        } catch (SQLException e) {
            LogUtil.logError("查询DM远程数据库失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return orders;
    }
    
    /**
     * 查询订单的子表数据
     * 主子表关联逻辑：子表order_no格式为"主表order_no-序号"（如MO2401001-01）
     * 通过LIKE字符串匹配关联主表：d.order_no LIKE m.order_no + '-%'
     * @param orderNo 主表订单号
     * @return 订单明细列表
     */
    public List<DmOrderDetail> fetchOrderDetails(String orderNo) {
        List<DmOrderDetail> details = new ArrayList<>();
        String detailTableName = configManager.getDetailTableName();
        String mainTableName = configManager.getMainTableName();
        String orderNoField = configManager.getSourceFieldName("order_no");
        
        // 主子表关联：子表order_no格式为"主表order_no-序号"（如MO2401001-01）
        // 通过LIKE字符串匹配关联主表：d.order_no LIKE m.order_no + '-%'
        String sql = "SELECT d.* FROM " + detailTableName + " d " +
                     "INNER JOIN " + mainTableName + " o ON d." + orderNoField + " LIKE o." + orderNoField + " + '-%' " +
                     "WHERE o." + orderNoField + " = ?";
        
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, orderNo);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                DmOrderDetail detail = mapResultSetToDetail(rs);
                details.add(detail);
            }
            
        } catch (SQLException e) {
            LogUtil.logError("查询DM订单明细失败 (orderNo=" + orderNo + "): " + e.getMessage());
        }
        
        return details;
    }
    
    /**
     * 将ResultSet映射为DmOrder对象
     * 使用字段映射配置，支持客户数据库字段名与本地字段名不一致的情况
     */
    private DmOrder mapResultSetToOrder(ResultSet rs) throws SQLException {
        DmOrder order = new DmOrder();
        
        // 使用配置文件中的字段映射，添加异常处理
        order.setSourceId(getIntSafely(rs, configManager.getSourceFieldName("source_id")));
        order.setOrderNo(getStringSafely(rs, configManager.getSourceFieldName("order_no")));
        order.setMonthSettlement(getStringSafely(rs, configManager.getSourceFieldName("month_settlement")));
        order.setFactory(getStringSafely(rs, configManager.getSourceFieldName("factory")));
        order.setPersonInCharge(getStringSafely(rs, configManager.getSourceFieldName("person_in_charge")));
        order.setCurrency(getStringSafely(rs, configManager.getSourceFieldName("currency")));
        order.setMark(getStringSafely(rs, configManager.getSourceFieldName("mark")));
        order.setTaxRate(getBigDecimalSafely(rs, configManager.getSourceFieldName("tax_rate")));
        order.setPaymentTerms(getStringSafely(rs, configManager.getSourceFieldName("payment_terms")));
        order.setRemarks(getStringSafely(rs, configManager.getSourceFieldName("remarks")));
        order.setInWarehouse(getStringSafely(rs, configManager.getSourceFieldName("in_warehouse")));
        order.setMaterialWarehouse(getStringSafely(rs, configManager.getSourceFieldName("material_warehouse")));
        order.setOriginalTerms(getStringSafely(rs, configManager.getSourceFieldName("original_terms")));
        order.setTotalQuantity(getBigDecimalSafely(rs, configManager.getSourceFieldName("total_quantity")));
        order.setTotalTaxAmount(getBigDecimalSafely(rs, configManager.getSourceFieldName("total_tax_amount")));
        order.setDepartment(getStringSafely(rs, configManager.getSourceFieldName("department")));
        order.setCreator(getStringSafely(rs, configManager.getSourceFieldName("creator")));
        order.setAuditor(getStringSafely(rs, configManager.getSourceFieldName("auditor")));
        order.setApprover(getStringSafely(rs, configManager.getSourceFieldName("approver")));
        
        Timestamp submitTime = getTimestampSafely(rs, configManager.getSourceFieldName("submit_time"));
        if (submitTime != null) {
            order.setSubmitTime(submitTime.toLocalDateTime());
        }
        
        Timestamp modifyTime = getTimestampSafely(rs, configManager.getSourceFieldName("modify_time"));
        if (modifyTime != null) {
            order.setModifyTime(modifyTime.toLocalDateTime());
        }
        
        order.setOrderStatus(getIntSafely(rs, configManager.getSourceFieldName("order_status")));
        
        return order;
    }
    
    /**
     * 将ResultSet映射为DmOrderDetail对象
     * 使用字段映射配置，支持客户数据库字段名与本地字段名不一致的情况
     */
    private DmOrderDetail mapResultSetToDetail(ResultSet rs) throws SQLException {
        DmOrderDetail detail = new DmOrderDetail();
        
        // 使用配置文件中的字段映射，添加异常处理
        detail.setOrderNo(getStringSafely(rs, configManager.getSourceDetailFieldName("order_no")));
        detail.setLineNo(getIntSafely(rs, configManager.getSourceDetailFieldName("line_no")));
        detail.setMaterialCode(getStringSafely(rs, configManager.getSourceDetailFieldName("material_code")));
        detail.setMaterialDesc(getStringSafely(rs, configManager.getSourceDetailFieldName("material_desc")));
        detail.setQuantity(getBigDecimalSafely(rs, configManager.getSourceDetailFieldName("quantity")));
        detail.setUnitPrice(getBigDecimalSafely(rs, configManager.getSourceDetailFieldName("unit_price")));
        detail.setTaxUnitPrice(getBigDecimalSafely(rs, configManager.getSourceDetailFieldName("tax_unit_price")));
        detail.setTaxAmount(getBigDecimalSafely(rs, configManager.getSourceDetailFieldName("tax_amount")));
        detail.setPriceBook(getStringSafely(rs, configManager.getSourceDetailFieldName("price_book")));
        detail.setSuggestedQuantity(getBigDecimalSafely(rs, configManager.getSourceDetailFieldName("suggested_quantity")));
        detail.setSourceDocNo(getStringSafely(rs, configManager.getSourceDetailFieldName("source_doc_no")));
        
        Timestamp modifyTime = getTimestampSafely(rs, configManager.getSourceDetailFieldName("modify_time"));
        if (modifyTime != null) {
            detail.setModifyTime(modifyTime.toLocalDateTime());
        }
        
        return detail;
    }
    
    /**
     * 安全地从ResultSet获取String值
     * 如果字段不存在，返回null而不是抛出异常
     */
    private String getStringSafely(ResultSet rs, String columnName) {
        try {
            return rs.getString(columnName);
        } catch (SQLException e) {
            LogUtil.logWarning("字段不存在或读取失败: " + columnName + ", 使用NULL值");
            return null;
        }
    }
    
    /**
     * 安全地从ResultSet获取int值
     * 如果字段不存在，返回0而不是抛出异常
     */
    private int getIntSafely(ResultSet rs, String columnName) {
        try {
            return rs.getInt(columnName);
        } catch (SQLException e) {
            LogUtil.logWarning("字段不存在或读取失败: " + columnName + ", 使用0值");
            return 0;
        }
    }
    
    /**
     * 安全地从ResultSet获取BigDecimal值
     * 如果字段不存在，返回null而不是抛出异常
     */
    private java.math.BigDecimal getBigDecimalSafely(ResultSet rs, String columnName) {
        try {
            return rs.getBigDecimal(columnName);
        } catch (SQLException e) {
            LogUtil.logWarning("字段不存在或读取失败: " + columnName + ", 使用NULL值");
            return null;
        }
    }
    
    /**
     * 安全地从ResultSet获取Timestamp值
     * 如果字段不存在，返回null而不是抛出异常
     */
    private Timestamp getTimestampSafely(ResultSet rs, String columnName) {
        try {
            return rs.getTimestamp(columnName);
        } catch (SQLException e) {
            LogUtil.logWarning("字段不存在或读取失败: " + columnName + ", 使用NULL值");
            return null;
        }
    }
}
