package com.example.jira.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

final class BrevoMailSender implements MailSender {
    private static final URI SEND_URI = URI.create("https://api.brevo.com/v3/smtp/email");

    private final String apiKey;
    private final Map<String, String> sender;
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI sendUri;
    private final Duration timeout;
    private final Clock clock;
    private volatile boolean credentialsRejected;

    BrevoMailSender(String apiKey, String email, String name, ObjectMapper json) {
        this(apiKey, email, name, json,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build(),
                SEND_URI, Duration.ofSeconds(10), Clock.systemUTC());
    }

    // Chỉ test nội bộ dùng endpoint loopback và timeout riêng.
    BrevoMailSender(String apiKey, String email, String name, ObjectMapper json,
                    HttpClient http, URI sendUri, Duration timeout, Clock clock) {
        if (apiKey == null || !apiKey.matches("[!-~]{1,1024}")) {
            throw new IllegalArgumentException("BREVO_API_KEY thiếu hoặc không hợp lệ");
        }
        MailMessage.requireAddress(email);
        if (name == null || name.isBlank() || name.length() > 100 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("BREVO_SENDER_NAME không hợp lệ");
        }
        this.apiKey = apiKey;
        this.sender = Map.of("email", email, "name", name);
        this.json = json;
        this.http = http;
        this.sendUri = sendUri;
        this.timeout = timeout;
        this.clock = clock;
    }

    @Override public boolean ready() { return !credentialsRejected; }

    @Override public Result send(MailMessage message, BooleanSupplier canDispatch) {
        if (!ready()) return Result.failed(Code.BREVO_AUTH);
        String body;
        try {
            body = json.writeValueAsString(Map.of("sender", sender, "to", List.of(Map.of("email", message.recipient())),
                    "subject", message.subject(), "textContent", message.text()));
        } catch (IOException invalid) {
            return Result.failed(Code.INVALID_PAYLOAD);
        }
        var request = HttpRequest.newBuilder(sendUri).timeout(timeout)
                .header("api-key", apiKey).header("Accept", "application/json").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        if (Thread.currentThread().isInterrupted() || !canDispatch.getAsBoolean()) return Result.failed(Code.INELIGIBLE);
        var future = http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return classify(future.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.unknown();
        } catch (ExecutionException | TimeoutException unavailable) {
            return Result.unknown();
        } finally {
            if (!future.isDone()) future.cancel(true);
        }
    }

    private Result classify(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            try {
                JsonNode body = json.readTree(response.body());
                JsonNode id = body == null ? null : body.get("messageId");
                if (id != null && id.isTextual() && id.textValue().matches("[!-~]{1,255}")) {
                    return Result.accepted(id.textValue());
                }
            } catch (IOException invalid) {
                return Result.unknown();
            }
            return Result.unknown();
        }
        if (status == 401) {
            credentialsRejected = true;
            return Result.failed(Code.BREVO_AUTH);
        }
        if (status == 429) {
            Duration retry = response.headers().firstValue("Retry-After").map(this::retryAfter).orElse(Duration.ZERO);
            Duration reset = response.headers().firstValue("x-sib-ratelimit-reset").map(BrevoMailSender::seconds).orElse(Duration.ZERO);
            return Result.retry(Code.RATE_LIMIT, retry.compareTo(reset) >= 0 ? retry : reset);
        }
        if (status == 408 || status >= 500) return Result.unknown();
        return Result.failed(Code.REJECTED);
    }

    private Duration retryAfter(String value) {
        if (value.matches("[0-9]+")) return seconds(value);
        try {
            var retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration delay = Duration.between(clock.instant(), retryAt);
            return delay.isNegative() ? Duration.ZERO : delay;
        } catch (DateTimeParseException invalid) {
            return Duration.ZERO;
        }
    }

    private static Duration seconds(String value) {
        if (!value.matches("[0-9]+")) return Duration.ZERO;
        try {
            return Duration.ofSeconds(Math.min(Long.parseLong(value), 1_000_000_000_000L));
        } catch (NumberFormatException overflow) {
            return Duration.ofSeconds(1_000_000_000_000L);
        }
    }
}
