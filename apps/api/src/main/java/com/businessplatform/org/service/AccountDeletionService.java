package com.businessplatform.org.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.security.Tokens;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.identity.repository.UserSessionRepository;
import com.businessplatform.identity.service.TwoStepService;
import com.businessplatform.mail.MailService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.domain.Organisation;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.repository.OrganisationRepository;

import lombok.RequiredArgsConstructor;

/**
 * "Delete my account". The person confirms with their password (and a code, with two-step sign-in on). Primary
 * owners and only owners hand their organisations over (or delete them) first. Then the sign-in is erased: the email
 * is freed (it can sign up again), the password, photo, two-step sign-in, devices, connected apps and links stop
 * working, and every membership is suspended. The organisations keep what they must keep as employers (their name
 * on tasks, HR and payroll records), as in Slack or Google Workspace, where the company owns its records.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final MembershipRepository memberships;
    private final OrganisationRepository orgs;
    private final PasswordEncoder encoder;
    private final TwoStepService twoStep;
    private final ActivityService activity;
    private final MailService mail;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    /** The organisations to hand over first: where the person is the primary owner or the only active owner. */
    @Transactional(readOnly = true)
    public List<String> blockers(UUID userId) {
        List<String> names = new ArrayList<>();
        for (Membership m : memberships.findByUserIdAndStatus(userId, Membership.ACTIVE)) {
            Organisation o = orgs.findById(m.getOrgId()).filter(x -> !x.isDeleted()).orElse(null);
            if (o == null) {
                continue;
            }
            boolean onlyOwner = m.getRole() == OrgRole.OWNER
                    && memberships.countByOrgIdAndRoleAndStatus(o.getId(), OrgRole.OWNER, Membership.ACTIVE) <= 1;
            if (userId.equals(o.getPrimaryOwnerId()) || onlyOwner) {
                names.add(o.getName());
            }
        }
        return names;
    }

    @Transactional
    public void delete(UUID userId, String password, String code) {
        AppUser u = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (password == null || !encoder.matches(password, u.getPasswordHash())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "password", "The password is wrong");
        }
        twoStep.requireCode(userId, code);
        List<String> blocked = blockers(userId);
        if (!blocked.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Hand your organisations to someone else, or delete them, first", List.of());
        }
        Instant now = clock.instant();
        String email = u.getEmail();
        String lang = u.getLocale();
        for (Membership m : memberships.findByUserIdAndStatus(userId, Membership.ACTIVE)) {
            m.setStatus(Membership.SUSPENDED);
            activity.record(m.getOrgId(), "org", "member", userId, userId, "org.member.deleted_account", Map.of("name", u.getName()));
        }
        twoStep.forget(userId);
        sessions.revokeAll(userId, now);
        jdbc.update("UPDATE password_reset SET used_at = ? WHERE user_id = ? AND used_at IS NULL", java.sql.Timestamp.from(now), userId);
        jdbc.update("UPDATE email_verification SET used_at = ? WHERE user_id = ? AND used_at IS NULL", java.sql.Timestamp.from(now), userId);
        jdbc.update("DELETE FROM oidc_consent WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM oidc_code WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM notification WHERE user_id = ?", userId);
        u.setEmail("deleted-" + userId + "@deleted.invalid");
        u.setPasswordHash(encoder.encode(Tokens.random()));
        u.setAvatarUrl(null);
        u.setSuperAdmin(false);
        u.setDeletedAt(now);
        u.setDisabledAt(now);
        u.setDisabledReason("Deleted by its owner");
        users.save(u);
        mail.queue(mail.letter(email, lang, null, Text.of("Your Business Platform account was deleted"),
                List.of(Text.of("The account {email} was deleted: you can no longer sign in with it, and the address is free to use again.", Map.of("email", email)),
                        Text.of("Organisations you worked with keep the records they need as your employer, such as tasks and HR records.")),
                null, null, Text.of("If it wasn't you, contact your organisation's administrator now.")));
    }
}
