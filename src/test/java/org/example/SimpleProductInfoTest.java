package org.example;

import org.example.config.ProductInfoDatabase;

import java.util.Map;

/**
 * 简单的产品信息提取测试
 */
public class SimpleProductInfoTest {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("S60产品信息提取测试");
        System.out.println("========================================\n");
        
        // 初始化产品信息数据库
        ProductInfoDatabase productInfoDatabase = ProductInfoDatabase.getInstance();
        
        // 测试文本
        String testText = "1、FOR Lexar S60 2、文件参考：U-00173 Lexar品牌USB产品生产参数清单 " +
                "3、包装文件参考：迈仕渡UDP模块产品工业包装规范 4、主机ZJCL003X0132，返工测试，全匹 " +
                "5、订单需求3k#按雷克沙生产清单执行即可，参考文件U-00173  Lexar品牌USB产品生产参数清单#For V40/S60/TT2";
        
        System.out.println("【测试1】从文本中提取型号和容量");
        System.out.println("测试文本: " + testText);
        System.out.println();
        
        // 提取型号和容量
        Map<String, Object> modelInfo = productInfoDatabase.extractModelAndCapacity(testText);
        
        if (!modelInfo.isEmpty()) {
            String model = (String) modelInfo.get("model");
            Integer capacity = (Integer) modelInfo.get("capacity");
            
            System.out.println("✓ 成功提取型号: " + model);
            if (capacity != null) {
                System.out.println("✓ 成功提取容量: " + capacity + "GB");
            }
            
            // 验证是否为S60
            if ("S60".equalsIgnoreCase(model)) {
                System.out.println("✓ 正确识别为S60型号");
            } else {
                System.out.println("✗ 识别错误，期望S60，实际: " + model);
            }
            
            // 从数据库获取产品信息
            System.out.println("\n【测试2】从数据库获取S60产品信息");
            Map<String, String> productInfo = productInfoDatabase.getProductInfo(model, capacity);
            
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
                    System.out.println("\n  ✗ VID错误，期望21C4，实际: " + productInfo.get("vid"));
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
                if (!"USB Flash Drive".equals(productInfo.get("product_str"))) {
                    System.out.println("  ✗ 产品名错误，期望USB Flash Drive，实际: " + productInfo.get("product_str"));
                    allCorrect = false;
                }
                if (!"FAT32".equals(productInfo.get("file_system"))) {
                    System.out.println("  ✗ 文件系统错误，期望FAT32，实际: " + productInfo.get("file_system"));
                    allCorrect = false;
                }
                if (!"Lexar".equals(productInfo.get("volume_name"))) {
                    System.out.println("  ✗ 卷标错误，期望Lexar，实际: " + productInfo.get("volume_name"));
                    allCorrect = false;
                }
                
                if (allCorrect) {
                    System.out.println("\n  ✓ 所有字段值正确！");
                }
            } else {
                System.out.println("✗ 未能从数据库获取产品信息");
            }
            
        } else {
            System.out.println("✗ 未能提取到型号信息");
        }
        
        // 测试多种格式
        System.out.println("\n【测试3】测试S60在不同文本格式中的识别");
        String[] testTexts = {
            "FOR Lexar S60",
            "FOR Lexar S60 32GB",
            "Lexar S60 64GB",
            "S60 128GB",
            "For V40/S60/TT2"
        };
        
        for (String text : testTexts) {
            Map<String, Object> info = productInfoDatabase.extractModelAndCapacity(text);
            System.out.print("文本: \"" + text + "\" -> ");
            
            if (!info.isEmpty()) {
                String model = (String) info.get("model");
                Integer capacity = (Integer) info.get("capacity");
                System.out.print("型号: " + model);
                if (capacity != null) {
                    System.out.print(", 容量: " + capacity + "GB");
                }
                
                if ("S60".equalsIgnoreCase(model)) {
                    System.out.println(" ✓");
                } else {
                    System.out.println(" (识别为" + model + ")");
                }
            } else {
                System.out.println("✗ 未识别");
            }
        }
        
        System.out.println("\n========================================");
        System.out.println("测试完成");
        System.out.println("========================================");
    }
}
