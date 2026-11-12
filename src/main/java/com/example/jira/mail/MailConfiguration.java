package com.example.jira.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
@EnableScheduling
class MailConfiguration {
    @Bean
    MailOutbox mailOutbox(JdbcTemplate db, ObjectMapper json,
                          @Value("${app.mail.encryption-key:}") String key,
                          @Value("${app.mail.key-version:1}") int version) {
        return new MailOutbox(db, new MailPayloadCipher(key, version, json));
    }

    /** Chỉ dựng worker khi provider được chọn có cấu hình gửi hợp lệ. */
    @Bean
    MailSender outboxMailSender(ObjectMapper json, @Value("${app.mail.provider:gmail-api}") String provider,
                          @Value("${app.mail.sender-address:}") String sender,
                          @Value("${app.mail.sender-name:Jira}") String name,
                          @Value("${app.gmail.token-file:}") String tokenFile,
                          @Value("${BREVO_API_KEY:}") String brevoKey,
                          @Value("${BREVO_SENDER_EMAIL:}") String brevoSender,
                          @Value("${BREVO_SENDER_NAME:Jira}") String brevoName) {
        return switch (provider) {
            case "brevo" -> new BrevoMailSender(brevoKey, brevoSender, brevoName, json);
            case "gmail-api" -> {
                if (tokenFile.isBlank()) throw new IllegalArgumentException("Thiếu cấu hình Gmail API");
                MailMessage.requireAddress(sender);
                yield new GmailApiMailSender(GmailCredentials.read(Path.of(tokenFile), sender, json),
                        sender, name, json, Clock.systemUTC());
            }
            default -> throw new IllegalArgumentException("Provider gửi thư không được hỗ trợ");
        };
    }

    @Bean
    MailOutboxWorker mailOutboxWorker(MailOutbox outbox, MailSender sender) {
        return new MailOutboxWorker(outbox, sender, Clock.systemUTC());
    }
}
