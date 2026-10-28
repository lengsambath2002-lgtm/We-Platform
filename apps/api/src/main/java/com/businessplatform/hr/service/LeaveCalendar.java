package com.businessplatform.hr.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.calendar.PeriodSource;
import com.businessplatform.hr.domain.LeaveRequest;
import com.businessplatform.hr.payload.HrPayloads.PersonRef;
import com.businessplatform.hr.repository.LeaveRequestRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Approved leave on the calendar, for every non-guest member (titled with the person's name); nothing when HR is off. */
@Component
@RequiredArgsConstructor
public class LeaveCalendar implements PeriodSource {

    private final HrAccess hr;
    private final LeaveRequestRepository leaves;
    private final ProfileService profiles;

    @Override
    @Transactional(readOnly = true)
    public List<Period> periods(OrgContext ctx, LocalDate from, LocalDate to, boolean mineOnly) {
        if (!hr.usable(ctx)) {
            return List.of();
        }
        List<LeaveRequest> found = leaves.approvedBetween(ctx.orgId(), from, to).stream()
                .filter(l -> !mineOnly || l.getUserId().equals(ctx.userId())).toList();
        Map<UUID, PersonRef> people = profiles.persons(found.stream().map(LeaveRequest::getUserId).toList());
        return found.stream().filter(l -> people.containsKey(l.getUserId()))
                .map(l -> new Period(HrAccess.MODULE, LeaveService.RECORD, l.getId(), people.get(l.getUserId()).name(),
                        l.getStartDate(), l.getEndDate(), l.isHalfStart(), l.isHalfEnd(), LeaveService.link(ctx, l),
                        l.getUserId().equals(ctx.userId())))
                .toList();
    }
}
