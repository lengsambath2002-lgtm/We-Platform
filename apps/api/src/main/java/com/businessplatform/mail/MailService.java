package com.businessplatform.mail;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

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
     * A short email: paragraphs, then an optional button (text + link), then a note. All text is
     * already translated; it is HTML-escaped here.
     */
    public static MailMessage compose(String to, String subject, List<String> paragraphs, String action,
            String link, String note) {
        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder("<!doctype html><html><body style=\"margin:0;padding:24px;"
                + "background:#f5f5f5;font-family:-apple-system,'Segoe UI',Roboto,'Noto Sans Khmer',sans-serif;"
                + "color:#18181b\"><div style=\"max-width:520px;margin:0 auto;background:#fff;border-radius:8px;"
                + "padding:24px;font-size:15px;line-height:1.6\"><p style=\"margin:0 0 16px;font-weight:600\">")
                .append(PRODUCT).append("</p>");
        for (String p : paragraphs) {
            text.append(p).append("\n\n");
            html.append("<p style=\"margin:0 0 12px\">").append(esc(p)).append("</p>");
        }
        if (link != null) {
            text.append(action).append(":\n").append(link).append("\n\n");
            html.append("<p style=\"margin:20px 0\"><a href=\"").append(esc(link))
                    .append("\" style=\"display:inline-block;background:#18181b;color:#fff;text-decoration:none;"
                            + "padding:10px 16px;border-radius:6px;font-weight:500\">")
                    .append(esc(action)).append("</a></p><p style=\"margin:0 0 12px;font-size:13px;color:#71717a;"
                            + "word-break:break-all\">").append(esc(link)).append("</p>");
        }
        if (note != null) {
            text.append(note).append("\n\n");
            html.append("<p style=\"margin:16px 0 0;font-size:13px;color:#71717a\">").append(esc(note)).append("</p>");
        }
        text.append("— ").append(PRODUCT).append('\n');
        html.append("</div></body></html>");
        return new MailMessage(to, subject, text.toString(), html.toString());
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s, "UTF-8");
    }
}
