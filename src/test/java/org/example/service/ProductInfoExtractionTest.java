package org.example.service;

import org.example.config.ProductInfoDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * 产品信息提取测试类
 * 测试从文本中提取产品型号、VID、PID等字段的功能
 */
public class ProductInfoExtractionTest {

    private DataTransformService transformService;
    private ProductInfoDatabase productInfoDatabase;

    public ProductInfoExtractionTest() {
        transformService = DataTransformService.getInstance();
        productInfoDatabase = ProductInfoDatabase.getInstance();
    }
    
    public static void main(String[] args) {
        ProductInfoExtractionTest test = new ProductInfoExtractionTest();
        
        System.out.println("========================================");
        System.out.println("产品信息提取测试");
        System.out.println("========================================\n");
        
        test.testS60ProductInfoExtraction();
        test.testIntelligentProductInfoExtraction();
        test.testMultipleModelRecognition();
        test.testS60InDifferentFormats();
        
        System.out.println("\n========================================");
        System.out.println("所有测试完成");
        System.out.println("========================================");
    }

    public void testS60ProductInfoExtraction() {
        System.out.println("\n【测试1】S60型号识别和产品信息提取");
        // 测试文本
        String testText = "1、FOR Lexar S60 2、文件参考：U-00173 Lexar品牌USB产品生产参数清单 " +
                "3、包装文件参考：迈仕渡UDP模块产品工业包装规范 4、主机ZJCL003X0132，返工测试，全匹 " +
                "5、订单需求3k#按雷克沙生产清单执行即可，参考文件U-00173  Lexar品牌USB产品生产参数清单#For V40/S60/TT2";

        // 测试型号和容量提取
        Map<String, Object> modelInfo = productInfoDatabase.extractModelAndCapacity(testText);
        
        System.out.println("原始文本: " + testText);
        System.out.println("提取结果: " + modelInfo);
        
        if (!modelInfo.isEmpty()) {
            String model = (String) modelInfo.get("model");
            Integer capacity = (Integer) modelInfo.get("capacity");
            
            System.out.println("✓ 提取的型号: " + model);
            if (capacity != null) {
                System.out.println("✓ 提取的容量: " + capacity + "GB");
            }
            
            if ("S60".equalsIgnoreCase(model)) {
                System.out.println("✓ 成功识别为S60型号");
            } else {
                System.out.println("✗ 识别错误，期望S60，实际: " + model);
            }
        } else {
            System.out.println("✗ 未能提取到型号信息");
        }

        // 测试从数据库获取产品信息
        if (!modelInfo.isEmpty()) {
            String model = (String) modelInfo.get("model");
            Integer capacity = (Integer) modelInfo.get("capacity");
            
            Map<String, String> productInfo = productInfoDatabase.getProductInfo(model, capacity);
            
            System.out.println("\n产品信息数据库查询:");
            System.out.println("查询型号: " + model);
            if (capacity != null) {
                System.out.println("查询容量: " + capacity + "GB");
            }
            
            if (!productInfo.isEmpty()) {
                System.out.println("✓ 成功从数据库获取产品信息:");
                System.out.println("  VID: " + productInfo.get("vid"));
                System.out.println("  PID: " + productInfo.get("pid"));
                System.out.println("  厂商名: " + productInfo.get("vendor_str"));
                System.out.println("  产品名: " + productInfo.get("product_str"));
                System.out.println("  文件系统: " + productInfo.get("file_system"));
                System.out.println("  卷标: " + productInfo.get("volume_name"));
                
                // 验证字段值
                boolean allCorrect = true;
                if (!"21C4".equals(productInfo.get("vid"))) {
                    System.out.println("  ✗ VID错误，期望21C4，实际: " + productInfo.get("vid"));
                    allCorrect = false;
                }
                if (!"0CC7".equals(productInfo.get("pid"))) {
                    System.out.println("  ✗ PID错误，期望0CC7，实际: " + productInfo.get("pid"));
                    allCorrect = false;
                }
                if (!"Lexar".equals(productInfo.get("vendor_str"))) {
                    System.out.println("  ✗ 厂商名错误，期望Lexar，实际: " + productInfo.get("vendor_str"));
                    allCorrect = false;
                }
                if (allCorrect) {
                    System.out.println("  ✓ 所有字段值正确");
                }
            } else {
                System.out.println("✗ 未能从数据库获取产品信息");
            }
        }
    }

