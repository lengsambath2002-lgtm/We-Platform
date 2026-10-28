package com.businessplatform.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.api.ApiError;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Messages;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.common.security.AuthProperties;
import com.businessplatform.common.security.Tokens;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.domain.UserSession;
import com.businessplatform.identity.payload.AuthPayloads.SessionResponse;
import com.businessplatform.identity.payload.AuthPayloads.UserResponse;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.identity.repository.UserSessionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Sign-up, sign-in, refresh (rotating, reuse revokes every session) and sign-out. Five wrong
 * passwords lock the account for 15 minutes. Wrong email and wrong password answer the same. A new
 * account takes the request language ({@code en} or {@code km}).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK = Duration.ofMinutes(15);

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final AuthProperties props;
    private final Clock clock;

    @Transactional
    public SessionResponse signUp(String name, String rawEmail, String password, String userAgent, String ip) {
        String email = normalise(rawEmail);
        if (users.existsByEmail(email)) {
            throw BusinessException.field(ErrorCode.CONFLICT, "email", "An account already uses this email");
        }
        checkPassword(password, email);
        AppUser u = new AppUser();
        u.setId(Ids.next());
        u.setEmail(email);
        u.setName(name.strip());
        u.setPasswordHash(encoder.encode(password));
        u.setLocale(Messages.requestLanguage());
        users.save(u);
        return start(u, userAgent, ip);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public SessionResponse signIn(String rawEmail, String password, String userAgent, String ip) {
        AppUser u = users.findByEmail(normalise(rawEmail))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        Instant now = clock.instant();
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!encoder.matches(password, u.getPasswordHash())) {
            u.setFailedLogins((short) (u.getFailedLogins() + 1));
            if (u.getFailedLogins() >= MAX_FAILURES) {
                u.setLockedUntil(now.plus(LOCK));
                u.setFailedLogins((short) 0);
            }
            users.save(u);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        u.setFailedLogins((short) 0);
        u.setLockedUntil(null);
        return start(u, userAgent, ip);
    }

    // noRollbackFor: revoking every session on token reuse must stick even though the call is refused.
    @Transactional(noRollbackFor = BusinessException.class)
    public SessionResponse refresh(String refreshToken, String userAgent, String ip) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        UserSession s = sessions.findByRefreshTokenHash(Tokens.sha256(refreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        Instant now = clock.instant();
        if (s.getRevokedAt() != null) {
            // A used (rotated) token came back: someone else holds it. End every session of this user.
            sessions.revokeAll(s.getUserId(), now);
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (!s.getExpiresAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        s.setRevokedAt(now);
        AppUser u = users.findById(s.getUserId()).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        return start(u, userAgent, ip, s);
    }

    @Transactional
    public void signOut(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        sessions.findByRefreshTokenHash(Tokens.sha256(refreshToken)).ifPresent(s -> {
            if (s.getRevokedAt() == null) {
                s.setRevokedAt(clock.instant());
            }
        });
    }

    public static UserResponse toUser(AppUser u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getLocale(), u.getAvatarUrl());
    }

    /** A new signed-in device. */
    SessionResponse start(AppUser u, String userAgent, String ip) {
        return start(u, userAgent, ip, null);
    }

    /** A new session row; {@code previous} is the row a refresh rotates from (same device), or null. */
    private SessionResponse start(AppUser u, String userAgent, String ip, UserSession previous) {
        String refresh = Tokens.random();
        Instant now = clock.instant();
        UserSession s = new UserSession();
        s.setId(Ids.next());
        s.setFamilyId(previous == null ? s.getId() : previous.getFamilyId());
        s.setStartedAt(previous == null ? now : previous.getStartedAt());
        s.setLastUsedAt(now);
        s.setUserId(u.getId());
        s.setRefreshTokenHash(Tokens.sha256(refresh));
        s.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length())));
        s.setIp(ip);
        s.setCreatedAt(now);
        s.setExpiresAt(now.plus(props.refreshTtl()));
        sessions.save(s);
        return new SessionResponse(tokens.accessToken(u.getId(), s.getId()), tokens.accessSeconds(), toUser(u), refresh);
    }

    static String normalise(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /** 10+ characters with a letter and a digit, not containing the email's name. */
    static void checkPassword(String password, String email) {
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        boolean ok = password.length() >= 10 && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit)
                && (local.length() < 4 || !password.toLowerCase(Locale.ROOT).contains(local));
        if (!ok) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Weak password",
                    List.of(new ApiError.FieldError("password",
                            "Use 10 or more characters with letters and numbers, not your email name")));
        }
    }

    public UUID requireUser(UUID id) {
        return users.findById(id).map(AppUser::getId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
