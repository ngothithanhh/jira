package com.example.jira.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

final class MailOutboxWorker {
    private static final Logger LOG = LoggerFactory.getLogger(MailOutboxWorker.class);
    private static final int MAX_ATTEMPTS = 4;
    private final MailOutbox outbox;
    private final MailSender sender;
    private final Clock clock;

    MailOutboxWorker(MailOutbox outbox, MailSender sender, Clock clock) {
        this.outbox = outbox;
        this.sender = sender;
        this.clock = clock;
    }

    /** Worker lấy từng lease để hàng chờ lâu không hết lease trước lượt gửi. */
    @Scheduled(fixedDelayString = "${app.mail.poll-delay-ms:2000}")
    void poll() {
        try {
            for (int i = 0; i < 16 && processOne(); i++) { /* Xử lý tối đa 16 job mỗi lượt. */ }
        } catch (RuntimeException failure) {
            LOG.warn("Mail outbox poll failed. Active leases will be reconciled.");
        }
    }

    boolean processOne() {
        if (Thread.currentThread().isInterrupted()) return false;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Worker không được gửi mail trong transaction DB");
        }
        outbox.reap();
        if (!sender.ready() || Thread.currentThread().isInterrupted()) return false;
        var claimed = outbox.claim();
        if (claimed.isEmpty()) return false;
        MailOutbox.Lease lease = claimed.get();
        outbox.beginDispatch(lease).ifPresent(job -> deliver(lease, job));
        return true;
    }

    private void deliver(MailOutbox.Lease lease, MailOutbox.Dispatch job) {
        MailMessage message;
        try { message = outbox.decrypt(job); }
        catch (IllegalStateException invalidPayload) {
            outbox.finish(lease, MailOutbox.Status.FAILED, MailSender.Code.INVALID_PAYLOAD, null, null);
            return;
        }
        MailSender.Result result = sender.send(message, () -> outbox.canDispatch(lease));
        if (result.code() == MailSender.Code.MAIL_REAUTH) LOG.warn("Gmail sender needs reauthorization.");
        if (result.code() == MailSender.Code.BREVO_AUTH) LOG.warn("Brevo rejected API credentials. Check local configuration and restart the sender.");
        switch (result.outcome()) {
            case ACCEPTED -> outbox.finish(lease, MailOutbox.Status.PROVIDER_ACCEPTED, null, result.providerMessageId(), null);
            case UNKNOWN -> outbox.finish(lease, MailOutbox.Status.UNKNOWN, result.code(), null, null);
            case FAILED -> outbox.finish(lease, MailOutbox.Status.FAILED, result.code(), null, null);
            case RETRYABLE -> retry(lease, job, result);
        }
    }

    /** Retry chỉ dành cho từ chối xác định hoặc lỗi lấy token trước gửi, trong hạn của generation. */
    private void retry(MailOutbox.Lease lease, MailOutbox.Dispatch job, MailSender.Result result) {
        long baseSeconds = 5L << Math.min(6, job.attempt() - 1);
        Duration delay = Duration.ofSeconds(baseSeconds + ThreadLocalRandom.current().nextLong(baseSeconds + 1));
        if (result.retryAfter() != null && result.retryAfter().compareTo(delay) > 0) delay = result.retryAfter();
        Instant next = clock.instant().plus(delay);
        boolean eligible = job.attempt() < MAX_ATTEMPTS && next.isBefore(job.expiresAt());
        outbox.finish(lease, eligible ? MailOutbox.Status.RETRY_WAIT : MailOutbox.Status.FAILED,
                result.code(), null, eligible ? next : null);
    }
}
