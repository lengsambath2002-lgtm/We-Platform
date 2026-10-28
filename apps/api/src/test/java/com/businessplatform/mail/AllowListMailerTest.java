package com.businessplatform.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** With PLATFORM_MAIL_ONLY_TO set, only listed addresses reach the real mailer. */
class AllowListMailerTest {

    static class Capture implements Mailer {
        final List<String> to = new ArrayList<>();

        @Override
        public void send(MailMessage m) {
            to.add(m.to());
        }
    }

    @Test
    void only_listed_addresses_get_real_email() throws Exception {
        Capture real = new Capture();
        Capture logged = new Capture();
        Mailer mailer = new AllowListMailer(real, logged, " Owner@Example.com, second@example.com ");
        for (String to : List.of("owner@example.com", "SECOND@example.com", "someone@e2e.test")) {
            mailer.send(new MailMessage(to, "s", "t", "h"));
        }
        assertThat(real.to).containsExactly("owner@example.com", "SECOND@example.com");
        assertThat(logged.to).containsExactly("someone@e2e.test");
    }
}
