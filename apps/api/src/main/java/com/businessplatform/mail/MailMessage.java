package com.businessplatform.mail;

/** A short email with a plain-text and an HTML body, already in the recipient's language. */
public record MailMessage(String to, String subject, String text, String html) {

    @Override
    public String toString() {
        // Bodies can carry single-use links; keep them out of accidental logging.
        return "MailMessage[to=" + to + ", subject=" + subject + "]";
    }
}