    public void testIntelligentProductInfoExtraction() {
        System.out.println("\n【测试2】智能提取产品信息功能");
        // 创建模拟的订单记录
        Map<String, Object> record = new HashMap<>();
        record.put("factory_product_instructions", 
            "1、FOR Lexar S60 2、文件参考：U-00173 Lexar品牌USB产品生产参数清单 " +
            "3、包装文件参考：迈仕渡UDP模块产品工业包装规范 4、主机ZJCL003X0132，返工测试，全匹 " +
            "5、订单需求3k#按雷克沙生产清单执行即可，参考文件U-00173  Lexar品牌USB产品生产参数清单#For V40/S60/TT2");
        record.put("cutomized_work_remark", "");
        record.put("production_requirements", "");
        record.put("product_infomation", "");

        // 合并产品信息
        String mergedInfo = mergeProdInfo(record);
        System.out.println("合并后的产品信息: " + mergedInfo);

        // 测试智能提取卷标
        String volumeLabel = transformService.extractVolumeLabelIntelligently(mergedInfo);
        System.out.println("\n卷标提取:");
        System.out.println("提取的卷标: " + volumeLabel);
        
        if ("Lexar".equals(volumeLabel)) {
            System.out.println("✓ 成功提取到Lexar卷标");
        } else {
            System.out.println("✗ 卷标提取错误，期望Lexar，实际: " + volumeLabel);
        }

        // 测试智能提取产品信息（VID、PID等）
        System.out.println("\n产品信息智能提取:");
        testFullConversion(record);
    }

    public void testMultipleModelRecognition() {
        System.out.println("\n【测试3】多个型号的识别");
        String[] testTexts = {
            "FOR Lexar S60 32GB",
            "FOR Lexar V40 64GB",
            "FOR Lexar TT2 128GB",
            "For V40/S60/TT2"
        };
        
        for (String text : testTexts) {
            Map<String, Object> modelInfo = productInfoDatabase.extractModelAndCapacity(text);
            System.out.println("\n文本: " + text);
            
            if (!modelInfo.isEmpty()) {
                String model = (String) modelInfo.get("model");
                Integer capacity = (Integer) modelInfo.get("capacity");
                
                System.out.println("  ✓ 型号: " + model + (capacity != null ? ", 容量: " + capacity + "GB" : ""));
                
                Map<String, String> productInfo = productInfoDatabase.getProductInfo(model, capacity);
                if (!productInfo.isEmpty()) {
                    System.out.println("  ✓ VID: " + productInfo.get("vid") + ", PID: " + productInfo.get("pid"));
                } else {
                    System.out.println("  ✗ 未能获取产品信息");
                }
            } else {
                System.out.println("  ✗ 未能提取型号");
            }
        }
    }

    public void testS60InDifferentFormats() {
        System.out.println("\n【测试4】S60在不同文本格式中的识别");
        String[] testTexts = {
            "FOR Lexar S60",
            "FOR Lexar S60 32GB",
            "Lexar S60 64GB",
            "S60 128GB",
            "For V40/S60/TT2",
            "按雷克沙生产清单执行即可，参考文件U-00173  Lexar品牌USB产品生产参数清单#For V40/S60/TT2"
        };
        
        for (String text : testTexts) {
            Map<String, Object> modelInfo = productInfoDatabase.extractModelAndCapacity(text);
            System.out.println("\n文本: " + text);
            
            if (!modelInfo.isEmpty()) {
                String model = (String) modelInfo.get("model");
                System.out.println("  ✓ 成功识别型号: " + model);
                
                // 验证是否识别为S60
                if ("S60".equalsIgnoreCase(model)) {
                    Map<String, String> productInfo = productInfoDatabase.getProductInfo(model, null);
                    if (!productInfo.isEmpty()) {
                        System.out.println("  ✓ 成功获取产品信息");
                        System.out.println("    VID: " + productInfo.get("vid"));
                        System.out.println("    PID: " + productInfo.get("pid"));
                    } else {
                        System.out.println("  ✗ 未能获取产品信息");
                    }
                }
            } else {
                System.out.println("  ✗ 未能识别型号");
            }
        }
    }

    /**
     * 辅助方法：合并产品信息
     */
    private String mergeProdInfo(Map<String, Object> record) {
        String factoryProdInst = getStringValue(record.get("factory_product_instructions"));
        String customWorkRemark = getStringValue(record.get("cutomized_work_remark"));
        String prodRequirements = getStringValue(record.get("production_requirements"));
        String prodInfo = getStringValue(record.get("product_infomation"));

        StringBuilder result = new StringBuilder();
        result.append(factoryProdInst);

        if (!customWorkRemark.isEmpty() && !factoryProdInst.contains(customWorkRemark)) {
            if (result.length() > 0) result.append("#");
            result.append(customWorkRemark);
        }

        if (!prodRequirements.isEmpty() && !factoryProdInst.contains(prodRequirements)) {
            if (result.length() > 0) result.append("#");
            result.append(prodRequirements);
        }

        if (!prodInfo.isEmpty() && !factoryProdInst.contains(prodInfo)) {
            if (result.length() > 0) result.append("#");
            result.append(prodInfo);
        }

        return result.toString();
    }

