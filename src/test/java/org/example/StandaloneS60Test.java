package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 独立的S60产品信息提取测试
 * 不依赖主程序的任何初始化，直接复制核心逻辑进行测试
 */
public class StandaloneS60Test {

    private static Map<String, Map<String, String>> productDatabase = new HashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 型号和容量提取的正则表达式
    // 匹配格式：FOR Lexar S60 32GB 或 FOR Lexar S60 或 S60 32GB
    // 容量必须紧跟GB才有效，避免将序号误识别为容量
    private static final Pattern MODEL_CAPACITY_PATTERN = Pattern.compile(
            "(?:FOR\\s+)?(?:Lexar\\s+)?([A-Z]\\d+[A-Z]?|[A-Z]+\\d+)(?:\\s+(\\d+)\\s*GB)?",
            Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("S60产品信息提取独立测试");
        System.out.println("========================================\n");

        // 加载产品数据库
        if (!loadProductDatabase()) {
            System.out.println("✗ 产品数据库加载失败");
            return;
        }

        // 测试文本
        String testText = "1、FOR Lexar S60 2、文件参考：U-00173 Lexar品牌USB产品生产参数清单 " +
                "3、包装文件参考：迈仕渡UDP模块产品工业包装规范 4、主机ZJCL003X0132，返工测试，全匹 " +
                "5、订单需求3k#按雷克沙生产清单执行即可，参考文件U-00173  Lexar品牌USB产品生产参数清单#For V40/S60/TT2";

        System.out.println("【测试1】从文本中提取型号和容量");
        System.out.println("测试文本: " + testText);
        System.out.println();

        // 提取型号和容量
        Map<String, Object> modelInfo = extractModelAndCapacity(testText);

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
            Map<String, String> productInfo = getProductInfo(model, capacity);

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
                "For V40/S60/TT2",
                "按雷克沙生产清单执行即可，参考文件U-00173  Lexar品牌USB产品生产参数清单#For V40/S60/TT2"
        };

        for (String text : testTexts) {
            Map<String, Object> info = extractModelAndCapacity(text);
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

        // 测试正则表达式的匹配细节
        System.out.println("\n【测试4】正则表达式匹配细节分析");
        testRegexMatching(testText);

        System.out.println("\n========================================");
        System.out.println("测试完成");
        System.out.println("========================================");
    }

    /**
     * 加载产品信息数据库
     */
    private static boolean loadProductDatabase() {
        try {
            File configFile = new File("product_info_database.json");
            if (!configFile.exists()) {
                System.out.println("✗ 产品信息数据库文件不存在: product_info_database.json");
                return false;
            }

            JsonNode rootNode = objectMapper.readTree(configFile);
            JsonNode productsNode = rootNode.get("products");

            if (productsNode != null) {
                productsNode.fields().forEachRemaining(entry -> {
                    String model = entry.getKey();
                    JsonNode productInfo = entry.getValue();

                    Map<String, String> info = new HashMap<>();
                    productInfo.fields().forEachRemaining(field -> {
                        info.put(field.getKey(), field.getValue().asText());
                    });

                    productDatabase.put(model.toUpperCase(), info);
                });
            }

            System.out.println("✓ 成功加载产品信息数据库，包含 " + productDatabase.size() + " 个产品型号");
            System.out.println("  支持的型号: " + productDatabase.keySet());
            System.out.println();

            return true;

        } catch (Exception e) {
            System.out.println("✗ 加载产品信息数据库失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 从文本中提取型号和容量信息
     */
    private static Map<String, Object> extractModelAndCapacity(String text) {
        Map<String, Object> result = new HashMap<>();

        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        Matcher matcher = MODEL_CAPACITY_PATTERN.matcher(text);
        if (matcher.find()) {
            String model = matcher.group(1);
            String capacityStr = matcher.group(2);

            result.put("model", model);

            if (capacityStr != null && !capacityStr.trim().isEmpty()) {
                try {
                    Integer capacity = Integer.parseInt(capacityStr);
                    result.put("capacity", capacity);
                } catch (NumberFormatException e) {
                    System.out.println("解析容量失败: " + capacityStr);
                }
            }
        }

        return result;
    }

    /**
     * 根据型号和容量获取产品信息
     */
    private static Map<String, String> getProductInfo(String model, Integer capacityGB) {
        if (model == null || model.trim().isEmpty()) {
            return new HashMap<>();
        }

        String upperModel = model.toUpperCase().trim();
        Map<String, String> baseInfo = productDatabase.get(upperModel);

        if (baseInfo == null) {
            return new HashMap<>();
        }

        // 创建返回结果的副本
        Map<String, String> result = new HashMap<>(baseInfo);

        // 特殊处理：只有S80和D400需要根据容量判断文件系统
        if ("S80".equals(upperModel) || "D400".equals(upperModel)) {
            if (capacityGB != null && capacityGB >= 512) {
                // S80和D400在≥512GB时使用exFAT
                String specialFileSystem = baseInfo.get("file_system_512gb_plus");
                if (specialFileSystem != null && !specialFileSystem.isEmpty()) {
                    result.put("file_system", specialFileSystem);
                } else {
                    result.put("file_system", "exFAT");
                }
            } else if (capacityGB == null) {
                // S80和D400识别不到容量时默认FAT32
                result.put("file_system", "FAT32");
            }
            // 如果有容量但<512GB，使用数据库中的file_system值（已在baseInfo中）
        }
        // 其他型号直接使用数据库中的file_system值，不需要根据容量判断

        return result;
    }

    /**
     * 测试正则表达式的匹配细节
     */
    private static void testRegexMatching(String text) {
        System.out.println("原始文本: " + text);
        System.out.println("\n正则表达式: " + MODEL_CAPACITY_PATTERN.pattern());
        System.out.println("\n匹配过程:");

        Matcher matcher = MODEL_CAPACITY_PATTERN.matcher(text);
        int matchCount = 0;

        while (matcher.find()) {
            matchCount++;
            System.out.println("\n  匹配 #" + matchCount + ":");
            System.out.println("    完整匹配: \"" + matcher.group(0) + "\"");
            System.out.println("    位置: " + matcher.start() + " - " + matcher.end());
            System.out.println("    型号 (group 1): \"" + matcher.group(1) + "\"");
            if (matcher.group(2) != null) {
                System.out.println("    容量 (group 2): \"" + matcher.group(2) + "\"");
            }
        }

        if (matchCount == 0) {
            System.out.println("  ✗ 没有找到匹配项");
        } else {
            System.out.println("\n  ✓ 共找到 " + matchCount + " 个匹配项");
        }
    }
}
