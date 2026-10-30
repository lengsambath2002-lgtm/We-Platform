package com.businessplatform.ops.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.ops.domain.Ticket;
import com.businessplatform.ops.payload.OpsPayloads.Overview;
import com.businessplatform.ops.payload.OpsPayloads.WeekCount;
import com.businessplatform.ops.repository.MaintenancePlanRepository;
import com.businessplatform.ops.repository.TicketRepository;
import com.businessplatform.ops.repository.WorkOrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * The service dashboard: tickets by status, SLA breaches and tickets at risk, this week's work orders, drafts
 * waiting for approval, tickets opened vs resolved over the last {@value #WEEKS} weeks and maintenance due in the
 * next {@value #MAINTENANCE_DAYS} days. A technician sees only their own tickets and work orders.
 */
@Service
@RequiredArgsConstructor
public class OpsOverviewService {

    static final int WEEKS = 8;
    static final int MAINTENANCE_DAYS = 30;

    private final OpsAccess access;
    private final TicketRepository tickets;
    private final WorkOrderRepository workOrders;
    private final MaintenancePlanRepository plans;
    private final OpsViews views;
    private final java.time.Clock clock;

    @Transactional(readOnly = true)
    public Overview overview(String slug) {
        OpsAccess.Who w = access.require(slug);
        boolean all = w.manager();
        UUID mine = all ? null : w.userId();
        Instant now = clock.instant();
        LocalDate today = access.today();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Ticket.STATUSES.forEach(s -> byStatus.put(s, 0L));
        tickets.countByStatus(w.orgId(), mine).forEach(r -> byStatus.put((String) r[0], ((Number) r[1]).longValue()));
        long open = Ticket.OPEN.stream().mapToLong(byStatus::get).sum();
        var breached = tickets.breached(w.orgId(), mine, now);
        long atRisk = tickets.atRisk(w.orgId(), mine, now);
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        var week = workOrders.scheduledBetween(w.orgId(), monday.atStartOfDay(CalendarService.ZONE).toInstant(),
                monday.plusDays(7).atStartOfDay(CalendarService.ZONE).toInstant(), mine);
        List<WeekCount> weeks = new ArrayList<>();
        if (all) {
            LocalDate first = monday.minusWeeks(WEEKS - 1);
            Instant from = first.atStartOfDay(CalendarService.ZONE).toInstant();
            Instant to = monday.plusWeeks(1).atStartOfDay(CalendarService.ZONE).toInstant();
            Map<LocalDate, Long> opened = perWeek(tickets.openedPerWeek(w.orgId(), from, to));
            Map<LocalDate, Long> resolved = perWeek(tickets.resolvedPerWeek(w.orgId(), from, to));
            for (LocalDate d = first; !d.isAfter(monday); d = d.plusWeeks(1)) {
                weeks.add(new WeekCount(d, opened.getOrDefault(d, 0L), resolved.getOrDefault(d, 0L)));
            }
        }
        return new Overview(all ? "ALL" : "MINE", byStatus, open, breached.size(), atRisk,
                views.tickets(w.orgId(), breached.stream().limit(5).toList()), views.workOrders(w.orgId(), week),
                all ? workOrders.awaitingApproval(w.orgId()) : 0, weeks,
                all ? views.plans(w.orgId(), plans.dueBetween(w.orgId(), today, today.plusDays(MAINTENANCE_DAYS))) : List.of());
    }

    static Map<LocalDate, Long> perWeek(List<Object[]> rows) {
        Map<LocalDate, Long> out = new HashMap<>();
        for (Object[] r : rows) {
            LocalDate d = r[0] instanceof java.sql.Date x ? x.toLocalDate() : (LocalDate) r[0];
            out.put(d, ((Number) r[1]).longValue());
        }
        return out;
    }
}
