package com.businessplatform.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.businessplatform.common.persistence.Ids;
import com.businessplatform.common.security.Tokens;
import com.businessplatform.identity.domain.TwoStepChallenge;
import com.businessplatform.identity.repository.TwoStepRepositories.Challenges;

import lombok.RequiredArgsConstructor;

/** Issues the "now the code" step after a right password (5 minutes, single use); {@link TwoStepService} completes it. */
@Component
@RequiredArgsConstructor
class TwoStepChallenges {

    static final Duration TTL = Duration.ofMinutes(5);

    private final Challenges challenges;
    private final Clock clock;

    String issue(UUID userId) {
        Instant now = clock.instant();
        challenges.retireAll(userId, now);
        String token = Tokens.random();
        TwoStepChallenge c = new TwoStepChallenge();
        c.setId(Ids.next());
        c.setUserId(userId);
        c.setTokenHash(Tokens.sha256(token));
        c.setCreatedAt(now);
        c.setExpiresAt(now.plus(TTL));
        challenges.save(c);
        return token;
    }
}
