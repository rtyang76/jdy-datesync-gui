package org.example.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/**
 * HTTP工具类
 * 提供HTTP请求相关的工具方法
 */
public class HttpUtil {
    // 字符集常量
    public static final String CHARSET_UTF8 = "UTF-8";
    
    /**
     * 发送POST请求
     * @param url 请求URL
     * @param jsonBody 请求体JSON字符串
     * @param apiToken API令牌
     * @return 响应内容
     * @throws IOException 如果发生IO异常
     */
    public static String sendPostRequest(String url, String jsonBody, String apiToken) throws IOException {
        URL urlObj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", apiToken);
        conn.setRequestProperty("Content-Type", "application/json; charset=" + CHARSET_UTF8);
        conn.setRequestProperty("Accept-Charset", CHARSET_UTF8);
        conn.setRequestProperty("X-Request-ID", UUID.randomUUID().toString());
        conn.setConnectTimeout(10000); // 设置连接超时
        conn.setReadTimeout(30000); // 设置读取超时
        conn.setDoOutput(true);

        // 写入请求体
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(CHARSET_UTF8);
            os.write(input, 0, input.length);
        }

        // 获取响应
        String responseContent = getResponseContent(conn);
        conn.disconnect();
        
        return responseContent;
    }
    
    /**
     * 获取HTTP连接响应内容
     * @param conn HTTP连接
     * @return 响应内容字符串
     * @throws IOException 如果发生IO异常
     */
    public static String getResponseContent(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        BufferedReader br = null;
        StringBuilder responseContent = new StringBuilder();

        try {
            if (responseCode >= 200 && responseCode < 300) {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream(), CHARSET_UTF8));
            } else {
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), CHARSET_UTF8));
            }

            String line;
            while ((line = br.readLine()) != null) {
                responseContent.append(line);
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }

        return responseContent.toString();
    }
    
    /**
     * 获取HTTP响应状态码
     * @param conn HTTP连接
     * @return 响应状态码
     * @throws IOException 如果发生IO异常
     */
    public static int getResponseCode(HttpURLConnection conn) throws IOException {
        return conn.getResponseCode();
    }
} 