package com.businessplatform.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code platform.mail.*}: the SMTP server. Without a host, emails are only logged (see
 * {@link LoggingMailer}). {@code security} is {@code starttls} (port 587), {@code ssl} (465) or
 * {@code none}. {@code onlyTo}: when set, only these addresses get real email (see {@link AllowListMailer}).
 */
@ConfigurationProperties("platform.mail")
public record MailProperties(String host, Integer port, String username, String password, String from,
        String security, String onlyTo) {

    public boolean configured() {
        return host != null && !host.isBlank();
    }

    public boolean restricted() {
        return onlyTo != null && !onlyTo.isBlank();
    }
}
