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
    private final Set<String> never;

    /** {@code onlyTo} empty = everyone (but {@code neverTo}). */
    public AllowListMailer(Mailer real, Mailer others, String onlyTo, String neverTo) {
        this.real = real;
        this.others = others;
        this.allowed = parse(onlyTo);
        this.never = parse(neverTo);
    }

    public AllowListMailer(Mailer real, Mailer others, String onlyTo) {
        this(real, others, onlyTo, "");
    }

    /** Comma- or space-separated addresses, domains ("example.com") or domain endings (".test"), any case. */
    static Set<String> parse(String list) {
        return list == null ? Set.of() : Arrays.stream(list.split("[,\\s]+")).map(s -> s.strip().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /** Whether the address is on the list: itself, its domain, or a domain ending (".test"). */
    static boolean listed(Set<String> list, String address) {
        String a = address.strip().toLowerCase(Locale.ROOT);
        String domain = a.substring(a.indexOf('@') + 1);
        return list.contains(a) || list.contains(domain) || list.stream().anyMatch(e -> e.startsWith(".") && domain.endsWith(e));
    }

    @Override
    public void send(MailMessage m) throws Exception {
        boolean ok = !listed(never, m.to()) && (allowed.isEmpty() || listed(allowed, m.to()));
        if (ok) {
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
