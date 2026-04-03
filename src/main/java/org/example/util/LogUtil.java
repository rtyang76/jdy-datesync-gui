package org.example.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日志工具类
 * 提供统一的日志输出方法
 */
public class LogUtil {
    private static final DateTimeFormatter LOG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 输出信息日志
     * @param message 日志消息
     */
    public static void logInfo(String message) {
        System.out.println(String.format("[%s] [INFO] %s",
                LocalDateTime.now().format(LOG_FORMATTER), message));
    }

    /**
     * 输出错误日志
     * @param message 日志消息
     */
    public static void logError(String message) {
        System.err.println(String.format("[%s] [ERROR] %s",
                LocalDateTime.now().format(LOG_FORMATTER), message));
    }

    /**
     * 输出警告日志
     * @param message 日志消息
     */
    public static void logWarning(String message) {
        System.out.println(String.format("[%s] [WARN] %s",
                LocalDateTime.now().format(LOG_FORMATTER), message));
    }

    /**
     * 输出调试日志
     * @param message 日志消息
     */
    public static void logDebug(String message) {
        System.out.println(String.format("[%s] [DEBUG] %s",
                LocalDateTime.now().format(LOG_FORMATTER), message));
    }
} 