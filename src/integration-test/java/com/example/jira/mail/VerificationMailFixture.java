package com.example.jira.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/** Collector chỉ có trong profile integration. Worker gọi HTTP qua Brevo adapter thật với khóa giả. */
@TestConfiguration
public class VerificationMailFixture implements AutoCloseable {
    private static final Pattern CODE = Pattern.compile("\\b[0-9]{6}\\b");
    private static final Pattern INVITE = Pattern.compile("https?://[^\\s]+/app/invitations/[0-9a-f-]{36}#token=[A-Za-z0-9_-]{43}");
    private static final String KEY = "e1-fake-brevo";
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> codes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> invitations = new ConcurrentHashMap<>();
    private final HttpServer server;

    public VerificationMailFixture() throws Exception {
        var json = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            try (exchange) {
                if (!"POST".equals(exchange.getRequestMethod()) || !KEY.equals(exchange.getRequestHeaders().getFirst("api-key"))) {
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }
                var payload = json.readTree(exchange.getRequestBody());
                String email = payload.path("to").path(0).path("email").asText();
                String content = payload.path("textContent").asText();
                var invite = INVITE.matcher(content);
                var code = CODE.matcher(content);
                if (invite.find()) invitations.computeIfAbsent(email, ignored -> new CopyOnWriteArrayList<>()).add(invite.group());
                else if (code.find()) codes.computeIfAbsent(email, ignored -> new CopyOnWriteArrayList<>()).add(code.group());
                else {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                int status = email.startsWith("unknown-") ? 503 : 201;
                byte[] body = "{\"messageId\":\"<test@brevo.example>\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.createContext("/test-mail", exchange -> {
            try (exchange) {
                String email = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
                var mailbox = exchange.getRequestURI().getPath().equals("/test-mail/invitations") ? invitations : codes;
                byte[] body = json.writeValueAsBytes(mailbox.getOrDefault(email, new CopyOnWriteArrayList<>()));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
    }

    @Bean @Primary MailSender verificationTestSender(ObjectMapper json) {
        return new BrevoMailSender(KEY, "sender@example.test", "Jira Test", json, HttpClient.newHttpClient(),
                URI.create(base() + "/v3/smtp/email"), Duration.ofSeconds(3), Clock.systemUTC());
    }

    public String base() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    public String awaitCode(String email, int number) throws InterruptedException {
        return await(codes, email, number);
    }

    public String awaitInvitation(String email, int number) throws InterruptedException {
        return await(invitations, email, number);
    }

    private String await(ConcurrentHashMap<String, CopyOnWriteArrayList<String>> mailbox, String email, int number)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        while (System.nanoTime() < deadline) {
            var sent = mailbox.get(email);
            if (sent != null && sent.size() >= number) return sent.get(number - 1);
            Thread.sleep(50);
        }
        throw new AssertionError("Fake Brevo did not receive expected message");
    }

    @Override public void close() { server.stop(0); }
}
