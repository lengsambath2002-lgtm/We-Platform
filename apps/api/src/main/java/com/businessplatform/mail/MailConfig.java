package com.businessplatform.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * SMTP when {@code PLATFORM_MAIL_HOST} is set, otherwise the logging stand-in. Production without
 * mail only warns: invitation links can still be copied from the invite dialog, but "forgot
 * password" cannot reach anyone.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    Mailer mailer(MailProperties props, @Value("${platform.environment:local}") String environment) {
        if (props.configured()) {
            log.info("Mail: SMTP {}", props.host());
            if (props.restricted()) {
                log.info("Mail: only {} addresses in PLATFORM_MAIL_ONLY_TO get real email; others are logged",
                        AllowListMailer.parse(props.onlyTo()).size());
                return new AllowListMailer(new SmtpMailer(props), new LoggingMailer("local".equalsIgnoreCase(environment),
                        "Not in PLATFORM_MAIL_ONLY_TO"), props.onlyTo());
            }
            return new SmtpMailer(props);
        }
        if ("production".equalsIgnoreCase(environment)) {
            log.warn("Mail is not configured (PLATFORM_MAIL_HOST): invitations and password resets are not emailed");
        }
        return new LoggingMailer("local".equalsIgnoreCase(environment));
    }
}
