package org.example.gui.service;

import org.example.gui.model.JdyAppConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class JdyApiTestService {

    private static final String APP_LIST_URL = "https://api.jiandaoyun.com/api/v5/app/list";

    public static TestResult testConnection(JdyAppConfig config) {
        if (config.getApiToken() == null || config.getApiToken().trim().isEmpty()) {
            return new TestResult(false, "API Token 不能为空");
        }

        String token = config.getApiToken().trim();
        if (!token.startsWith("Bearer ")) {
            token = "Bearer " + token;
        }

        try {
            URL url = new URL(APP_LIST_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            String body = "{\"skip\":0,\"limit\":1}";
            conn.getOutputStream().write(body.getBytes("UTF-8"));
            conn.getOutputStream().flush();
            conn.getOutputStream().close();

            int responseCode = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode == 200 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            if (responseCode == 200) {
                String resp = response.toString();
                if (resp.contains("\"apps\"")) {
                    return new TestResult(true, "连接成功！简道云API响应正常");
                } else {
                    return new TestResult(false, "API返回: " + resp);
                }
            } else if (responseCode == 401) {
                return new TestResult(false, "认证失败：API Token 无效或已过期");
            } else {
                return new TestResult(false, "HTTP " + responseCode + ": " + response.toString());
            }
        } catch (Exception e) {
            return new TestResult(false, "连接失败: " + e.getMessage());
        }
    }

    public static class TestResult {
        private final boolean success;
        private final String message;

        public TestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
