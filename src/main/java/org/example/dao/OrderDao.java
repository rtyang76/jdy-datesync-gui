package org.example.dao;

import org.example.DatabaseConnectionPool;
import org.example.model.OrderRecord;
import org.example.util.LogUtil;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 订单数据访问对象
 * 负责订单相关的数据库操作
 */
public class OrderDao {
    private static OrderDao instance;
    private static final int MAX_RETRY = 10;
    private static final long RETRY_INTERVAL = 5000;
    private static final int MAX_BATCH_SIZE = 50;

    // 数据库字段列表
    private static final String ORDER_FIELDS = String.join(", ",
            "id", "sid", "order_number", "order_type", "risk_flag", "stock_type", "product_line_name", "work_classify",
            "job_num", "job_version", "job_status", "po_num", "po_version", "po_status", "customs_organization_name",
            "erp_work_classify", "is_formal_work_type", "vendor_code", "osp_code", "process_factory", "process_project",
            "process_price", "currency", "product_mode", "work_required_date", "work_start_date", "work_end_date",
            "expands_bom", "push_item_type", "sync_mps", "job_last_update_date", "po_last_update_date", "job_creater",
            "po_line_id", "item_number", "order_quantity", "required_yield", "guaranteed_quantity",
            "transfer_order_quantity",
            "completed_quantity", "scrap_quantity", "uncompleted_quantity", "po_received_quantity", "pmc_reply_date",
            "plan_finish_date", "factory_delivery_date", "customer_code", "customer_pn", "customer_po", "remark",
            "ac_code_ext_quantity", "host_qr_code_s", "host_qr_code_sz", "sn_source_code", "pmc_product_info",
            "lot_number_rule", "laser_code_rule", "return_warehouse_code", "return_warehouse_name", "delivery_area",
            "shipping_warehouse_name", "is_vacuo", "software_package", "resource_item_number", "sticker_requirements",
            "real_fw", "user_fw", "sequence_requirements", "product_infomation", "risk_description", "setup_diagram",
            "shell_color", "led_lamp_color", "case_requirements", "factory_product_instructions", "cp_program",
            "reli_veri_reserved_quantity", "is_open_card", "product_cer_requirements", "production_requirements",
            "inspection_requirements", "package_methods", "screen_printing_or_image_file", "laser_code_or_image_file",
            "item_gold", "item_no_gold", "package_documents", "product_verify_require", "verification_documents",
            "fp_write_requirements", "fp_read_requirements", "pd_shipment_inspection_report", "pk_pallet",
            "fp_production_process", "cutomized_work_remark", "inspection_report_supply_way", "bs_soa_document_name",
            "pd_fqc_report_template_name", "fp_product_msg_id_requirements", "shell_material", "customized_version",
            "pd_first_confirm", "pd_host_sn_sticker_name", "bs_Soa_Document_Name", "fp_uhead_laser_code_require",
            "sync_batch", "trial_production_report", "before_item_number", "program", "resource_item_type",
            "source_item_number", "corresponding_package");

    private OrderDao() {
    }

    public static synchronized OrderDao getInstance() {
        if (instance == null) {
            instance = new OrderDao();
        }
        return instance;
    }

    /**
     * 获取上次同步ID
     */
    public Integer getLastSyncId() {
        try (Connection conn = DatabaseConnectionPool.getConnection();
                Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT TOP 1 last_sync_id FROM sync_status ORDER BY id DESC");
            return rs.next() ? rs.getInt("last_sync_id") : null;
        } catch (SQLException e) {
            LogUtil.logError("获取同步ID失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取上次同步日期和计数
     */
    public Map<String, Object> getLastSyncDateAndCount() {
        Map<String, Object> result = new HashMap<>();
        result.put("sync_date", LocalDate.now());
        result.put("sync_count", 0);

        try (Connection conn = DatabaseConnectionPool.getConnection();
                Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT TOP 1 sync_date, sync_count FROM sync_status ORDER BY id DESC");
            if (rs.next()) {
                java.sql.Date syncDate = rs.getDate("sync_date");
                if (syncDate != null) {
                    result.put("sync_date", syncDate.toLocalDate());
                }
                result.put("sync_count", rs.getInt("sync_count"));
            }
        } catch (SQLException e) {
            LogUtil.logError("获取同步日期和计数失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取新增订单数据
     * 
     * @param lastSyncId   上次同步的ID
     * @param maxBatchSize 最大批次大小
     * @return 订单数据列表
     */
    public List<OrderRecord> fetchNewData(Integer lastSyncId, int maxBatchSize) {
        List<OrderRecord> data = new ArrayList<>();
        String sql = "SELECT TOP " + maxBatchSize + " " + ORDER_FIELDS +
                " FROM oms_order " +
                (lastSyncId != null ? "WHERE id > ? ORDER BY id ASC" : "ORDER BY id ASC");

        int retryCount = 0;
        while (retryCount < MAX_RETRY) {
            try (Connection conn = DatabaseConnectionPool.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                if (lastSyncId != null)
                    pstmt.setInt(1, lastSyncId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    for (String field : ORDER_FIELDS.split(", ")) {
                        Object value = rs.getObject(field);
                        if (value instanceof java.sql.Date) {
                            value = ((java.sql.Date) value).toLocalDate();
                        } else if (value instanceof java.sql.Timestamp) {
                            value = ((java.sql.Timestamp) value).toLocalDateTime();
                        }
                        record.put(field, value);
                    }
                    data.add(OrderRecord.fromMap(record));
                }

                LogUtil.logInfo("获取到 " + data.size() + " 条订单记录");
                return data;
            } catch (SQLException e) {
                retryCount++;
                if (retryCount >= MAX_RETRY) {
                    LogUtil.logError("查询数据失败，已重试" + MAX_RETRY + "次: " + e.getMessage());
                    return data;
                }
                LogUtil.logWarning("查询数据失败，准备第" + retryCount + "次重试...");
                try {
                    Thread.sleep(RETRY_INTERVAL);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return data;
    }

    /**
     * 更新同步状态
     */
    public void updateSyncStatus(int lastSyncId, LocalDate syncDate, int syncCount) {
        String sql = "UPDATE sync_status SET last_sync_id = ?, sync_date = ?, sync_count = ? WHERE id = 1";

        try (Connection conn = DatabaseConnectionPool.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, lastSyncId);
            pstmt.setDate(2, java.sql.Date.valueOf(syncDate));
            pstmt.setInt(3, syncCount);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                LogUtil.logError("更新同步状态失败，不存在ID为1的记录");
            } else {
                // 同步状态已更新
            }
        } catch (SQLException e) {
            LogUtil.logError("更新同步状态失败: " + e.getMessage());
        }
    }
}