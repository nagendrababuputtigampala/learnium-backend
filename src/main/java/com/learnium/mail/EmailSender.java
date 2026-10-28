package com.learnium.mail;

/**
 * Transport seam for outgoing mail. Production uses SMTP ({@link SmtpEmailSender});
 * tests substitute a stub, so no test ever opens a real mail connection.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
