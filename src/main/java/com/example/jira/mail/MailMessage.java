package com.example.jira.mail;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/** Nội dung do nghiệp vụ dựng. toString không chứa email, OTP hay link. */
public record MailMessage(String recipient, String subject, String text) {
    public MailMessage {
        requireAddress(recipient);
        if (subject == null || subject.isBlank() || subject.length() > 200 || subject.chars().anyMatch(Character::isISOControl)
                || text == null || text.isBlank() || text.length() > 16000) {
            throw new IllegalArgumentException("Nội dung thư không hợp lệ");
        }
    }

    static void requireAddress(String address) {
        if (address == null || address.length() > 254 || address.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Địa chỉ thư không hợp lệ");
        }
        try {
            InternetAddress parsed = new InternetAddress(address, true);
            parsed.validate();
            if (parsed.isGroup() || !address.equals(parsed.getAddress())) throw new AddressException();
        } catch (AddressException invalid) {
            throw new IllegalArgumentException("Địa chỉ thư không hợp lệ");
        }
    }

    @Override public String toString() { return "MailMessage[redacted]"; }
}
