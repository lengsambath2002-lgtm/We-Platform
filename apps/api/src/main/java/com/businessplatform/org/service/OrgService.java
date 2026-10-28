package com.businessplatform.org.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Messages;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.common.security.CurrentUser;
import com.businessplatform.common.security.Tokens;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.mail.MailService;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.domain.Invitation;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.domain.Organisation;
import com.businessplatform.org.domain.Team;
import com.businessplatform.org.payload.OrgPayloads.*;
import com.businessplatform.org.repository.InvitationRepository;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.repository.OrganisationRepository;
import com.businessplatform.org.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

/** Organisations, members, invitations and teams. Owners and Admins manage; everyone else reads. */
@Service
@RequiredArgsConstructor
public class OrgService {

    static final Duration INVITE_TTL = Duration.ofDays(7);

    private final OrganisationRepository orgs;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final TeamRepository teams;
    private final UserRepository users;
    private final ModuleService modules;
    private final OrgAccess access;
    private final ActivityService activity;
    private final Clock clock;
    private final org.springframework.core.env.Environment env;
    private final MailService mail;
    private final Messages messages;

    /** Addresses the web app uses for its own pages (/{slug} would shadow them). */
    private static final Set<String> RESERVED_SLUGS = Set.of("account", "onboarding", "invite", "sign-in", "sign-up",
            "api", "admin", "settings", "help", "login", "logout", "static", "_next", "oauth");

