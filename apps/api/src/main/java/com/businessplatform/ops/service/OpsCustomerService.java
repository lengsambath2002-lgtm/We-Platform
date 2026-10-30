package com.businessplatform.ops.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.ops.domain.Asset;
import com.businessplatform.ops.domain.OpsCustomer;
import com.businessplatform.ops.domain.Site;
import com.businessplatform.ops.domain.Ticket;
import com.businessplatform.ops.payload.OpsPayloads.CustomerDetail;
import com.businessplatform.ops.payload.OpsPayloads.CustomerRequest;
import com.businessplatform.ops.payload.OpsPayloads.CustomerView;
import com.businessplatform.ops.payload.OpsPayloads.SiteRequest;
import com.businessplatform.ops.payload.OpsPayloads.SiteView;
import com.businessplatform.ops.repository.AssetRepository;
import com.businessplatform.ops.repository.OpsCustomerRepository;
import com.businessplatform.ops.repository.SiteRepository;
import com.businessplatform.ops.repository.TicketRepository;
import com.businessplatform.ops.repository.WorkOrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service customers and their sites, kept inside this module (General Business has its own customers). Dispatchers
 * and Admins manage them; archived customers stay on their records but can't get new tickets or assets.
 */
@Service
@RequiredArgsConstructor
public class OpsCustomerService {

    public static final String RECORD = "service_customer";
    static final int MAX_PAGE = 100;

