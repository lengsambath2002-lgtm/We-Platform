package com.businessplatform.hr.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.hr.domain.Holiday;
import com.businessplatform.hr.payload.HrPayloads.HolidayImportResult;
import com.businessplatform.hr.payload.HrPayloads.HolidayRequest;
import com.businessplatform.hr.payload.HrPayloads.HolidayView;
import com.businessplatform.hr.repository.HolidayRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * The organisation's public holidays. Everyone in HR sees them; HR managers add, remove, or copy in a
 * year of Cambodia's official holidays ({@link CambodiaHolidays}). Leave requests don't count them.
 */
@Service
@RequiredArgsConstructor
public class HolidayService {

    public static final String RECORD = "holiday";

    private final HrAccess hr;
    private final HolidayRepository holidays;
    private final ActivityService activity;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<HolidayView> list(String slug, int year) {
        OrgContext ctx = hr.require(slug);
        return between(ctx, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream().map(HolidayService::view)
                .toList();
    }

    @Transactional
    public HolidayView add(String slug, HolidayRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        if (holidays.existsByOrgIdAndDate(ctx.orgId(), in.date())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "date", "There is already a holiday on this day");
        }
        String en = in.nameEn().strip();
        Holiday h = create(ctx, in.date(), en, in.nameKm() == null || in.nameKm().isBlank() ? en : in.nameKm().strip());
        activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, h.getId(), ctx.userId(), "holiday.added",
                Map.of("name", h.getNameEn(), "date", h.getDate().toString()));
        return view(h);
    }

    @Transactional
    public void remove(String slug, UUID id) {
        OrgContext ctx = hr.requireManager(slug);
        Holiday h = holidays.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        holidays.delete(h);
        activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, h.getId(), ctx.userId(), "holiday.removed",
                Map.of("name", h.getNameEn(), "date", h.getDate().toString()));
    }

    /** Copies in Cambodia's official holidays of {@code year}; days already set are left as they are. */
    @Transactional
    public HolidayImportResult importCambodia(String slug, int year) {
        OrgContext ctx = hr.requireManager(slug);
        List<CambodiaHolidays.Day> official = CambodiaHolidays.of(year);
        if (official.isEmpty()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "year",
                    "This year's official holidays aren't built in yet; add them one by one");
        }
        Set<LocalDate> taken = between(ctx, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream()
                .map(Holiday::getDate).collect(Collectors.toSet());
        int added = 0;
        for (CambodiaHolidays.Day d : official) {
            if (!taken.contains(d.date())) {
                create(ctx, d.date(), d.nameEn(), d.nameKm());
                added++;
            }
        }
        activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, ctx.orgId(), ctx.userId(), "holiday.imported",
                Map.of("year", year, "added", added));
        return new HolidayImportResult(added, official.size() - added);
    }

    // ── For the rest of HR ───────────────────────────────────────────────────────────

    public List<Holiday> between(OrgContext ctx, LocalDate from, LocalDate to) {
        return holidays.findByOrgIdAndDateBetweenOrderByDate(ctx.orgId(), from, to);
    }

    public Set<LocalDate> dates(OrgContext ctx, LocalDate from, LocalDate to) {
        return between(ctx, from, to).stream().map(Holiday::getDate).collect(Collectors.toSet());
    }

    private Holiday create(OrgContext ctx, LocalDate date, String nameEn, String nameKm) {
        Holiday h = new Holiday();
        h.setId(Ids.next());
        h.setOrgId(ctx.orgId());
        h.setDate(date);
        h.setNameEn(nameEn);
        h.setNameKm(nameKm);
        h.setCreatedBy(ctx.userId());
        h.setCreatedAt(clock.instant());
        return holidays.save(h);
    }

    static HolidayView view(Holiday h) {
        return new HolidayView(h.getId(), h.getDate(), h.getNameEn(), h.getNameKm());
    }
}