    @Transactional
    public OrgSummary create(CreateOrgRequest in) {
        UUID me = CurrentUser.id();
        if (RESERVED_SLUGS.contains(in.slug()) || orgs.existsBySlug(in.slug())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "slug", "This address is taken");
        }
        Organisation o = new Organisation();
        o.setId(Ids.next());
        o.setSlug(in.slug());
        o.setName(in.name().strip());
        o.setCreatedBy(me);
        orgs.save(o);
        memberships.save(membership(o.getId(), me, OrgRole.OWNER));
        modules.set(o.getId(), Module.PROJECTS, true, me);
        return new OrgSummary(o.getId(), o.getSlug(), o.getName(), OrgRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<OrgSummary> mine() {
        UUID me = CurrentUser.id();
        List<Membership> ms = memberships.findByUserIdAndStatus(me, Membership.ACTIVE);
        Map<UUID, Organisation> byId = orgs.findAllById(ms.stream().map(Membership::getOrgId).toList()).stream()
                .collect(Collectors.toMap(Organisation::getId, Function.identity()));
        return ms.stream().filter(m -> byId.containsKey(m.getOrgId()) && !byId.get(m.getOrgId()).isDeleted()).map(m -> {
            Organisation o = byId.get(m.getOrgId());
            return new OrgSummary(o.getId(), o.getSlug(), o.getName(), m.getRole());
        }).sorted(Comparator.comparing(OrgSummary::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional(readOnly = true)
    public OrgContextResponse context(String slug) {
        OrgContext ctx = access.require(slug);
        Set<String> on = modules.enabled(ctx.orgId());
        List<ModuleState> states = modules.registry().stream()
                .map(m -> new ModuleState(m.getKey(), m.getNameEn(), m.getNameKm(), m.getKind(), modules.available(m),
                        on.contains(m.getKey())))
                .toList();
        return new OrgContextResponse(new OrgSummary(ctx.orgId(), ctx.slug(), ctx.orgName(), ctx.role()),
                Permissions.of(ctx, on), states);
    }

    @Transactional
    public OrgSummary update(String slug, UpdateOrgRequest in) {
        OrgContext ctx = access.requireAdmin(slug);
        Organisation o = orgs.findById(ctx.orgId()).orElseThrow();
        o.setName(in.name().strip());
        audit(ctx, "organisation", o.getId(), "org.updated", Map.of("name", o.getName()));
        return new OrgSummary(o.getId(), o.getSlug(), o.getName(), ctx.role());
    }

    /** Leaves the organisation (any member); the last owner must hand over or delete it first. */
    @Transactional
    public void leave(String slug) {
        OrgContext ctx = access.require(slug);
        if (ctx.role() == OrgRole.OWNER
                && memberships.countByOrgIdAndRoleAndStatus(ctx.orgId(), OrgRole.OWNER, Membership.ACTIVE) <= 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "You are the only owner. Make another member an owner first, or delete the organisation", List.of());
        }
        audit(ctx, "member", ctx.userId(), "org.member.left", Map.of());
        memberships.deleteById(ctx.membershipId());
    }

    /**
     * Owners only, with the name typed as confirmation. A soft delete: the organisation disappears for
     * everyone at once (NOT_FOUND), pending invitations stop working, the data stays in the database
     * (recoverable by an operator) and the web address stays taken, so old links never reach someone else.
     */
    @Transactional
    public void delete(String slug, DeleteOrgRequest in) {
        OrgContext ctx = access.require(slug);
        if (ctx.role() != OrgRole.OWNER) {
            throw BusinessException.forbidden();
        }
        Organisation o = orgs.findById(ctx.orgId()).orElseThrow();
        if (!o.getName().strip().equals(in.name().strip())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "name", "Type the organisation's name exactly");
        }
        Instant now = clock.instant();
        o.setDeletedAt(now);
        o.setDeletedBy(ctx.userId());
        audit(ctx, "organisation", o.getId(), "org.deleted", Map.of("name", o.getName()));
        invitations.findByOrgIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(ctx.orgId())
                .forEach(i -> i.setRevokedAt(now));
    }

    @Transactional
    public void setModule(String slug, String key, boolean on) {
        OrgContext ctx = access.requireAdmin(slug);
        modules.set(ctx.orgId(), key, on, ctx.userId());
        audit(ctx, "module", ctx.orgId(), on ? "org.module.enabled" : "org.module.disabled", Map.of("module", key));
    }

    /** Organisation changes go to the audit log (module {@code org}). */
    private void audit(OrgContext ctx, String recordType, UUID recordId, String verb, Map<String, Object> data) {
        activity.record(ctx.orgId(), "org", recordType, recordId, ctx.userId(), verb, data);
    }

    // ── Members ──────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MemberResponse> members(String slug) {
        OrgContext ctx = access.require(slug);
        List<Membership> ms = memberships.findByOrgIdOrderByCreatedAt(ctx.orgId());
        Map<UUID, AppUser> people = users.findByIdIn(ms.stream().map(Membership::getUserId).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return ms.stream()
                .filter(m -> ctx.isAdmin() || Membership.ACTIVE.equals(m.getStatus()))
                .map(m -> toMember(m, people.get(m.getUserId()))).toList();
    }

    @Transactional
    public MemberResponse updateMember(String slug, UUID membershipId, UpdateMemberRequest in) {
        OrgContext ctx = access.requireAdmin(slug);
        Membership m = memberships.findById(membershipId).filter(x -> x.getOrgId().equals(ctx.orgId()))
                .orElseThrow(BusinessException::notFound);
        boolean touchesOwner = m.getRole() == OrgRole.OWNER || in.role() == OrgRole.OWNER;
        if (touchesOwner && ctx.role() != OrgRole.OWNER) {
            throw BusinessException.forbidden();
        }
        boolean losesOwner = m.getRole() == OrgRole.OWNER
                && ((in.role() != null && in.role() != OrgRole.OWNER) || Membership.SUSPENDED.equals(in.status()));
        if (losesOwner && memberships.countByOrgIdAndRoleAndStatus(ctx.orgId(), OrgRole.OWNER, Membership.ACTIVE) <= 1) {
            throw BusinessException.field(ErrorCode.INVALID_STATE, "role", "An organisation needs at least one owner");
        }
        if (in.role() != null) {
            m.setRole(in.role());
        }
        if (in.status() != null) {
            m.setStatus(in.status());
        }
        if (in.title() != null) {
            m.setTitle(in.title().isBlank() ? null : in.title().strip());
        }
        AppUser who = users.findById(m.getUserId()).orElseThrow();
        Map<String, Object> changed = new java.util.HashMap<>(Map.of("name", who.getName()));
        if (in.role() != null) {
            changed.put("role", in.role().name());
        }
        if (in.status() != null) {
            changed.put("status", in.status());
        }
        audit(ctx, "member", m.getUserId(), "org.member.updated", changed);
        return toMember(m, who);
    }

    // ── Invitations ──────────────────────────────────────────────────────────────────

    @Transactional
    public InvitationResponse invite(String slug, InviteRequest in) {
        OrgContext ctx = access.requireAdmin(slug);
        if (in.role() == OrgRole.OWNER) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "role", "Invite as Admin, Member or Guest");
        }
        String email = in.email().strip().toLowerCase(java.util.Locale.ROOT);
        users.findByEmail(email).ifPresent(u -> {
            if (memberships.existsByOrgIdAndUserIdAndStatus(ctx.orgId(), u.getId(), Membership.ACTIVE)) {
                throw BusinessException.field(ErrorCode.CONFLICT, "email", "Already a member");
            }
        });
        String token = Tokens.random();
        Invitation i = new Invitation();
        i.setId(Ids.next());
        i.setOrgId(ctx.orgId());
        i.setEmail(email);
        i.setRole(in.role());
        i.setTokenHash(Tokens.sha256(token));
        i.setInvitedBy(ctx.userId());
        i.setCreatedAt(clock.instant());
        i.setExpiresAt(clock.instant().plus(INVITE_TTL));
        invitations.save(i);
        audit(ctx, "invitation", i.getId(), "org.member.invited", Map.of("email", email, "role", i.getRole().name()));
        sendInvitation(ctx, i, token);
        return new InvitationResponse(i.getId(), email, i.getRole(), i.getExpiresAt(), link(token), mail.delivers());
    }