    private final OpsAccess access;
    private final OpsCustomerRepository customers;
    private final SiteRepository sites;
    private final AssetRepository assets;
    private final TicketRepository tickets;
    private final WorkOrderRepository workOrders;
    private final OpsViews views;
    private final ActivityService activity;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<CustomerView> list(String slug, String q, boolean archived, int page, int size) {
        OpsAccess.Who w = access.requireManager(slug);
        Specification<OpsCustomer> spec = (c, query, cb) -> cb.and(cb.equal(c.get("orgId"), w.orgId()),
                cb.equal(c.get("archived"), archived));
        if (q != null && !q.isBlank()) {
            String like = OpsPeople.like(q);
            spec = spec.and((c, query, cb) -> cb.or(cb.like(cb.lower(c.get("name")), like, '\\'),
                    cb.like(cb.lower(c.get("contactName")), like, '\\'), cb.like(c.get("phone"), like, '\\'),
                    cb.like(cb.lower(c.get("email")), like, '\\')));
        }
        Page<OpsCustomer> found = customers.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE),
                Sort.by(Sort.Order.asc("name").ignoreCase())));
        return PageResponse.of(new PageImpl<>(views(w.orgId(), found.getContent()), found.getPageable(), found.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public CustomerDetail get(String slug, UUID id) {
        OpsAccess.Who w = access.requireManager(slug);
        OpsCustomer c = find(w.orgId(), id);
        List<Asset> as = assets.findByOrgIdAndCustomerIdOrderByName(w.orgId(), id);
        return new CustomerDetail(views(w.orgId(), List.of(c)).get(0),
                sites.findByOrgIdAndCustomerIdOrderByName(w.orgId(), id).stream().map(OpsCustomerService::site).toList(),
                views.assets(w.orgId(), as, access.today()),
                views.tickets(w.orgId(), tickets.findTop20ByOrgIdAndCustomerIdOrderByCreatedAtDesc(w.orgId(), id)),
                views.workOrders(w.orgId(), workOrders.findTop20ByOrgIdAndCustomerIdOrderByCreatedAtDesc(w.orgId(), id)));
    }

    @Transactional
    public CustomerView create(String slug, CustomerRequest in) {
        OpsAccess.Who w = access.requireManager(slug);
        OpsCustomer c = new OpsCustomer();
        c.setId(Ids.next());
        c.setOrgId(w.orgId());
        c.setCreatedBy(w.userId());
        c.setCreatedAt(clock.instant());
        apply(c, in);
        customers.saveAndFlush(c);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, c.getId(), w.userId(), "service.customer.created",
                Map.of("name", c.getName()));
        return views(w.orgId(), List.of(c)).get(0);
    }

    @Transactional
    public CustomerView update(String slug, UUID id, CustomerRequest in) {
        OpsAccess.Who w = access.requireManager(slug);
        OpsCustomer c = find(w.orgId(), id);
        apply(c, in);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, c.getId(), w.userId(), "service.customer.updated",
                Map.of("name", c.getName()));
        return views(w.orgId(), List.of(c)).get(0);
    }

    @Transactional
    public CustomerView archive(String slug, UUID id, boolean archived) {
        OpsAccess.Who w = access.requireManager(slug);
        OpsCustomer c = find(w.orgId(), id);
        c.setArchived(archived);
        c.setUpdatedAt(clock.instant());
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, c.getId(), w.userId(),
                archived ? "service.customer.archived" : "service.customer.restored", Map.of("name", c.getName()));
        return views(w.orgId(), List.of(c)).get(0);
    }

    // ── Sites ────────────────────────────────────────────────────────────────────────

    @Transactional
    public SiteView addSite(String slug, UUID customerId, SiteRequest in) {
        OpsAccess.Who w = access.requireManager(slug);
        OpsCustomer c = find(w.orgId(), customerId);
        Site s = new Site();
        s.setId(Ids.next());
        s.setOrgId(w.orgId());
        s.setCustomerId(c.getId());
        s.setCreatedAt(clock.instant());
        applySite(s, in);
        sites.saveAndFlush(s);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, c.getId(), w.userId(), "service.site.added",
                Map.of("name", s.getName(), "customer", c.getName()));
        return site(s);
    }

    @Transactional
    public SiteView updateSite(String slug, UUID id, SiteRequest in) {
        OpsAccess.Who w = access.requireManager(slug);
        Site s = sites.findByIdAndOrgId(id, w.orgId()).orElseThrow(BusinessException::notFound);
        applySite(s, in);
        return site(s);
    }

    /** Removes a site nothing points at. */
    @Transactional
    public void removeSite(String slug, UUID id) {
        OpsAccess.Who w = access.requireManager(slug);
        Site s = sites.findByIdAndOrgId(id, w.orgId()).orElseThrow(BusinessException::notFound);
        if (sites.inUse(s.getId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Tickets, assets or work orders are at this site", List.of());
        }
        sites.delete(s);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, s.getCustomerId(), w.userId(), "service.site.removed",
                Map.of("name", s.getName()));
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    OpsCustomer find(UUID orgId, UUID id) {
        return customers.findByIdAndOrgId(id, orgId).orElseThrow(BusinessException::notFound);
    }

    /** A customer (not archived) and, when given, one of its sites, for a new or changed record. */
    void checkLink(UUID orgId, UUID customerId, UUID siteId, boolean allowArchived) {
        if (customerId == null) {
            if (siteId != null) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "siteId", "Choose a site of the customer");
            }
            return;
        }
        OpsCustomer c = customers.findByIdAndOrgId(customerId, orgId)
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "customerId", "Choose a customer"));
        if (c.isArchived() && !allowArchived) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "customerId", "This customer is archived");
        }
        if (siteId != null && sites.findByIdAndOrgId(siteId, orgId).filter(s -> s.getCustomerId().equals(customerId)).isEmpty()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "siteId", "Choose a site of the customer");
        }
    }

    static SiteView site(Site s) {
        return new SiteView(s.getId(), s.getCustomerId(), s.getName(), s.getAddress(), s.getNotes());
    }

    private List<CustomerView> views(UUID orgId, List<OpsCustomer> found) {
        List<UUID> ids = found.stream().map(OpsCustomer::getId).toList();
        Map<UUID, Integer> siteCount = new HashMap<>();
        Map<UUID, Integer> assetCount = new HashMap<>();
        Map<UUID, Integer> openCount = new HashMap<>();
        if (!ids.isEmpty()) {
            sites.findByOrgIdAndCustomerIdIn(orgId, ids).forEach(s -> siteCount.merge(s.getCustomerId(), 1, Integer::sum));
            assets.findAll((Specification<Asset>) (a, q, cb) -> cb.and(cb.equal(a.get("orgId"), orgId), a.get("customerId").in(ids)))
                    .forEach(a -> assetCount.merge(a.getCustomerId(), 1, Integer::sum));
            tickets.findAll((Specification<Ticket>) (t, q, cb) -> cb.and(cb.equal(t.get("orgId"), orgId), t.get("customerId").in(ids),
                    t.get("status").in(Ticket.OPEN))).forEach(t -> openCount.merge(t.getCustomerId(), 1, Integer::sum));
        }
        return found.stream().map(c -> new CustomerView(c.getId(), c.getName(), c.getContactName(), c.getPhone(), c.getEmail(),
                c.getAddress(), c.getNotes(), c.isArchived(), siteCount.getOrDefault(c.getId(), 0),
                assetCount.getOrDefault(c.getId(), 0), openCount.getOrDefault(c.getId(), 0), c.getCreatedAt())).toList();
    }

    private void apply(OpsCustomer c, CustomerRequest in) {
        c.setName(in.name().strip());
        c.setContactName(OpsPeople.clean(in.contactName()));
        c.setPhone(OpsPeople.clean(in.phone()));
        c.setEmail(OpsPeople.clean(in.email()));
        c.setAddress(OpsPeople.clean(in.address()));
        c.setNotes(OpsPeople.clean(in.notes()));
        c.setUpdatedAt(clock.instant());
    }

    private static void applySite(Site s, SiteRequest in) {
        s.setName(in.name().strip());
        s.setAddress(OpsPeople.clean(in.address()));
        s.setNotes(OpsPeople.clean(in.notes()));
    }
}
