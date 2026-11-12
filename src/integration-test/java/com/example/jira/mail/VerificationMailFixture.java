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
    private static final String KEY = "e1-fake-brevo";
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> codes = new ConcurrentHashMap<>();
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
                var match = CODE.matcher(payload.path("textContent").asText());
                if (!match.find()) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                codes.computeIfAbsent(email, ignored -> new CopyOnWriteArrayList<>()).add(match.group());
                int status = email.startsWith("unknown-") ? 503 : 201;
                byte[] body = "{\"messageId\":\"<test@brevo.example>\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.createContext("/test-mail", exchange -> {
            try (exchange) {
                String email = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
                byte[] body = json.writeValueAsBytes(codes.getOrDefault(email, new CopyOnWriteArrayList<>()));
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
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        while (System.nanoTime() < deadline) {
            var sent = codes.get(email);
            if (sent != null && sent.size() >= number) return sent.get(number - 1);
            Thread.sleep(50);
        }
        throw new AssertionError("Fake Brevo did not receive expected message");
    }

    @Override public void close() { server.stop(0); }
}
