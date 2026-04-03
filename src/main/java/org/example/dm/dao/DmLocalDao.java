package org.example.dm.dao;

import org.example.DatabaseConnectionPool;
import org.example.dm.model.DmOrder;
import org.example.dm.model.DmOrderDetail;
import org.example.util.LogUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DM本地数据访问对象
 * 负责本地数据库的DM订单数据操作
 */
public class DmLocalDao {
    private static DmLocalDao instance;
    
    private DmLocalDao() {
    }
    
    public static synchronized DmLocalDao getInstance() {
        if (instance == null) {
            instance = new DmLocalDao();
        }
        return instance;
    }
    
    /**
     * 获取上次同步时间戳
     * @return 上次同步时间
     */
    public LocalDateTime getLastSyncTime() {
        try (Connection conn = DatabaseConnectionPool.getConnection();
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery(
                "SELECT TOP 1 last_sync_time FROM sync_status ORDER BY id DESC");
            
            if (rs.next()) {
                Timestamp timestamp = rs.getTimestamp("last_sync_time");
                if (timestamp != null) {
                    return timestamp.toLocalDateTime();
                }
            }
        } catch (SQLException e) {
            LogUtil.logError("获取DM同步时间戳失败: " + e.getMessage());
        }
        
        return LocalDateTime.now().minusDays(7);
    }
    
