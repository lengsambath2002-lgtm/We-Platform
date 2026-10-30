package com.businessplatform.ops.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.ops.domain.Asset;
import com.businessplatform.ops.domain.ChecklistItem;
import com.businessplatform.ops.domain.MaintenancePlan;
import com.businessplatform.ops.domain.OpsCustomer;
import com.businessplatform.ops.domain.Site;
import com.businessplatform.ops.domain.Sla;
import com.businessplatform.ops.domain.Ticket;
import com.businessplatform.ops.domain.WoTechnician;
import com.businessplatform.ops.domain.WorkOrder;
import com.businessplatform.ops.payload.OpsPayloads.AssetView;
import com.businessplatform.ops.payload.OpsPayloads.PersonRef;
import com.businessplatform.ops.payload.OpsPayloads.PlanView;
import com.businessplatform.ops.payload.OpsPayloads.Ref;
import com.businessplatform.ops.payload.OpsPayloads.TicketSummary;
import com.businessplatform.ops.payload.OpsPayloads.WorkOrderSummary;
import com.businessplatform.ops.repository.AssetRepository;
import com.businessplatform.ops.repository.ChecklistItemRepository;
import com.businessplatform.ops.repository.MaintenancePlanRepository;
import com.businessplatform.ops.repository.OpsCustomerRepository;
import com.businessplatform.ops.repository.SiteRepository;
import com.businessplatform.ops.repository.WoTechnicianRepository;

import lombok.RequiredArgsConstructor;

/** Summaries of tickets, work orders, assets and plans as lists show them (repositories only, batched per list). */
@Component
@RequiredArgsConstructor
public class OpsViews {

