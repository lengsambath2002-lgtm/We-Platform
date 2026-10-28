package com.businessplatform.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * Used when no SMTP server is configured: logs who would get which email. The body (with its
 * single-use link) is logged only on a developer machine ({@code platform.environment=local}).
 */
@Slf4j
public class LoggingMailer implements Mailer {

    private final boolean local;

    public LoggingMailer(boolean local) {
        this.local = local;
    }

    @Override
    public void send(MailMessage m) {
        if (local) {
            log.info("Mail not configured; would send to {} subject \"{}\":\n{}", m.to(), m.subject(), m.text());
        } else {
            log.info("Mail not configured; would send to {} subject \"{}\"", m.to(), m.subject());
        }
    }

    @Override
    public boolean delivers() {
        return false;
    }
}
