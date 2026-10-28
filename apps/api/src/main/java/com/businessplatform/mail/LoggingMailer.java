package com.businessplatform.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * Used when no SMTP server is configured: logs who would get which email. The body (with its
 * single-use link) is logged only on a developer machine ({@code platform.environment=local}).
 */
@Slf4j
public class LoggingMailer implements Mailer {

    private final boolean local;
    private final String reason;

    public LoggingMailer(boolean local) {
        this(local, "Mail not configured");
    }

    /** {@code reason} starts the log line, e.g. why this address gets no real email. */
    public LoggingMailer(boolean local, String reason) {
        this.local = local;
        this.reason = reason;
    }

    @Override
    public void send(MailMessage m) {
        if (local) {
            log.info("{}; would send to {} subject \"{}\":\n{}", reason, m.to(), m.subject(), m.text());
        } else {
            log.info("{}; would send to {} subject \"{}\"", reason, m.to(), m.subject());
        }
    }

    @Override
    public boolean delivers() {
        return false;
    }
}
