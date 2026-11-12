package com.example.jira.mail;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static com.example.jira.mail.GmailApiMailSenderTest.*;
import static com.example.jira.mail.MailOutbox.Kind.REGISTRATION_OTP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailOutboxPostgresIT {
    private static PostgreSQLContainer<?> container;
    private static JdbcTemplate db;
    private static TransactionTemplate tx;
    private MailOutbox outbox;

    @BeforeAll static void setupPostgres() throws Exception {
        String url = System.getProperty("test.pg.url");
        DriverManagerDataSource source;
        if (url == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            source = new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        } else {
            String user = System.getProperty("test.pg.user");
            String directory = System.getProperty("test.pg.data", "");
            // Giống guard của suite PostgreSQL: chỉ nhận cluster riêng do test-postgres.ps1 tạo.
            if (!url.equals("jdbc:postgresql://127.0.0.1:55432/jira_regression") || !"regression".equals(user)
                    || directory.isBlank() || !Path.of(directory).toAbsolutePath().normalize().getParent()
                    .getFileName().toString().matches("test-postgres-[a-f0-9]{32}")) throw new IllegalStateException("Unsafe mail test database");
            source = new DriverManagerDataSource(url, user, System.getProperty("test.pg.password", ""));
            try (var connection = source.getConnection(); var stmt = connection.createStatement();
                 var row = stmt.executeQuery("select current_database(),current_user,current_setting('data_directory')")) {
                if (!row.next() || !row.getString(1).equals("jira_regression") || !row.getString(2).equals("regression")
                        || !Path.of(row.getString(3)).toRealPath().equals(Path.of(directory).toRealPath())) {
                    throw new IllegalStateException("Unsafe mail test cluster");
                }
            }
        }
        var migration = new ResourceDatabasePopulator(new FileSystemResource("scripts/migrations/011_mail_outbox.sql"));
        migration.execute(source);
        migration.execute(source);
        db = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @AfterAll static void stopContainer() { if (container != null) container.stop(); }

    @BeforeEach void clean() {
        db.execute("TRUNCATE mail_outbox");
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        outbox = new MailOutbox(db, new MailPayloadCipher(Base64.getEncoder().encodeToString(key), 1, JSON));
    }

    @Test void rollbackAndDuplicatesDoNotDispatchOrWritePlaintext() throws Exception {
        UUID aggregate = UUID.randomUUID();
        assertThatThrownBy(() -> outbox.enqueue(REGISTRATION_OTP, aggregate, 1, MESSAGE, expires()))
                .isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(state -> {
            assertThat(outbox.enqueue(REGISTRATION_OTP, aggregate, 1, MESSAGE, expires())).isPresent();
            state.setRollbackOnly();
        });
        AtomicInteger posts = new AtomicInteger();
        try (var google = new GoogleStub(exchange -> token(exchange, "access"), exchange -> {
            posts.incrementAndGet(); respond(exchange, 200, "{\"id\":\"provider-pg\"}");
        })) {
            var worker = new MailOutboxWorker(outbox, google.sender(), Clock.systemUTC());
            assertThat(worker.processOne()).isFalse();
            assertThat(posts).hasValue(0);
            UUID id = enqueue(aggregate, 1);
            tx.executeWithoutResult(state -> assertThat(outbox.enqueue(REGISTRATION_OTP, aggregate, 1, MESSAGE, expires())).isEmpty());
            byte[] encrypted = db.queryForObject("SELECT payload_ciphertext FROM mail_outbox WHERE id=?", byte[].class, id);
            assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain("104269", "Tên");
            assertThat(worker.processOne()).isTrue();
            assertThat(worker.processOne()).isFalse();
            assertThat(posts).hasValue(1);
            assertThat(status(id)).isEqualTo("PROVIDER_ACCEPTED");
            assertThat(db.queryForObject("SELECT provider_message_id FROM mail_outbox WHERE id=?", String.class, id)).isEqualTo("provider-pg");
            assertErased(id);
        }
    }

    @Test void twoWorkersCannotDispatchTheSameLease() throws Exception {
        UUID id = enqueue(UUID.randomUUID(), 1);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger posts = new AtomicInteger();
        MailSender sender = fake(guard -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(guard.getAsBoolean()).isTrue();
            posts.incrementAndGet();
            started.countDown();
            await(release);
            return MailSender.Result.accepted("only-once");
        });
        var callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(() -> worker(sender).processOne());
            await(started);
            assertThat(status(id)).isEqualTo("SENDING");
            assertThat(callers.submit(() -> worker(sender).processOne()).get(5, TimeUnit.SECONDS)).isFalse();
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); callers.shutdownNow(); }
        assertThat(posts).hasValue(1);
        assertThat(status(id)).isEqualTo("PROVIDER_ACCEPTED");
    }

    @Test void supersedeBlocksClaimedMailAndRetryOfMailAlreadyInFlight() {
        UUID aggregate = UUID.randomUUID();
        UUID first = enqueue(aggregate, 1);
        MailOutbox.Lease old = outbox.claim().orElseThrow();
        tx.executeWithoutResult(state -> outbox.supersedeThrough(REGISTRATION_OTP, aggregate, 1));
        assertThat(outbox.beginDispatch(old)).isEmpty();
        assertThat(status(first)).isEqualTo("SUPERSEDED");
        assertErased(first);
        UUID second = enqueue(aggregate, 2);
        var sending = worker(fake(guard -> {
            assertThat(guard.getAsBoolean()).isTrue();
            tx.executeWithoutResult(state -> {
                outbox.supersedeThrough(REGISTRATION_OTP, aggregate, 2);
                outbox.enqueue(REGISTRATION_OTP, aggregate, 3, MESSAGE, expires()).orElseThrow();
            });
            assertThat(guard.getAsBoolean()).isFalse();
            return MailSender.Result.retry(MailSender.Code.RATE_LIMIT, Duration.ofSeconds(15));
        }));
        assertThat(sending.processOne()).isTrue();
        assertThat(status(second)).isEqualTo("SUPERSEDED");
        assertErased(second);
        assertThat(db.queryForObject("SELECT count(*) FROM mail_outbox WHERE generation=3 AND status='QUEUED'", Integer.class)).isEqualTo(1);
    }

    @Test void expiredLeaseBecomesUnknownAndLateResultCannotChangeIt() {
        UUID id = enqueue(UUID.randomUUID(), 1);
        var lease = outbox.claim().orElseThrow();
        outbox.beginDispatch(lease).orElseThrow();
        db.update("UPDATE mail_outbox SET lease_until=clock_timestamp()-interval '1 second' WHERE id=?", id);
        assertThat(outbox.canDispatch(lease)).isFalse();
        outbox.reap();
        assertThat(status(id)).isEqualTo("UNKNOWN");
        assertThat(outbox.finish(lease, MailOutbox.Status.PROVIDER_ACCEPTED, null, "late-id", null)).isFalse();
        assertThat(outbox.claim()).isEmpty();
        assertErased(id);
    }

    @Test void lockedExpiredRowDoesNotBlockAnotherDueJob() throws Exception {
        UUID expired = enqueue(UUID.randomUUID(), 1);
        db.update("UPDATE mail_outbox SET expires_at=clock_timestamp()-interval '1 second' WHERE id=?", expired);
        UUID due = enqueue(UUID.randomUUID(), 1);
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        var callers = Executors.newFixedThreadPool(2);
        try {
            var locker = callers.submit(() -> tx.executeWithoutResult(state -> {
                db.queryForObject("SELECT id FROM mail_outbox WHERE id=? FOR UPDATE", UUID.class, expired);
                locked.countDown();
                await(release);
            }));
            await(locked);
            assertThat(callers.submit(() -> worker(fake(guard -> MailSender.Result.accepted("due"))).processOne())
                    .get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(status(due)).isEqualTo("PROVIDER_ACCEPTED");
            release.countDown();
            locker.get(5, TimeUnit.SECONDS);
            outbox.reap();
            assertThat(status(expired)).isEqualTo("FAILED");
            assertErased(expired);
        } finally { release.countDown(); callers.shutdownNow(); }
    }

    @Test void retryLimitAndExpiryKeepAmbiguousOrExpiredJobsFromDispatch() {
        UUID id = enqueue(UUID.randomUUID(), 1);
        var retry = worker(fake(guard -> MailSender.Result.retry(MailSender.Code.RATE_LIMIT, Duration.ofSeconds(45))));
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(retry.processOne()).isTrue();
            assertThat(status(id)).isEqualTo(attempt < 4 ? "RETRY_WAIT" : "FAILED");
            assertThat(retry.processOne()).isFalse();
            db.update("UPDATE mail_outbox SET next_attempt_at=clock_timestamp()-interval '1 second' WHERE id=?", id);
        }
        assertErased(id);
        UUID ambiguous = enqueue(UUID.randomUUID(), 1);
        var unknown = worker(fake(guard -> MailSender.Result.unknown()));
        assertThat(unknown.processOne()).isTrue();
        assertThat(status(ambiguous)).isEqualTo("UNKNOWN");
        assertThat(unknown.processOne()).isFalse();
        assertErased(ambiguous);
        UUID almostExpired = tx.execute(state -> outbox.enqueue(REGISTRATION_OTP, UUID.randomUUID(), 1, MESSAGE,
                Instant.now().plusSeconds(15)).orElseThrow());
        assertThat(retry.processOne()).isTrue();
        assertThat(status(almostExpired)).isEqualTo("FAILED");
        assertErased(almostExpired);
    }

    private UUID enqueue(UUID aggregate, int generation) {
        return tx.execute(state -> outbox.enqueue(REGISTRATION_OTP, aggregate, generation, MESSAGE, expires()).orElseThrow());
    }

    private static Instant expires() { return Instant.now().plus(Duration.ofMinutes(10)); }
    private static String status(UUID id) { return db.queryForObject("SELECT status FROM mail_outbox WHERE id=?", String.class, id); }
    private static void assertErased(UUID id) {
        assertThat(db.queryForObject("SELECT payload_ciphertext IS NULL AND nonce IS NULL FROM mail_outbox WHERE id=?", Boolean.class, id)).isTrue();
    }
    private MailOutboxWorker worker(MailSender sender) { return new MailOutboxWorker(outbox, sender, Clock.systemUTC()); }
    private static MailSender fake(java.util.function.Function<BooleanSupplier, MailSender.Result> send) {
        return new MailSender() {
            @Override public boolean ready() { return true; }
            @Override public Result send(MailMessage message, BooleanSupplier guard) { return send.apply(guard); }
        };
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
