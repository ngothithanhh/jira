package com.example.jira.verification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.verification.cleanup-enabled", havingValue = "true", matchIfMissing = true)
class VerificationCleanup {
    private final VerificationStore store;
    VerificationCleanup(VerificationStore store) { this.store = store; }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    @Transactional
    public void clean() {
        if (store.installed()) store.clean(Instant.now());
    }
}
