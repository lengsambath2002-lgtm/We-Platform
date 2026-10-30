package com.businessplatform.ops.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.calendar.CalendarService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.ops.domain.Asset;
import com.businessplatform.ops.domain.MaintenancePlan;
import com.businessplatform.ops.domain.WorkOrder;
import com.businessplatform.ops.payload.OpsPayloads.GenerateResult;
import com.businessplatform.ops.payload.OpsPayloads.PlanRequest;
import com.businessplatform.ops.payload.OpsPayloads.PlanView;
import com.businessplatform.ops.repository.AssetRepository;
import com.businessplatform.ops.repository.MaintenancePlanRepository;
import com.businessplatform.ops.repository.WorkOrderRepository;
import com.businessplatform.org.repository.OrganisationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Preventive maintenance plans (every N days or months per asset). A plan's next work order is generated
 * {@code leadDays} before its due date by the hourly job or a dispatcher's "Generate now"; generation is idempotent:
 * each plan is locked while it is handled, its due date gets at most one work order (a unique index backs this),
 * and then the due date moves on one interval (past missed dates are skipped, not piled up).
 */
@Slf4j
@Service
public class MaintenanceService {

    public static final String RECORD = "maintenance_plan";

    private final OpsAccess access;
    private final AssetService assetService;
    private final WorkOrderService workOrderService;
    private final MaintenancePlanRepository plans;
    private final AssetRepository assets;
    private final WorkOrderRepository workOrders;
    private final OrganisationRepository orgs;
    private final OpsViews views;
    private final ActivityService activity;
    private final TransactionTemplate tx;
    private final Clock clock;

    public MaintenanceService(OpsAccess access, AssetService assetService, WorkOrderService workOrderService,
            MaintenancePlanRepository plans, AssetRepository assets, WorkOrderRepository workOrders, OrganisationRepository orgs,
            OpsViews views, ActivityService activity, PlatformTransactionManager txm, Clock clock) {
        this.access = access;
        this.assetService = assetService;
        this.workOrderService = workOrderService;
        this.plans = plans;
        this.assets = assets;
        this.workOrders = workOrders;
        this.orgs = orgs;
        this.views = views;
        this.activity = activity;
        this.tx = new TransactionTemplate(txm);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlanView> list(String slug, UUID assetId) {
        OpsAccess.Who w = access.requireManager(slug);
        return views.plans(w.orgId(), assetId == null ? plans.findByOrgIdOrderByNextDue(w.orgId())
                : plans.findByOrgIdAndAssetIdOrderByNextDue(w.orgId(), assetId));
    }

    @Transactional
    public PlanView create(String slug, PlanRequest in) {
        OpsAccess.Who w = access.requireManager(slug);
        MaintenancePlan p = new MaintenancePlan();
        p.setId(Ids.next());
        p.setOrgId(w.orgId());
        p.setCreatedBy(w.userId());
        p.setCreatedAt(clock.instant());
        apply(w, p, in);
        plans.saveAndFlush(p);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, p.getId(), w.userId(), "service.plan.created",
                Map.of("title", p.getTitle(), "due", p.getNextDue().toString()));
        return views.plans(w.orgId(), List.of(p)).get(0);
    }

    @Transactional
    public PlanView update(String slug, UUID id, PlanRequest in) {
        OpsAccess.Who w = access.requireManager(slug);
        MaintenancePlan p = plans.findByIdAndOrgId(id, w.orgId()).orElseThrow(BusinessException::notFound);
        apply(w, p, in);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, p.getId(), w.userId(), "service.plan.updated",
                Map.of("title", p.getTitle(), "due", p.getNextDue().toString(), "active", p.isActive()));
        return views.plans(w.orgId(), List.of(p)).get(0);
    }

    /** "Generate now": the organisation's plans that are due get their work orders. Safe to repeat. */
    public GenerateResult generateNow(String slug) {
        OpsAccess.Who w = access.requireManager(slug);
        List<WorkOrder> made = generate(w.orgId(), w.ctx().slug(), w.userId());
        return tx.execute(s -> new GenerateResult(made.size(), views.workOrders(w.orgId(), made)));
    }

    /** The hourly job: every live organisation (not deleted or suspended) with Service &amp; Operations on and a plan due. */
    public int generateAll() {
        LocalDate today = access.today();
        int n = 0;
        for (UUID orgId : plans.orgsWithDue(today)) {
            String slug = orgs.findById(orgId).filter(o -> o.isLive()).map(o -> o.getSlug()).orElse(null);
            if (slug != null) {
                n += generate(orgId, slug, null).size();
            }
        }
        return n;
    }

    /** Each due plan in its own transaction, so one failing plan doesn't stop the others. */
    List<WorkOrder> generate(UUID orgId, String slug, UUID actorId) {
        List<WorkOrder> made = new ArrayList<>();
        for (UUID planId : plans.dueIds(orgId, access.today())) {
            try {
                Optional<WorkOrder> o = tx.execute(s -> one(planId, slug, actorId));
                o.ifPresent(made::add);
            } catch (RuntimeException e) {
                log.warn("Maintenance plan {} could not generate its work order", planId, e);
            }
        }
        return made;
    }

    private Optional<WorkOrder> one(UUID planId, String slug, UUID actorId) {
        MaintenancePlan p = plans.lock(planId).orElse(null);
        LocalDate today = access.today();
        if (p == null || !p.isActive() || p.generateOn().isAfter(today)) {
            return Optional.empty(); // Another run handled it meanwhile.
        }
        Asset a = assets.findByIdAndOrgId(p.getAssetId(), p.getOrgId()).orElse(null);
        WorkOrder made = null;
        if (a != null && !Asset.RETIRED.equals(a.getStatus()) && !workOrders.existsByPlanIdAndPlanDue(p.getId(), p.getNextDue())) {
            made = workOrderService.generated(p.getOrgId(), slug, actorId, p, a);
        }
        // Move on to the next due date that is still ahead of its generation day (missed ones are skipped).
        LocalDate next = p.after(p.getNextDue());
        while (next.isBefore(today)) {
            next = p.after(next);
        }
        p.setNextDue(next);
        p.setLastGeneratedAt(clock.instant());
        p.setUpdatedAt(clock.instant());
        return Optional.ofNullable(made);
    }

    private void apply(OpsAccess.Who w, MaintenancePlan p, PlanRequest in) {
        assetService.find(w.orgId(), in.assetId());
        if (in.technicianId() != null && !access.technician(w.orgId(), in.technicianId())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "technicianId", "Choose active technicians");
        }
        if (p.getCreatedAt() != null && p.getId() != null && in.nextDue().isBefore(LocalDate.now(clock.withZone(CalendarService.ZONE)).minusYears(1))) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "nextDue", "Choose a date within the last year or later");
        }
        p.setAssetId(in.assetId());
        p.setTitle(in.title().strip());
        p.setIntervalCount(in.intervalCount().shortValue());
        p.setIntervalUnit(in.intervalUnit());
        p.setNextDue(in.nextDue());
        p.setLeadDays(in.leadDays() == null ? 7 : in.leadDays().shortValue());
        p.setTechnicianId(in.technicianId());
        p.setChecklist(in.checklist() == null ? List.of() : in.checklist().stream().map(String::strip).toList());
        p.setActive(in.active() == null || in.active());
        p.setUpdatedAt(clock.instant());
    }
}
