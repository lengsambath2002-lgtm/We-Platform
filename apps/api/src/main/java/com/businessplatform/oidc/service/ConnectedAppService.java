package com.businessplatform.oidc.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.oidc.payload.OidcPayloads.ConnectedApp;
import com.businessplatform.oidc.repository.OidcConsentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The apps a person allowed to sign them in (account page). Revoking means the app has to ask again next time;
 * a session the app already started is the app's own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectedAppService {

    private final OidcConsentRepository consents;
    private final OidcClients clients;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ConnectedApp> list(UUID userId) {
        return consents.findByUserIdOrderByGrantedAtDesc(userId).stream()
                .map(c -> new ConnectedApp(c.getClientId(),
                        clients.find(c.getClientId()).map(OidcClients.Client::name).orElse(c.getClientId()),
                        OidcAuthorizeService.split(c.getScopes()), c.getGrantedAt()))
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, String clientId) {
        if (consents.revoke(userId, clientId) == 0) {
            throw BusinessException.notFound();
        }
        log.info("OIDC: user {} revoked client {} at {}", userId, clientId, clock.instant());
    }
}
