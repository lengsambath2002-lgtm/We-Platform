package com.businessplatform.retail.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.oidc.service.ClientApiException;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.Organisation;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.repository.OrganisationRepository;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.retail.domain.ShopLink;
import com.businessplatform.retail.payload.RetailPayloads.LinkRequest;
import com.businessplatform.retail.payload.RetailPayloads.LinkResponse;
import com.businessplatform.retail.payload.RetailPayloads.Person;
import com.businessplatform.retail.payload.RetailPayloads.RetailShops;
import com.businessplatform.retail.payload.RetailPayloads.Shop;
import com.businessplatform.retail.repository.ShopLinkRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Links between VirtualBiz shops and organisations (contract §4). VirtualBiz reports links with its client
 * credentials; the person who linked must be an active Owner or Admin of the organisation. Links and unlinks go
 * to the organisation's audit log (module {@code retail}, record type {@value #RECORD}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopLinkService {

    public static final String RECORD = "shop";
    static final int MAX_NAME = 200;

    private final ShopLinkRepository links;
    private final OrganisationRepository orgs;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final ActivityService activity;
    private final OrgAccess access;
    private final ModuleService modules;
    private final RetailGate gate;
    private final Clock clock;

    // ── VirtualBiz (client credentials) ──────────────────────────────────────────────

    @Transactional
    public LinkResponse link(String clientId, UUID businessId, LinkRequest in) {
        if (in == null || in.orgId() == null || in.businessName() == null || in.businessName().isBlank()
                || in.linkedBy() == null || in.linkedBy().isBlank()) {
            throw ClientApiException.invalidRequest("orgId, businessName and linkedBy are required");
        }
        String name = in.businessName().strip();
        if (name.length() > MAX_NAME) {
            throw ClientApiException.invalidRequest("businessName is longer than " + MAX_NAME + " characters");
        }
        UUID by = parse(in.linkedBy());
        Organisation org = orgs.findById(in.orgId()).filter(o -> !o.isDeleted()).orElse(null);
        boolean allowed = org != null && by != null && memberships.findByOrgIdAndUserId(org.getId(), by)
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole().isAdmin()).isPresent();
        if (!allowed) {
            log.info("Retail: refused a link of shop {} by client {}: not an active owner or admin", businessId, clientId);
            throw ClientApiException.forbidden("linkedBy must be an active owner or admin of the organisation");
        }
        Instant now = clock.instant();
        Optional<ShopLink> existing = links.findById(businessId);
        ShopLink link = existing.orElseGet(ShopLink::new);
        boolean moved = existing.isPresent() && !existing.get().getOrgId().equals(org.getId());
        boolean fresh = existing.isEmpty() || moved;
        if (moved) {
            activity.record(link.getOrgId(), Module.RETAIL, RECORD, businessId, null, "retail.shop.unlinked",
                    Map.of("name", link.getBusinessName(), "reason", "moved"));
        }
        link.setBusinessId(businessId);
        link.setOrgId(org.getId());
        link.setBusinessName(name);
        link.setClientId(clientId);
        if (fresh) {
            link.setLinkedBy(by);
            link.setLinkedAt(now);
        }
        links.save(link);
        if (fresh) {
            activity.record(org.getId(), Module.RETAIL, RECORD, businessId, by, "retail.shop.linked", Map.of("name", name));
            log.info("Retail: shop {} linked to organisation {} by {}", businessId, org.getId(), by);
        }
        return new LinkResponse(link.getOrgId(), businessId, link.getBusinessName(), link.getLinkedAt());
    }

    /**
     * The shop's current link, so VirtualBiz can confirm it before granting access through it (an admin may
     * have unlinked it here). A link to a deleted organisation counts as gone.
     */
    @Transactional(readOnly = true)
    public LinkResponse get(UUID businessId) {
        ShopLink link = links.findById(businessId).orElseThrow(() -> ClientApiException.notFound("This shop is not linked"));
        if (orgs.findById(link.getOrgId()).filter(o -> !o.isDeleted()).isEmpty()) {
            throw ClientApiException.notFound("This shop is not linked");
        }
        return new LinkResponse(link.getOrgId(), businessId, link.getBusinessName(), link.getLinkedAt());
    }

    /** Idempotent: unlinking a shop that is not linked is fine. */
    @Transactional
    public void unlink(String clientId, UUID businessId) {
        links.findById(businessId).ifPresent(link -> {
            links.delete(link);
            activity.record(link.getOrgId(), Module.RETAIL, RECORD, businessId, null, "retail.shop.unlinked",
                    Map.of("name", link.getBusinessName(), "reason", "virtualbiz"));
            log.info("Retail: shop {} unlinked from organisation {} by client {}", businessId, link.getOrgId(), clientId);
        });
    }

    // ── The Retail page ──────────────────────────────────────────────────────────────

    /** Members (not guests) with the module on. */
    @Transactional(readOnly = true)
    public RetailShops list(String slug) {
        OrgContext ctx = requireView(slug);
        List<ShopLink> found = links.findByOrgIdOrderByBusinessNameAsc(ctx.orgId());
        Map<UUID, AppUser> people = new HashMap<>();
        List<UUID> ids = found.stream().map(ShopLink::getLinkedBy).filter(java.util.Objects::nonNull).distinct().toList();
        if (!ids.isEmpty()) {
            users.findByIdIn(ids).forEach(u -> people.put(u.getId(), u));
        }
        List<Shop> shops = found.stream().map(l -> {
            AppUser u = l.getLinkedBy() == null ? null : people.get(l.getLinkedBy());
            return new Shop(l.getBusinessId(), l.getBusinessName(), l.getLinkedAt(),
                    u == null ? null : new Person(u.getId(), u.getName()));
        }).toList();
        return new RetailShops(gate.ready(), gate.url(), ctx.orgId(), ctx.isAdmin(), shops);
    }

    /** Owners and Admins: removes the link on the platform (VirtualBiz keeps the shop). */
    @Transactional
    public void unlinkByAdmin(String slug, UUID businessId) {
        OrgContext ctx = requireView(slug);
        if (!ctx.isAdmin()) {
            throw BusinessException.forbidden();
        }
        ShopLink link = links.findById(businessId).filter(l -> l.getOrgId().equals(ctx.orgId()))
                .orElseThrow(BusinessException::notFound);
        links.delete(link);
        activity.record(ctx.orgId(), Module.RETAIL, RECORD, businessId, ctx.userId(), "retail.shop.unlinked",
                Map.of("name", link.getBusinessName(), "reason", "platform"));
        log.info("Retail: shop {} unlinked from organisation {} by admin {}", businessId, ctx.orgId(), ctx.userId());
    }

    private OrgContext requireView(String slug) {
        OrgContext ctx = access.require(slug);
        modules.require(ctx.orgId(), Module.RETAIL);
        if (ctx.isGuest()) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