    private final OpsCustomerRepository customers;
    private final SiteRepository sites;
    private final AssetRepository assets;
    private final MaintenancePlanRepository plans;
    private final WoTechnicianRepository woTechnicians;
    private final ChecklistItemRepository checklist;
    private final OpsPeople people;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TicketSummary> tickets(UUID orgId, Collection<Ticket> found) {
        Map<UUID, Ref> cs = customerRefs(orgId, found.stream().map(Ticket::getCustomerId).toList());
        Map<UUID, PersonRef> ps = people.of(found.stream().map(Ticket::getAssigneeId).toList());
        var now = clock.instant();
        return found.stream().map(t -> new TicketSummary(t.getId(), t.getNumber(), t.getTitle(), t.getCategory(),
                t.getPriority(), t.getStatus(), ref(cs, t.getCustomerId()), t.getAssigneeId() == null ? null : ps.get(t.getAssigneeId()),
                Sla.overall(t, now), t.getResponseDueAt(), t.getResolutionDueAt(), t.getCreatedAt(), t.getUpdatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkOrderSummary> workOrders(UUID orgId, Collection<WorkOrder> found) {
        List<UUID> ids = found.stream().map(WorkOrder::getId).toList();
        Map<UUID, Ref> cs = customerRefs(orgId, found.stream().map(WorkOrder::getCustomerId).toList());
        Map<UUID, Ref> as = assetRefs(orgId, found.stream().map(WorkOrder::getAssetId).toList());
        Map<UUID, List<UUID>> techs = new HashMap<>();
        Map<UUID, int[]> checks = new HashMap<>();
        if (!ids.isEmpty()) {
            woTechnicians.findByWorkOrderIdIn(ids).forEach(t -> techs.computeIfAbsent(t.getWorkOrderId(), k -> new ArrayList<>()).add(t.getUserId()));
            for (ChecklistItem c : checklist.findByWorkOrderIdIn(ids)) {
                int[] n = checks.computeIfAbsent(c.getWorkOrderId(), k -> new int[2]);
                n[1]++;
                if (c.isDone()) {
                    n[0]++;
                }
            }
        }
        Map<UUID, PersonRef> ps = people.of(techs.values().stream().flatMap(List::stream).toList());
        return found.stream().map(w -> {
            int[] n = checks.getOrDefault(w.getId(), new int[2]);
            return new WorkOrderSummary(w.getId(), w.getNumber(), w.getTitle(), w.getPriority(), w.getStatus(),
                    w.getApprovalState(), ref(cs, w.getCustomerId()), ref(as, w.getAssetId()),
                    techs.getOrDefault(w.getId(), List.of()).stream().map(ps::get).filter(Objects::nonNull)
                            .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList(),
                    w.getScheduledStart(), w.getScheduledEnd(), w.getTotal(), w.getPlanId() != null, n[0], n[1], w.getCreatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<AssetView> assets(UUID orgId, Collection<Asset> found, LocalDate today) {
        Map<UUID, Ref> cs = customerRefs(orgId, found.stream().map(Asset::getCustomerId).toList());
        Map<UUID, Ref> ss = siteRefs(orgId, found.stream().map(Asset::getSiteId).toList());
        Map<UUID, LocalDate> next = new HashMap<>();
        if (!found.isEmpty()) {
            plans.findByOrgIdOrderByNextDue(orgId).stream().filter(MaintenancePlan::isActive)
                    .forEach(p -> next.merge(p.getAssetId(), p.getNextDue(), (a, b) -> a.isBefore(b) ? a : b));
        }
        return found.stream().map(a -> new AssetView(a.getId(), ref(cs, a.getCustomerId()), ref(ss, a.getSiteId()), a.getName(),
                a.getSerial(), a.getModel(), a.getManufacturer(), a.getInstalledOn(), a.getWarrantyEnd(),
                a.getWarrantyEnd() != null && !a.getWarrantyEnd().isBefore(today), a.getStatus(), a.getNotes(),
                next.get(a.getId()), a.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<PlanView> plans(UUID orgId, Collection<MaintenancePlan> found) {
        Map<UUID, Asset> as = new HashMap<>();
        assets.findAllById(found.stream().map(MaintenancePlan::getAssetId).distinct().toList()).stream()
                .filter(a -> a.getOrgId().equals(orgId)).forEach(a -> as.put(a.getId(), a));
        Map<UUID, Ref> cs = customerRefs(orgId, as.values().stream().map(Asset::getCustomerId).toList());
        Map<UUID, PersonRef> ps = people.of(found.stream().map(MaintenancePlan::getTechnicianId).toList());
        return found.stream().map(p -> {
            Asset a = as.get(p.getAssetId());
            return new PlanView(p.getId(), a == null ? null : new Ref(a.getId(), a.getName()), a == null ? null : ref(cs, a.getCustomerId()),
                    p.getTitle(), p.getIntervalCount(), p.getIntervalUnit(), p.getNextDue(), p.getLeadDays(), p.generateOn(),
                    p.getTechnicianId() == null ? null : ps.get(p.getTechnicianId()), p.getChecklist(), p.isActive(),
                    p.getLastGeneratedAt());
        }).toList();
    }

    // ── References ───────────────────────────────────────────────────────────────────

    public Map<UUID, Ref> customerRefs(UUID orgId, Collection<UUID> ids) {
        return refs(orgId, ids, customers::findAllById, OpsCustomer::getOrgId, OpsCustomer::getId, OpsCustomer::getName);
    }

    public Map<UUID, Ref> siteRefs(UUID orgId, Collection<UUID> ids) {
        return refs(orgId, ids, sites::findAllById, Site::getOrgId, Site::getId, Site::getName);
    }

    public Map<UUID, Ref> assetRefs(UUID orgId, Collection<UUID> ids) {
        return refs(orgId, ids, assets::findAllById, Asset::getOrgId, Asset::getId,
                a -> a.getSerial() == null ? a.getName() : a.getName() + " · " + a.getSerial());
    }

    public static Ref ref(Map<UUID, Ref> refs, UUID id) {
        return id == null ? null : refs.get(id);
    }

    private static <T> Map<UUID, Ref> refs(UUID orgId, Collection<UUID> ids, Function<List<UUID>, List<T>> load,
            Function<T, UUID> org, Function<T, UUID> id, Function<T, String> name) {
        Map<UUID, Ref> out = new HashMap<>();
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!wanted.isEmpty()) {
            load.apply(wanted).stream().filter(x -> org.apply(x).equals(orgId))
                    .forEach(x -> out.put(id.apply(x), new Ref(id.apply(x), name.apply(x))));
        }
        return out;
    }

    /** Technician user ids of a work order. */
    @Transactional(readOnly = true)
    public List<UUID> technicians(UUID workOrderId) {
        return woTechnicians.findByWorkOrderIdIn(List.of(workOrderId)).stream().map(WoTechnician::getUserId).toList();
    }
}
