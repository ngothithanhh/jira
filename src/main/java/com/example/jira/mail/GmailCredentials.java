package com.example.jira.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record GmailCredentials(String clientId, String clientSecret, String refreshToken) {
    GmailCredentials {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()
                || refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Thiếu cấu hình OAuth Gmail");
        }
    }

    /** File do công cụ OAuth ghi ngoài Git. Kiểm sender đã xác nhận và giữ lỗi ở dạng rút gọn. */
    static GmailCredentials read(Path path, String sender, ObjectMapper json) {
        try (var input = Files.newInputStream(path)) {
            JsonNode data = json.readTree(input);
            if (data == null || !sender.equalsIgnoreCase(data.path("senderAddress").asText())
                    || !data.path("scope").asText().equals(GmailApiMailSender.SCOPE)) {
                throw new IllegalArgumentException("Tệp OAuth Gmail không khớp sender hoặc scope");
            }
            return new GmailCredentials(data.path("clientId").asText(), data.path("clientSecret").asText(), data.path("refreshToken").asText());
        } catch (IOException unreadable) {
            throw new IllegalArgumentException("Không đọc được tệp OAuth Gmail");
        }
    }

    @Override public String toString() { return "GmailCredentials[redacted]"; }
}
