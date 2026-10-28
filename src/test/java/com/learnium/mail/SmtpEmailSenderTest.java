package com.learnium.mail;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderTest {

    @Test
    void buildsAndSendsAMimeMessage() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, "no-reply@learnium.local");

        sender.send(new EmailMessage("a@b.test", "Subject", "<p>Hi</p>", "Hi"));

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void propagatesTransportFailures() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        org.mockito.Mockito.doThrow(new MailSendException("smtp down"))
                .when(mailSender)
                .send(any(MimeMessage.class));
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, "no-reply@learnium.local");

        assertThatThrownBy(() -> sender.send(new EmailMessage("a@b.test", "S", "<p>x</p>", "x")))
                .isInstanceOf(MailSendException.class);
    }
}
