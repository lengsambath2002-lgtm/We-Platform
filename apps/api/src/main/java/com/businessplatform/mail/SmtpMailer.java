package com.businessplatform.mail;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.internet.MimeMessage;

/** Sends through the configured SMTP server, as multipart text + HTML. */
public class SmtpMailer implements Mailer {

    private final JavaMailSenderImpl sender = new JavaMailSenderImpl();
    private final String from;

    public SmtpMailer(MailProperties p) {
        String security = p.security() == null ? "starttls" : p.security().strip().toLowerCase(java.util.Locale.ROOT);
        sender.setHost(p.host().strip());
        sender.setPort(p.port() != null ? p.port() : "ssl".equals(security) ? 465 : 587);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        boolean auth = p.username() != null && !p.username().isBlank();
        if (auth) {
            sender.setUsername(p.username());
            sender.setPassword(p.password());
        }
        Properties js = sender.getJavaMailProperties();
        js.put("mail.smtp.auth", String.valueOf(auth));
        js.put("mail.smtp.connectiontimeout", "10000");
        js.put("mail.smtp.timeout", "10000");
        js.put("mail.smtp.writetimeout", "10000");
        if ("ssl".equals(security)) {
            js.put("mail.smtp.ssl.enable", "true");
            js.put("mail.smtp.ssl.checkserveridentity", "true");
        } else if (!"none".equals(security)) {
            js.put("mail.smtp.starttls.enable", "true");
            js.put("mail.smtp.starttls.required", "true");
            js.put("mail.smtp.ssl.checkserveridentity", "true");
        }
        this.from = p.from();
    }

    @Override
    public void send(MailMessage m) throws Exception {
        MimeMessage mime = sender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
        h.setFrom(from);
        h.setTo(m.to());
        h.setSubject(m.subject());
        h.setText(m.text(), m.html());
        sender.send(mime);
    }
}
