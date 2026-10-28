package com.businessplatform.mail;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sends real email only to the addresses in {@code PLATFORM_MAIL_ONLY_TO}; anyone else gets the logging
 * stand-in. For development and test servers that use a real mailbox, so test sign-ups
 * ({@code @e2e.test}, {@code @demo.test}) never bounce off it.
 */
public class AllowListMailer implements Mailer {

    private final Mailer real;
    private final Mailer others;
    private final Set<String> allowed;

    public AllowListMailer(Mailer real, Mailer others, String onlyTo) {
        this.real = real;
        this.others = others;
        this.allowed = parse(onlyTo);
    }

    /** Comma- or space-separated addresses, compared case-insensitively. */
    static Set<String> parse(String onlyTo) {
        return Arrays.stream(onlyTo.split("[,\\s]+")).map(s -> s.strip().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void send(MailMessage m) throws Exception {
        if (allowed.contains(m.to().strip().toLowerCase(Locale.ROOT))) {
            real.send(m);
        } else {
            others.send(m);
        }
    }

    @Override
    public boolean delivers() {
        return real.delivers();
    }
}
