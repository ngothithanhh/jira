package com.example.jira.mail;

import java.time.Duration;
import java.util.function.BooleanSupplier;

interface MailSender {
    enum Outcome { ACCEPTED, RETRYABLE, UNKNOWN, FAILED }
    enum Code { RATE_LIMIT, TOKEN_UNAVAILABLE, MAIL_REAUTH, REJECTED, DELIVERY_UNKNOWN, INVALID_PAYLOAD, INELIGIBLE }

    record Result(Outcome outcome, Code code, Duration retryAfter, String providerMessageId) {
        static Result accepted(String id) { return new Result(Outcome.ACCEPTED, null, null, id); }
        static Result retry(Code code, Duration delay) { return new Result(Outcome.RETRYABLE, code, delay, null); }
        static Result unknown() { return new Result(Outcome.UNKNOWN, Code.DELIVERY_UNKNOWN, null, null); }
        static Result failed(Code code) { return new Result(Outcome.FAILED, code, null, null); }
    }

    boolean ready();
    Result send(MailMessage message, BooleanSupplier canDispatch);
}
