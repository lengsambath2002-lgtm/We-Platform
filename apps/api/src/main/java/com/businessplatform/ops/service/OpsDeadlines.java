package com.businessplatform.ops.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.calendar.DeadlineSource;
import com.businessplatform.ops.domain.WorkOrder;
import com.businessplatform.ops.payload.OpsPayloads.WorkOrderSummary;
import com.businessplatform.ops.repository.MaintenancePlanRepository;
import com.businessplatform.ops.repository.WorkOrderRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Service work on the calendar: scheduled work orders on their start day (technicians see theirs; dispatchers and
 * Admins everyone's, or theirs with "only mine"), and upcoming preventive maintenance due dates for dispatchers and
 * Admins. Completed work orders show as done. Nothing when the module is off or the person has no role in it.
 */
@Component
@RequiredArgsConstructor
public class OpsDeadlines implements DeadlineSource {

    private final OpsAccess access;
    private final WorkOrderRepository workOrders;
    private final MaintenancePlanRepository plans;
    private final OpsViews views;

    @Override
    @Transactional(readOnly = true)
    public List<Deadline> deadlines(OrgContext ctx, LocalDate from, LocalDate to, boolean mineOnly) {
        Optional<OpsAccess.Who> who = access.usable(ctx);
        if (who.isEmpty()) {
            return List.of();
        }
        OpsAccess.Who w = who.get();
        UUID tech = !w.manager() || mineOnly ? w.userId() : null;
        List<WorkOrder> found = workOrders.scheduledBetween(ctx.orgId(), from.atStartOfDay(CalendarService.ZONE).toInstant(),
                to.plusDays(1).atStartOfDay(CalendarService.ZONE).toInstant(), tech);
        List<Deadline> out = new ArrayList<>();
        List<WorkOrderSummary> ss = views.workOrders(ctx.orgId(), found);
        for (int i = 0; i < found.size(); i++) {
            WorkOrder o = found.get(i);
            boolean mine = ss.get(i).technicians().stream().anyMatch(p -> p.id().equals(w.userId()));
            out.add(new Deadline(OpsAccess.MODULE, o.getId(), o.getNumber(), o.getTitle(),
                    o.getScheduledStart().atZone(CalendarService.ZONE).toLocalDate(),
                    WorkOrder.COMPLETED.equals(o.getStatus()) ? "DONE" : "TODO", WorkOrderService.link(ctx.slug(), o.getId()), mine));
        }
        if (w.manager() && !mineOnly) {
            plans.dueBetween(ctx.orgId(), from, to).forEach(p -> out.add(new Deadline(OpsAccess.MODULE, p.getId(), "PM",
                    p.getTitle(), p.getNextDue(), "TODO", "/" + ctx.slug() + "/service/maintenance", false)));
        }
        return out;
    }

    @Override
    public boolean canSeeProject(OrgContext ctx, UUID projectId) {
        return false;
    }
}
