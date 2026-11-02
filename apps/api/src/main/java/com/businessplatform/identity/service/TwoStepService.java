package com.businessplatform.identity.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.common.security.Tokens;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.domain.TwoStepChallenge;
import com.businessplatform.identity.domain.TwoStepRecoveryCode;
import com.businessplatform.identity.payload.AccountPayloads.TwoStepSetup;
import com.businessplatform.identity.payload.AccountPayloads.TwoStepStatus;
import com.businessplatform.identity.payload.AuthPayloads.SessionResponse;
import com.businessplatform.identity.repository.TwoStepRepositories.Challenges;
import com.businessplatform.identity.repository.TwoStepRepositories.RecoveryCodes;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.mail.MailService;

/**
 * Two-step sign-in. Set up from the account page: scan the QR code with an authenticator app, confirm with a first
 * code, keep the 10 recovery codes (shown once, each works once). Then every sign-in, and a password reset, asks for
 * a code after the password ({@link TwoStepChallenges}); wrong codes count towards the sign-in lockout. Turning it off
 * takes the password and a code (or a recovery code); a super admin can turn it off for someone who lost both. The
 * account is emailed each time it is turned on or off.
 */
@Service
public class TwoStepService {

    static final int RECOVERY_CODES = 10;
    private static final String CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RecoveryCodes codes;
    private final Challenges challenges;
    private final SecretBox box;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final MailService mail;
    private final Clock clock;
    private final String appUrl;

    public TwoStepService(UserRepository users, RecoveryCodes codes, Challenges challenges, SecretBox box,
            PasswordEncoder encoder, AuthService auth, MailService mail, Clock clock,
            @Value("${platform.app-url}") String appUrl) {
        this.users = users;
        this.codes = codes;
        this.challenges = challenges;
        this.box = box;
        this.encoder = encoder;
        this.auth = auth;
        this.mail = mail;
        this.clock = clock;
        this.appUrl = appUrl;
    }

    @Transactional(readOnly = true)
    public TwoStepStatus status(UUID userId) {
        AppUser u = user(userId);
        return new TwoStepStatus(u.isTwoStepOn(), u.getTotpEnabledAt(),
                u.isTwoStepOn() ? codes.findByUserIdAndUsedAtIsNull(userId).size() : 0);
    }

    /** A new secret to scan; it takes effect once {@link #enable} confirms a code from it. */
    @Transactional
    public TwoStepSetup setup(UUID userId) {
        AppUser u = user(userId);
        if (u.isTwoStepOn()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Two-step sign-in is already on", List.of());
        }
        String secret = Totp.newSecret();
        u.setTotpPendingSecret(box.seal(secret));
        return new TwoStepSetup(secret, Totp.uri("Business Platform", u.getEmail(), secret));
    }

