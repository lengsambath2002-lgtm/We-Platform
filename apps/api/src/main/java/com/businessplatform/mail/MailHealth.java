package com.businessplatform.mail;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** The "mail" part of /actuator/health: whether the SMTP server answers (when mail is configured). */
@Component("mail")
@RequiredArgsConstructor
public class MailHealth implements HealthIndicator {

    private final MailProperties props;

    @Override
    public Health health() {
        if (!props.configured()) {
            return Health.up().withDetail("sending", "off (emails are logged)").build();
        }
        int port = props.port() == null ? 587 : props.port();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(props.host(), port), 3000);
            return Health.up().withDetail("smtp", props.host() + ":" + port).build();
        } catch (Exception e) {
            return Health.down().withDetail("smtp", props.host() + ":" + port).withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
