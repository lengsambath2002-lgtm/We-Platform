package com.businessplatform.hr.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.hr.domain.EmployeeProfile;
import com.businessplatform.hr.domain.LeaveAdjustment;
import com.businessplatform.hr.domain.LeaveRequest;
import com.businessplatform.hr.domain.LeaveType;
import com.businessplatform.hr.payload.HrPayloads.*;
import com.businessplatform.hr.repository.LeaveAdjustmentRepository;
import com.businessplatform.hr.repository.LeaveRequestRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Leave balances, per person, type and calendar year (a request counts in the year it starts):
 * allowance = the type's yearly days + HR adjustments; used = approved; pending = waiting for a
 * decision; remaining = allowance − used − pending. Unlimited types keep no allowance or remainder.
 * Balances are always computed from requests and adjustments, never stored, so they can't drift.
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final HrAccess hr;
    private final LeaveTypeService types;
    private final ProfileService profiles;
    private final LeaveRequestRepository requests;
    private final LeaveAdjustmentRepository adjustments;
    private final ActivityService activity;
    private final Clock clock;

    /** {@code userId} null = the caller; someone else's balances need {@code hr.manage}. */
    @Transactional
    public List<Balance> balances(String slug, UUID userId, Integer year) {
        OrgContext ctx = hr.require(slug);
        UUID who = userId == null ? ctx.userId() : userId;
        if (!who.equals(ctx.userId()) && !HrAccess.manages(ctx)) {
            throw BusinessException.forbidden();
        }
        profiles.profile(ctx, who);
        return balances(ctx, who, year == null ? LeaveService.today(clock).getYear() : year, false);
    }

    @Transactional
    public List<AdjustmentView> adjustments(String slug, UUID userId, int year) {
        OrgContext ctx = hr.requireManager(slug);
        profiles.profile(ctx, userId);
        Map<UUID, LeaveType> byId = new HashMap<>();
        types.all(ctx).forEach(t -> byId.put(t.getId(), t));
        List<LeaveAdjustment> found = adjustments.findByOrgIdAndUserIdAndYearOrderByCreatedAtDesc(ctx.orgId(), userId, (short) year);
        Map<UUID, PersonRef> by = profiles.persons(found.stream().map(LeaveAdjustment::getCreatedBy).toList());
        return found.stream().map(a -> new AdjustmentView(a.getId(), LeaveTypeService.view(byId.get(a.getLeaveTypeId())),
                a.getYear(), a.getDays(), a.getReason(), by.get(a.getCreatedBy()), a.getCreatedAt())).toList();
    }

    /** Adds (or with negative days removes) allowance for one person, type and year; the reason is kept. */
    @Transactional
    public List<Balance> adjust(String slug, AdjustRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        EmployeeProfile p = profiles.profile(ctx, in.userId());
        LeaveType t = types.find(ctx, in.leaveTypeId());
        if (!t.limited()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "leaveTypeId", "This leave type is unlimited — there is no balance to adjust");
        }
        if (in.days().signum() == 0 || in.days().remainder(LeaveDays.HALF).signum() != 0) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "days", "Use whole or half days, not 0");
        }
        LeaveAdjustment a = new LeaveAdjustment();
        a.setId(Ids.next());
        a.setOrgId(ctx.orgId());
        a.setUserId(in.userId());
        a.setLeaveTypeId(t.getId());
        a.setYear(in.year().shortValue());
        a.setDays(in.days().setScale(1));
        a.setReason(in.reason().strip());
        a.setCreatedBy(ctx.userId());
        a.setCreatedAt(clock.instant());
        adjustments.save(a);
        activity.record(ctx.orgId(), HrAccess.MODULE, ProfileService.RECORD, p.getId(), ctx.userId(), "leave.balance.adjusted",
                Map.of("type", t.getNameEn(), "year", in.year(), "days", a.getDays().toPlainString(), "reason", a.getReason()));
        return balances(ctx, in.userId(), in.year(), false);
    }

    // ── For the rest of HR ───────────────────────────────────────────────────────────

    /** Balances of every active type (and of switched-off types with days in the year when {@code all}). */
    List<Balance> balances(OrgContext ctx, UUID userId, int year, boolean all) {
        Map<UUID, BigDecimal> adjusted = new HashMap<>();
        adjustments.totals(ctx.orgId(), userId, (short) year).forEach(r -> adjusted.put((UUID) r[0], (BigDecimal) r[1]));
        Map<UUID, BigDecimal> used = new HashMap<>();
        Map<UUID, BigDecimal> pending = new HashMap<>();
        requests.totals(ctx.orgId(), userId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).forEach(r ->
                (LeaveRequest.APPROVED.equals(r[1]) ? used : pending).put((UUID) r[0], (BigDecimal) r[2]));
        return types.all(ctx).stream()
                .filter(t -> t.isActive() || all || used.containsKey(t.getId()) || pending.containsKey(t.getId()))
                .map(t -> {
                    BigDecimal adj = adjusted.getOrDefault(t.getId(), BigDecimal.ZERO).setScale(1);
                    BigDecimal u = used.getOrDefault(t.getId(), BigDecimal.ZERO).setScale(1);
                    BigDecimal pe = pending.getOrDefault(t.getId(), BigDecimal.ZERO).setScale(1);
                    BigDecimal allowance = t.limited() ? t.getAllowanceDays().add(adj) : null;
                    return new Balance(LeaveTypeService.view(t), year, allowance, adj, u, pe,
                            allowance == null ? null : allowance.subtract(u).subtract(pe));
                }).toList();
    }

    /** Days left of {@code type} for the person in {@code year}; null = unlimited. */
    BigDecimal remaining(OrgContext ctx, UUID userId, LeaveType type, int year) {
        return balances(ctx, userId, year, true).stream().filter(b -> b.type().id().equals(type.getId()))
                .findFirst().map(Balance::remaining).orElse(null);
    }
}
