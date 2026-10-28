package com.businessplatform.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Email bodies escape what people typed; a failing mail server never reaches the caller. */
class MailServiceTest {

    @Test
    void names_are_escaped_in_html_but_kept_in_text() {
        MailMessage m = MailService.compose("a@example.com", "Hi", List.of("<b>Acme & Co</b> invited you"),
                "Accept", "https://app.example.com/invite/abc?x=1&y=2", "Ignore if unexpected");
        assertThat(m.html()).contains("&lt;b&gt;Acme &amp; Co&lt;/b&gt;").doesNotContain("<b>Acme")
                .contains("href=\"https://app.example.com/invite/abc?x=1&amp;y=2\"").contains("Business Platform");
        assertThat(m.text()).contains("<b>Acme & Co</b> invited you").contains("https://app.example.com/invite/abc?x=1&y=2");
        assertThat(m.toString()).doesNotContain("invite/abc");
    }

    @Test
    void a_send_failure_is_logged_not_thrown() {
        Mailer broken = message -> {
            throw new IllegalStateException("SMTP down");
        };
        MailService mail = new MailService(event -> { }, broken);
        assertThatCode(() -> mail.deliver(new MailService.Queued(new MailMessage("a@example.com", "s", "t", "h"))))
                .doesNotThrowAnyException();
    }
}
