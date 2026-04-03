package org.example.util;

/**
 * 常量类
 * 存储应用程序中使用的常量
 */
public class Constants {
    // 重试配置
    public static final int MAX_RETRY = 10; // 最大重试次数
    public static final long RETRY_INTERVAL = 5000; // 重试间隔(毫秒)
    public static final int MAX_BATCH_SIZE = 50; // 每批最大数据量
    public static final int SYNC_INTERVAL_MINUTES = 5; // 定时同步间隔（分钟）
    
    // 自定义码字段ID
    public static final String CUSTOM_CODE_FIELD = "_widget_1748317817210";
    // 自定义码字符集（23个字母+10个数字，排除I、O、Z）
    public static final char[] CUSTOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXY0123456789".toCharArray();
    // 自定义码最大数量
    public static final int CUSTOM_CODE_MAX_COUNT = CUSTOM_CODE_CHARS.length;
    
    // 日期格式
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    
    // 配置文件路径
    public static final String DEFAULT_FIELD_MAPPING_PATH = "field_mapping.json";
    public static final String DEFAULT_ITEM_FIELD_MAPPING_PATH = "item_field_mapping.json";
    
    // 简道云API相关常量
    public static final String API_CONTENT_TYPE = "application/json; charset=UTF-8";
    public static final String CHARSET_UTF8 = "UTF-8";
    
    // 数据库字段列表
    public static final String ORDER_FIELDS = String.join(", ",
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

    public static final String ITEM_FIELDS = String.join(", ",
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
    
    // 简道云特殊字段ID
    public static final String WIDGET_DATE_FIELD = "_widget_1748238705999"; // 日期字段
    public static final String WIDGET_MATERIAL_SUMMARY = "_widget_1748246253992"; // 物料需求清单汇总
    public static final String WIDGET_MERGED_INFO = "_widget_1749429768338"; // 合并信息字段
    public static final String WIDGET_PRODUCT_TYPE = "_widget_1747712590429"; // 产品类型字段
    public static final String WIDGET_ITEM_PRODUCT_TYPE = "_widget_1749434741968"; // 物料产品类型字段
    
    // 批处理配置
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int DELAYED_UPDATE_WAIT_TIME = 3000; // 延迟更新等待时间（毫秒）
    
    // 私有构造函数，防止实例化
    private Constants() {
        throw new IllegalStateException("Constants class");
    }
} 