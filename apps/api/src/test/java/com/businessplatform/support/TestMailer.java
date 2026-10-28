package com.businessplatform.support;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.businessplatform.mail.MailMessage;
import com.businessplatform.mail.Mailer;

/** Captures emails instead of sending them; tests wait for the one they expect (delivery is async). */
public class TestMailer implements Mailer {

    private final List<MailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(MailMessage message) {
        sent.add(message);
    }

    public List<MailMessage> to(String email) {
        return sent.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
    }

    /** Waits up to 5 s for the {@code n}-th (1-based) email to {@code email}. */
    public MailMessage await(String email, int n) throws InterruptedException {
        Instant until = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(until)) {
            List<MailMessage> mine = to(email);
            if (mine.size() >= n) {
                return mine.get(n - 1);
            }
            Thread.sleep(20);
        }
        throw new AssertionError("No email #" + n + " to " + email + "; got " + to(email));
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        TestMailer testMailer() {
            return new TestMailer();
        }
    }
}
