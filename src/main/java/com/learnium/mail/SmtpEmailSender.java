package com.learnium.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP transport via Spring's {@link JavaMailSender}. Connection settings come
 * from {@code spring.mail.*} (env-driven: MAIL_HOST / MAIL_PORT / ...), with
 * Mailpit-friendly local defaults. Failures propagate to the caller (the
 * outbox dispatcher records the exception class as the row's last_error).
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(
            JavaMailSender mailSender, @Value("${learnium.mail.from:no-reply@learnium.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    @SneakyThrows
    public void send(EmailMessage message) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(message.to());
        helper.setSubject(message.subject());
        helper.setText(message.textBody(), message.htmlBody());
        mailSender.send(mimeMessage);
    }
}
