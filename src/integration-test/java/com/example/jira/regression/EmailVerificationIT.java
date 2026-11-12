package com.example.jira.regression;

import com.example.jira.JiraApplication;
import com.example.jira.mail.VerificationMailFixture;
import com.example.jira.repository.UserRepository;
import com.example.jira.verification.VerificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = {JiraApplication.class, VerificationMailFixture.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.default_schema=verification_e1", "spring.datasource.hikari.schema=verification_e1",
        "app.jwt.secret=" + SecurityRegressionTest.SECRET, "app.jwt.expiration=600000",
        "app.mail.enabled=true", "app.mail.provider=brevo", "app.mail.poll-delay-ms=250",
        "app.mail.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.verification.hmac-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
        "BREVO_API_KEY=e1-test-unused-provider-bean", "BREVO_SENDER_EMAIL=sender@example.test",
        "spring.mail.host=127.0.0.1", "spring.mail.port=1", "spring.mail.test-connection=false", "spring.sql.init.mode=never"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailVerificationIT {
    private static final String AUTH = "/api/auth/registration";
    private static final String ACCOUNT = "/api/account/email-verification";
    private static final String PASSWORD = "E1-fixture-password";
    private static final String MIGRATION = "scripts/migrations/012_email_verification.sql";
    private static PostgreSQLContainer<?> container;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(4)).build();

    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired VerificationMailFixture brevo;
    @Autowired VerificationService verification;
    @LocalServerPort int port;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getProperty("test.pg.url");
        String user = System.getProperty("test.pg.user");
        String password = System.getProperty("test.pg.password", "");
        if (url == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            url = container.getJdbcUrl();
            user = container.getUsername();
            password = container.getPassword();
        } else PostgresRegressionIT.verifyLocalDatabase(url, user, password, System.getProperty("test.pg.data"));
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA verification_e1");
        }
        String target = url, actor = user, credential = password;
        registry.add("spring.datasource.url", () -> target);
        registry.add("spring.datasource.username", () -> actor);
        registry.add("spring.datasource.password", () -> credential);
    }

    @BeforeAll void migrate() throws Exception {
        db.execute(Files.readString(Path.of("scripts/migrations/011_mail_outbox.sql")));
        db.execute(Files.readString(Path.of(MIGRATION)));
        db.execute(Files.readString(Path.of(MIGRATION)));
    }

    @AfterAll void stopContainer() { if (container != null) container.stop(); }

    @Test void registrationRequiresProofAndCreatesOneUserWithoutASession() throws Exception {
        String email = "registration-e1@test.invalid";
        int refreshCount = count("refresh_tokens");
        int roleCount = count("user_role_assignments");
        int members = count("project_members");
        var started = register(email, PASSWORD, "e1-verified", 202);
        assertThat(started.path("deliveryState").asText()).isEqualTo("QUEUED");
        assertThat(started.has("token")).isFalse();
        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(count("refresh_tokens")).isEqualTo(refreshCount);
        call("POST", "/api/auth/login", null, Map.of("email", email, "password", PASSWORD), 401);
        String otp = brevo.awaitCode(email, 1);
        assertThat(otp).matches("[0-9]{6}");
        assertThat(call("GET", AUTH + "/status", null, null, 400).path("code").asText()).isEqualTo("INVALID_PROOF");
        assertThat(status(AUTH, null, started).has("proofToken")).isFalse();
        assertThat(db.queryForObject("SELECT octet_length(otp_hmac)=32 AND password_hash<>? FROM registration_challenges WHERE email_normalized=?",
                Boolean.class, PASSWORD, email)).isTrue();
        verify(AUTH, null, started, wrong(otp), 400);
        assertThat(db.queryForObject("SELECT attempt_count FROM registration_challenges WHERE email_normalized=?", Integer.class, email)).isEqualTo(1);
        var input = proof(started, otp);
        var first = http.sendAsync(request("POST", AUTH + "/verify", null, input), HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(request("POST", AUTH + "/verify", null, input), HttpResponse.BodyHandlers.ofString());
        assertThat(first.get(12, TimeUnit.SECONDS).statusCode()).isEqualTo(204);
        assertThat(second.get(12, TimeUnit.SECONDS).statusCode()).isEqualTo(204);
        var created = users.findByEmail(email).orElseThrow();
        assertThat(created.getEmailVerifiedAt()).isNotNull();
        assertThat(encoder.matches(PASSWORD, created.getPasswordHash())).isTrue();
        assertThat(count("refresh_tokens")).isEqualTo(refreshCount);
        assertThat(count("user_role_assignments")).isEqualTo(roleCount);
        assertThat(count("project_members")).isEqualTo(members);
        assertThat(db.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='VERIFY_EMAIL'", Integer.class, created.getUserId())).isEqualTo(1);
        assertThat(status(AUTH, null, started).path("state").asText()).isEqualTo("CONSUMED");
        assertThat(login(email, PASSWORD)).isNotBlank();
    }

    @Test void concurrentStartsReplacePendingProfileAndResendRejectsEveryOlderCode() throws Exception {
        String email = "resend-e1@test.invalid";
        var first = http.sendAsync(request("POST", "/api/auth/register", null, form(email.toUpperCase(java.util.Locale.ROOT), "old-password", "old")), HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(request("POST", "/api/auth/register", null, form(email, PASSWORD, "new")), HttpResponse.BodyHandlers.ofString());
        var olderOrNewer = first.get(12, TimeUnit.SECONDS);
        var newerOrOlder = second.get(12, TimeUnit.SECONDS);
        assertThat(List.of(olderOrNewer.statusCode(), newerOrOlder.statusCode())).containsOnly(202);
        String activeId = db.queryForObject("SELECT registration_id::text FROM registration_challenges WHERE email_normalized=?", String.class, email);
        JsonNode left = json.readTree(olderOrNewer.body()), right = json.readTree(newerOrOlder.body());
        boolean leftWon = left.path("challengeId").asText().equals(activeId);
        JsonNode active = leftWon ? left : right;
        JsonNode replaced = leftWon ? right : left;
        verify(AUTH, null, replaced, "123456", 400);
        assertThat(call("POST", AUTH + "/resend", null, proof(active, null), 429).path("code").asText()).isEqualTo("COOLDOWN");
        waitDelivery(AUTH, active, "PROVIDER_ACCEPTED");
        int priorPosts = mailCount(email);
        String initial = brevo.awaitCode(email, priorPosts);
        permitResend(email);
        var resentOne = http.sendAsync(request("POST", AUTH + "/resend", null, proof(active, null)), HttpResponse.BodyHandlers.ofString());
        var resentTwo = http.sendAsync(request("POST", AUTH + "/resend", null, proof(active, null)), HttpResponse.BodyHandlers.ofString());
        assertThat(List.of(resentOne.get(12, TimeUnit.SECONDS).statusCode(), resentTwo.get(12, TimeUnit.SECONDS).statusCode()))
                .containsExactlyInAnyOrder(202, 429);
        String secondCode = brevo.awaitCode(email, priorPosts + 1);
        permitResend(email);
        call("POST", AUTH + "/resend", null, proof(active, null), 202);
        String third = brevo.awaitCode(email, priorPosts + 2);
        assertThat(List.of(initial, secondCode, third)).doesNotHaveDuplicates();
        verify(AUTH, null, active, initial, 400);
        verify(AUTH, null, active, secondCode, 400);
        verify(AUTH, null, active, third, 204);
        assertThat(users.findByEmail(email).orElseThrow().getUserName()).isEqualTo(leftWon ? "old" : "new");
        assertThat(login(email, leftWon ? "old-password" : PASSWORD)).isNotBlank();
        call("POST", "/api/auth/login", null, Map.of("email", email, "password", leftWon ? PASSWORD : "old-password"), 401);
    }

    @Test void expiredOtpCanResendAndWrongAttemptsLockWithoutResettingEmailRate() throws Exception {
        String email = "unknown-locked-e1@test.invalid";
        var challenge = register(email, PASSWORD, "locked", 202);
        String first = brevo.awaitCode(email, 1);
        waitDelivery(AUTH, challenge, "UNKNOWN");
        db.update("UPDATE registration_challenges SET expires_at=clock_timestamp()-interval '1 second' WHERE email_normalized=?", email);
        verify(AUTH, null, challenge, first, 410);
        permitResend(email);
        call("POST", AUTH + "/resend", null, proof(challenge, null), 202);
        String next = brevo.awaitCode(email, 2);
        for (int attempt = 1; attempt <= 5; attempt++) verify(AUTH, null, challenge, wrong(next), attempt < 5 ? 400 : 429);
        verify(AUTH, null, challenge, next, 429);
        assertThat(status(AUTH, null, challenge).path("state").asText()).isEqualTo("LOCKED");
        assertThat(db.queryForObject("SELECT attempt_count FROM registration_challenges WHERE email_normalized=?", Integer.class, email)).isEqualTo(5);
        assertThat(db.queryForObject("SELECT max(attempts) FROM verification_rate_limits WHERE action='VERIFY' AND attempts=5", Integer.class)).isEqualTo(5);
        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test void restartingChallengesDoesNotResetTheEmailSendOrGuessBudget() throws Exception {
        String email = "email-limit-e1@test.invalid";
        for (int generation = 1; generation <= 4; generation++) {
            var challenge = register(email, PASSWORD, "rate-limit", 202);
            String code = brevo.awaitCode(email, generation);
            for (int attempt = 1; attempt <= 5; attempt++) {
                verify(AUTH, null, challenge, wrong(code), attempt < 5 ? 400 : 429);
            }
        }
        var fifth = register(email, PASSWORD, "rate-limit", 202);
        String validCode = brevo.awaitCode(email, 5);
        assertThat(call("POST", AUTH + "/verify", null, proof(fifth, validCode), 429).path("code").asText()).isEqualTo("RATE_LIMIT");
        assertThat(register(email, PASSWORD, "rate-limit", 429).path("code").asText()).isEqualTo("RATE_LIMIT");
        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test void verifyAndResendFromDifferentIpBucketsSerializeOnTheChallenge() throws Exception {
        String email = "verify-resend-e1@test.invalid";
        var challenge = register(email, PASSWORD, "verify-resend", 202);
        String otp = brevo.awaitCode(email, 1);
        UUID id = UUID.fromString(challenge.path("challengeId").asText());
        String proof = challenge.path("proofToken").asText();
        permitResend(email);
        var start = new CountDownLatch(1);
        var callers = Executors.newFixedThreadPool(2);
        try {
            var verify = callers.submit(() -> {
                start.await();
                return verification.verifyRegistration(id, proof, otp, "192.0.2.10").status().value();
            });
            var resend = callers.submit(() -> {
                start.await();
                return verification.resend(id, proof, "192.0.2.11").status().value();
            });
            start.countDown();
            int verified = verify.get(12, TimeUnit.SECONDS), resent = resend.get(12, TimeUnit.SECONDS);
            assertThat(List.of(verified + "/" + resent)).containsAnyOf("204/409", "400/202");
            if (verified == 204) assertThat(users.findByEmail(email)).isPresent();
            else {
                assertThat(users.findByEmail(email)).isEmpty();
                verify(AUTH, null, challenge, brevo.awaitCode(email, 2), 204);
            }
        } finally { callers.shutdownNow(); }
    }

    @Test void legacyUsesStoredEmailAndActorWithoutRevokingLogin() throws Exception {
        String email = "Legacy-E1@test.invalid";
        int id = seed(email);
        String other = "other-e1@test.invalid";
        seed(other);
        String token = login(email.toLowerCase(java.util.Locale.ROOT), PASSWORD), otherToken = login(other, PASSWORD);
        assertThat(call("GET", "/api/auth/me", token, null, 200).path("user").path("email").asText()).isEqualTo(email);
        assertThat(call("GET", ACCOUNT + "/status", token, null, 200).path("state").asText()).isEqualTo("UNVERIFIED");
        call("POST", ACCOUNT + "/request", null, null, 401);
        call("POST", ACCOUNT + "/request", token, Map.of("email", other), 400);
        var challenge = call("POST", ACCOUNT + "/request", token, null, 202);
        String otp = brevo.awaitCode(email, 1);
        verify(ACCOUNT, otherToken, challenge, otp, 400);
        verify(ACCOUNT, token, challenge, otp, 204);
        assertThat(users.findById(id).orElseThrow().getEmailVerifiedAt()).isNotNull();
        assertThat(users.findByEmail(other).orElseThrow().getEmailVerifiedAt()).isNull();
        assertThat(call("POST", ACCOUNT + "/request", token, null, 200).path("state").asText()).isEqualTo("VERIFIED");
        assertThat(call("GET", "/api/auth/me", token, null, 200).path("user").path("email").asText()).isEqualTo(email);
        register(email.toLowerCase(java.util.Locale.ROOT), "ignored-password", "intruder", 409);
        assertThat(login(email, PASSWORD)).isNotBlank();
    }

    @Test void auditErrorRollsBackUserAndChallenge() throws Exception {
        String email = "audit-e1@test.invalid";
        var challenge = register(email, PASSWORD, "audit", 202);
        String otp = brevo.awaitCode(email, 1);
        db.execute("""
                CREATE FUNCTION reject_e1_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.action = 'VERIFY_EMAIL' THEN RAISE EXCEPTION 'E1 audit fixture'; END IF; RETURN NEW; END $$;
                CREATE TRIGGER reject_e1_audit BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION reject_e1_audit()
                """);
        try {
            verify(AUTH, null, challenge, otp, 500);
            assertThat(users.findByEmail(email)).isEmpty();
            assertThat(status(AUTH, null, challenge).path("state").asText()).isEqualTo("PENDING");
        } finally {
            db.execute("DROP TRIGGER reject_e1_audit ON audit_logs; DROP FUNCTION reject_e1_audit()");
        }
        verify(AUTH, null, challenge, otp, 204);
    }

    @Test void migrationPreflightAbortsOnCanonicalDuplicate() throws Exception {
        String migration = Files.readString(Path.of(MIGRATION)).replaceFirst("(?s)^BEGIN;\\s*", "").replaceFirst("(?s)COMMIT;\\s*$", "");
        try (var connection = db.getDataSource().getConnection(); var sql = connection.createStatement()) {
            connection.setAutoCommit(false);
            sql.execute("CREATE SCHEMA verification_preflight; SET LOCAL search_path TO verification_preflight; "
                    + "CREATE TABLE users(user_id int PRIMARY KEY,email text); "
                    + "INSERT INTO users VALUES (1701,'Same@test.invalid'),(1702,' same@test.invalid ')");
            assertThatThrownBy(() -> sql.execute(migration)).isInstanceOf(SQLException.class).hasMessageContaining("1701, 1702");
            connection.rollback();
            try (var rows = sql.executeQuery("SELECT to_regnamespace('verification_preflight') IS NULL")) {
                assertThat(rows.next() && rows.getBoolean(1)).isTrue();
            }
        }
    }

    @Test void browserWithBuiltNextAndFakeBrevo() throws Exception {
        String root = System.getenv("E1_FE_ROOT");
        String node = System.getenv("E1_NODE_EXE");
        Assumptions.assumeTrue(root != null && node != null, "Set E1_FE_ROOT and E1_NODE_EXE to run Chromium");
        Path frontend = Path.of(root).toRealPath();
        int fePort;
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            fePort = socket.getLocalPort();
        }
        String origin = "http://127.0.0.1:" + fePort;
        String legacy = "browser-legacy-e1@test.invalid";
        seed(legacy);
        var next = new ProcessBuilder(node, frontend.resolve("node_modules/next/dist/bin/next").toString(), "start",
                "--hostname", "127.0.0.1", "--port", Integer.toString(fePort))
                .directory(frontend.toFile()).redirectErrorStream(true).redirectOutput(Path.of("target/e1-next.log").toFile());
        next.environment().put("BACKEND_API_URL", "http://127.0.0.1:" + port + "/api");
        next.environment().put("APP_ORIGIN", origin);
        var server = next.start();
        try {
            awaitNext(origin);
            var test = new ProcessBuilder(node, frontend.resolve("tests/connected-email-verification.browser.cjs").toString())
                    .directory(frontend.toFile()).redirectErrorStream(true).redirectOutput(Path.of("target/e1-browser.log").toFile());
            test.environment().put("E1_ORIGIN", origin);
            test.environment().put("E1_FAKE_MAIL", brevo.base());
            test.environment().put("E1_LEGACY_EMAIL", legacy);
            test.environment().put("E1_LEGACY_PASSWORD", PASSWORD);
            var browser = test.start();
            try {
                assertThat(browser.waitFor(85, TimeUnit.SECONDS)).isTrue();
                assertThat(browser.exitValue()).as("Chromium result; see target/e1-browser.log").isZero();
            } finally { if (browser.isAlive()) browser.destroyForcibly(); }
        } finally {
            server.descendants().forEach(ProcessHandle::destroy);
            server.destroy();
            if (!server.waitFor(5, TimeUnit.SECONDS)) server.destroyForcibly();
        }
    }

    private void awaitNext(String origin) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        int lastStatus = 0;
        while (System.nanoTime() < deadline) {
            try {
                var get = HttpRequest.newBuilder(URI.create(origin + "/app/sign-up")).timeout(Duration.ofSeconds(3)).build();
                lastStatus = http.send(get, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (lastStatus == 200) return;
            } catch (java.io.IOException starting) { /* Chờ Next bind loopback. */ }
            Thread.sleep(150);
        }
        throw new AssertionError("Next signup did not return 200 (last HTTP status " + lastStatus + "); see target/e1-next.log");
    }

    private int seed(String email) {
        var user = Fixtures.user(0);
        user.setEmail(email);
        user.setUserName(email.split("@")[0]);
        user.setPasswordHash(encoder.encode(PASSWORD));
        return users.saveAndFlush(user).getUserId();
    }

    private int count(String table) { return db.queryForObject("SELECT count(*) FROM " + table, Integer.class); }

    private String login(String email, String password) throws Exception {
        return call("POST", "/api/auth/login", null, Map.of("email", email, "password", password), 200).path("token").asText();
    }

    private JsonNode register(String email, String password, String name, int expected) throws Exception {
        return call("POST", "/api/auth/register", null, form(email, password, name), expected);
    }

    private static Map<String, String> form(String email, String password, String name) {
        return Map.of("email", email, "password", password, "userName", name, "fullName", "E1 Fixture");
    }

    private static Map<String, String> proof(JsonNode challenge, String code) {
        var data = new java.util.HashMap<String, String>();
        data.put("challengeId", challenge.path("challengeId").asText());
        data.put("proofToken", challenge.path("proofToken").asText());
        if (code != null) data.put("code", code);
        return data;
    }

    private void verify(String path, String token, JsonNode challenge, String code, int expected) throws Exception {
        call("POST", path + "/verify", token, proof(challenge, code), expected);
    }

    private JsonNode status(String path, String token, JsonNode challenge) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path + "/status"))
                .header("X-Jira-Challenge-Id", challenge.path("challengeId").asText())
                .header("X-Jira-Verification-Proof", challenge.path("proofToken").asText());
        if (token != null) builder.header("Authorization", "Bearer " + token);
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private void waitDelivery(String path, JsonNode challenge, String expected) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        while (System.nanoTime() < end) {
            if (status(path, null, challenge).path("deliveryState").asText().equals(expected)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Expected mail delivery state " + expected);
    }

    private int mailCount(String email) throws Exception {
        var get = HttpRequest.newBuilder(URI.create(brevo.base() + "/test-mail?" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8))).build();
        return json.readTree(http.send(get, HttpResponse.BodyHandlers.ofString()).body()).size();
    }

    private void permitResend(String email) {
        db.update("UPDATE registration_challenges SET resend_after=clock_timestamp()-interval '1 second' WHERE email_normalized=?", email);
    }

    private static String wrong(String otp) { return otp.equals("000000") ? "000001" : "000000"; }

    private HttpRequest request(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body == null) return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        return builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
    }

    private JsonNode call(String method, String path, String token, Object body, int expected) throws Exception {
        var response = http.send(request(method, path, token, body), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s", method, path).isEqualTo(expected);
        if (expected == 429) assertThat(response.headers().firstValue("Retry-After")).isPresent();
        if (expected == 204) assertThat(response.body()).isEmpty();
        return response.body().isEmpty() ? json.nullNode() : json.readTree(response.body());
    }
}