    /** Turns it on with a first code from the app; returns the recovery codes (shown this once). */
    @Transactional
    public List<String> enable(UUID userId, String code) {
        AppUser u = user(userId);
        if (u.isTwoStepOn()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Two-step sign-in is already on", List.of());
        }
        if (u.getTotpPendingSecret() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Start the set-up again", List.of());
        }
        String secret = box.open(u.getTotpPendingSecret());
        long step = Totp.match(secret, code, clock.instant(), null);
        if (step < 0) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "code", "That code isn't right. Check the time on your phone.");
        }
        u.setTotpSecret(u.getTotpPendingSecret());
        u.setTotpPendingSecret(null);
        u.setTotpLastStep(step);
        u.setTotpEnabledAt(clock.instant());
        notify(u, true, false);
        return newCodes(u.getId());
    }

    /** New recovery codes (the old ones stop working); takes the password. */
    @Transactional
    public List<String> regenerate(UUID userId, String password) {
        AppUser u = user(userId);
        requirePassword(u, password);
        if (!u.isTwoStepOn()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Two-step sign-in is off", List.of());
        }
        return newCodes(u.getId());
    }

    /** Turns it off with the password and a code (or a recovery code). */
    @Transactional
    public void disable(UUID userId, String password, String code) {
        AppUser u = user(userId);
        requirePassword(u, password);
        if (!u.isTwoStepOn()) {
            return;
        }
        if (!accepts(u, code)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "code", "That code isn't right");
        }
        turnOff(u);
        notify(u, false, false);
    }

    /** VALIDATION_FAILED unless {@code code} (or a recovery code) is right; nothing to check while it is off. */
    @Transactional
    public void requireCode(UUID userId, String code) {
        AppUser u = user(userId);
        if (u.isTwoStepOn() && !accepts(u, code)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "code", "That code isn't right");
        }
    }

    /** Erases it without an email (the account is being deleted). */
    @Transactional
    public void forget(UUID userId) {
        turnOff(user(userId));
    }

    /** A super admin turns it off for someone who lost their phone and their recovery codes. */
    @Transactional
    public boolean reset(UUID userId) {
        AppUser u = user(userId);
        if (!u.isTwoStepOn()) {
            return false;
        }
        turnOff(u);
        notify(u, false, true);
        return true;
    }

    /**
     * Completes a sign-in with the code (or a recovery code). A wrong code counts towards the lockout like a wrong
     * password; the challenge stays usable until it expires.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public SessionResponse complete(String token, String code, String userAgent, String ip) {
        Instant now = clock.instant();
        TwoStepChallenge c = token == null || token.isBlank() ? null : challenges.findByTokenHash(Tokens.sha256(token))
                .filter(x -> x.getUsedAt() == null && x.getExpiresAt().isAfter(now)).orElse(null);
        if (c == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        AppUser u = user(c.getUserId());
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!u.isTwoStepOn() || !accepts(u, code)) {
            u.setFailedLogins((short) (u.getFailedLogins() + 1));
            if (u.getFailedLogins() >= AuthService.MAX_FAILURES) {
                u.setLockedUntil(now.plus(AuthService.LOCK));
                u.setFailedLogins((short) 0);
            }
            users.save(u);
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "code", "That code isn't right");
        }
        u.setFailedLogins((short) 0);
        c.setUsedAt(now);
        return auth.start(u, userAgent, ip);
    }

    /** A code from the app (each works once) or an unused recovery code (used up). */
    private boolean accepts(AppUser u, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        long step = Totp.match(box.open(u.getTotpSecret()), code, clock.instant(), u.getTotpLastStep());
        if (step >= 0) {
            u.setTotpLastStep(step);
            return true;
        }
        String hash = Tokens.sha256(normalise(code));
        for (TwoStepRecoveryCode r : codes.findByUserIdAndUsedAtIsNull(u.getId())) {
            if (MessageDigest.isEqual(r.getCodeHash().getBytes(), hash.getBytes())) {
                r.setUsedAt(clock.instant());
                return true;
            }
        }
        return false;
    }

    private void turnOff(AppUser u) {
        u.setTotpSecret(null);
        u.setTotpPendingSecret(null);
        u.setTotpEnabledAt(null);
        u.setTotpLastStep(null);
        codes.deleteAllOf(u.getId());
        challenges.retireAll(u.getId(), clock.instant());
    }

    private List<String> newCodes(UUID userId) {
        codes.deleteAllOf(userId);
        List<String> plain = new ArrayList<>();
        Instant now = clock.instant();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder b = new StringBuilder();
            for (int j = 0; j < 10; j++) {
                b.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = b.substring(0, 5) + "-" + b.substring(5);
            TwoStepRecoveryCode r = new TwoStepRecoveryCode();
            r.setId(Ids.next());
            r.setUserId(userId);
            r.setCodeHash(Tokens.sha256(normalise(code)));
            r.setCreatedAt(now);
            codes.save(r);
            plain.add(code);
        }
        return plain;
    }

    private static String normalise(String code) {
        return code.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
    }

    private void requirePassword(AppUser u, String password) {
        if (password == null || !encoder.matches(password, u.getPasswordHash())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "password", "The password is wrong");
        }
    }

    private void notify(AppUser u, boolean on, boolean byAdmin) {
        Map<String, String> args = Map.of("email", u.getEmail());
        List<Text> lines = on
                ? List.of(Text.of("From now on, signing in to {email} asks for a code from your authenticator app after the password.", args),
                        Text.of("Keep your recovery codes somewhere safe: each one works once if you lose your phone."))
                : List.of(Text.of("Signing in to {email} now asks only for the password.", args),
                        byAdmin ? Text.of("A platform administrator turned it off, as asked when the phone was lost.")
                                : Text.of("You turned it off from your account page."));
        mail.queue(mail.letter(u.getEmail(), u.getLocale(), null,
                on ? Text.of("Two-step sign-in is on for your Business Platform account")
                        : Text.of("Two-step sign-in was turned off for your Business Platform account"),
                lines, Text.of("Review your account"), appUrl + "/account",
                Text.of("If it wasn't you, reset your password now and tell your organisation's administrator.")));
    }

    private AppUser user(UUID id) {
        return users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