    /**
     * 辅助方法：获取字符串值
     */
    private String getStringValue(Object obj) {
        if (obj == null) return "";
        return obj.toString().trim();
    }

    /**
     * 测试完整的数据转换流程
     */
    private void testFullConversion(Map<String, Object> record) {
        // 添加必要的字段
        record.put("id", 1);
        record.put("job_num", "TEST001");
        record.put("job_status", "已发放");
        record.put("product_category", "UDP");
        
        // 模拟字段映射
        Map<String, String> fieldMapping = new HashMap<>();
        fieldMapping.put("job_num", "_widget_test_job_num");
        
        Map<String, Map<String, String>> subTables = new HashMap<>();
        
        try {
            Map<String, Object> converted = transformService.convertData(record, fieldMapping, subTables);
            
            if (converted != null) {
                System.out.println("✓ 数据转换成功");
                
                boolean allCorrect = true;
                
                // 检查VID字段
                Object vidObj = converted.get("_widget_1750382988528");
                if (vidObj != null && vidObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> vidMap = (Map<String, Object>) vidObj;
                    String vid = (String) vidMap.get("value");
                    System.out.println("  VID: " + vid);
                    if (!"21C4".equals(vid)) {
                        System.out.println("  ✗ VID错误，期望21C4，实际: " + vid);
                        allCorrect = false;
                    }
                }
                
                // 检查PID字段
                Object pidObj = converted.get("_widget_1750382988529");
                if (pidObj != null && pidObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pidMap = (Map<String, Object>) pidObj;
                    String pid = (String) pidMap.get("value");
                    System.out.println("  PID: " + pid);
                    if (!"0CC7".equals(pid)) {
                        System.out.println("  ✗ PID错误，期望0CC7，实际: " + pid);
                        allCorrect = false;
                    }
                }
                
                // 检查厂商名
                Object vendorObj = converted.get("_widget_1750382988524");
                if (vendorObj != null && vendorObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> vendorMap = (Map<String, Object>) vendorObj;
                    String vendor = (String) vendorMap.get("value");
                    System.out.println("  厂商名: " + vendor);
                    if (!"Lexar".equals(vendor)) {
                        System.out.println("  ✗ 厂商名错误，期望Lexar，实际: " + vendor);
                        allCorrect = false;
                    }
                }
                
                // 检查产品名
                Object productObj = converted.get("_widget_1750382988526");
                if (productObj != null && productObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> productMap = (Map<String, Object>) productObj;
                    String product = (String) productMap.get("value");
                    System.out.println("  产品名: " + product);
                    if (!"USB Flash Drive".equals(product)) {
                        System.out.println("  ✗ 产品名错误，期望USB Flash Drive，实际: " + product);
                        allCorrect = false;
                    }
                }
                
                // 检查文件系统
                Object fsObj = converted.get("_widget_1750389457663");
                if (fsObj != null && fsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fsMap = (Map<String, Object>) fsObj;
                    String fileSystem = (String) fsMap.get("value");
                    System.out.println("  文件系统: " + fileSystem);
                    if (!"FAT32".equals(fileSystem)) {
                        System.out.println("  ✗ 文件系统错误，期望FAT32，实际: " + fileSystem);
                        allCorrect = false;
                    }
                }
                
                // 检查卷标
                Object volumeObj = converted.get("_widget_1750382988523");
                if (volumeObj != null && volumeObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> volumeMap = (Map<String, Object>) volumeObj;
                    String volumeLabel = (String) volumeMap.get("value");
                    System.out.println("  卷标: " + volumeLabel);
                    if (!"Lexar".equals(volumeLabel)) {
                        System.out.println("  ✗ 卷标错误，期望Lexar，实际: " + volumeLabel);
                        allCorrect = false;
                    }
                }
                
                if (allCorrect) {
                    System.out.println("  ✓ 所有字段值正确");
                }
            } else {
                System.out.println("✗ 数据转换失败，返回null");
            }
        } catch (Exception e) {
            System.out.println("✗ 数据转换过程中发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
