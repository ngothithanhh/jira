package com.example.jira.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

final class GmailApiMailSender implements MailSender {
    static final String SCOPE = "https://www.googleapis.com/auth/gmail.send";
    private static final URI TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    private static final URI SEND_URI = URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send");
    private static final String UTF8 = StandardCharsets.UTF_8.name();

    private final GmailCredentials credentials;
    private final String senderAddress;
    private final String senderName;
    private final ObjectMapper json;
    private final Clock clock;
    private final HttpClient http;
    private final URI tokenUri;
    private final URI sendUri;
    private final Duration timeout;
    private AccessToken token;
    private volatile boolean needsReauth;

    GmailApiMailSender(GmailCredentials credentials, String senderAddress, String senderName, ObjectMapper json, Clock clock) {
        this(credentials, senderAddress, senderName, json, clock,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build(),
                TOKEN_URI, SEND_URI, Duration.ofSeconds(10));
    }

    // Chỉ constructor nội bộ của test nhận URL khác Google.
    GmailApiMailSender(GmailCredentials credentials, String senderAddress, String senderName, ObjectMapper json, Clock clock,
                       HttpClient http, URI tokenUri, URI sendUri, Duration timeout) {
        MailMessage.requireAddress(senderAddress);
        if (senderName == null || senderName.isBlank() || senderName.length() > 100 || senderName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Tên người gửi không hợp lệ");
        }
        this.credentials = credentials;
        this.senderAddress = senderAddress;
        this.senderName = senderName;
        this.json = json;
        this.clock = clock;
        this.http = http;
        this.tokenUri = tokenUri;
        this.sendUri = sendUri;
        this.timeout = timeout;
    }

    @Override public boolean ready() { return !needsReauth; }

    /** Gmail chỉ được gọi sau khi MIME hợp lệ. Một 401 rõ ràng cho phép đúng một lần gửi lại. */
    @Override public Result send(MailMessage message, BooleanSupplier canDispatch) {
        String body;
        try {
            body = json.writeValueAsString(Map.of("raw", mime(message)));
        } catch (IOException | MessagingException invalid) {
            return Result.failed(Code.INVALID_PAYLOAD);
        }
        AccessToken current = accessToken(null);
        if (current == null) return tokenFailure();
        if (!canDispatch.getAsBoolean()) return Result.failed(Code.INELIGIBLE);
        HttpResponse<String> result = dispatch(body, current);
        if (result != null && result.statusCode() == 401) {
            current = accessToken(current);
            if (current == null) return tokenFailure();
            if (!canDispatch.getAsBoolean()) return Result.failed(Code.INELIGIBLE);
            result = dispatch(body, current);
        }
        return classify(result);
    }

    private Result tokenFailure() {
        return needsReauth ? Result.failed(Code.MAIL_REAUTH) : Result.retry(Code.TOKEN_UNAVAILABLE, null);
    }

    /** Worker chia sẻ access token trong một tiến trình. Worker gặp 401 chỉ làm mới token đã bị từ chối. */
    private synchronized AccessToken accessToken(AccessToken rejected) {
        if (needsReauth) return null;
        if (token != null && token != rejected && clock.instant().isBefore(token.refreshAt)) return token;
        token = null;
        String body = "grant_type=refresh_token&client_id=" + form(credentials.clientId())
                + "&client_secret=" + form(credentials.clientSecret()) + "&refresh_token=" + form(credentials.refreshToken());
        HttpResponse<String> response = exchange(HttpRequest.newBuilder(tokenUri).timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build());
        if (response == null) return null;
        JsonNode data = tree(response.body());
        if (data == null) return null;
        if ("invalid_grant".equals(data.path("error").asText()) || "invalid_client".equals(data.path("error").asText())) {
            needsReauth = true;
            return null;
        }
        if (response.statusCode() != 200) return null;
        if (data.has("scope") && !SCOPE.equals(data.path("scope").asText())) {
            needsReauth = true;
            return null;
        }
        String value = data.path("access_token").asText();
        long lifetime = data.path("expires_in").asLong();
        if (!value.matches("[A-Za-z0-9._~+/=-]{1,8192}") || lifetime <= 0
                || !"Bearer".equalsIgnoreCase(data.path("token_type").asText())) return null;
        token = new AccessToken(value, clock.instant().plusSeconds(Math.max(0, Math.min(lifetime, 86400) - 30)));
        return token;
    }

    private HttpResponse<String> dispatch(String body, AccessToken access) {
        return exchange(HttpRequest.newBuilder(sendUri).timeout(timeout).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + access.value).POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    /** Giới hạn cả thời gian đọc body. Không đưa URL có secret, response body hay exception provider vào log. */
    private HttpResponse<String> exchange(HttpRequest request) {
        if (Thread.currentThread().isInterrupted()) return null;
        var future = http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (TimeoutException | ExecutionException unavailable) {
            return null;
        } finally {
            if (!future.isDone()) future.cancel(true);
        }
    }

    private Result classify(HttpResponse<String> response) {
        if (response == null || response.statusCode() >= 500 || response.statusCode() == 408) return Result.unknown();
        int status = response.statusCode();
        JsonNode data = tree(response.body());
        if (status >= 200 && status < 300) {
            String id = data == null ? "" : data.path("id").asText();
            return id.matches("[A-Za-z0-9_-]{1,200}") ? Result.accepted(id) : Result.unknown();
        }
        if (status == 429 || status == 403 && rateLimited(data)) {
            return Result.retry(Code.RATE_LIMIT, response.headers().firstValue("Retry-After").map(this::retryAfter).orElse(null));
        }
        return Result.failed(Code.REJECTED);
    }

    private static boolean rateLimited(JsonNode body) {
        if (body == null) return false;
        for (JsonNode error : body.path("error").path("errors")) {
            String reason = error.path("reason").asText();
            if (reason.equals("rateLimitExceeded") || reason.equals("userRateLimitExceeded")) return true;
        }
        return false;
    }

    private Duration retryAfter(String value) {
        try {
            if (value.matches("[0-9]+")) return Duration.ofSeconds(value.length() > 12 ? 1_000_000_000_000L : Long.parseLong(value));
            Duration delay = Duration.between(clock.instant(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            return delay.isNegative() ? Duration.ZERO : delay;
        } catch (RuntimeException invalid) { return null; }
    }

    private JsonNode tree(String body) {
        try { return json.readTree(body); } catch (IOException invalid) { return null; }
    }

    /** Bản HTML xuất phát từ plain text đã escape, gồm cả tên, OTP và URL do nghiệp vụ dựng. */
    private String mime(MailMessage input) throws IOException, MessagingException {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(senderAddress, senderName, UTF8));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(input.recipient(), true));
        message.setSubject(input.subject(), UTF8);
        var plain = new MimeBodyPart();
        plain.setText(input.text(), UTF8);
        var html = new MimeBodyPart();
        html.setContent("<html><body><pre>" + HtmlUtils.htmlEscape(input.text(), UTF8) + "</pre></body></html>", "text/html; charset=UTF-8");
        var alternatives = new MimeMultipart("alternative");
        alternatives.addBodyPart(plain);
        alternatives.addBodyPart(html);
        message.setContent(alternatives);
        message.saveChanges();
        var output = new ByteArrayOutputStream();
        message.writeTo(output);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    }

    private static String form(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private record AccessToken(String value, Instant refreshAt) {
        @Override public String toString() { return "AccessToken[redacted]"; }
    }
}
