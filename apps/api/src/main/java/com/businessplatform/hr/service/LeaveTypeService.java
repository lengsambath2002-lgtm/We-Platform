package com.businessplatform.hr.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.hr.domain.LeaveType;
import com.businessplatform.hr.payload.HrPayloads.LeaveTypeRequest;
import com.businessplatform.hr.payload.HrPayloads.LeaveTypeView;
import com.businessplatform.hr.repository.LeaveTypeRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * The kinds of leave an organisation offers. The first time HR is used it gets three editable defaults
 * close to Cambodian labour law: Annual leave 18 days, Sick leave 7 days, Unpaid leave unlimited.
 * Types are switched off rather than deleted, so past requests keep their type.
 */
@Service
@RequiredArgsConstructor
public class LeaveTypeService {

    public static final String RECORD = "leave_type";

    private final HrAccess hr;
    private final LeaveTypeRepository types;
    private final ActivityService activity;
    private final Clock clock;

    /** Active types; HR managers also see switched-off ones. */
    @Transactional
    public List<LeaveTypeView> list(String slug) {
        OrgContext ctx = hr.require(slug);
        return all(ctx).stream().filter(t -> t.isActive() || HrAccess.manages(ctx)).map(LeaveTypeService::view).toList();
    }

    @Transactional
    public LeaveTypeView save(String slug, UUID id, LeaveTypeRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        LeaveType t;
        if (id == null) {
            t = new LeaveType();
            t.setId(Ids.next());
            t.setOrgId(ctx.orgId());
            t.setCreatedAt(clock.instant());
            t.setSortOrder((short) (all(ctx).size() * 10 + 10));
        } else {
            t = types.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        }
        String name = in.nameEn().strip();
        if (types.existsByOrgIdAndNameEnIgnoreCaseAndIdNot(ctx.orgId(), name, t.getId())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "nameEn", "Another leave type has this name");
        }
        BigDecimal allowance = in.allowanceDays();
        if (allowance != null && allowance.remainder(LeaveDays.HALF).signum() != 0) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "allowanceDays", "Use whole or half days");
        }
        t.setNameEn(name);
        t.setNameKm(in.nameKm().strip());
        t.setPaid(in.paid());
        t.setAllowanceDays(allowance == null ? null : allowance.setScale(1));
        t.setHalfDays(in.halfDays());
        t.setActive(in.active() == null || in.active());
        types.save(t);
        activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, t.getId(), ctx.userId(),
                id == null ? "leave_type.created" : "leave_type.updated", Map.of("name", t.getNameEn()));
        return view(t);
    }

    // ── For the rest of HR ───────────────────────────────────────────────────────────

    /** Every type of the organisation (seeding the defaults on first use). */
    @Transactional
    public List<LeaveType> all(OrgContext ctx) {
        if (types.countByOrgId(ctx.orgId()) == 0) {
            seed(ctx, "Annual leave", "ច្បាប់ឈប់សម្រាកប្រចាំឆ្នាំ", true, new BigDecimal("18.0"), 10);
            seed(ctx, "Sick leave", "ច្បាប់ឈប់សម្រាកព្យាបាលជំងឺ", true, new BigDecimal("7.0"), 20);
            seed(ctx, "Unpaid leave", "ច្បាប់ឈប់សម្រាកគ្មានប្រាក់ឈ្នួល", false, null, 30);
        }
        return types.findByOrgIdOrderBySortOrderAscNameEnAsc(ctx.orgId());
    }

    @Transactional(readOnly = true)
    public LeaveType find(OrgContext ctx, UUID id) {
        return types.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private void seed(OrgContext ctx, String en, String km, boolean paid, BigDecimal days, int order) {
        types.seed(ctx.orgId(), en, km, paid, days, (short) order);
    }

    public static LeaveTypeView view(LeaveType t) {
        return new LeaveTypeView(t.getId(), t.getNameEn(), t.getNameKm(), t.isPaid(), t.getAllowanceDays(),
                t.isHalfDays(), t.isActive());
    }
}
