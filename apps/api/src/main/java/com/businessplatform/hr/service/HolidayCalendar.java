package com.businessplatform.hr.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.calendar.PeriodSource;
import com.businessplatform.common.i18n.Messages;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Public holidays on the calendar for every member (in the reader's language); nothing when HR is off. */
@Component
@RequiredArgsConstructor
public class HolidayCalendar implements PeriodSource {

    public static final String KIND = "holiday";

    private final HrAccess hr;
    private final HolidayService holidays;

    @Override
    @Transactional(readOnly = true)
    public List<Period> periods(OrgContext ctx, LocalDate from, LocalDate to, boolean mineOnly) {
        if (!hr.usable(ctx)) {
            return List.of();
        }
        boolean khmer = "km".equals(Messages.requestLanguage());
        return holidays.between(ctx, from, to).stream()
                .map(h -> new Period(HrAccess.MODULE, KIND, h.getId(), khmer ? h.getNameKm() : h.getNameEn(), h.getDate(),
                        h.getDate(), false, false, "/" + ctx.slug() + "/hr", false))
                .toList();
    }
}
