package com.businessplatform.business.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.calendar.CalendarService;
import com.businessplatform.business.domain.Customer;
import com.businessplatform.business.domain.Deal;
import com.businessplatform.business.domain.DealActivity;
import com.businessplatform.business.domain.Quote;
import com.businessplatform.business.payload.BusinessPayloads.CustomerRef;
import com.businessplatform.business.payload.BusinessPayloads.CustomerRequest;
import com.businessplatform.business.payload.BusinessPayloads.CustomerView;
import com.businessplatform.business.payload.BusinessPayloads.LineInput;
import com.businessplatform.business.payload.BusinessPayloads.PersonRef;
import com.businessplatform.business.payload.BusinessPayloads.QuoteRequest;
import com.businessplatform.business.payload.BusinessPayloads.QuoteView;
import com.businessplatform.business.payload.CrmPayloads.*;
import com.businessplatform.business.repository.CustomerRepository;
import com.businessplatform.business.repository.DealRepositories.DealActivityRepository;
import com.businessplatform.business.repository.DealRepositories.DealRepository;
import com.businessplatform.business.repository.QuoteRepository;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * The sales pipeline. Everyone in General Business sees every deal (the team's pipeline) and adds deals and
 * activities; a deal's owner, the person who added it, and Admins change it. A lead (company or contact) becomes a
 * customer on request, or when a quote is started from the deal. Won and lost deals are closed; a lost one keeps
 * its reason.
 */
@Service
@RequiredArgsConstructor
public class DealService {

    /** How deals appear in the activity log. */
    public static final String RECORD = "deal";
    static final int WIN_RATE_DAYS = 90;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BusinessAccess access;
    private final BizPeople people;
    private final DealRepository deals;
    private final DealActivityRepository activities;
    private final CustomerRepository customers;
    private final QuoteRepository quotes;
    private final CustomerService customerService;
    private final QuoteService quoteService;
    private final MembershipRepository memberships;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Pipeline pipeline(String slug) {
        OrgContext ctx = access.require(slug);
        List<Deal> all = deals.findByOrgIdOrderByUpdatedAtDesc(ctx.orgId());
        List<StageTotal> stages = new ArrayList<>();
        BigDecimal open = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        for (String s : Deal.STAGES) {
            List<Deal> here = all.stream().filter(d -> s.equals(d.getStage())).toList();
            BigDecimal value = sum(here);
            BigDecimal w = Customers.money(value.multiply(BigDecimal.valueOf(Deal.PROBABILITY.get(s))).divide(HUNDRED, 2, RoundingMode.HALF_UP));
            stages.add(new StageTotal(s, here.size(), value, w));
            if (!Deal.WON.equals(s) && !Deal.LOST.equals(s)) {
                open = open.add(value);
                weighted = weighted.add(w);
            }
        }
        LocalDate today = access.today();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(CalendarService.ZONE).toInstant();
        List<Deal> wonThisMonth = all.stream().filter(d -> Deal.WON.equals(d.getStage()) && d.getClosedAt() != null
                && !d.getClosedAt().isBefore(monthStart)).toList();
        Instant since = clock.instant().minus(WIN_RATE_DAYS, ChronoUnit.DAYS);
        List<Deal> recent = all.stream().filter(d -> d.closed() && d.getClosedAt() != null && d.getClosedAt().isAfter(since)).toList();
        Integer winRate = recent.isEmpty() ? null
                : (int) Math.round(100.0 * recent.stream().filter(d -> Deal.WON.equals(d.getStage())).count() / recent.size());
        return new Pipeline(stages, Customers.money(open), Customers.money(weighted), sum(wonThisMonth), wonThisMonth.size(),
                winRate, views(ctx, all));
    }

    @Transactional(readOnly = true)
    public DealDetail get(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        return detail(ctx, deal(ctx, id));
    }

    @Transactional
    public DealDetail create(String slug, DealRequest in) {
        OrgContext ctx = access.require(slug);
        Deal d = new Deal();
        d.setId(Ids.next());
        d.setOrgId(ctx.orgId());
        d.setStage("LEAD");
        d.setCreatedBy(ctx.userId());
        d.setCreatedAt(clock.instant());
        apply(ctx, d, in);
        deals.saveAndFlush(d);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, d.getId(), ctx.userId(), "business.deal.created",
                Map.of("title", d.getTitle(), "value", d.getValue().toPlainString()));
        tellOwner(ctx, d);
        return detail(ctx, d);
    }

    @Transactional
    public DealDetail update(String slug, UUID id, DealRequest in) {
        OrgContext ctx = access.require(slug);
        Deal d = editable(ctx, id);
        UUID before = d.getOwnerId();
        apply(ctx, d, in);
        if (!d.getOwnerId().equals(before)) {
            tellOwner(ctx, d);
        }
        return detail(ctx, d);
    }

    /** Moves a deal; WON and LOST close it (LOST with a reason), any open stage reopens it. */
    @Transactional
    public DealDetail move(String slug, UUID id, DealStageRequest in) {
        OrgContext ctx = access.require(slug);
        Deal d = editable(ctx, id);
        String reason = BizPeople.clean(in.reason());
        if (Deal.LOST.equals(in.stage()) && reason == null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "reason", "Say why the deal was lost");
        }
        if (in.stage().equals(d.getStage())) {
            return detail(ctx, d);
        }
        String from = d.getStage();
        d.setStage(in.stage());
        d.setLostReason(Deal.LOST.equals(in.stage()) ? reason : null);
        d.setClosedAt(d.closed() ? clock.instant() : null);
        d.setUpdatedAt(clock.instant());
        Map<String, Object> data = new HashMap<>(Map.of("title", d.getTitle(), "from", from, "to", in.stage()));
        if (reason != null && Deal.LOST.equals(in.stage())) {
            data.put("reason", reason);
        }
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, d.getId(), ctx.userId(),
                Deal.WON.equals(in.stage()) ? "business.deal.won" : Deal.LOST.equals(in.stage()) ? "business.deal.lost" : "business.deal.moved", data);
        return detail(ctx, d);
    }

    @Transactional
    public DealDetail addActivity(String slug, UUID id, DealActivityRequest in) {
        OrgContext ctx = access.require(slug);
        Deal d = deal(ctx, id);
        DealActivity a = new DealActivity();
        a.setId(Ids.next());
        a.setOrgId(ctx.orgId());
        a.setDealId(d.getId());
        a.setKind(in.kind());
        a.setBody(in.body().strip());
        a.setHappenedOn(in.happenedOn() == null ? access.today() : in.happenedOn());
        a.setCreatedBy(ctx.userId());
        a.setCreatedAt(clock.instant());
        activities.save(a);
        d.setUpdatedAt(clock.instant());
        return detail(ctx, d);
    }

    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Deal d = editable(ctx, id);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, d.getId(), ctx.userId(), "business.deal.deleted", Map.of("title", d.getTitle()));
        deals.delete(d);
    }

    /** Turns the lead into a customer (or returns the deal as it is when it has one already). */
    @Transactional
    public DealDetail convert(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Deal d = editable(ctx, id);
        customerFor(slug, ctx, d);
        return detail(ctx, d);
    }

    /** Starts a draft quote for the deal's customer (making the lead a customer first) and links it; moves a deal not yet at Proposal there. */
    @Transactional
    public QuoteView startQuote(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Deal d = editable(ctx, id);
        if (d.closed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This deal is closed", List.of());
        }
        UUID customerId = customerFor(slug, ctx, d);
        QuoteView q = quoteService.create(slug, new QuoteRequest(customerId, access.today(), access.today().plusDays(30), null,
                List.of(new LineInput(null, d.getTitle(), null, BigDecimal.ONE, d.getValue(), null, true)), null));
        d.setQuoteId(q.id());
        if (Deal.STAGES.indexOf(d.getStage()) < Deal.STAGES.indexOf("PROPOSAL")) {
            d.setStage("PROPOSAL");
        }
        d.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, d.getId(), ctx.userId(), "business.deal.quoted",
                Map.of("title", d.getTitle(), "quote", q.number()));
        return q;
    }

    /** Deals for one customer (their page). */
    @Transactional(readOnly = true)
    public List<DealView> forCustomer(OrgContext ctx, UUID customerId) {
        return views(ctx, deals.findByOrgIdAndCustomerIdOrderByUpdatedAtDesc(ctx.orgId(), customerId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private Deal deal(OrgContext ctx, UUID id) {
        return deals.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private static boolean canEdit(OrgContext ctx, Deal d) {
        return BusinessAccess.manages(ctx) || ctx.userId().equals(d.getOwnerId()) || ctx.userId().equals(d.getCreatedBy());
    }

    private Deal editable(OrgContext ctx, UUID id) {
        Deal d = deal(ctx, id);
        if (!canEdit(ctx, d)) {
            throw BusinessException.forbidden();
        }
        return d;
    }

    private void apply(OrgContext ctx, Deal d, DealRequest in) {
        String company = BizPeople.clean(in.company());
        String contact = BizPeople.clean(in.contactName());
        if (in.customerId() != null) {
            customers.findByIdAndOrgId(in.customerId(), ctx.orgId()).orElseThrow(() ->
                    BusinessException.field(ErrorCode.VALIDATION_FAILED, "customerId", "Choose a customer"));
        } else if (company == null && contact == null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "company", "Choose a customer, or give the lead's company or contact");
        }
        UUID owner = in.ownerId() == null ? (d.getOwnerId() == null ? ctx.userId() : d.getOwnerId()) : in.ownerId();
        boolean member = memberships.findByOrgIdAndUserId(ctx.orgId(), owner)
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST).isPresent();
        if (!member) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "ownerId", "Choose a member of the organisation");
        }
        d.setTitle(in.title().strip());
        d.setCustomerId(in.customerId());
        d.setCompany(company);
        d.setContactName(contact);
        d.setEmail(BizPeople.clean(in.email()));
        d.setPhone(BizPeople.clean(in.phone()));
        d.setValue(Customers.money(in.value()));
        d.setSource(in.source());
        d.setExpectedClose(in.expectedClose());
        d.setOwnerId(owner);
        d.setNextStep(BizPeople.clean(in.nextStep()));
        d.setNextStepDate(in.nextStepDate());
        d.setNotes(BizPeople.clean(in.notes()));
        d.setUpdatedAt(clock.instant());
    }

    private UUID customerFor(String slug, OrgContext ctx, Deal d) {
        if (d.getCustomerId() != null) {
            return d.getCustomerId();
        }
        boolean company = d.getCompany() != null;
        CustomerView c = customerService.create(slug, new CustomerRequest(company ? "COMPANY" : "INDIVIDUAL",
                company ? d.getCompany() : d.getContactName(), company ? d.getContactName() : null, d.getEmail(), d.getPhone(),
                null, null, null));
        d.setCustomerId(c.id());
        d.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, d.getId(), ctx.userId(), "business.deal.converted",
                Map.of("title", d.getTitle(), "customer", c.name()));
        return c.id();
    }

    private void tellOwner(OrgContext ctx, Deal d) {
        notifications.send(ctx.orgId(), List.of(d.getOwnerId()), ctx.userId(), "business.deal.owner",
                Text.of("You own the deal {title}", Map.of("title", d.getTitle())), null, "/" + ctx.slug() + "/business/deals/" + d.getId());
    }

    private static BigDecimal sum(List<Deal> ds) {
        return Customers.money(ds.stream().map(Deal::getValue).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private DealDetail detail(OrgContext ctx, Deal d) {
        deals.flush();
        List<DealActivity> as = activities.findByDealIdOrderByHappenedOnDescCreatedAtDesc(d.getId());
        Map<UUID, PersonRef> by = people.of(as.stream().map(DealActivity::getCreatedBy).distinct().toList());
        return new DealDetail(views(ctx, List.of(d)).get(0), as.stream().map(a -> new DealActivityView(a.getId(), a.getKind(),
                a.getBody(), a.getHappenedOn(), by.get(a.getCreatedBy()), a.getCreatedAt())).toList());
    }

    private List<DealView> views(OrgContext ctx, List<Deal> found) {
        Map<UUID, Customer> cs = customers.findAllById(found.stream().map(Deal::getCustomerId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<UUID, Quote> qs = quotes.findAllById(found.stream().map(Deal::getQuoteId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Quote::getId, Function.identity()));
        Map<UUID, PersonRef> owners = people.of(found.stream().flatMap(d -> Stream.of(d.getOwnerId())).distinct().toList());
        LocalDate today = access.today();
        return found.stream().map(d -> {
            Customer c = d.getCustomerId() == null ? null : cs.get(d.getCustomerId());
            Quote q = d.getQuoteId() == null ? null : qs.get(d.getQuoteId());
            return new DealView(d.getId(), d.getTitle(), c == null ? null : new CustomerRef(c.getId(), c.getName(), c.isArchived()),
                    d.getCompany(), d.getContactName(), d.getEmail(), d.getPhone(), d.getValue(), d.getStage(),
                    Deal.PROBABILITY.get(d.getStage()), d.getSource(), d.getExpectedClose(), owners.get(d.getOwnerId()), d.getNextStep(),
                    d.getNextStepDate(), !d.closed() && d.getNextStepDate() != null && !d.getNextStepDate().isAfter(today),
                    q == null ? null : new QuoteRef(q.getId(), q.getNumber(), q.getStatus()), d.getLostReason(), d.getClosedAt(),
                    d.getNotes(), d.getCreatedAt(), d.getUpdatedAt(), canEdit(ctx, d));
        }).toList();
    }
}
