package org.example.service;

import org.example.config.FieldMappingConfig;
import org.example.config.ProductInfoDatabase;
import org.example.util.LogUtil;
import org.example.util.Constants;
import org.example.service.DatabaseService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 数据转换服务类
 * 负责处理数据转换
 */
public class DataTransformService {
    private static DataTransformService instance;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(Constants.DATETIME_FORMAT);
    private final ProductInfoDatabase productInfoDatabase;

    // 私有构造函数，防止外部实例化
    private DataTransformService() {
        this.productInfoDatabase = ProductInfoDatabase.getInstance();
    }

    // 单例模式获取实例
    public static synchronized DataTransformService getInstance() {
        if (instance == null) {
            instance = new DataTransformService();
        }
        return instance;
    }

    /**
     * 转换采购物料通知单数据
     * 
     * @param record       原始数据
     * @param fieldMapping 字段映射
     * @param subTables    子表映射
     * @return 转换后的数据
     */
    public Map<String, Object> convertDeliveryData(Map<String, Object> record, Map<String, String> fieldMapping,
            Map<String, Map<String, String>> subTables) {
        Map<String, Object> converted = new HashMap<>();
        try {
            // 主表字段处理
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                String srcField = entry.getKey();
                if (subTables.containsKey(srcField))
                    continue;

                String destField = entry.getValue();
                Object value = record.get(srcField);
                converted.put(destField, Collections.singletonMap("value",
                        value == null ? "" : value.toString().trim()));
            }

            // 子表数据处理
            Integer noticeId = (Integer) record.get("id");

            if (noticeId != null && !subTables.isEmpty()) {
                DatabaseService databaseService = DatabaseService.getInstance();

                // 处理送货通知单明细子表 (po_delivery_notice_detail)
                if (subTables.containsKey("delivery_details")) {
                    Map<String, String> deliveryDetailsMapping = subTables.get("delivery_details");
                    String destField = fieldMapping.get("delivery_details"); // 从字段映射中获取目标字段ID

                    List<Map<String, Object>> deliveryDetails = databaseService.querySubTableWithMapping(
                            noticeId, "po_delivery_notice_detail", deliveryDetailsMapping, "notice_id");

                    // 按照简道云官方格式，子表数据需要包装在 {"value": [...]} 结构中
                    converted.put(destField, Collections.singletonMap("value", deliveryDetails));
                }
            }

            // 日期字段处理
            List<String> dateFields = Arrays.asList("tran_date", "create_date", "delivery_date");
            for (String field : dateFields) {
                String destField = fieldMapping.get(field);
                if (destField != null) {
                    Object dateValue = record.get(field);
                    String formattedDate = formatDateValue(dateValue);
                    converted.put(destField, Collections.singletonMap("value", formattedDate));
                }
            }

        } catch (Exception e) {
            LogUtil.logError("采购物料通知单数据转换异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        return converted;
    }

    /**
     * 格式化日期值
     */
    private String formatDateValue(Object dateValue) {
        String formattedDate = "";

        if (dateValue != null) {
            if (dateValue instanceof LocalDate) {
                formattedDate = ((LocalDate) dateValue).format(DATE_FORMATTER);
            } else if (dateValue instanceof LocalDateTime) {
                formattedDate = ((LocalDateTime) dateValue).format(DATE_FORMATTER);
            } else if (dateValue instanceof java.sql.Date) {
                formattedDate = ((java.sql.Date) dateValue).toLocalDate().format(DATE_FORMATTER);
            } else if (dateValue instanceof java.sql.Timestamp) {
                formattedDate = ((java.sql.Timestamp) dateValue).toLocalDateTime().format(DATE_FORMATTER);
            } else if (dateValue instanceof String) {
                try {
                    if (((String) dateValue).contains(":")) {
                        formattedDate = LocalDateTime.parse(
                                ((String) dateValue).replace(" ", "T"),
                                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSSS]"))
                                .format(DATE_FORMATTER);
                    } else {
                        formattedDate = LocalDate.parse((String) dateValue).format(DATE_FORMATTER);
                    }
                } catch (Exception e) {
                    if (dateValue != null && ((String) dateValue).length() >= 10) {
                        formattedDate = ((String) dateValue).substring(0, 10);
                    } else {
                        formattedDate = (String) dateValue;
                    }
                }
            } else {
                formattedDate = dateValue.toString();
            }
        }

        return formattedDate;
    }

    /**
     * 转换数据
     * 
     * @param record       原始数据
     * @param fieldMapping 字段映射
     * @param subTables    子表映射
     * @return 转换后的数据
     */
    public Map<String, Object> convertData(Map<String, Object> record, Map<String, String> fieldMapping,
            Map<String, Map<String, String>> subTables) {
        Map<String, Object> converted = new HashMap<>();
        try {
            // 主表字段处理
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                String srcField = entry.getKey();
                if (subTables.containsKey(srcField))
                    continue;

                String destField = entry.getValue();
                Object value = record.get(srcField);
                converted.put(destField, Collections.singletonMap("value",
                        value == null ? "" : value.toString().trim()));
            }

            // 子表数据处理
            Integer orderId = (Integer) record.get("id");
            List<Map<String, Object>> requireComponents = null;

            if (orderId != null && !subTables.isEmpty()) {
                DatabaseService databaseService = DatabaseService.getInstance();

                // 处理需求组件子表 (oms_require_component)
                if (subTables.containsKey("requireComponentList")) {
                    Map<String, String> requireComponentMapping = subTables.get("requireComponentList");
                    String destField = fieldMapping.get("requireComponentList");
                    requireComponents = databaseService.querySubTableWithMapping(
                            orderId, "oms_require_component", requireComponentMapping);
                    // 按照简道云官方格式，子表数据需要包装在 {"value": [...]} 结构中
                    converted.put(destField, Collections.singletonMap("value", requireComponents));
                }

                // 处理测试工艺方案子表 (oms_test_process_scheme)
                if (subTables.containsKey("testProcessSchemeList")) {
                    Map<String, String> testProcessMapping = subTables.get("testProcessSchemeList");
                    String destField = fieldMapping.get("testProcessSchemeList");
                    List<Map<String, Object>> testProcesses = databaseService.querySubTableWithMapping(
                            orderId, "oms_test_process_scheme", testProcessMapping);
                    // 按照简道云官方格式，子表数据需要包装在 {"value": [...]} 结构中
                    converted.put(destField, Collections.singletonMap("value", testProcesses));
                }

                // 处理晶圆DC子表 (oms_wafer_dc)
                if (subTables.containsKey("waferDcList")) {
                    Map<String, String> waferDcMapping = subTables.get("waferDcList");
                    String destField = fieldMapping.get("waferDcList");
                    List<Map<String, Object>> waferDcs = databaseService.querySubTableWithMapping(
                            orderId, "oms_wafer_dc", waferDcMapping);
                    // 按照简道云官方格式，子表数据需要包装在 {"value": [...]} 结构中
                    converted.put(destField, Collections.singletonMap("value", waferDcs));
                }
            }

            // 生成物料需求清单汇总
            String materialSummary = generateRequireComponentSummary(requireComponents);
            converted.put(Constants.WIDGET_MATERIAL_SUMMARY, Collections.singletonMap("value", materialSummary));

            // 日期字段处理
            List<String> dateFields = Arrays.asList(
                    "work_required_date", "pmc_reply_date",
                    "work_start_date", "work_end_date",
                    "plan_finish_date", "factory_delivery_date");
            for (String field : dateFields) {
                String destField = fieldMapping.get(field);
                Object dateValue = record.get(field);
                String formattedDate = "";

                if (dateValue != null) {
                    if (dateValue instanceof LocalDate) {
                        formattedDate = ((LocalDate) dateValue).format(DATE_FORMATTER);
                    } else if (dateValue instanceof LocalDateTime) {
                        formattedDate = ((LocalDateTime) dateValue).format(DATE_FORMATTER);
                    } else if (dateValue instanceof java.sql.Date) {
                        formattedDate = ((java.sql.Date) dateValue).toLocalDate()
                                .format(DATE_FORMATTER);
                    } else if (dateValue instanceof java.sql.Timestamp) {
                        formattedDate = ((java.sql.Timestamp) dateValue).toLocalDateTime()
                                .format(DATE_FORMATTER);
                    } else if (dateValue instanceof String) {
                        // 尝试解析字符串日期，支持多种格式
                        try {
                            // 先尝试解析带时间的完整格式
                            if (((String) dateValue).contains(":")) {
                                formattedDate = LocalDateTime.parse(
                                        ((String) dateValue).replace(" ", "T"),
                                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSSS]"))
                                        .format(DATE_FORMATTER);
                            } else {
                                formattedDate = LocalDate.parse((String) dateValue)
                                        .format(DATE_FORMATTER);
                            }
                        } catch (Exception e) {
                            // 无法解析日期字符串，使用默认处理
                            // 尝试提取日期部分
                            if (dateValue != null && ((String) dateValue).length() >= 10) {
                                formattedDate = ((String) dateValue).substring(0, 10);
                            } else {
                                formattedDate = (String) dateValue;
                            }
                        }
                    } else {
                        formattedDate = dateValue.toString();
                    }
                }

                converted.put(destField, Collections.singletonMap("value", formattedDate));
            }

            // 添加新增的字段
            // 获取当前北京时间日期字符串 (yyyy-MM-dd)
            String currentDate = LocalDate.now().format(DATE_FORMATTER);

            // 添加_widget_1748238705999字段
            // 修改条件：只有当JOB_STATUS为"已发放"时才赋值当前日期，否则为空
            // 注意：更新操作时，此字段会在OrderSyncServiceImpl中被移除，不会覆盖简道云中的现有值
            String jobStatus = (String) record.get("job_status");
            String dateValue = (jobStatus != null && "已发放".equals(jobStatus.trim())) ? currentDate : "";

            converted.put("_widget_1748238705999", Collections.singletonMap("value", dateValue));

            // 添加产品类型字段 _widget_1747712590429
            String productCategory = getStringValue(record.get("product_category"));
            String productType = determineProductType(productCategory);
            converted.put("_widget_1747712590429", Collections.singletonMap("value", productType));

            // 添加合并字段 _widget_1749429768338
            String mergedProdInfo = mergeProdInfo(record);
            converted.put("_widget_1749429768338", Collections.singletonMap("value", mergedProdInfo));

            // 从合并后的产品信息中提取图档号
            Map<String, String> documentNumbers = extractDocumentNumbers(mergedProdInfo);

            // 添加各类图档号字段
            for (Map.Entry<String, String> entry : documentNumbers.entrySet()) {
                converted.put(entry.getKey(), Collections.singletonMap("value", entry.getValue()));
            }

            // 智能提取卷标内容
            String volumeLabel = extractVolumeLabelIntelligently(mergedProdInfo);
            
            // 如果智能提取失败，尝试针对性提取方法
            if (volumeLabel == null || volumeLabel.trim().isEmpty()) {
                String targetedLabel = extractVolumeLabelTargeted(mergedProdInfo);
                if (targetedLabel != null && !targetedLabel.trim().isEmpty()) {
                    volumeLabel = targetedLabel;
                }
            }
            
            converted.put("_widget_1750382988523", Collections.singletonMap("value", volumeLabel));

            // 智能提取产品信息（VID、PID、厂商名、产品名、文件格式）
            Map<String, String> productInfo = extractProductInfoIntelligently(mergedProdInfo);

            for (Map.Entry<String, String> entry : productInfo.entrySet()) {
                converted.put(entry.getKey(), Collections.singletonMap("value", entry.getValue()));
            }

            // 时间戳字段处理
            List<String> timestampFields = Arrays.asList(
                    "job_last_update_date", "po_last_update_date");
            for (String field : timestampFields) {
                String destField = fieldMapping.get(field);
                Object timestampValue = record.get(field);
                String formattedTimestamp = "";

                if (timestampValue != null) {
                    if (timestampValue instanceof LocalDateTime) {
                        formattedTimestamp = ((LocalDateTime) timestampValue)
                                .format(DATETIME_FORMATTER);
                    } else if (timestampValue instanceof java.sql.Timestamp) {
                        formattedTimestamp = ((java.sql.Timestamp) timestampValue).toLocalDateTime()
                                .format(DATETIME_FORMATTER);
                    } else if (timestampValue instanceof String) {
                        // 尝试解析字符串时间戳
                        String strValue = ((String) timestampValue).trim();
                        boolean parsed = false;

                        // 处理 "MM dd yyyy h:mma" 格式 (例如: "03 29 2025 3:53PM")
                        try {
                            // 先尝试标准格式
                            LocalDateTime dt = LocalDateTime.parse(strValue);
                            formattedTimestamp = dt.format(DATETIME_FORMATTER);
                            parsed = true;
                        } catch (Exception e1) {
                            try {
                                // 尝试处理 "MM dd yyyy h:mma" 格式
                                if (strValue.matches("\\d{2}\\s+\\d{2}\\s+\\d{4}\\s+\\d{1,2}:\\d{2}(AM|PM|am|pm)")) {
                                    DateTimeFormatter customFormatter = DateTimeFormatter.ofPattern("MM dd yyyy h:mma",
                                            Locale.ENGLISH);
                                    LocalDateTime dt = LocalDateTime.parse(strValue, customFormatter);
                                    formattedTimestamp = dt.format(DATETIME_FORMATTER);
                                    parsed = true;
                                }
                            } catch (Exception e2) {
                                // 继续尝试其他格式
                            }

                            if (!parsed) {
                                try {
                                    // 尝试处理日期部分，忽略时间部分
                                    if (strValue.contains(" ")) {
                                        String[] parts = strValue.split("\\s+");
                                        if (parts.length >= 3) {
                                            // 假设格式为 "MM dd yyyy" 的日期部分
                                            String datePart = parts[0] + " " + parts[1] + " " + parts[2];
                                            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM dd yyyy",
                                                    Locale.ENGLISH);
                                            LocalDate date = LocalDate.parse(datePart, dateFormatter);
                                            formattedTimestamp = date.format(DATE_FORMATTER)
                                                    + " 00:00:00";
                                            parsed = true;
                                        }
                                    }
                                } catch (Exception e3) {
                                    // 无法解析，使用原始字符串
                                }
                            }
                        }

                        if (!parsed) {
                            // 记录无法解析的时间戳，但不重复记录相同格式
                            // 无法解析时间戳字符串，使用原值
                            formattedTimestamp = strValue;
                        }
                    } else {
                        formattedTimestamp = timestampValue.toString();
                    }
                }

                converted.put(destField, Collections.singletonMap("value", formattedTimestamp));
            }

        } catch (Exception e) {
            LogUtil.logError("数据转换异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
        return converted;
    }

    /**
     * 从字符串中提取值
     * 
     * @param obj 对象
     * @return 字符串值
     */
    private String getStringValue(Object obj) {
        if (obj == null)
            return "";
        return obj.toString().trim();
    }

    /**
     * 合并产品信息
     * 
     * @param record 记录
     * @return 合并后的产品信息
     */
    private String mergeProdInfo(Map<String, Object> record) {
        // 获取字段值
        String factoryProdInst = getStringValue(record.get("factory_product_instructions"));
        String customWorkRemark = getStringValue(record.get("cutomized_work_remark"));
        String prodRequirements = getStringValue(record.get("production_requirements"));
        String prodInfo = getStringValue(record.get("product_infomation"));

        // 存储要合并的内容
        StringBuilder result = new StringBuilder();

        // 首先添加工厂产品说明
        result.append(factoryProdInst);

        // 检查其他字段是否需要合并
        // 对于每个字段，检查其内容是否已包含在factory_product_instructions中
        // 如果不包含，则添加到结果中

        // 添加cutomized_work_remark
        if (!customWorkRemark.isEmpty()) {
            if (!factoryProdInst.contains(customWorkRemark)) {
                if (result.length() > 0)
                    result.append("#");
                result.append(customWorkRemark);
            }
        }

        // 添加production_requirements
        if (!prodRequirements.isEmpty()) {
            if (!factoryProdInst.contains(prodRequirements)) {
                if (result.length() > 0)
                    result.append("#");
                result.append(prodRequirements);
            }
        }

        // 添加product_infomation
        if (!prodInfo.isEmpty()) {
            if (!factoryProdInst.contains(prodInfo)) {
                if (result.length() > 0)
                    result.append("#");
                result.append(prodInfo);
            }
        }

        return result.toString();
    }

    /**
     * 根据产品类别生成产品类型
     * 
     * @param productCategory 产品类别
     * @return 产品类型
     */
    private String determineProductType(String productCategory) {
        if (productCategory == null) {
            return "";
        }

        // 统一转为大写进行比较
        String upperCategory = productCategory.toUpperCase();

        // 按优先级判断
        if (upperCategory.contains("MICRO SD")) {
            return "TF";
        } else if (upperCategory.contains("UPA")) {
            return "UPA";
        } else if (upperCategory.contains("UDP")) {
            return "UDP";
        } else if (upperCategory.contains("SD")) {
            return "SD";
        }

        return "";
    }

    /**
     * 从文本中提取图档号
     * 功能：根据指定规则提取不同类型的图档号
     * 返回：包含各类图档号的Map
     * 
     * @param factoryProdInst 工厂产品说明
     * @return 图档号映射
     */
    private Map<String, String> extractDocumentNumbers(String factoryProdInst) {
        Map<String, String> result = new HashMap<>();

        // 初始化所有图档号字段为空字符串
        result.put("_widget_1749434759620", ""); // 移印图档号
        result.put("_widget_1749437052124", ""); // 移印客制中性图档
        result.put("_widget_1749434759621", ""); // 彩喷图档号
        result.put("_widget_1749434759622", ""); // 镭雕图档号
        result.put("_widget_1749434759623", ""); // 彩卡图档号
        result.put("_widget_1749434759624", ""); // 彩盒图档号
        result.put("_widget_1749467309784", ""); // 客制箱唛贴纸
        result.put("_widget_1749467309785", ""); // 不干胶贴纸

        if (factoryProdInst == null || factoryProdInst.trim().isEmpty()) {
            return result;
        }

        // 开始提取图档号

        // 图档号正则表达式模式 (例如 CL120001A, MTB124001A, ABC123)
        java.util.regex.Pattern docNumberPattern = java.util.regex.Pattern.compile("[A-Z]{2,4}\\d{3,7}[A-Z]?");

        // 将文本按行、分号或数字序号分割，便于逐段处理
        String[] segments = factoryProdInst.split("[\\n;]|(?<=\\d\\.)|(?<=\\d、)");

        for (String segment : segments) {
            segment = segment.trim();
            if (segment.isEmpty())
                continue;

            // 修改逻辑：对每个段落，独立检查每种类型的图档号
            // 这样可以从同一段落中提取多种不同类型的图档号

            // 检查移印客制
            if (segment.contains("移印客制") || segment.contains("客制移印") ||
                    (segment.contains("移印") && segment.contains("客制")) ||
                    (segment.contains("中性") && segment.contains("图档"))) {

                // 查找关键词的位置
                int keywordPos = findKeywordPosition(segment, new String[] { "移印客制", "客制移印", "中性" });

                // 只在关键词后面的文本中查找图档号
                if (keywordPos >= 0) {
                    String afterKeyword = segment.substring(keywordPos);
                    java.util.regex.Matcher docMatcher = docNumberPattern.matcher(afterKeyword);
                    if (docMatcher.find()) {
                        String docNumber = docMatcher.group();
                        result.put("_widget_1749437052124", docNumber); // 移印客制中性图档
                    }
                }
            }

            // 检查移印/丝印
            if (segment.contains("移印") || segment.contains("丝印")) {
                // 检查是否包含"客制"关键词但未被第一优先级捕获
                if (segment.contains("客制") && result.get("_widget_1749437052124").isEmpty()) {
                    // 查找客制关键词的位置
                    int keywordPos = segment.indexOf("客制");
                    // 只在关键词后面的文本中查找图档号
                    if (keywordPos >= 0) {
                        String afterKeyword = segment.substring(keywordPos);
                        java.util.regex.Matcher docMatcher = docNumberPattern.matcher(afterKeyword);
                        if (docMatcher.find()) {
                            String docNumber = docMatcher.group();
                            result.put("_widget_1749437052124", docNumber); // 移印客制中性图档
                        }
                    }
                } else if (!segment.contains("客制")) {
                    // 查找移印/丝印关键词的位置
                    int keywordPos = findKeywordPosition(segment, new String[] { "移印", "丝印" });
                    // 只在关键词后面的文本中查找图档号
                    if (keywordPos >= 0) {
                        String afterKeyword = segment.substring(keywordPos);
                        java.util.regex.Matcher docMatcher = docNumberPattern.matcher(afterKeyword);
                        if (docMatcher.find()) {
                            String docNumber = docMatcher.group();
                            result.put("_widget_1749434759620", docNumber); // 移印图档号
                        }
                    }
                }
            }

            // 检查彩喷/彩印
            if (segment.contains("彩喷") || segment.contains("彩印")) {
                // 查找彩喷/彩印关键词的位置
                int keywordPos = findKeywordPosition(segment, new String[] { "彩喷", "彩印" });
                // 只在关键词后面的文本中查找图档号
                if (keywordPos >= 0) {
                    String afterKeyword = segment.substring(keywordPos);
                    java.util.regex.Matcher docMatcher = docNumberPattern.matcher(afterKeyword);
                    if (docMatcher.find()) {
                        String docNumber = docMatcher.group();
                        result.put("_widget_1749434759621", docNumber); // 彩喷图档号
                    }
                }
            }

            // 检查镭雕相关 - 修改逻辑，排除包含"非金手指面"的情况
            // 修改：确保"镭雕"或"金手指面"关键词在图档号之前
            if ((segment.contains("镭雕") || segment.contains("金手指面图档") || segment.contains("金手指面")) &&
                    !segment.contains("非金手指面")) {

                // 查找关键词的位置
                int laserKeywordPos = findKeywordPosition(segment, new String[] { "镭雕", "金手指面图档", "金手指面" });
                // 只在关键词后面的文本中查找图档号
                if (laserKeywordPos >= 0) {
                    String afterKeyword = segment.substring(laserKeywordPos);
                    java.util.regex.Matcher docMatcher = docNumberPattern.matcher(afterKeyword);
                    if (docMatcher.find()) {
                        String docNumber = docMatcher.group();
                        result.put("_widget_1749434759622", docNumber); // 镭雕图档号
                    }
                }
            }

            // 特殊处理：镭雕-图档号 格式 (如 "背面镭雕-MTB124001A")
            if (segment.contains("镭雕-") && result.get("_widget_1749434759622").isEmpty()) {
                int dashPos = segment.indexOf("镭雕-");
                if (dashPos >= 0) {
                    String afterDash = segment.substring(dashPos + 3); // "镭雕-" 长度为3
                    java.util.regex.Matcher docMatcher = docNumberPattern.matcher(afterDash);
                    if (docMatcher.find()) {
                        String docNumber = docMatcher.group();
                        result.put("_widget_1749434759622", docNumber); // 镭雕图档号
                    }
                }
            }

            // 检查彩卡图档号
            if (segment.contains("彩卡")) {
                // 查找彩卡关键词的位置
                int keywordPos = segment.indexOf("彩卡");
                // 只在关键词后面的文本中查找图档号
                if (keywordPos >= 0) {
                    String afterKeyword = segment.substring(keywordPos);
                    java.util.regex.Matcher docMatcher = docNumberPattern.matcher(afterKeyword);
                    if (docMatcher.find()) {
                        String docNumber = docMatcher.group();
                        result.put("_widget_1749434759623", docNumber); // 彩卡图档号
                    }
                }
            }

            // 检查彩盒图档号
            if (segment.contains("彩盒")) {
                // 查找彩盒关键词的位置
                int keywordPos = segment.indexOf("彩盒");
                // 只在关键词后面的文本中查找图档号
                if (keywordPos >= 0) {
                    String afterKeyword = segment.substring(keywordPos);
                    java.util.regex.Matcher docMatcher = docNumberPattern.matcher(afterKeyword);
                    if (docMatcher.find()) {
                        String docNumber = docMatcher.group();
                        result.put("_widget_1749434759624", docNumber); // 彩盒图档号
                    }
                }
            }

            // 检查客制箱唛贴纸
            if (segment.contains("客制箱唛") || segment.contains("箱唛贴纸") ||
                    (segment.contains("箱唛") && segment.contains("贴纸"))) {

                // 查找箱唛关键词的位置
                int keywordPos = findKeywordPosition(segment, new String[] { "客制箱唛", "箱唛贴纸", "箱唛" });
                // 只在关键词后面的文本中查找图档号
                if (keywordPos >= 0) {
                    String afterKeyword = segment.substring(keywordPos);
                    java.util.regex.Matcher docMatcher = docNumberPattern.matcher(afterKeyword);
                    if (docMatcher.find()) {
                        String docNumber = docMatcher.group();
                        result.put("_widget_1749467309784", docNumber); // 客制箱唛贴纸
                    }
                }
            }

            // 检查不干胶贴纸
            if (segment.contains("不干胶") || segment.contains("不干胶贴纸") ||
                    (segment.contains("贴纸") && !segment.contains("箱唛"))) {

                // 查找不干胶关键词的位置
                int keywordPos = findKeywordPosition(segment, new String[] { "不干胶", "不干胶贴纸", "贴纸" });
                // 只在关键词后面的文本中查找图档号
                if (keywordPos >= 0) {
                    String afterKeyword = segment.substring(keywordPos);
                    java.util.regex.Matcher docMatcher = docNumberPattern.matcher(afterKeyword);
                    if (docMatcher.find()) {
                        String docNumber = docMatcher.group();
                        result.put("_widget_1749467309785", docNumber); // 不干胶贴纸
                    }
                }
            }

            // 处理特殊情况：包含多个图档号的复合段落（如示例中的"丝印：UN410003A + 彩卡：UN504001A"）
            if (segment.contains("+") || segment.contains("，") || segment.contains(",")) {
                // 尝试按+号、逗号分割
                String[] subSegments = segment.split("\\+|，|,");
                for (String subSegment : subSegments) {
                    subSegment = subSegment.trim();
                    if (subSegment.isEmpty())
                        continue;

                    // 为每个子段落单独提取图档号
                    processSubSegment(subSegment, docNumberPattern, result);
                }
            }
        }

        // 二次处理：如果未能提取到图档号，尝试全文搜索
        performFullTextSearch(factoryProdInst, docNumberPattern, result);

        return result;
    }

    /**
     * 查找关键词位置
     */
    private int findKeywordPosition(String text, String[] keywords) {
        int maxPos = -1;
        for (String keyword : keywords) {
            int pos = text.indexOf(keyword);
            if (pos >= 0) {
                maxPos = Math.max(maxPos, pos);
            }
        }
        return maxPos;
    }

    /**
     * 处理子段落
     */
    private void processSubSegment(String subSegment, java.util.regex.Pattern docNumberPattern,
            Map<String, String> result) {
        if (subSegment.contains("移印") || subSegment.contains("丝印")) {
            if (subSegment.contains("客制") && result.get("_widget_1749437052124").isEmpty()) {
                extractDocFromSegment(subSegment, docNumberPattern, "客制", "_widget_1749437052124", "移印客制中性图档", result);
            } else if (!subSegment.contains("客制") && result.get("_widget_1749434759620").isEmpty()) {
                extractDocFromSegment(subSegment, docNumberPattern, new String[] { "移印", "丝印" },
                        "_widget_1749434759620", "移印图档号", result);
            }
        }

        if ((subSegment.contains("彩喷") || subSegment.contains("彩印")) && result.get("_widget_1749434759621").isEmpty()) {
            extractDocFromSegment(subSegment, docNumberPattern, new String[] { "彩喷", "彩印" }, "_widget_1749434759621",
                    "彩喷图档号", result);
        }

        if ((subSegment.contains("镭雕") || subSegment.contains("金手指面")) &&
                !subSegment.contains("非金手指面") && result.get("_widget_1749434759622").isEmpty()) {
            extractDocFromSegment(subSegment, docNumberPattern, new String[] { "镭雕", "金手指面" }, "_widget_1749434759622",
                    "镭雕图档号", result);
        }

        if (subSegment.contains("彩卡") && result.get("_widget_1749434759623").isEmpty()) {
            extractDocFromSegment(subSegment, docNumberPattern, "彩卡", "_widget_1749434759623", "彩卡图档号", result);
        }

        if (subSegment.contains("彩盒") && result.get("_widget_1749434759624").isEmpty()) {
            extractDocFromSegment(subSegment, docNumberPattern, "彩盒", "_widget_1749434759624", "彩盒图档号", result);
        }

        if ((subSegment.contains("客制箱唛") || subSegment.contains("箱唛贴纸") ||
                (subSegment.contains("箱唛") && subSegment.contains("贴纸")))
                && result.get("_widget_1749467309784").isEmpty()) {
            extractDocFromSegment(subSegment, docNumberPattern, new String[] { "客制箱唛", "箱唛贴纸", "箱唛" },
                    "_widget_1749467309784", "客制箱唛贴纸", result);
        }

        if ((subSegment.contains("不干胶") || subSegment.contains("不干胶贴纸") ||
                (subSegment.contains("贴纸") && !subSegment.contains("箱唛")))
                && result.get("_widget_1749467309785").isEmpty()) {
            extractDocFromSegment(subSegment, docNumberPattern, new String[] { "不干胶", "不干胶贴纸", "贴纸" },
                    "_widget_1749467309785", "不干胶贴纸", result);
        }
    }

    /**
     * 从段落中提取图档号
     */
    private void extractDocFromSegment(String segment, java.util.regex.Pattern docNumberPattern, String keyword,
            String fieldId, String logName, Map<String, String> result) {
        extractDocFromSegment(segment, docNumberPattern, new String[] { keyword }, fieldId, logName, result);
    }

    private void extractDocFromSegment(String segment, java.util.regex.Pattern docNumberPattern, String[] keywords,
            String fieldId, String logName, Map<String, String> result) {
        int keywordPos = findKeywordPosition(segment, keywords);
        if (keywordPos >= 0) {
            String afterKeyword = segment.substring(keywordPos);
            java.util.regex.Matcher docMatcher = docNumberPattern.matcher(afterKeyword);
            if (docMatcher.find()) {
                String docNumber = docMatcher.group();
                result.put(fieldId, docNumber);
                // 子段落提取完成
            }
        }
    }

    /**
     * 执行全文搜索
     */
    private void performFullTextSearch(String factoryProdInst, java.util.regex.Pattern docNumberPattern,
            Map<String, String> result) {
        // 检查是否需要全文搜索
        boolean needFullTextSearch = false;

        if (result.get("_widget_1749434759620").isEmpty() &&
                (factoryProdInst.contains("移印") || factoryProdInst.contains("丝印")) && !factoryProdInst.contains("客制")) {
            needFullTextSearch = true;
        }

        if (result.get("_widget_1749437052124").isEmpty() &&
                ((factoryProdInst.contains("移印") && factoryProdInst.contains("客制")) ||
                        factoryProdInst.contains("移印客制") || factoryProdInst.contains("客制移印"))) {
            needFullTextSearch = true;
        }

        if (result.get("_widget_1749434759621").isEmpty() &&
                (factoryProdInst.contains("彩喷") || factoryProdInst.contains("彩印"))) {
            needFullTextSearch = true;
        }

        if (result.get("_widget_1749434759622").isEmpty() &&
                ((factoryProdInst.contains("镭雕") || factoryProdInst.contains("金手指面"))
                        && !factoryProdInst.contains("非金手指面"))) {
            needFullTextSearch = true;
        }

        if (result.get("_widget_1749434759623").isEmpty() && factoryProdInst.contains("彩卡")) {
            needFullTextSearch = true;
        }

        if (result.get("_widget_1749434759624").isEmpty() && factoryProdInst.contains("彩盒")) {
            needFullTextSearch = true;
        }

        if (result.get("_widget_1749467309784").isEmpty() &&
                (factoryProdInst.contains("客制箱唛") || factoryProdInst.contains("箱唛贴纸") ||
                        (factoryProdInst.contains("箱唛") && factoryProdInst.contains("贴纸")))) {
            needFullTextSearch = true;
        }

        if (result.get("_widget_1749467309785").isEmpty() &&
                (factoryProdInst.contains("不干胶") || factoryProdInst.contains("不干胶贴纸"))) {
            needFullTextSearch = true;
        }

        if (needFullTextSearch) {
            java.util.regex.Matcher matcher = docNumberPattern.matcher(factoryProdInst);
            while (matcher.find()) {
                String docNumber = matcher.group();
                // 查找该图档号前后20个字符的上下文
                int start = Math.max(0, matcher.start() - 20);
                int end = Math.min(factoryProdInst.length(), matcher.end() + 20);
                String context = factoryProdInst.substring(start, end);

                // 根据上下文判断图档号类型，按优先级顺序检查
                processFullTextMatch(context, docNumber, result);
            }
        }
    }

    /**
     * 处理全文搜索匹配
     */
    private void processFullTextMatch(String context, String docNumber, Map<String, String> result) {
        // 检查图档号是否在关键词之后
        int docPos = context.indexOf(docNumber);

        // 移印客制中性图档 - 最高优先级
        if (result.get("_widget_1749437052124").isEmpty() &&
                (context.contains("移印客制") || context.contains("客制移印") ||
                        (context.contains("移印") && context.contains("客制")) ||
                        (context.contains("中性") && context.contains("图档")))) {

            int keywordPos = findKeywordPosition(context, new String[] { "移印客制", "客制移印", "移印", "客制", "中性" });
            if (keywordPos >= 0 && docPos > keywordPos) {
                result.put("_widget_1749437052124", docNumber);
                return;
            }
        }

        // 移印图档号
        if (result.get("_widget_1749434759620").isEmpty() &&
                (context.contains("移印") || context.contains("丝印")) && !context.contains("客制")) {

            int keywordPos = findKeywordPosition(context, new String[] { "移印", "丝印" });
            if (keywordPos >= 0 && docPos > keywordPos) {
                result.put("_widget_1749434759620", docNumber);
                return;
            }
        }

        // 彩喷图档号
        if (result.get("_widget_1749434759621").isEmpty() &&
                (context.contains("彩喷") || context.contains("彩印"))) {

            int keywordPos = findKeywordPosition(context, new String[] { "彩喷", "彩印" });
            if (keywordPos >= 0 && docPos > keywordPos) {
                result.put("_widget_1749434759621", docNumber);
                return;
            }
        }

        // 镭雕图档号
        if (result.get("_widget_1749434759622").isEmpty() &&
                ((context.contains("镭雕") || context.contains("金手指面")) && !context.contains("非金手指面"))) {

            int keywordPos = findKeywordPosition(context, new String[] { "镭雕", "金手指面" });
            if (keywordPos >= 0 && docPos > keywordPos) {
                result.put("_widget_1749434759622", docNumber);
                return;
            }
        }

        // 彩卡图档号
        if (result.get("_widget_1749434759623").isEmpty() && context.contains("彩卡")) {
            int keywordPos = context.indexOf("彩卡");
            if (keywordPos >= 0 && docPos > keywordPos) {
                result.put("_widget_1749434759623", docNumber);
                return;
            }
        }

        // 彩盒图档号
        if (result.get("_widget_1749434759624").isEmpty() && context.contains("彩盒")) {
            int keywordPos = context.indexOf("彩盒");
            if (keywordPos >= 0 && docPos > keywordPos) {
                result.put("_widget_1749434759624", docNumber);
                return;
            }
        }

        // 客制箱唛贴纸
        if (result.get("_widget_1749467309784").isEmpty() &&
                (context.contains("客制箱唛") || context.contains("箱唛贴纸") ||
                        (context.contains("箱唛") && context.contains("贴纸")))) {

            int keywordPos = findKeywordPosition(context, new String[] { "客制箱唛", "箱唛贴纸", "箱唛" });
            if (keywordPos >= 0 && docPos > keywordPos) {
                result.put("_widget_1749467309784", docNumber);
                return;
            }
        }

        // 不干胶贴纸
        if (result.get("_widget_1749467309785").isEmpty() &&
                (context.contains("不干胶") || context.contains("不干胶贴纸") ||
                        (context.contains("贴纸") && !context.contains("箱唛")))) {

            int keywordPos = findKeywordPosition(context, new String[] { "不干胶", "不干胶贴纸", "贴纸" });
            if (keywordPos >= 0 && docPos > keywordPos) {
                result.put("_widget_1749467309785", docNumber);
            }
        }
    }

    /**
     * 智能提取卷标内容
     * 优先从文本直接提取，如果没有则根据型号从数据库查找
     * 
     * @param text 文本
     * @return 卷标内容
     */
    public String extractVolumeLabelIntelligently(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // 第一步：尝试直接从文本提取卷标
        String directLabel = extractVolumeLabel(text);

        if (!directLabel.isEmpty()) {
            return directLabel;
        }

        // 第二步：尝试从型号数据库查找
        Map<String, Object> modelInfo = productInfoDatabase.extractModelAndCapacity(text);

        if (!modelInfo.isEmpty()) {
            String model = (String) modelInfo.get("model");
            Integer capacity = (Integer) modelInfo.get("capacity");

            Map<String, String> dbProductInfo = productInfoDatabase.getProductInfo(model, capacity);

            if (!dbProductInfo.isEmpty() && dbProductInfo.containsKey("volume_name")) {
                String dbVolumeLabel = dbProductInfo.get("volume_name");
                return dbVolumeLabel;
            }
        }

        return "";
    }

    /**
     * 从文本中提取卷标内容（原有方法）
     * 
     * @param text 文本
     * @return 卷标内容
     */
    public String extractVolumeLabel(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // 多种卷标格式的正则表达式，支持中英文混合、特殊字符和多种分隔符
        String[] volumeLabelPatterns = {
                // 烧录卷标格式 - 最高优先级，支持特殊字符
                "烧录卷标[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s+\\-_./()（）]+?)(?=\\s*[；;，。]|\\s*其他|\\s*\\d+\\.|$)", // 烧录卷标：EVM
                                                                                                               // Nano+
                // 卷标烧录格式 - 支持"卷标烧录：EVM EnX"这种格式
                "卷标烧录[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s+\\-_./()（）]+?)(?=\\s*[；;，。]|\\s*其他|\\s*\\d+\\.|$)", // 卷标烧录：EVM EnX
                // 英文格式 - 支持特殊字符
                "Volume\\s+name[：:]\\s*([A-Za-z0-9\\s+\\-_./()（）]+?)(?=\\s+R/W|\\s+Actual|\\s*[；;，。]|\\s*$)", // Volume
                                                                                                              // name：Lexar
                // 中文格式 - 支持中文字符和特殊字符
                "卷标[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s+\\-_./()（）]+?)(?=\\s*[；;，。]|\\s*(?:VID|PID|厂商|产品|文件|其他|\\d+\\.|$))", // 卷标：小绿魔+
                // 简化格式 - 数字序号后的卷标，支持特殊字符
                "\\d+\\.?\\s*(?:烧录)?卷标[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s+\\-_./()（）]+?)(?=\\s*[；;，。]|\\s*其他|\\s*\\d+\\.|$)", // 1.
                                                                                                                                // 卷标：小绿魔+
                // 混合格式 - 支持空格分隔的多词卷标和特殊字符
                "卷标[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s+\\-_./()（）]+?)(?=\\s*[；;，。]|\\s*VID|\\s*PID|\\s*厂商|\\s*产品|\\s*文件|\\s*其他|\\s*\\d+\\.|$)",
                // 通用格式 - 支持所有字符
                "(?:烧录)?卷标[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s+\\-_./()（）]+)"
        };

        for (String patternStr : volumeLabelPatterns) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String result = matcher.group(1).trim();

                // 智能清理结果，保留有效的特殊字符如+、-等
                // 移除尾部的数字序号（如 "1."、"2."）
                result = result.replaceAll("\\s*\\d+\\.$", "").trim();

                // 移除尾部的中文标点，但保留+、-等有效字符
                result = result.replaceAll("[，。；]+$", "").trim();

                // 移除尾部的英文分号，但保留其他有效字符
                result = result.replaceAll(";+$", "").trim();

                // 特殊处理：如果结果以"其他"等词结尾，移除这些词
                result = result.replaceAll("\\s*其他.*$", "").trim();

                if (!result.isEmpty()) {
                    return result;
                }
            }
        }

