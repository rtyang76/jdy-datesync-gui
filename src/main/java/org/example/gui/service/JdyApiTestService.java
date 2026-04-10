package org.example.gui.service;

import okhttp3.*;
import org.example.gui.model.JdyAppConfig;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class JdyApiTestService {

    private static final String APP_LIST_URL = "https://api.jiandaoyun.com/api/v5/app/list";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public static TestResult testConnection(JdyAppConfig config) {
        if (config.getApiToken() == null || config.getApiToken().trim().isEmpty()) {
            return new TestResult(false, "API Token 不能为空");
        }

        String token = config.getApiToken().trim();
        if (!token.startsWith("Bearer ")) {
            token = "Bearer " + token;
        }

        try {
            RequestBody body = RequestBody.create("{\"skip\":0,\"limit\":1}", JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(APP_LIST_URL)
                    .post(body)
                    .header("Content-Type", "application/json")
                    .header("Authorization", token)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody responseBody = response.body();
                String responseStr = responseBody != null ? responseBody.string() : "";

                if (response.code() == 200) {
                    if (responseStr.contains("\"apps\"")) {
                        return new TestResult(true, "连接成功！简道云API响应正常");
                    } else {
                        return new TestResult(false, "API返回: " + responseStr);
                    }
                } else if (response.code() == 401) {
                    return new TestResult(false, "认证失败：API Token 无效或已过期");
                } else {
                    return new TestResult(false, "HTTP " + response.code() + ": " + responseStr);
                }
            }
        } catch (IOException e) {
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
