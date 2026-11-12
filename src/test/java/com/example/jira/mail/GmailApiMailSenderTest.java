package com.example.jira.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GmailApiMailSenderTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final MailMessage MESSAGE = new MailMessage("recipient@example.test", "Mã xác minh", "Chào <Tên> & bạn\nMã thử: 104269");

    @Test void sendsUtf8MimeWithoutHeaderInjectionOrRawHtml() throws Exception {
        AtomicReference<String> raw = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        try (var fake = new GoogleStub(exchange -> token(exchange, "access-one"), exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            raw.set(JSON.readTree(exchange.getRequestBody().readAllBytes()).path("raw").asText());
            respond(exchange, 200, "{\"id\":\"fake-id-1\"}");
        })) {
            assertThat(fake.sender().send(MESSAGE, () -> true)).isEqualTo(MailSender.Result.accepted("fake-id-1"));
        }
        assertThat(auth.get()).isEqualTo("Bearer access-one");
        assertThat(raw.get()).matches("[A-Za-z0-9_-]+");
        var mime = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw.get())));
        assertThat(mime.getSubject()).isEqualTo(MESSAGE.subject());
        assertThat(mime.getAllRecipients()[0].toString()).isEqualTo(MESSAGE.recipient());
        assertThat(mime.getFrom()[0].toString()).contains("sender@example.test");
        var parts = (MimeMultipart) mime.getContent();
        assertThat(parts.getCount()).isEqualTo(2);
        assertThat(parts.getBodyPart(0).getContent().toString()).contains("<Tên> & bạn");
        assertThat(parts.getBodyPart(1).getContent().toString()).contains("&lt;Tên&gt; &amp; bạn").doesNotContain("<Tên>");
        assertThat(MESSAGE.toString()).doesNotContain("104269", MESSAGE.recipient());
        assertThatThrownBy(() -> new MailMessage("a@example.test\r\nBcc: x@example.test", "x", "y")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MailMessage("a@example.test", "x\r\nBcc: y", "z")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void simultaneous401sShareOneRefreshAndEachRetryOnce() throws Exception {
        AtomicInteger refreshes = new AtomicInteger(), posts = new AtomicInteger();
        CountDownLatch oldRequests = new CountDownLatch(2);
        try (var fake = new GoogleStub(exchange -> token(exchange, "access-" + refreshes.incrementAndGet()), exchange -> {
            posts.incrementAndGet();
            if ("Bearer access-1".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                oldRequests.countDown();
                try { oldRequests.await(3, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                respond(exchange, 401, "{}");
            } else respond(exchange, 200, "{\"id\":\"fake-id-2\"}");
        })) {
            var sender = fake.sender();
            var callers = Executors.newFixedThreadPool(2);
            try {
                var first = callers.submit(() -> sender.send(MESSAGE, () -> true));
                var second = callers.submit(() -> sender.send(MESSAGE, () -> true));
                assertThat(first.get(8, TimeUnit.SECONDS).outcome()).isEqualTo(MailSender.Outcome.ACCEPTED);
                assertThat(second.get(8, TimeUnit.SECONDS).outcome()).isEqualTo(MailSender.Outcome.ACCEPTED);
            } finally { callers.shutdownNow(); }
        }
        assertThat(refreshes).hasValue(2);
        assertThat(posts).hasValue(4);
    }

    @Test void invalidGrantStopsSendingAndDoesNotRepeatRefresh() throws Exception {
        AtomicInteger refreshes = new AtomicInteger(), posts = new AtomicInteger();
        try (var fake = new GoogleStub(exchange -> {
            refreshes.incrementAndGet(); respond(exchange, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"private\"}");
        }, exchange -> { posts.incrementAndGet(); respond(exchange, 200, "{}"); })) {
            var sender = fake.sender();
            assertThat(sender.send(MESSAGE, () -> true)).isEqualTo(MailSender.Result.failed(MailSender.Code.MAIL_REAUTH));
            assertThat(sender.ready()).isFalse();
            assertThat(sender.send(MESSAGE, () -> true).code()).isEqualTo(MailSender.Code.MAIL_REAUTH);
        }
        assertThat(refreshes).hasValue(1);
        assertThat(posts).hasValue(0);
    }

    @Test void honorsRetryAfterButNeverRetriesAnAmbiguousSend() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        try (var fake = new GoogleStub(exchange -> token(exchange, "access"), exchange -> {
            int attempt = posts.incrementAndGet();
            if (attempt == 1) exchange.getResponseHeaders().set("Retry-After", "45");
            respond(exchange, attempt == 1 ? 429 : 503, "{\"error\":{\"message\":\"provider details\"}}");
        })) {
            var sender = fake.sender();
            assertThat(sender.send(MESSAGE, () -> true)).isEqualTo(MailSender.Result.retry(MailSender.Code.RATE_LIMIT, Duration.ofSeconds(45)));
            assertThat(sender.send(MESSAGE, () -> true)).isEqualTo(MailSender.Result.unknown());
        }
        assertThat(posts).hasValue(2);
    }

    @Test void timeoutWhileReadingProviderBodyIsUnknownAndDispatchIsNotRepeated() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        try (var fake = new GoogleStub(exchange -> token(exchange, "access"), exchange -> {
            posts.incrementAndGet();
            exchange.sendResponseHeaders(200, 15);
            try { Thread.sleep(3000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        })) {
            assertThat(fake.sender(Duration.ofSeconds(1)).send(MESSAGE, () -> true)).isEqualTo(MailSender.Result.unknown());
        }
        assertThat(posts).hasValue(1);
    }

    @Test void supersedeCheckRunsAgainBeforeRetryOfExplicit401() throws Exception {
        AtomicInteger posts = new AtomicInteger(), checks = new AtomicInteger();
        try (var fake = new GoogleStub(exchange -> token(exchange, "access"), exchange -> {
            posts.incrementAndGet(); respond(exchange, 401, "{}");
        })) {
            var result = fake.sender().send(MESSAGE, () -> checks.incrementAndGet() == 1);
            assertThat(result).isEqualTo(MailSender.Result.failed(MailSender.Code.INELIGIBLE));
        }
        assertThat(posts).hasValue(1);
        assertThat(checks).hasValue(2);
    }

    static void token(HttpExchange exchange, String value) throws IOException {
        respond(exchange, 200, "{\"access_token\":\"" + value + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
    }

    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    static final class GoogleStub implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService threads = Executors.newCachedThreadPool();

        GoogleStub(com.sun.net.httpserver.HttpHandler token, com.sun.net.httpserver.HttpHandler send) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(threads);
            server.createContext("/token", exchange -> { try (exchange) { token.handle(exchange); } });
            server.createContext("/gmail/v1/users/me/messages/send", exchange -> { try (exchange) { send.handle(exchange); } });
            server.start();
        }

        GmailApiMailSender sender() { return sender(Duration.ofSeconds(5)); }

        GmailApiMailSender sender(Duration timeout) {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            return new GmailApiMailSender(new GmailCredentials("test-client", "test-secret", "test-refresh"), "sender@example.test", "Jira", JSON,
                    Clock.systemUTC(), HttpClient.newBuilder().connectTimeout(timeout).build(), URI.create(base + "/token"),
                    URI.create(base + "/gmail/v1/users/me/messages/send"), timeout);
        }

        @Override public void close() { server.stop(0); threads.shutdownNow(); }
    }
}