    /** A new link (the old one stops working), valid 7 days from now, emailed again. */
    @Transactional
    public InvitationResponse resendInvitation(String slug, UUID id) {
        OrgContext ctx = access.requireAdmin(slug);
        Invitation i = invitations.findById(id).filter(x -> x.getOrgId().equals(ctx.orgId()))
                .filter(x -> x.getAcceptedAt() == null && x.getRevokedAt() == null)
                .orElseThrow(BusinessException::notFound);
        String token = Tokens.random();
        i.setTokenHash(Tokens.sha256(token));
        i.setExpiresAt(clock.instant().plus(INVITE_TTL));
        audit(ctx, "invitation", i.getId(), "org.invitation.resent", Map.of("email", i.getEmail()));
        sendInvitation(ctx, i, token);
        return new InvitationResponse(i.getId(), i.getEmail(), i.getRole(), i.getExpiresAt(), link(token),
                mail.delivers());
    }

    /** In the invitee's language when they have an account, otherwise the inviter's (request) language. */
    private void sendInvitation(OrgContext ctx, Invitation i, String token) {
        String lang = users.findByEmail(i.getEmail()).map(AppUser::getLocale).orElse(Messages.requestLanguage());
        String inviter = users.findById(ctx.userId()).map(AppUser::getName).orElse("");
        String role = switch (i.getRole()) {
            case ADMIN -> messages.in(lang, "an admin", Map.of());
            case GUEST -> messages.in(lang, "a guest", Map.of());
            default -> messages.in(lang, "a member", Map.of());
        };
        Map<String, String> args = Map.of("inviter", inviter, "org", ctx.orgName(), "role", role, "email", i.getEmail());
        mail.queue(MailService.compose(i.getEmail(),
                messages.in(lang, "{inviter} invited you to {org} on Business Platform", args),
                List.of(messages.in(lang, "{inviter} invited you to join {org} as {role}.", args),
                        messages.in(lang, "Sign in or create an account with {email} to accept. The link works for 7 days.", args)),
                messages.in(lang, "Accept the invitation", Map.of()), link(token),
                messages.in(lang, "If you weren't expecting this, you can ignore this email.", Map.of())));
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> pendingInvitations(String slug) {
        OrgContext ctx = access.requireAdmin(slug);
        return invitations.findByOrgIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(ctx.orgId()).stream()
                .map(i -> new InvitationResponse(i.getId(), i.getEmail(), i.getRole(), i.getExpiresAt(), null, false))
                .toList();
    }

    @Transactional
    public void revokeInvitation(String slug, UUID id) {
        OrgContext ctx = access.requireAdmin(slug);
        Invitation i = invitations.findById(id).filter(x -> x.getOrgId().equals(ctx.orgId()))
                .orElseThrow(BusinessException::notFound);
        i.setRevokedAt(clock.instant());
        audit(ctx, "invitation", i.getId(), "org.invitation.revoked", Map.of("email", i.getEmail()));
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(String token) {
        Invitation i = live(token, false);
        Organisation o = orgs.findById(i.getOrgId()).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        return new InvitationPreview(o.getName(), i.getEmail(), i.getRole(), !i.getExpiresAt().isAfter(clock.instant()));
    }

    /** Joins the organisation; the signed-in account's email must be the invited one. */
    @Transactional
    public OrgSummary accept(String token) {
        Invitation i = live(token, true);
        orgs.findById(i.getOrgId()).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        AppUser me = users.findById(CurrentUser.id()).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!me.getEmail().equalsIgnoreCase(i.getEmail())) {
            throw BusinessException.field(ErrorCode.FORBIDDEN, "email", "This invitation is for another email address");
        }
        Membership m = memberships.findByOrgIdAndUserId(i.getOrgId(), me.getId()).orElse(null);
        if (m == null) {
            m = memberships.save(membership(i.getOrgId(), me.getId(), i.getRole()));
        } else {
            m.setStatus(Membership.ACTIVE);
        }
        i.setAcceptedAt(clock.instant());
        activity.record(i.getOrgId(), "org", "member", me.getId(), me.getId(), "org.member.joined",
                Map.of("name", me.getName(), "role", m.getRole().name()));
        Organisation o = orgs.findById(i.getOrgId()).orElseThrow();
        return new OrgSummary(o.getId(), o.getSlug(), o.getName(), m.getRole());
    }

    // ── Teams ────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TeamResponse> teams(String slug) {
        OrgContext ctx = access.require(slug);
        return teams.findByOrgIdOrderByName(ctx.orgId()).stream()
                .map(t -> new TeamResponse(t.getId(), t.getName(), teams.memberIds(t.getId()))).toList();
    }

