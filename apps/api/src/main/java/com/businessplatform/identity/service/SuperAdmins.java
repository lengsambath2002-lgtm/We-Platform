package com.businessplatform.identity.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.identity.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * The first super admins: the emails in {@code platform.super-admins} ({@code PLATFORM_SUPER_ADMINS}, comma-separated).
 * Such an account becomes a super admin when it signs up, or at start-up if it already exists. The list only ever
 * grants: revoking is done in the console, and an address removed from the list keeps what it has.
 */
@Slf4j
@Component
public class SuperAdmins {

    private final Set<String> emails;
    private final UserRepository users;

    public SuperAdmins(@Value("${platform.super-admins:}") String list, UserRepository users) {
        this.emails = Arrays.stream(list.split(",")).map(e -> e.strip().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty()).collect(Collectors.toUnmodifiableSet());
        this.users = users;
    }

    public boolean listed(String email) {
        return emails.contains(email);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void promoteListed() {
        emails.forEach(e -> users.findByEmail(e).filter(u -> !u.isSuperAdmin()).ifPresent(u -> {
            u.setSuperAdmin(true);
            log.info("Platform: {} is a super admin (listed in PLATFORM_SUPER_ADMINS)", e);
        }));
    }
}
