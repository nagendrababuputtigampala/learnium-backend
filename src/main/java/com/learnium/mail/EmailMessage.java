package com.learnium.mail;

/** One rendered email ready for transport: HTML body plus a plain-text fallback. */
public record EmailMessage(String to, String subject, String htmlBody, String textBody) {}