    @Transactional
    public TeamResponse saveTeam(String slug, UUID id, TeamRequest in) {
        OrgContext ctx = access.requireAdmin(slug);
        Team t = id == null ? new Team() : teams.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        if (id == null) {
            t.setId(Ids.next());
            t.setOrgId(ctx.orgId());
            t.setCreatedAt(clock.instant());
        }
        t.setName(in.name().strip());
        teams.saveAndFlush(t);
        List<UUID> wanted = in.membershipIds() == null ? List.of() : in.membershipIds().stream().distinct().toList();
        Set<UUID> valid = memberships.findAllById(wanted).stream().filter(m -> m.getOrgId().equals(ctx.orgId()))
                .map(Membership::getId).collect(Collectors.toSet());
        if (valid.size() != wanted.size()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "membershipIds", "Unknown member");
        }
        teams.clearMembers(t.getId());
        wanted.forEach(mid -> teams.addMember(t.getId(), mid));
        audit(ctx, "team", t.getId(), id == null ? "org.team.created" : "org.team.updated", Map.of("name", t.getName()));
        return new TeamResponse(t.getId(), t.getName(), wanted);
    }

    @Transactional
    public void deleteTeam(String slug, UUID id) {
        OrgContext ctx = access.requireAdmin(slug);
        Team t = teams.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        teams.delete(t);
        audit(ctx, "team", t.getId(), "org.team.deleted", Map.of("name", t.getName()));
    }

    private Invitation live(String token, boolean mustBeValid) {
        Invitation i = invitations.findByTokenHash(Tokens.sha256(Objects.toString(token, "")))
                .filter(x -> x.getRevokedAt() == null && x.getAcceptedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (mustBeValid && !i.getExpiresAt().isAfter(clock.instant())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return i;
    }

    private Membership membership(UUID orgId, UUID userId, OrgRole role) {
        Membership m = new Membership();
        m.setId(Ids.next());
        m.setOrgId(orgId);
        m.setUserId(userId);
        m.setRole(role);
        m.setCreatedAt(Instant.now(clock));
        return m;
    }

    private String link(String token) {
        return env.getProperty("platform.app-url", "http://localhost:3300") + "/invite/" + token;
    }

    private static MemberResponse toMember(Membership m, AppUser u) {
        return new MemberResponse(m.getId(), u.getId(), u.getName(), u.getEmail(), u.getAvatarUrl(), m.getRole(),
                m.getStatus(), m.getTitle(), m.getCreatedAt());
    }
}
