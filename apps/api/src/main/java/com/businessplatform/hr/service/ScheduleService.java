package com.businessplatform.hr.service;

import java.time.Clock;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.hr.domain.EmployeeProfile;
import com.businessplatform.hr.domain.WorkSchedule;
import com.businessplatform.hr.payload.AttendancePayloads.AssignScheduleRequest;
import com.businessplatform.hr.payload.AttendancePayloads.ScheduleRequest;
import com.businessplatform.hr.payload.AttendancePayloads.ScheduleView;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.hr.repository.WorkScheduleRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Work schedules. Everyone in HR sees them; HR managers create and change them, pick the default and give
 * people their own. An organisation starts with "Office hours" (08:00–17:00, an hour's break, Monday to
 * Friday) as its default the first time attendance is used.
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    /** How schedules appear in the activity log. */
    public static final String RECORD = "schedule";

    private final HrAccess hr;
    private final WorkScheduleRepository schedules;
    private final EmployeeProfileRepository profileRows;
    private final ProfileService profiles;
    private final ActivityService activity;
    private final Clock clock;

    // Not read-only: the first use seeds the default schedule.
    @Transactional
    public List<ScheduleView> list(String slug) {
        OrgContext ctx = hr.require(slug);
        return views(ctx, all(ctx));
    }

    @Transactional
    public ScheduleView save(String slug, UUID id, ScheduleRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        all(ctx);
        WorkSchedule s = id == null ? new WorkSchedule() : find(ctx, id);
        String name = in.name().strip();
        if ((id == null || !s.getName().equalsIgnoreCase(name)) && schedules.existsByOrgIdAndNameIgnoreCase(ctx.orgId(), name)) {
            throw BusinessException.field(ErrorCode.CONFLICT, "name", "Another schedule has this name");
        }
        if (!in.endTime().isAfter(in.startTime())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endTime", "The day ends before it starts");
        }
        long span = java.time.Duration.between(in.startTime(), in.endTime()).toMinutes();
        if (in.breakMinutes() >= span) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "breakMinutes", "The break is longer than the day");
        }
        if (id != null && s.isDefaultSchedule() && !in.makeDefault()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "makeDefault", "Make another schedule the default instead");
        }
        if (id == null) {
            s.setId(Ids.next());
            s.setOrgId(ctx.orgId());
            s.setCreatedAt(clock.instant());
        }
        s.setName(name);
        s.setStartTime(in.startTime());
        s.setEndTime(in.endTime());
        s.setBreakMinutes((short) in.breakMinutes());
        s.setGraceMinutes((short) in.graceMinutes());
        s.setWorkDays(in.workDays().stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(",")));
        s.setUpdatedAt(clock.instant());
        if (in.makeDefault() && !s.isDefaultSchedule()) {
            schedules.findByOrgIdAndDefaultScheduleTrue(ctx.orgId()).ifPresent(d -> {
                d.setDefaultSchedule(false);
                schedules.saveAndFlush(d);
            });
            s.setDefaultSchedule(true);
        }
        s = schedules.save(s);
        activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, s.getId(), ctx.userId(),
                id == null ? "schedule.created" : "schedule.updated", Map.of("name", s.getName()));
        return view(ctx, s);
    }

    /** Removes a schedule nobody has as the default; its people fall back to the default. */
    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = hr.requireManager(slug);
        WorkSchedule s = find(ctx, id);
        if (s.isDefaultSchedule()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Make another schedule the default first", List.of());
        }
        profileRows.findByOrgId(ctx.orgId()).stream().filter(p -> id.equals(p.getScheduleId()))
                .forEach(p -> p.setScheduleId(null));
        activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, s.getId(), ctx.userId(), "schedule.deleted",
                Map.of("name", s.getName()));
        schedules.delete(s);
    }

    /** Gives a person their own schedule, or (null) puts them back on the default. */
    @Transactional
    public ScheduleView assign(String slug, UUID userId, AssignScheduleRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        EmployeeProfile p = profiles.profile(ctx, userId);
        WorkSchedule s = in.scheduleId() == null ? defaultFor(ctx) : find(ctx, in.scheduleId());
        if (!Objects.equals(p.getScheduleId(), in.scheduleId())) {
            p.setScheduleId(in.scheduleId());
            activity.record(ctx.orgId(), HrAccess.MODULE, ProfileService.RECORD, p.getId(), ctx.userId(),
                    "employee.schedule", Map.of("schedule", s.getName()));
        }
        return view(ctx, s);
    }

    // ── For the rest of HR ───────────────────────────────────────────────────────────

    /** The organisation's schedules, seeding "Office hours" the first time. */
    @Transactional
    public List<WorkSchedule> all(OrgContext ctx) {
        if (schedules.countByOrgId(ctx.orgId()) == 0) {
            WorkSchedule s = new WorkSchedule();
            s.setId(Ids.next());
            s.setOrgId(ctx.orgId());
            s.setName("Office hours");
            s.setStartTime(LocalTime.of(8, 0));
            s.setEndTime(LocalTime.of(17, 0));
            s.setBreakMinutes((short) 60);
            s.setGraceMinutes((short) 10);
            s.setWorkDays("1,2,3,4,5");
            s.setDefaultSchedule(true);
            s.setCreatedAt(clock.instant());
            s.setUpdatedAt(s.getCreatedAt());
            schedules.saveAndFlush(s);
        }
        return schedules.findByOrgIdOrderByName(ctx.orgId());
    }

    @Transactional
    public WorkSchedule defaultFor(OrgContext ctx) {
        List<WorkSchedule> all = all(ctx);
        return all.stream().filter(WorkSchedule::isDefaultSchedule).findFirst().orElse(all.get(0));
    }

    /** Each person's schedule: their own, else the default. */
    @Transactional
    public Map<UUID, WorkSchedule> forPeople(OrgContext ctx, List<EmployeeProfile> people) {
        Map<UUID, WorkSchedule> byId = all(ctx).stream().collect(Collectors.toMap(WorkSchedule::getId, Function.identity()));
        WorkSchedule fallback = defaultFor(ctx);
        return people.stream().collect(Collectors.toMap(EmployeeProfile::getUserId,
                p -> p.getScheduleId() == null ? fallback : byId.getOrDefault(p.getScheduleId(), fallback)));
    }

    WorkSchedule find(OrgContext ctx, UUID id) {
        return schedules.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    public ScheduleView view(OrgContext ctx, WorkSchedule s) {
        return views(ctx, List.of(s)).get(0);
    }

    private List<ScheduleView> views(OrgContext ctx, List<WorkSchedule> found) {
        Map<UUID, Long> counts = profileRows.findByOrgId(ctx.orgId()).stream().filter(p -> p.getScheduleId() != null)
                .collect(Collectors.groupingBy(EmployeeProfile::getScheduleId, Collectors.counting()));
        return found.stream().map(s -> new ScheduleView(s.getId(), s.getName(), s.getStartTime(), s.getEndTime(),
                s.getBreakMinutes(), s.getGraceMinutes(), Arrays.stream(s.getWorkDays().split(",")).map(Integer::valueOf).toList(),
                s.isDefaultSchedule(), s.dayMinutes(), counts.getOrDefault(s.getId(), 0L))).toList();
    }
}
