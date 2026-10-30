package com.businessplatform.mail;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import com.businessplatform.common.i18n.Messages;
import com.businessplatform.common.i18n.Text;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Queues emails: they go out on a background thread once the current transaction commits (right
 * away when there is none), so a slow or failing mail server never blocks or rolls back a request.
 * A failure is logged, not retried.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    public static final String PRODUCT = "Business Platform";

    private final ApplicationEventPublisher events;
    private final Mailer mailer;
    private final Messages messages;

    /** Whether queued emails actually leave the server. */
    public boolean delivers() {
        return mailer.delivers();
    }

    public void queue(MailMessage message) {
        events.publishEvent(new Queued(message));
    }

    /** Published by {@link #queue}; delivered after commit. */
    public record Queued(MailMessage message) {
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void deliver(Queued q) {
        try {
            mailer.send(q.message());
        } catch (Exception e) {
            log.error("Could not send email to {} subject \"{}\": {}", q.message().to(), q.message().subject(),
                    e.toString());
        }
    }

    /**
     * A letter in both languages, as VirtualBiz writes them: English, then Khmer, in one shared layout; the subject in
     * the reader's language ({@code lang}). {@code from}: the organisation (or null for the product) shown on top.
     */
    public MailMessage letter(String to, String lang, String from, Text subject, List<Text> lines, Text action, String link,
            Text note) {
        Email e = new Email().preheader(messages.in(lang, lines.isEmpty() ? subject : lines.get(0))).badge(from == null ? PRODUCT : from);
        for (String l : List.of("en", "km")) {
            if ("km".equals(l)) {
                e.divider();
            }
            e.heading(messages.in(l, subject));
            lines.forEach(x -> e.line(messages.in(l, x)));
            if (action != null && link != null) {
                e.button(messages.in(l, action), link);
            }
            if (note != null) {
                e.small(messages.in(l, note));
            }
        }
        return new MailMessage(to, messages.in(lang, subject), e.text(), e.html());
    }
}