        return "";
    }

    /**
     * 智能提取产品信息
     * 优先从文本直接提取，如果没有则根据型号从数据库查找
     * 
     * @param text 文本
     * @return 产品信息映射
     */
    private Map<String, String> extractProductInfoIntelligently(String text) {
        Map<String, String> result = new HashMap<>();
        result.put("_widget_1750382988528", ""); // VID
        result.put("_widget_1750382988529", ""); // PID
        result.put("_widget_1750382988524", ""); // 厂商名
        result.put("_widget_1750382988526", ""); // 产品名
        result.put("_widget_1750389457663", ""); // 文件格式

        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        // 第一步：尝试直接从文本提取产品信息
        Map<String, String> directExtracted = extractProductInfo(text);

        // 检查是否成功提取到关键信息
        boolean hasVID = !directExtracted.get("_widget_1750382988528").isEmpty();
        boolean hasPID = !directExtracted.get("_widget_1750382988529").isEmpty();
        boolean hasVendor = !directExtracted.get("_widget_1750382988524").isEmpty();
        boolean hasProduct = !directExtracted.get("_widget_1750382988526").isEmpty();
        boolean hasFileSystem = !directExtracted.get("_widget_1750389457663").isEmpty();

        // 如果直接提取到了完整信息，直接返回
        if (hasVID && hasPID && hasVendor && hasProduct && hasFileSystem) {
            return directExtracted;
        }

        // 第二步：尝试从型号数据库查找
        Map<String, Object> modelInfo = productInfoDatabase.extractModelAndCapacity(text);

        if (!modelInfo.isEmpty()) {
            String model = (String) modelInfo.get("model");
            Integer capacity = (Integer) modelInfo.get("capacity");



            Map<String, String> dbProductInfo = productInfoDatabase.getProductInfo(model, capacity);

            if (!dbProductInfo.isEmpty()) {
                // 使用数据库信息填充缺失的字段
                if (!hasVID && dbProductInfo.containsKey("vid")) {
                    result.put("_widget_1750382988528", dbProductInfo.get("vid"));
                } else {
                    result.put("_widget_1750382988528", directExtracted.get("_widget_1750382988528"));
                }

                if (!hasPID && dbProductInfo.containsKey("pid")) {
                    result.put("_widget_1750382988529", dbProductInfo.get("pid"));
                } else {
                    result.put("_widget_1750382988529", directExtracted.get("_widget_1750382988529"));
                }

                if (!hasVendor && dbProductInfo.containsKey("vendor_str")) {
                    result.put("_widget_1750382988524", dbProductInfo.get("vendor_str"));
                } else {
                    result.put("_widget_1750382988524", directExtracted.get("_widget_1750382988524"));
                }

                if (!hasProduct && dbProductInfo.containsKey("product_str")) {
                    result.put("_widget_1750382988526", dbProductInfo.get("product_str"));
                } else {
                    result.put("_widget_1750382988526", directExtracted.get("_widget_1750382988526"));
                }

                if (!hasFileSystem && dbProductInfo.containsKey("file_system")) {
                    result.put("_widget_1750389457663", dbProductInfo.get("file_system"));
                } else {
                    result.put("_widget_1750389457663", directExtracted.get("_widget_1750389457663"));
                }

                // 同时提取卷标信息
                if (dbProductInfo.containsKey("volume_name")) {
                    // 这里可以考虑也设置卷标字段，但需要在调用处处理
                }
                return result;
            }
        }

        // 如果都没有找到，返回直接提取的结果（可能部分为空）
        return directExtracted;
    }

    /**
     * 从文本中提取产品信息（原有方法）
     * 
     * @param text 文本
     * @return 产品信息映射
     */
    public Map<String, String> extractProductInfo(String text) {
        Map<String, String> result = new HashMap<>();
        result.put("_widget_1750382988528", ""); // VID
        result.put("_widget_1750382988529", ""); // PID
        result.put("_widget_1750382988524", ""); // 厂商名
        result.put("_widget_1750382988526", ""); // 产品名
        result.put("_widget_1750389457663", ""); // 文件格式

        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        // 提取VID - 支持多种格式，包括数字序号格式和VID/PID格式
        String[] vidPatterns = {
                "VID/PID[：:]\\s*([A-Fa-f0-9]{4})/[A-Fa-f0-9]{4}", // VID/PID:21C4/0CD1 格式
                "\\d+\\.?\\s*VID[：:]\\s*([A-Fa-f0-9]+)(?=\\s*\\d+\\.|\\s*PID|\\s*厂商|\\s*产品|\\s*文件|$)", // 2. VID：3535 格式
                "VID[：:]\\s*([A-Fa-f0-9]+)(?=\\s*PID|\\s*厂商|\\s*产品|\\s*文件|\\s*\\d+\\.|$)", // VID: 18A5 格式
                "VID[：:]\\s*([A-Fa-f0-9]+)"
        };

        for (String vidPattern : vidPatterns) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(vidPattern,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String vid = matcher.group(1).trim().toUpperCase().replaceAll("[^A-F0-9]", "");
                // 支持3位或4位十六进制VID
                if (vid.length() >= 3 && vid.length() <= 4) {
                    // 如果是3位，补齐为4位
                    if (vid.length() == 3) {
                        vid = "0" + vid;
                    }
                    result.put("_widget_1750382988528", vid);
                    break;
                }
            }
        }

        // 提取PID - 支持多种格式，包括数字序号格式和VID/PID格式
        String[] pidPatterns = {
                "VID/PID[：:]\\s*[A-Fa-f0-9]{4}/([A-Fa-f0-9]{4})", // VID/PID:21C4/0CD1 格式
                "\\d+\\.?\\s*PID[：:]\\s*([A-Fa-f0-9]+)(?=\\s*\\d+\\.|\\s*厂商|\\s*产品|\\s*文件|$)", // 4. PID：764 格式
                "PID[：:]\\s*([A-Fa-f0-9]+)(?=\\s*厂商|\\s*产品|\\s*文件|\\s*\\d+\\.|$)", // PID: 0251 格式
                "PID[：:]\\s*([A-Fa-f0-9]+)"
        };

        for (String pidPattern : pidPatterns) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(pidPattern,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String pid = matcher.group(1).trim().toUpperCase().replaceAll("[^A-F0-9]", "");
                // 支持3位或4位十六进制PID
                if (pid.length() >= 3 && pid.length() <= 4) {
                    // 如果是3位，补齐为4位
                    if (pid.length() == 3) {
                        pid = "0" + pid;
                    }
                    result.put("_widget_1750382988529", pid);
                    break;
                }
            }
        }

        // 提取厂商名 - 支持多种格式，包括中英文混合格式
        String[] vendorPatterns = {
                // 英文格式
                "Inquiry\\s*-?\\s*Vendor[：:]\\s*([A-Za-z0-9\\s]+?)(?=\\s+Product|\\s+Inquiry|\\s*$)", // Inquiry
                                                                                                      // -Vendor:Lexar
                "Vendor\\s+Str[：:]\\s*([A-Za-z0-9\\s]+?)(?=\\s+Product|\\s+Inquiry|\\s*Volume|\\s*$)", // Vendor
                                                                                                       // Str:Lexar
                // 中文格式 - 支持数字序号
                "\\d+\\.?\\s*厂商名[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s]+?)(?=\\s*\\d+\\.|\\s*产品名|\\s*文件|$)", // 3.
                                                                                                            // 厂商名：aigo
                "厂商名&厂商信息[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s]+?)(?=\\s*产品名|\\s*文件|$)", // 厂商名&厂商信息: Verbatim
                "厂商名[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s]+?)(?=\\s*\\d+\\.|\\s*产品名|\\s*文件|$)", // 厂商名: aigo
                "厂商名[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s]+)"
        };

        for (String vendorPattern : vendorPatterns) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(vendorPattern,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String vendor = matcher.group(1).trim();
                // 清理尾部的数字序号和标点
                vendor = vendor.replaceAll("\\s*\\d+\\.$", "").trim(); // 移除尾部数字序号
                vendor = vendor.replaceAll("[，。；;]+$", "").trim(); // 移除尾部标点
                if (!vendor.isEmpty()) {
                    result.put("_widget_1750382988524", vendor);
                    break;
                }
            }
        }

        // 提取产品名 - 支持多种格式，包括中英文混合格式
        String[] productPatterns = {
                // 英文格式
                "Inquiry\\s*-?\\s*product[：:]\\s*([A-Za-z0-9\\s]+?)(?=\\s+Volume|\\s+R/W|\\s*$)", // Inquiryproduct:USB
                                                                                                  // Flash Drive
                "Product\\s+Str[：:]\\s*([A-Za-z0-9\\s]+?)(?=\\s+Inquiry|\\s+Volume|\\s*$)", // Product Str:USB Flash
                                                                                            // Drive
                // 中文格式 - 支持数字序号和括号内容
                "\\d+\\.?\\s*产品名[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s]+?)(?=（|\\s*\\d+\\.|\\s*文件|$)", // 5.
                                                                                                      // 产品名：C2（不支持中文命名）
                "产品名&产品信息[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s]+?)(?=\\s*文件格式|\\s*文件|$)", // 产品名&产品信息: STORE N GO
                "产品名[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s]+?)(?=（|\\s*\\d+\\.|\\s*文件|$)", // 产品名：C2（不支持中文命名）
                "产品名[：:]\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s]+)"
        };

        for (String productPattern : productPatterns) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(productPattern,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String product = matcher.group(1).trim();
                // 清理尾部的数字序号和标点
                product = product.replaceAll("\\s*\\d+\\.$", "").trim(); // 移除尾部数字序号
                product = product.replaceAll("[，。；;]+$", "").trim(); // 移除尾部标点
                if (!product.isEmpty()) {
                    result.put("_widget_1750382988526", product);
                    break;
                }
            }
        }

        // 提取文件格式 - 支持多种表达方式和格式
        String[] formatPatterns = {
                // 英文格式
                "File\\s+system[：:]\\s*(FAT32|NTFS|exFAT|EXT4|FAT16)(?=\\s|，|。|VID|$)", // File system:FAT32
                // 中文格式 - 支持数字序号
                "\\d+\\.?\\s*文件系统[：:]\\s*(FAT32|NTFS|exFAT|EXT4|FAT16)(?=\\s|，|。|内嵌|$)", // 6. 文件系统：FAT32
                "文件格式[：:]\\s*(FAT32|NTFS|exFAT|EXT4|FAT16)(?=\\s|，|。|提前|$)", // 文件格式: FAT32
                "文件系统[：:]\\s*(FAT32|NTFS|exFAT|EXT4|FAT16)(?=\\s|，|。|内嵌|$)" // 文件系统：FAT32
        };

        for (String formatPattern : formatPatterns) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(formatPattern,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String format = matcher.group(1).trim().toUpperCase();
                result.put("_widget_1750389457663", format);
                break;
            }
        }

        return result;
    }

    /**
     * 生成物料需求清单的汇总文本
     * 
     * @param requireComponents 物料需求清单
     * @return 汇总文本
     */
    public String generateRequireComponentSummary(List<Map<String, Object>> requireComponents) {
        if (requireComponents == null || requireComponents.isEmpty()) {
            return "";
        }

        // 创建一个列表来存储有效的物料信息
        List<String> validComponents = new ArrayList<>();

        // 假设映射中require_item_number对应_widget_1742269557590，requirement_quantity对应_widget_1742269557596
        for (Map<String, Object> component : requireComponents) {
            String itemNumber = "";
            String quantity = "";

            // 提取组件料号
            if (component.containsKey("_widget_1742269557590")) {
                Object widgetObj = component.get("_widget_1742269557590");
                if (widgetObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> widgetMap = (Map<String, Object>) widgetObj;
                    Object itemObj = widgetMap.get("value");
                    if (itemObj != null) {
                        itemNumber = itemObj.toString().trim();
                    }
                }
            }

            // 提取需求量
            if (component.containsKey("_widget_1742269557596")) {
                Object widgetObj = component.get("_widget_1742269557596");
                if (widgetObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> widgetMap = (Map<String, Object>) widgetObj;
                    Object qtyObj = widgetMap.get("value");
                    if (qtyObj != null) {
                        quantity = qtyObj.toString().trim();
                    }
                }
            }

            // 如果两个字段都有值，则添加到列表中
            if (!itemNumber.isEmpty() && !quantity.isEmpty()) {
                validComponents.add(itemNumber + "*" + quantity);
            }
        }

        // 对物料清单按料号进行排序，确保顺序一致
        Collections.sort(validComponents);

        // 组合成最终的汇总文本
        return String.join("+", validComponents);
    }

    /**
     * 转换物料数据
     * 
     * @param record 原始物料数据
     * @return 转换后的数据
     */
    public Map<String, Object> convertItemData(Map<String, Object> record) {
        Map<String, Object> converted = new HashMap<>();
        try {
            // 获取物料字段映射配置
            FieldMappingConfig fieldMappingConfig = FieldMappingConfig.getInstance();
            Map<String, String> itemFieldMapping = fieldMappingConfig.getItemFields();

            // 主表字段处理
            for (Map.Entry<String, String> entry : itemFieldMapping.entrySet()) {
                String srcField = entry.getKey();
                String destField = entry.getValue();

                // 处理字段名映射
                String recordField = mapItemFieldName(srcField);
                Object value = record.get(recordField);
                converted.put(destField, Collections.singletonMap("value",
                        value == null ? "" : value.toString().trim()));
            }

            // 根据product_category生成产品类型并添加到新字段
            String productCategory = getStringValue(record.get("product_category"));
            String productType = determineItemProductType(productCategory);
            converted.put(Constants.WIDGET_ITEM_PRODUCT_TYPE, Collections.singletonMap("value", productType));

        } catch (Exception e) {
            LogUtil.logError("物料数据转换异常: " + e.getMessage());
            return null;
        }
        return converted;
    }

    /**
     * 映射物料字段名
     * 
     * @param srcField 源字段名
     * @return 映射后的字段名
     */
    private String mapItemFieldName(String srcField) {
        switch (srcField) {
            case "customer_sku":
                return "custumer_sku";
            case "customer_pn":
                return "custumer_pn";
            case "outer_packing_quantity":
                return "outter_packing_quantity";
            case "outer_box_size":
                return "outter_box_size";
            case "total_weight_of_outer":
                return "total_weight_of_outter";
            default:
                return srcField;
        }
    }

    /**
     * 根据产品类别确定物料产品类型
     * 
     * @param productCategory 产品类别
     * @return 产品类型
     */
    private String determineItemProductType(String productCategory) {
        if (productCategory == null || productCategory.trim().isEmpty()) {
            return "";
        }

        String upperCategory = productCategory.toUpperCase();

        if (upperCategory.contains("MICRO SD")) {
            return "TF";
        } else if (upperCategory.contains("UPA")) {
            return "UPA";
        } else if (upperCategory.contains("UDP")) {
            return "UDP";
        } else if (upperCategory.contains("TUP")) {
            return "UPA";
        } else if (upperCategory.contains("USB")) {
            return "U盘";
        } else if (upperCategory.contains("SD")) {
            return "SD";
        }

        return "";
    }

    /**
     * 针对性卷标提取方法 - 专门处理特定格式的卷标信息
     * 针对测试用例："第三批交期需求9/231. 卷标烧录：EVM EnX , 其他信息依照我司默认即可。"
     * 和"2. 烧录信息要求：卷标请烧录EVM Nano+ （注意带+号），其他信息依照我司默认即可。"
     * 
     * @param text 文本
     * @return 卷标内容
     */
    public String extractVolumeLabelTargeted(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // 专门针对用户提供的测试用例格式
        String[] targetedPatterns = {
                // 针对"卷标烧录：EVM EnX , 其他信息..."格式
                "卷标烧录[：:]\\s*([A-Za-z0-9\\s+\\-_./()（）]+?)(?=\\s*[，,。；;]|\\s*其他|\\s*\\d+\\.|$)",
                // 针对数字序号格式："1. 卷标烧录：EVM EnX"
                "\\d+\\.?\\s*卷标烧录[：:]\\s*([A-Za-z0-9\\s+\\-_./()（）]+?)(?=\\s*[，,。；;]|\\s*其他|\\s*\\d+\\.|$)",
                // 针对"卷标请烧录EVM Nano+ （注意带+号）"格式
                "卷标请烧录\\s*([A-Za-z0-9\\s+\\-_./()（）]+?)(?=\\s*[（(]|\\s*注意|\\s*其他|\\s*\\d+\\.|$)",
                // 针对"烧录信息要求：卷标请烧录EVM Nano+ "格式
                "烧录信息要求[：:]\\s*卷标请烧录\\s*([A-Za-z0-9\\s+\\-_./()（）]+?)(?=\\s*[（(]|\\s*注意|\\s*其他|\\s*\\d+\\.|$)",
                // 更宽松的匹配，提取冒号后的内容直到逗号、分号或括号
                "卷标烧录[：:]\\s*([^，,。；;(（\\d]+?)(?=\\s*[，,。；;(（]|\\s*其他|\\s*\\d+\\.|$)",
                // 支持带+号的卷标格式
                "卷标(?:请)?烧录[：:]?\\s*([A-Za-z0-9\\s+\\-_./()（）]+?)(?=\\s*[（(]|\\s*注意|\\s*其他|\\s*\\d+\\.|$)"
        };

        for (String patternStr : targetedPatterns) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String result = matcher.group(1).trim();

                // 清理结果
                result = result.replaceAll("^[，,。；;(（]+", "").trim(); // 移除开头标点
                result = result.replaceAll("[，,。；;)(）]+$", "").trim(); // 移除结尾标点
                result = result.replaceAll("\\s*其他.*$", "").trim(); // 移除"其他"及之后内容
                result = result.replaceAll("\\s*注意.*$", "").trim(); // 移除"注意"及之后内容

                if (!result.isEmpty()) {
                    return result;
                }
            }
        }


        return "";
    }

}