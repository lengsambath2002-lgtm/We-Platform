package com.businessplatform.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.businessplatform.common.i18n.Messages;
import com.businessplatform.common.i18n.Text;

/** Emails are in English then Khmer, escape what people typed, and a failing mail server never reaches the caller. */
class MailServiceTest {

    private final MailService mail = new MailService(event -> { }, message -> { }, new Messages());

    @Test
    void a_letter_is_in_both_languages_with_names_escaped_in_html_but_kept_in_text() {
        MailMessage m = mail.letter("a@example.com", "km", "<b>Acme & Co</b>", Text.of("Reset your Business Platform password"),
                List.of(Text.of("{inviter} invited you to join {org} as {role}.", Map.of("inviter", "<b>Acme & Co</b>", "org", "X",
                        "role", Text.of("a member")))),
                Text.of("Accept the invitation"), "https://app.example.com/invite/abc?x=1&y=2", null);
        assertThat(m.html()).contains("&lt;b&gt;Acme &amp; Co&lt;/b&gt;").doesNotContain("<b>Acme")
                .contains("href=\"https://app.example.com/invite/abc?x=1&amp;y=2\"").contains("Business Platform");
        assertThat(m.text()).contains("<b>Acme & Co</b> invited you").contains("https://app.example.com/invite/abc?x=1&y=2");
        // English first, then Khmer; the subject in the reader's language.
        assertThat(m.text().indexOf("Accept the invitation")).isLessThan(m.text().indexOf("———"));
        assertThat(m.subject()).isEqualTo(new Messages().in("km", "Reset your Business Platform password", Map.of()));
        assertThat(m.text().substring(m.text().indexOf("———"))).contains(m.subject());
        assertThat(m.toString()).doesNotContain("invite/abc");
    }

    @Test
    void reserved_test_domains_are_never_emailed() throws Exception {
        List<String> sent = new java.util.ArrayList<>();
        List<String> logged = new java.util.ArrayList<>();
        Mailer m = new AllowListMailer(x -> sent.add(x.to()), x -> logged.add(x.to()), "", ".test,example.com");
        for (String to : List.of("dara@kosign.test", "x@example.com", "lina@gmail.com")) {
            m.send(new MailMessage(to, "s", "t", "h"));
        }
        assertThat(sent).containsExactly("lina@gmail.com");
        assertThat(logged).containsExactly("dara@kosign.test", "x@example.com");
    }

    @Test
    void a_send_failure_is_logged_not_thrown() {
        Mailer broken = message -> {
            throw new IllegalStateException("SMTP down");
        };
        MailService failing = new MailService(event -> { }, broken, new Messages());
        assertThatCode(() -> failing.deliver(new MailService.Queued(new MailMessage("a@example.com", "s", "t", "h"))))
                .doesNotThrowAnyException();
    }
}