    /**
     * 更新同步时间戳
     * @param syncTime 同步时间
     */
    public void updateLastSyncTime(LocalDateTime syncTime) {
        String sql = "UPDATE sync_status SET last_sync_time = ? WHERE id = 1";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setTimestamp(1, Timestamp.valueOf(syncTime));
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected == 0) {
                LogUtil.logError("更新DM同步时间戳失败，不存在ID为1的记录");
            } else {
                LogUtil.logInfo("DM同步时间戳已更新: " + syncTime);
            }
        } catch (SQLException e) {
            LogUtil.logError("更新DM同步时间戳失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查订单是否存在（通过source_id）
     * @param sourceId 源系统ID
     * @return 本地订单ID，不存在返回null
     */
    public Integer checkOrderExistsBySourceId(Integer sourceId) {
        String sql = "SELECT id FROM dm_order WHERE source_id = ?";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, sourceId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            LogUtil.logError("检查订单是否存在失败 (source_id=" + sourceId + "): " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 检查订单是否存在（通过order_no）
     * @param orderNo 工单号
     * @return 本地订单ID，不存在返回null
     */
    public Integer checkOrderExistsByOrderNo(String orderNo) {
        String sql = "SELECT id FROM dm_order WHERE order_no = ?";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, orderNo);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            LogUtil.logError("检查订单是否存在失败 (order_no=" + orderNo + "): " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 插入订单（主表）
     * @param order 订单对象
     * @return 插入的订单ID
     */
    public Integer insertOrder(DmOrder order) {
        String sql = "INSERT INTO dm_order (" +
                     "source_id, order_no, month_settlement, factory, person_in_charge, " +
                     "currency, mark, tax_rate, payment_terms, remarks, " +
                     "in_warehouse, material_warehouse, original_terms, " +
                     "total_quantity, total_tax_amount, " +
                     "department, creator, auditor, approver, " +
                     "submit_time, modify_time, order_status, " +
                     "i_ord, fill_date, price_book, responsible_department, " +
                     "current_payment_date, original_payment_date, document_type, " +
                     "sync_status, created_time, updated_time" +
                     ") VALUES (" +
                     "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), GETDATE()" +
                     ")";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            int idx = 1;
            pstmt.setInt(idx++, order.getSourceId());
            pstmt.setString(idx++, order.getOrderNo());
            pstmt.setString(idx++, order.getMonthSettlement());
            pstmt.setString(idx++, order.getFactory());
            pstmt.setString(idx++, order.getPersonInCharge());
            pstmt.setString(idx++, order.getCurrency());
            pstmt.setString(idx++, order.getMark());
            pstmt.setBigDecimal(idx++, order.getTaxRate());
            pstmt.setString(idx++, order.getPaymentTerms());
            pstmt.setString(idx++, order.getRemarks());
            pstmt.setString(idx++, order.getInWarehouse());
            pstmt.setString(idx++, order.getMaterialWarehouse());
            pstmt.setString(idx++, order.getOriginalTerms());
            pstmt.setBigDecimal(idx++, order.getTotalQuantity());
            pstmt.setBigDecimal(idx++, order.getTotalTaxAmount());
            pstmt.setString(idx++, order.getDepartment());
            pstmt.setString(idx++, order.getCreator());
            pstmt.setString(idx++, order.getAuditor());
            pstmt.setString(idx++, order.getApprover());
            
            if (order.getSubmitTime() != null) {
                pstmt.setTimestamp(idx++, Timestamp.valueOf(order.getSubmitTime()));
            } else {
                pstmt.setNull(idx++, Types.TIMESTAMP);
            }
            
            pstmt.setTimestamp(idx++, Timestamp.valueOf(order.getModifyTime()));
            pstmt.setInt(idx++, order.getOrderStatus());
            
            pstmt.setString(idx++, order.getIOrd());
            
            if (order.getFillDate() != null) {
                pstmt.setTimestamp(idx++, Timestamp.valueOf(order.getFillDate()));
            } else {
                pstmt.setNull(idx++, Types.TIMESTAMP);
            }
            
            pstmt.setString(idx++, order.getPriceBook());
            pstmt.setString(idx++, order.getResponsibleDepartment());
            
            if (order.getCurrentPaymentDate() != null) {
                pstmt.setTimestamp(idx++, Timestamp.valueOf(order.getCurrentPaymentDate()));
            } else {
                pstmt.setNull(idx++, Types.TIMESTAMP);
            }
            
            if (order.getOriginalPaymentDate() != null) {
                pstmt.setTimestamp(idx++, Timestamp.valueOf(order.getOriginalPaymentDate()));
            } else {
                pstmt.setNull(idx++, Types.TIMESTAMP);
            }
            
            pstmt.setString(idx++, order.getDocumentType());
            pstmt.setInt(idx++, 0);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LogUtil.logError("插入DM订单失败 (order_no=" + order.getOrderNo() + "): " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * 更新订单（主表）
     * @param orderId 本地订单ID
     * @param order 订单对象
     * @return 是否成功
     */
    public boolean updateOrder(Integer orderId, DmOrder order) {
        String sql = "UPDATE dm_order SET " +
                     "source_id = ?, order_no = ?, month_settlement = ?, factory = ?, person_in_charge = ?, " +
                     "currency = ?, mark = ?, tax_rate = ?, payment_terms = ?, remarks = ?, " +
                     "in_warehouse = ?, material_warehouse = ?, original_terms = ?, " +
                     "total_quantity = ?, total_tax_amount = ?, " +
                     "department = ?, creator = ?, auditor = ?, approver = ?, " +
                     "submit_time = ?, modify_time = ?, order_status = ?, " +
                     "i_ord = ?, fill_date = ?, price_book = ?, responsible_department = ?, " +
                     "current_payment_date = ?, original_payment_date = ?, document_type = ?, " +
                     "sync_status = 0, updated_time = GETDATE() " +
                     "WHERE id = ?";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            int idx = 1;
            pstmt.setInt(idx++, order.getSourceId());
            pstmt.setString(idx++, order.getOrderNo());
            pstmt.setString(idx++, order.getMonthSettlement());
            pstmt.setString(idx++, order.getFactory());
            pstmt.setString(idx++, order.getPersonInCharge());
            pstmt.setString(idx++, order.getCurrency());
            pstmt.setString(idx++, order.getMark());
            pstmt.setBigDecimal(idx++, order.getTaxRate());
            pstmt.setString(idx++, order.getPaymentTerms());
            pstmt.setString(idx++, order.getRemarks());
            pstmt.setString(idx++, order.getInWarehouse());
            pstmt.setString(idx++, order.getMaterialWarehouse());
            pstmt.setString(idx++, order.getOriginalTerms());
            pstmt.setBigDecimal(idx++, order.getTotalQuantity());
            pstmt.setBigDecimal(idx++, order.getTotalTaxAmount());
            pstmt.setString(idx++, order.getDepartment());
            pstmt.setString(idx++, order.getCreator());
            pstmt.setString(idx++, order.getAuditor());
            pstmt.setString(idx++, order.getApprover());
            
            if (order.getSubmitTime() != null) {
                pstmt.setTimestamp(idx++, Timestamp.valueOf(order.getSubmitTime()));
            } else {
                pstmt.setNull(idx++, Types.TIMESTAMP);
            }
            
            pstmt.setTimestamp(idx++, Timestamp.valueOf(order.getModifyTime()));
            pstmt.setInt(idx++, order.getOrderStatus());
            
            pstmt.setString(idx++, order.getIOrd());
            
            if (order.getFillDate() != null) {
                pstmt.setTimestamp(idx++, Timestamp.valueOf(order.getFillDate()));
            } else {
                pstmt.setNull(idx++, Types.TIMESTAMP);
            }
            
            pstmt.setString(idx++, order.getPriceBook());
            pstmt.setString(idx++, order.getResponsibleDepartment());
            
            if (order.getCurrentPaymentDate() != null) {
                pstmt.setTimestamp(idx++, Timestamp.valueOf(order.getCurrentPaymentDate()));
            } else {
                pstmt.setNull(idx++, Types.TIMESTAMP);
            }
            
            if (order.getOriginalPaymentDate() != null) {
                pstmt.setTimestamp(idx++, Timestamp.valueOf(order.getOriginalPaymentDate()));
            } else {
                pstmt.setNull(idx++, Types.TIMESTAMP);
            }
            
            pstmt.setString(idx++, order.getDocumentType());
            pstmt.setInt(idx++, orderId);
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            LogUtil.logError("更新DM订单失败 (id=" + orderId + "): " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * 删除订单的所有子表数据
     * @param orderId 本地订单ID
     * @return 是否成功
     */
    public boolean deleteOrderDetails(Integer orderId) {
        String sql = "DELETE FROM dm_order_detail WHERE order_id = ?";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, orderId);
            pstmt.executeUpdate();
            return true;
            
        } catch (SQLException e) {
            LogUtil.logError("删除DM订单明细失败 (order_id=" + orderId + "): " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 批量插入订单明细
     * @param orderId 本地订单ID
     * @param details 订单明细列表
     * @return 成功插入的数量
     */
    public int batchInsertOrderDetails(Integer orderId, List<DmOrderDetail> details) {
        if (details == null || details.isEmpty()) {
            return 0;
        }
        
        String sql = "INSERT INTO dm_order_detail (" +
                     "order_id, order_no, line_no, material_code, material_desc, " +
                     "quantity, unit_price, tax_unit_price, tax_amount, price_book, " +
                     "suggested_quantity, source_doc_no, modify_time, " +
                     "source_id, i_ord, same_auxiliary, update_mark, expand_mark, " +
                     "created_time, updated_time" +
                     ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())";
        
        int successCount = 0;
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            for (DmOrderDetail detail : details) {
                int idx = 1;
                pstmt.setInt(idx++, orderId);
                pstmt.setString(idx++, detail.getOrderNo());
                
                if (detail.getLineNo() != null) {
                    pstmt.setInt(idx++, detail.getLineNo());
                } else {
                    pstmt.setNull(idx++, Types.INTEGER);
                }
                
                pstmt.setString(idx++, detail.getMaterialCode());
                pstmt.setString(idx++, detail.getMaterialDesc());
                pstmt.setBigDecimal(idx++, detail.getQuantity());
                pstmt.setBigDecimal(idx++, detail.getUnitPrice());
                pstmt.setBigDecimal(idx++, detail.getTaxUnitPrice());
                pstmt.setBigDecimal(idx++, detail.getTaxAmount());
                pstmt.setString(idx++, detail.getPriceBook());
                pstmt.setBigDecimal(idx++, detail.getSuggestedQuantity());
                pstmt.setString(idx++, detail.getSourceDocNo());
                pstmt.setTimestamp(idx++, Timestamp.valueOf(detail.getModifyTime()));
                
                if (detail.getSourceId() != null) {
                    pstmt.setInt(idx++, detail.getSourceId());
                } else {
                    pstmt.setNull(idx++, Types.INTEGER);
                }
                
                pstmt.setString(idx++, detail.getIOrd());
                pstmt.setString(idx++, detail.getSameAuxiliary());
                pstmt.setString(idx++, detail.getUpdateMark());
                pstmt.setString(idx++, detail.getExpandMark());
                
                pstmt.addBatch();
            }
            
            int[] results = pstmt.executeBatch();
            for (int result : results) {
                if (result > 0) {
                    successCount++;
                }
            }
            
        } catch (SQLException e) {
            LogUtil.logError("批量插入DM订单明细失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return successCount;
    }
    
    /**
     * 查询待同步的订单（sync_status = 0）
     * @return 待同步订单列表
     */
    public List<DmOrder> queryPendingOrders() {
        List<DmOrder> orders = new ArrayList<>();
        String sql = "SELECT * FROM dm_order WHERE sync_status = 0 AND sync_attempts < 10 ORDER BY id ASC";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                DmOrder order = new DmOrder();
                order.setId(rs.getInt("id"));
                order.setSourceId(rs.getInt("source_id"));
                order.setOrderNo(rs.getString("order_no"));
                order.setMonthSettlement(rs.getString("month_settlement"));
                order.setFactory(rs.getString("factory"));
                order.setPersonInCharge(rs.getString("person_in_charge"));
                order.setCurrency(rs.getString("currency"));
                order.setMark(rs.getString("mark"));
                order.setTaxRate(rs.getBigDecimal("tax_rate"));
                order.setPaymentTerms(rs.getString("payment_terms"));
                order.setRemarks(rs.getString("remarks"));
                order.setInWarehouse(rs.getString("in_warehouse"));
                order.setMaterialWarehouse(rs.getString("material_warehouse"));
                order.setOriginalTerms(rs.getString("original_terms"));
                order.setTotalQuantity(rs.getBigDecimal("total_quantity"));
                order.setTotalTaxAmount(rs.getBigDecimal("total_tax_amount"));
                order.setDepartment(rs.getString("department"));
                order.setCreator(rs.getString("creator"));
                order.setAuditor(rs.getString("auditor"));
                order.setApprover(rs.getString("approver"));
                
                Timestamp submitTime = rs.getTimestamp("submit_time");
                if (submitTime != null) {
                    order.setSubmitTime(submitTime.toLocalDateTime());
                }
                
                order.setModifyTime(rs.getTimestamp("modify_time").toLocalDateTime());
                order.setOrderStatus(rs.getInt("order_status"));
                order.setSyncStatus(rs.getInt("sync_status"));
                order.setSyncAttempts(rs.getInt("sync_attempts"));
                order.setSyncError(rs.getString("sync_error"));
                
                order.setIOrd(rs.getString("i_ord"));
                
                Timestamp fillDate = rs.getTimestamp("fill_date");
                if (fillDate != null) {
                    order.setFillDate(fillDate.toLocalDateTime());
                }
                
                order.setPriceBook(rs.getString("price_book"));
                order.setResponsibleDepartment(rs.getString("responsible_department"));
                
                Timestamp currentPaymentDate = rs.getTimestamp("current_payment_date");
                if (currentPaymentDate != null) {
                    order.setCurrentPaymentDate(currentPaymentDate.toLocalDateTime());
                }
                
                Timestamp originalPaymentDate = rs.getTimestamp("original_payment_date");
                if (originalPaymentDate != null) {
                    order.setOriginalPaymentDate(originalPaymentDate.toLocalDateTime());
                }
                
                order.setDocumentType(rs.getString("document_type"));
                
                orders.add(order);
            }
        } catch (SQLException e) {
            LogUtil.logError("查询待同步订单失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return orders;
    }
    
    /**
     * 查询订单的所有明细
     * @param orderId 订单ID
     * @return 订单明细列表
     */
    public List<DmOrderDetail> queryOrderDetails(Integer orderId) {
        List<DmOrderDetail> details = new ArrayList<>();
        String sql = "SELECT * FROM dm_order_detail WHERE order_id = ? ORDER BY line_no ASC";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, orderId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                DmOrderDetail detail = new DmOrderDetail();
                detail.setId(rs.getInt("id"));
                detail.setOrderId(rs.getInt("order_id"));
                detail.setOrderNo(rs.getString("order_no"));
                
                Integer lineNo = rs.getInt("line_no");
                if (!rs.wasNull()) {
                    detail.setLineNo(lineNo);
                }
                
                detail.setMaterialCode(rs.getString("material_code"));
                detail.setMaterialDesc(rs.getString("material_desc"));
                detail.setQuantity(rs.getBigDecimal("quantity"));
                detail.setUnitPrice(rs.getBigDecimal("unit_price"));
                detail.setTaxUnitPrice(rs.getBigDecimal("tax_unit_price"));
                detail.setTaxAmount(rs.getBigDecimal("tax_amount"));
                detail.setPriceBook(rs.getString("price_book"));
                detail.setSuggestedQuantity(rs.getBigDecimal("suggested_quantity"));
                detail.setSourceDocNo(rs.getString("source_doc_no"));
                
                Timestamp modifyTime = rs.getTimestamp("modify_time");
                if (modifyTime != null) {
                    detail.setModifyTime(modifyTime.toLocalDateTime());
                }
                
                Integer sourceId = rs.getInt("source_id");
                if (!rs.wasNull()) {
                    detail.setSourceId(sourceId);
                }
                
                detail.setIOrd(rs.getString("i_ord"));
                detail.setSameAuxiliary(rs.getString("same_auxiliary"));
                detail.setUpdateMark(rs.getString("update_mark"));
                detail.setExpandMark(rs.getString("expand_mark"));
                
                details.add(detail);
            }
        } catch (SQLException e) {
            LogUtil.logError("查询订单明细失败 (order_id=" + orderId + "): " + e.getMessage());
            e.printStackTrace();
        }
        
        return details;
    }
    
    /**
     * 更新同步状态
     * @param orderId 订单ID
     * @param status 同步状态（0=待同步，1=已同步）
     * @return 是否成功
     */
    public boolean updateSyncStatus(Integer orderId, int status) {
        String sql = "UPDATE dm_order SET sync_status = ?, sync_error = NULL, updated_time = GETDATE() WHERE id = ?";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, status);
            pstmt.setInt(2, orderId);
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            LogUtil.logError("更新同步状态失败 (id=" + orderId + "): " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 增加重试次数
     * @param orderId 订单ID
     * @return 是否成功
     */
    public boolean incrementSyncAttempts(Integer orderId) {
        String sql = "UPDATE dm_order SET sync_attempts = sync_attempts + 1, updated_time = GETDATE() WHERE id = ?";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, orderId);
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            LogUtil.logError("增加重试次数失败 (id=" + orderId + "): " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 更新同步错误信息
     * @param orderId 订单ID
     * @param error 错误信息
     * @return 是否成功
     */
    public boolean updateSyncError(Integer orderId, String error) {
        String sql = "UPDATE dm_order SET sync_error = ?, updated_time = GETDATE() WHERE id = ?";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            String truncatedError = error;
            if (error != null && error.length() > 500) {
                truncatedError = error.substring(0, 500);
            }
            
            pstmt.setString(1, truncatedError);
            pstmt.setInt(2, orderId);
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            LogUtil.logError("更新同步错误信息失败 (id=" + orderId + "): " + e.getMessage());
        }
        
        return false;
    }
}
