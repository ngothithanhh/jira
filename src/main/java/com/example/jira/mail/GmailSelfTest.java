package com.example.jira.mail;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Clock;

/** CLI tách khỏi web và DB. Chỉ gửi một thư tới địa chỉ sender đã khai báo trong tệp OAuth. */
public final class GmailSelfTest {
    private GmailSelfTest() { }

    public static void main(String[] args) {
        if (args.length != 1 || !args[0].equals("--send-to-self")) {
            System.err.println("Dùng --send-to-self sau khi xác nhận địa chỉ Gmail gửi.");
            System.exit(2);
        }
        var json = new ObjectMapper();
        MailSender.Result result;
        try {
            String address = System.getenv("MAIL_SENDER_ADDRESS");
            MailMessage.requireAddress(address);
            Path tokenFile = Path.of(System.getenv("GMAIL_TOKEN_FILE"));
            var credentials = GmailCredentials.read(tokenFile, address, json);
            String name = System.getenv().getOrDefault("MAIL_SENDER_NAME", "Jira");
            var sender = new GmailApiMailSender(credentials, address, name, json, Clock.systemUTC());
            result = sender.send(new MailMessage(address, "Jira - Kiểm tra gửi Gmail", "Thư kiểm tra cấu hình gửi Gmail API của Jira."), () -> true);
        } catch (RuntimeException invalid) {
            System.err.println("Không chạy được phép thử Gmail. Kiểm tra cấu hình sender và tệp OAuth local.");
            System.exit(2);
            return;
        }
        if (result.outcome() == MailSender.Outcome.ACCEPTED) {
            System.out.println("PROVIDER_ACCEPTED " + result.providerMessageId());
        } else {
            System.err.println(result.outcome() + " " + result.code());
            System.exit(1);
        }
    }
}
