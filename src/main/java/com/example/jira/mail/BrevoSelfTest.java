package com.example.jira.mail;

import com.fasterxml.jackson.databind.ObjectMapper;

/** CLI dùng cùng adapter với worker và chỉ gửi một thư tới địa chỉ sender. */
public final class BrevoSelfTest {
    private BrevoSelfTest() { }

    public static void main(String[] args) {
        if (args.length != 1 || !args[0].equals("--send-to-self")) {
            System.err.println("Dùng --send-to-self sau khi xác minh địa chỉ gửi trong Brevo.");
            System.exit(2);
        }
        MailSender.Result result;
        try {
            String email = System.getenv("BREVO_SENDER_EMAIL");
            String name = System.getenv().getOrDefault("BREVO_SENDER_NAME", "Jira");
            var sender = new BrevoMailSender(System.getenv("BREVO_API_KEY"), email, name, new ObjectMapper());
            result = sender.send(new MailMessage(email, "Jira - Kiểm tra gửi Brevo", "Thư kiểm tra cấu hình gửi Brevo của Jira."), () -> true);
        } catch (RuntimeException invalid) {
            System.err.println("Không chạy được phép thử Brevo. Kiểm tra API key và địa chỉ gửi trong môi trường local.");
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
