package com.businessplatform.ops.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.ops.domain.Sla;
import com.businessplatform.ops.domain.Ticket;
import com.businessplatform.ops.domain.WoLine;
import com.businessplatform.ops.domain.WoTechnician;
import com.businessplatform.ops.domain.WorkOrder;
import com.businessplatform.ops.payload.OpsPayloads.CategoryCount;
import com.businessplatform.ops.payload.OpsPayloads.PersonRef;
import com.businessplatform.ops.payload.OpsPayloads.Report;
import com.businessplatform.ops.payload.OpsPayloads.SlaReport;
import com.businessplatform.ops.payload.OpsPayloads.TechnicianReport;
import com.businessplatform.ops.repository.TicketRepository;
import com.businessplatform.ops.repository.WoLineRepository;
import com.businessplatform.ops.repository.WoTechnicianRepository;
import com.businessplatform.ops.repository.WorkOrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service reports for a period of days (default the last 30, at most 366): work completed per technician (a work
 * order shared by two technicians counts fully for each), SLA results of the tickets that came in, by priority, and
 * tickets by category. Dispatchers and Admins.
 */
@Service
@RequiredArgsConstructor
public class OpsReportService {

    private final OpsAccess access;
    private final TicketRepository tickets;
    private final WorkOrderRepository workOrders;
    private final WoTechnicianRepository technicians;
    private final WoLineRepository lines;
    private final OpsPeople people;
    private final java.time.Clock clock;

    @Transactional(readOnly = true)
    public Report report(String slug, LocalDate from, LocalDate to) {
        OpsAccess.Who w = access.requireManager(slug);
        LocalDate t = to == null ? access.today() : to;
        LocalDate f = from == null ? t.minusDays(29) : from;
        if (f.isAfter(t) || f.plusDays(366).isBefore(t)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "from", "Choose a period of at most a year");
        }
        Instant start = f.atStartOfDay(CalendarService.ZONE).toInstant();
        Instant end = t.plusDays(1).atStartOfDay(CalendarService.ZONE).toInstant();
        Instant now = clock.instant();

        List<Ticket> in = tickets.findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(w.orgId(), start, end);
        long resolved = tickets.count((Specification<Ticket>) (x, q, cb) -> cb.and(cb.equal(x.get("orgId"), w.orgId()),
                cb.greaterThanOrEqualTo(x.get("resolvedAt"), start), cb.lessThan(x.get("resolvedAt"), end)));
        List<SlaReport> sla = new ArrayList<>();
        for (String p : OpsSettingsService.PRIORITIES) {
            List<Ticket> ts = in.stream().filter(x -> x.getPriority().equals(p)).toList();
            sla.add(new SlaReport(p, ts.size(),
                    ts.stream().filter(x -> Sla.MET.equals(Sla.response(x, now))).count(),
                    ts.stream().filter(x -> Sla.BREACHED.equals(Sla.response(x, now))).count(),
                    ts.stream().filter(x -> Sla.MET.equals(Sla.resolution(x, now))).count(),
                    ts.stream().filter(x -> Sla.BREACHED.equals(Sla.resolution(x, now))).count(),
                    ts.stream().filter(Ticket::isOpen).count()));
        }
        Map<String, Long> cats = new HashMap<>();
        in.forEach(x -> cats.merge(x.getCategory(), 1L, Long::sum));
        List<CategoryCount> categories = Ticket.CATEGORIES.stream().map(c -> new CategoryCount(c, cats.getOrDefault(c, 0L))).toList();

        List<WorkOrder> done = workOrders.findByOrgIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(w.orgId(),
                WorkOrder.COMPLETED, start, end);
        Map<UUID, WorkOrder> byId = new HashMap<>();
        done.forEach(o -> byId.put(o.getId(), o));
        Map<UUID, List<WorkOrder>> perTech = new HashMap<>();
        if (!done.isEmpty()) {
            for (WoTechnician x : technicians.findByWorkOrderIdIn(byId.keySet())) {
                perTech.computeIfAbsent(x.getUserId(), k -> new ArrayList<>()).add(byId.get(x.getWorkOrderId()));
            }
        }
        Map<UUID, BigDecimal> hours = new HashMap<>();
        lines.labourHours(w.orgId(), start, end).forEach(r -> hours.put((UUID) r[0], new BigDecimal(r[1].toString())));
        Map<UUID, PersonRef> ps = people.of(perTech.keySet());
        List<TechnicianReport> techs = perTech.entrySet().stream().filter(e -> ps.containsKey(e.getKey())).map(e -> {
            BigDecimal labour = sum(e.getValue(), WorkOrder::getLabourTotal);
            BigDecimal parts = sum(e.getValue(), WorkOrder::getPartsTotal);
            return new TechnicianReport(ps.get(e.getKey()), e.getValue().size(),
                    hours.getOrDefault(e.getKey(), BigDecimal.ZERO).setScale(1, java.math.RoundingMode.HALF_UP), labour, parts,
                    labour.add(parts));
        }).sorted((a, b) -> Long.compare(b.completed(), a.completed()) != 0 ? Long.compare(b.completed(), a.completed())
                : a.technician().name().compareToIgnoreCase(b.technician().name())).toList();
        return new Report(f, t, in.size(), resolved, done.size(), sum(done, WorkOrder::getTotal), techs, sla, categories);
    }

    private static BigDecimal sum(List<WorkOrder> os, java.util.function.Function<WorkOrder, BigDecimal> f) {
        return os.stream().map(f).reduce(WoLine.ZERO, BigDecimal::add);
    }
}
