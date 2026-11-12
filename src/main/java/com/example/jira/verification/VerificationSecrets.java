package com.example.jira.verification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Component
class VerificationSecrets {
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    VerificationSecrets(@Value("${app.verification.hmac-key:${VERIFICATION_HMAC_KEY:}}") String configuredKey) {
        if (configuredKey.isBlank()) {
            key = null;
            return;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Khóa HMAC xác minh cần 32 byte mã hóa Base64");
        }
        if (decoded.length != 32) throw new IllegalArgumentException("Khóa HMAC xác minh cần 32 byte mã hóa Base64");
        key = new SecretKeySpec(decoded, "HmacSHA256");
    }

    boolean configured() { return key != null; }

    String proof() {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    byte[] proofHash(String proof) { return mac("proof:" + proof); }

    boolean proofMatches(byte[] stored, String supplied) {
        return stored != null && supplied != null && supplied.matches("[A-Za-z0-9_-]{43}")
                && MessageDigest.isEqual(stored, proofHash(supplied));
    }

    String otp() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }

    byte[] otpHash(UUID id, int generation, String code) {
        return mac("otp:" + id + ":" + generation + ":" + code);
    }

    byte[] otpHistoryHash(UUID id, String code) { return mac("otp-history:" + id + ":" + code); }

    byte[] subject(String scope, String identity) { return mac("rate:" + scope + ":" + identity); }

    private byte[] mac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("Không tạo được mã xác minh", unavailable);
        }
    }
}
