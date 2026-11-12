package com.example.jira.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BrevoMailSenderTest {
    static final String TEST_KEY = "brevo-test-key";
    static final String SENDER = "sender@example.test";
    static final String NAME = "Nhóm Jira";
    static final String MESSAGE_ID = "<201798300811.5787683@relay.domain.test>";
    static final ObjectMapper JSON = new ObjectMapper();
    static final MailMessage MESSAGE = new MailMessage("recipient@example.test", "Mã xác minh Jira", "Mã của bạn: 104269\n<Tên>");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test void usesBrevoJsonAndChecksLeaseBeforePosting() throws Exception {
        var request = new AtomicReference<CapturedRequest>();
        try (var brevo = new BrevoStub(exchange -> {
            request.set(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("api-key"),
                    exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            accepted(exchange);
        })) {
            var sender = brevo.sender();
            assertThat(sender.send(MESSAGE, () -> false)).isEqualTo(MailSender.Result.failed(MailSender.Code.INELIGIBLE));
            assertThat(request.get()).isNull();
            assertThat(sender.send(MESSAGE, () -> true)).isEqualTo(MailSender.Result.accepted(MESSAGE_ID));
            var sent = request.get();
            assertThat(sent.method()).isEqualTo("POST");
            assertThat(sent.key()).isEqualTo(TEST_KEY);
            assertThat(sent.authorization()).isNull();
            assertThat(sent.contentType()).isEqualTo("application/json");
            assertThat(JSON.readTree(sent.body())).isEqualTo(JSON.valueToTree(Map.of(
                    "sender", Map.of("email", SENDER, "name", NAME),
                    "to", java.util.List.of(Map.of("email", MESSAGE.recipient())),
                    "subject", MESSAGE.subject(), "textContent", MESSAGE.text())));
        }
    }

    @Test void unauthorizedKeyStopsFurtherPosts() throws Exception {
        var posts = new AtomicInteger();
        try (var brevo = new BrevoStub(exchange -> {
            posts.incrementAndGet();
            respond(exchange, 401, "{\"code\":\"unauthorized\"}");
        })) {
            var sender = brevo.sender();
            var authFailure = MailSender.Result.failed(MailSender.Code.BREVO_AUTH);
            assertThat(sender.send(MESSAGE, () -> true)).isEqualTo(authFailure);
            assertThat(sender.ready()).isFalse();
            assertThat(sender.send(MESSAGE, () -> true)).isEqualTo(authFailure);
            assertThat(posts).hasValue(1);
        }
    }

    @Test void rateLimitUsesLaterOfRetryAfterAndBrevoReset() throws Exception {
        try (var brevo = new BrevoStub(exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "Thu, 1 Jan 2026 00:00:45 GMT");
            exchange.getResponseHeaders().set("x-sib-ratelimit-reset", "15");
            respond(exchange, 429, "{}");
        })) {
            assertThat(brevo.sender().send(MESSAGE, () -> true))
                    .isEqualTo(MailSender.Result.retry(MailSender.Code.RATE_LIMIT, Duration.ofSeconds(45)));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 408, 503})
    void ambiguousResponsesStayUnknown(int status) throws Exception {
        try (var brevo = new BrevoStub(exchange -> respond(exchange, status, "{\"messageId\":123}"))) {
            assertThat(brevo.sender().send(MESSAGE, () -> true)).isEqualTo(MailSender.Result.unknown());
        }
    }

    @Test void forbiddenResponseIsTerminalForTheJob() throws Exception {
        try (var brevo = new BrevoStub(exchange -> respond(exchange, 403, "{}"))) {
            assertThat(brevo.sender().send(MESSAGE, () -> true)).isEqualTo(MailSender.Result.failed(MailSender.Code.REJECTED));
        }
    }

    @Test void deadlineAlsoCoversStalledResponseBody() throws Exception {
        var headersSent = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var brevo = new BrevoStub(exchange -> {
            exchange.sendResponseHeaders(201, 20);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            headersSent.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        })) {
            try {
                assertThat(brevo.sender(Duration.ofSeconds(1)).send(MESSAGE, () -> true)).isEqualTo(MailSender.Result.unknown());
                assertThat(headersSent.getCount()).isZero();
            } finally {
                release.countDown();
            }
        }
    }

    @Test void springSelectsBrevoFromEnvironmentWithoutGmailCredentials() {
        new ApplicationContextRunner().withUserConfiguration(MailConfiguration.class)
                .withBean(ObjectMapper.class, () -> JSON).withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("test-environment", Map.of(
                                "APP_MAIL_ENABLED", "true", "APP_MAIL_PROVIDER", "brevo",
                                "APP_MAIL_ENCRYPTION_KEY", java.util.Base64.getEncoder().encodeToString(new byte[32]),
                                "BREVO_API_KEY", TEST_KEY, "BREVO_SENDER_EMAIL", SENDER, "BREVO_SENDER_NAME", NAME))))
                .run(context -> {
                    assertThat(context).hasSingleBean(MailSender.class).hasSingleBean(MailOutboxWorker.class);
                    assertThat(context.getBean(MailSender.class)).isInstanceOf(BrevoMailSender.class);
                });
    }

    static void accepted(HttpExchange exchange) throws IOException {
        respond(exchange, 201, "{\"messageId\":\"" + MESSAGE_ID + "\"}");
    }

    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private record CapturedRequest(String method, String key, String authorization, String contentType, String body) { }

    static final class BrevoStub implements AutoCloseable {
        private final HttpServer server;

        BrevoStub(HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v3/smtp/email", exchange -> {
                try (exchange) { handler.handle(exchange); }
            });
            server.start();
        }

        BrevoMailSender sender() { return sender(Duration.ofSeconds(5)); }

        BrevoMailSender sender(Duration timeout) {
            var endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email");
            return new BrevoMailSender(TEST_KEY, SENDER, NAME, JSON, HttpClient.newHttpClient(), endpoint, timeout, CLOCK);
        }

        @Override public void close() { server.stop(0); }
    }
}
