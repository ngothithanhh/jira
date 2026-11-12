package com.example.jira.mail;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

final class MailPayloadCipher {
    record Context(UUID id, MailOutbox.Kind kind, UUID aggregateId, int generation, String recipient, int keyVersion) {
        private byte[] aad() {
            return (id + "\n" + kind.name() + "\n" + aggregateId + "\n" + generation + "\n" + keyVersion + "\n" + recipient)
                    .getBytes(StandardCharsets.UTF_8);
        }
        @Override public String toString() { return "MailPayloadContext[redacted]"; }
    }
    record Sealed(byte[] ciphertext, byte[] nonce) { }
    private record Content(String subject, String text) { }

    private final SecretKeySpec key;
    private final int version;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();

    MailPayloadCipher(String base64Key, int version, ObjectMapper json) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(base64Key); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Khóa mã hóa mail không hợp lệ"); }
        try {
            if (bytes.length != 32 || version <= 0) throw new IllegalArgumentException("Khóa mail cần 32 byte và phiên bản dương");
            this.key = new SecretKeySpec(bytes, "AES");
        } finally { Arrays.fill(bytes, (byte) 0); }
        this.version = version;
        this.json = json;
    }

    int version() { return version; }

    /** OTP/link nằm trong payload AES-GCM, gắn với ID, generation và recipient của job. */
    Sealed seal(Context context, MailMessage message) {
        byte[] clear = null;
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            clear = json.writeValueAsBytes(new Content(message.subject(), message.text()));
            return new Sealed(cipher(Cipher.ENCRYPT_MODE, context, nonce).doFinal(clear), nonce);
        } catch (GeneralSecurityException | IOException failure) {
            throw new IllegalStateException("Không mã hóa được nội dung thư");
        } finally { if (clear != null) Arrays.fill(clear, (byte) 0); }
    }

    MailMessage open(Context context, byte[] ciphertext, byte[] nonce) {
        byte[] clear = null;
        try {
            clear = cipher(Cipher.DECRYPT_MODE, context, nonce).doFinal(ciphertext);
            Content content = json.readValue(clear, Content.class);
            return new MailMessage(context.recipient(), content.subject(), content.text());
        } catch (GeneralSecurityException | IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Không giải mã được nội dung thư");
        } finally { if (clear != null) Arrays.fill(clear, (byte) 0); }
    }

    private Cipher cipher(int mode, Context context, byte[] nonce) throws GeneralSecurityException {
        if (context.keyVersion() != version) throw new GeneralSecurityException();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(context.aad());
        return cipher;
    }
}
