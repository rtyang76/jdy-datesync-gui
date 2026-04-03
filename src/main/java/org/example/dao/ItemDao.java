package org.example.dao;

import org.example.DatabaseConnectionPool;
import org.example.model.ItemRecord;
import org.example.util.LogUtil;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 物料数据访问对象
 * 负责物料相关的数据库操作
 */
public class ItemDao {
    private static ItemDao instance;
    private static final int MAX_RETRY = 10;
    private static final long RETRY_INTERVAL = 5000;

    // 数据库字段列表
    private static final String ITEM_FIELDS = String.join(", ",
            "id", "sid", "job_num", "job_version", "item_number", "lot_use", "sn_use", "cost_classify",
            "product_name", "product_type", "item_capacity", "pn", "uom", "item_project", "product_category",
            "item_product", "item_product_line", "customer_sku", "msl_level", "customer_pn", "product_series",
            "item_level", "quality_level", "speed_level", "quality", "hscode", "customs_model", "label_pn",
            "label_model", "item_brand", "lot_number_rule", "item_control", "flash", "dram_type", "item_substrate",
            "pcb_item_number", "overlapping_die_quantity", "packaging_mode", "item_size", "item_bin",
            "printed_content", "laser_code_rule", "item_gold", "item_no_gold", "bd_drawing_no",
            "laser_code_drawing_file_no", "model_name", "product_sticker", "packing_method",
            "packing_specification_no", "inner_packing_quantity", "outer_packing_quantity",
            "software_information", "real_fw", "user_fw", "test_program", "net_weight", "gross_weight",
            "color_card_size", "inner_box_size", "outer_box_size", "total_weight_of_inner",
            "total_weight_of_outer", "overpack_upc", "overpack_upc_qty", "overpack_ean", "overpack_ean_qty",
            "item_classification", "sync_batch", "long_description");

    private ItemDao() {
    }

    public static synchronized ItemDao getInstance() {
        if (instance == null) {
            instance = new ItemDao();
        }
        return instance;
    }

    /**
     * 获取上次物料同步ID
     */
    public Integer getLastItemSyncId() {
        try (Connection conn = DatabaseConnectionPool.getConnection();
                Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT TOP 1 item_sync_id FROM sync_status ORDER BY id DESC");
            return rs.next() ? rs.getInt("item_sync_id") : null;
        } catch (SQLException e) {
            LogUtil.logError("获取物料同步ID失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取新增物料数据
     * 
     * @param lastSyncId   上次同步的ID
     * @param maxBatchSize 最大批次大小
     * @return 物料数据列表
     */
    public List<ItemRecord> fetchNewItemData(Integer lastSyncId, int maxBatchSize) {
        List<ItemRecord> data = new ArrayList<>();
        String sql = "SELECT TOP " + maxBatchSize + " " + ITEM_FIELDS +
                " FROM oms_job_item_info " +
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
                    for (String field : ITEM_FIELDS.split(", ")) {
                        Object value = rs.getObject(field);
                        if (value instanceof java.sql.Date) {
                            value = ((java.sql.Date) value).toLocalDate();
                        } else if (value instanceof java.sql.Timestamp) {
                            value = ((java.sql.Timestamp) value).toLocalDateTime();
                        }
                        record.put(field, value);
                    }
                    data.add(ItemRecord.fromMap(record));
                }

                LogUtil.logInfo("获取到 " + data.size() + " 条物料记录");
                return data;
            } catch (SQLException e) {
                retryCount++;
                if (retryCount >= MAX_RETRY) {
                    LogUtil.logError("查询物料数据失败，已重试" + MAX_RETRY + "次: " + e.getMessage());
                    return data;
                }
                LogUtil.logWarning("查询物料数据失败，准备第" + retryCount + "次重试...");
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
     * 更新物料同步状态
     * 
     * @param lastItemSyncId 最后同步的物料ID
     */
    public void updateItemSyncStatus(int lastItemSyncId) {
        String sql = "UPDATE sync_status SET item_sync_id = ? WHERE id = 1";

        try (Connection conn = DatabaseConnectionPool.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, lastItemSyncId);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                LogUtil.logError("更新物料同步状态失败，不存在ID为1的记录");
            } else {
                // 物料同步状态已更新
            }
        } catch (SQLException e) {
            LogUtil.logError("更新物料同步状态失败: " + e.getMessage());
        }
    }
}