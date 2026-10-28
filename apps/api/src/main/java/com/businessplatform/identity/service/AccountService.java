package com.businessplatform.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Messages;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.common.security.Tokens;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.domain.PasswordReset;
import com.businessplatform.identity.domain.UserSession;
import com.businessplatform.identity.payload.AccountPayloads.SessionInfo;
import com.businessplatform.identity.payload.AuthPayloads.SessionResponse;
import com.businessplatform.identity.repository.PasswordResetRepository;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.identity.repository.UserSessionRepository;
import com.businessplatform.mail.MailService;

/**
 * Password change, "forgot password" (emailed single-use link, 30 minutes) and the signed-in devices.
 * A password change signs out every other device; a reset signs out all of them.
 */
@Service
public class AccountService {

    static final Duration RESET_TTL = Duration.ofMinutes(30);

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordResetRepository resets;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final MailService mail;
    private final Messages messages;
    private final Clock clock;
    private final String appUrl;
    /** Forgot-password requests: per email (silently dropped over the limit) and per client address. */
    private final AttemptLimiter perEmail;
    private final AttemptLimiter perIp;

    public AccountService(UserRepository users, UserSessionRepository sessions, PasswordResetRepository resets,
            PasswordEncoder encoder, AuthService auth, MailService mail, Messages messages, Clock clock,
            @Value("${platform.app-url}") String appUrl) {
        this.users = users;
        this.sessions = sessions;
        this.resets = resets;
        this.encoder = encoder;
        this.auth = auth;
        this.mail = mail;
        this.messages = messages;
        this.clock = clock;
        this.appUrl = appUrl;
        this.perEmail = new AttemptLimiter(3, Duration.ofMinutes(15), clock);
        this.perIp = new AttemptLimiter(20, Duration.ofMinutes(15), clock);
    }

    // ── Password ─────────────────────────────────────────────────────────────────────

    /** Wrong current passwords count towards the sign-in lockout. */
    @Transactional(noRollbackFor = BusinessException.class)
    public void changePassword(UUID userId, UUID sessionId, String current, String next) {
        AppUser u = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Instant now = clock.instant();
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!encoder.matches(current, u.getPasswordHash())) {
            u.setFailedLogins((short) (u.getFailedLogins() + 1));
            if (u.getFailedLogins() >= AuthService.MAX_FAILURES) {
                u.setLockedUntil(now.plus(AuthService.LOCK));
                u.setFailedLogins((short) 0);
            }
            users.save(u);
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "currentPassword",
                    "The current password is wrong");
        }
        AuthService.checkPassword(next, u.getEmail());
        if (encoder.matches(next, u.getPasswordHash())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "newPassword",
                    "Choose a password different from the current one");
        }
        u.setPasswordHash(encoder.encode(next));
        u.setFailedLogins((short) 0);
        UUID keep = familyOf(sessionId);
        if (keep == null) {
            sessions.revokeAll(userId, now);
        } else {
            sessions.revokeOthers(userId, keep, now);
        }
        resets.retireAll(userId, now);
        String lang = u.getLocale();
        mail.queue(MailService.compose(u.getEmail(),
                messages.in(lang, "Your Business Platform password was changed", Map.of()),
                List.of(messages.in(lang,
                        "The password of your account ({email}) was just changed, and your other devices were signed out.",
                        Map.of("email", u.getEmail()))),
                messages.in(lang, "Reset your password", Map.of()), appUrl + "/forgot-password",
                messages.in(lang,
                        "If it wasn't you, reset your password now and tell your organisation's administrator.",
                        Map.of())));
    }

    /**
     * Emails a reset link if an account uses {@code email}. Answers the same either way; a 4th request
     * for the same email within 15 minutes is ignored, and one address may ask 20 times in 15 minutes.
     */
    @Transactional
    public void forgotPassword(String rawEmail, String ip) {
        if (!perIp.tryAcquire(Objects.toString(ip, "?"))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        String email = AuthService.normalise(rawEmail);
        if (!perEmail.tryAcquire(email)) {
            return;
        }
        users.findByEmail(email).ifPresent(u -> {
            Instant now = clock.instant();
            resets.retireAll(u.getId(), now);
            String token = Tokens.random();
            PasswordReset r = new PasswordReset();
            r.setId(Ids.next());
            r.setUserId(u.getId());
            r.setTokenHash(Tokens.sha256(token));
            r.setCreatedAt(now);
            r.setExpiresAt(now.plus(RESET_TTL));
            resets.save(r);
            String lang = u.getLocale();
            mail.queue(MailService.compose(u.getEmail(),
                    messages.in(lang, "Reset your Business Platform password", Map.of()),
                    List.of(messages.in(lang, "Someone asked to reset the password of your account ({email}).",
                            Map.of("email", u.getEmail())),
                            messages.in(lang, "The link works once, within 30 minutes.", Map.of())),
                    messages.in(lang, "Choose a new password", Map.of()), appUrl + "/reset-password?token=" + token,
                    messages.in(lang, "If it wasn't you, ignore this email: your password stays the same.",
                            Map.of())));
        });
    }

    @Transactional(readOnly = true)
    public boolean resetTokenValid(String token) {
        return liveReset(token) != null;
    }

    /** Sets the new password, signs out every device, and signs in here. */
    @Transactional
    public SessionResponse resetPassword(String token, String password, String userAgent, String ip) {
        PasswordReset r = liveReset(token);
        if (r == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        AppUser u = users.findById(r.getUserId()).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        AuthService.checkPassword(password, u.getEmail());
        Instant now = clock.instant();
        u.setPasswordHash(encoder.encode(password));
        u.setFailedLogins((short) 0);
        u.setLockedUntil(null);
        resets.retireAll(u.getId(), now);
        sessions.revokeAll(u.getId(), now);
        return auth.start(u, userAgent, ip);
    }

    private PasswordReset liveReset(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return resets.findByTokenHash(Tokens.sha256(token))
                .filter(r -> r.getUsedAt() == null && r.getExpiresAt().isAfter(clock.instant()))
                .orElse(null);
    }

    // ── Signed-in devices ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SessionInfo> sessions(UUID userId, UUID sessionId) {
        UUID current = familyOf(sessionId);
        return sessions.findActive(userId, clock.instant()).stream()
                .map(s -> new SessionInfo(s.getFamilyId(), s.getUserAgent(), s.getIp(), s.getStartedAt(),
                        s.getLastUsedAt(), s.getFamilyId().equals(current)))
                .toList();
    }

    /** Signs out one device (this one too, if asked). */
    @Transactional
    public void revokeSession(UUID userId, UUID id) {
        if (sessions.revokeFamily(userId, id, clock.instant()) == 0) {
            throw BusinessException.notFound();
        }
    }

    /** Signs out every device but this one; how many were signed out. */
    @Transactional
    public int revokeOtherSessions(UUID userId, UUID sessionId) {
        UUID keep = familyOf(sessionId);
        if (keep == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        int others = (int) sessions.findActive(userId, clock.instant()).stream()
                .filter(s -> !s.getFamilyId().equals(keep)).count();
        sessions.revokeOthers(userId, keep, clock.instant());
        return others;
    }

    private UUID familyOf(UUID sessionId) {
        return sessionId == null ? null : sessions.findById(sessionId).map(UserSession::getFamilyId).orElse(null);
    }
}
