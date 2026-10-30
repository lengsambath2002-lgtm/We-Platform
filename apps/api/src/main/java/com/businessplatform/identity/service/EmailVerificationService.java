package com.businessplatform.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.common.security.Tokens;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.domain.EmailVerification;
import com.businessplatform.identity.repository.EmailVerificationRepository;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.mail.MailService;

/**
 * Email verification. A new account gets a "confirm your email" link (3 days, single use; "send again" at most 3 times
 * in 15 minutes). Until it is confirmed the account can sign in and join organisations it is invited to (the
 * invitation link proves the address too), but cannot create an organisation or invite people, so a typo'd or
 * borrowed address can't be used to send email to others. {@code platform.auth.require-verified-email=false} lifts
 * those limits (local development and the tests).
 */
@Service
public class EmailVerificationService {

    static final Duration TTL = Duration.ofDays(3);

    private final UserRepository users;
    private final EmailVerificationRepository links;
    private final MailService mail;
    private final Clock clock;
    private final String appUrl;
    private final boolean required;
    private final AttemptLimiter resends;

    public EmailVerificationService(UserRepository users, EmailVerificationRepository links, MailService mail, Clock clock,
            @Value("${platform.app-url}") String appUrl,
            @Value("${platform.auth.require-verified-email:true}") boolean required) {
        this.users = users;
        this.links = links;
        this.mail = mail;
        this.clock = clock;
        this.appUrl = appUrl;
        this.required = required;
        this.resends = new AttemptLimiter(3, Duration.ofMinutes(15), clock);
    }

    /** Emails a new link (earlier ones stop working). */
    @Transactional
    public void send(AppUser u) {
        Instant now = clock.instant();
        links.retireAll(u.getId(), now);
        String token = Tokens.random();
        EmailVerification v = new EmailVerification();
        v.setId(Ids.next());
        v.setUserId(u.getId());
        v.setTokenHash(Tokens.sha256(token));
        v.setCreatedAt(now);
        v.setExpiresAt(now.plus(TTL));
        links.save(v);
        Map<String, String> args = Map.of("email", u.getEmail(), "name", u.getName());
        mail.queue(mail.letter(u.getEmail(), u.getLocale(), null, Text.of("Confirm your email for Business Platform"),
                List.of(Text.of("Welcome to Business Platform, {name}. Confirm that {email} is your email address.", args),
                        Text.of("The link works once, within 3 days.")),
                Text.of("Confirm my email"), appUrl + "/verify-email?token=" + token,
                Text.of("If you didn't create an account, ignore this email: nothing happens without the link.")));
    }

    /** "Send the link again", from the signed-in account; nothing to do once confirmed. */
    @Transactional
    public void resend(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (u.getEmailVerifiedAt() != null) {
            return;
        }
        if (!resends.tryAcquire(userId.toString())) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        send(u);
    }

    /** Confirms the email the link was sent to; INVALID_TOKEN when used, expired or unknown. */
    @Transactional
    public AppUser verify(String token) {
        EmailVerification v = token == null || token.isBlank() ? null : links.findByTokenHash(Tokens.sha256(token))
                .filter(x -> x.getUsedAt() == null && x.getExpiresAt().isAfter(clock.instant())).orElse(null);
        if (v == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        AppUser u = users.findById(v.getUserId()).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        markVerified(u);
        return u;
    }

    /** Another emailed link (a password reset, an invitation) proved the address. */
    @Transactional
    public void markVerified(AppUser u) {
        if (u.getEmailVerifiedAt() == null) {
            u.setEmailVerifiedAt(clock.instant());
            users.save(u);
        }
        links.retireAll(u.getId(), clock.instant());
    }

    /** EMAIL_NOT_VERIFIED for an account that hasn't confirmed its email, when that is required. */
    @Transactional(readOnly = true)
    public void require(UUID userId) {
        if (required && users.findById(userId).map(u -> u.getEmailVerifiedAt() == null).orElse(true)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "Confirm your email address first", List.of());
        }
    }
}
