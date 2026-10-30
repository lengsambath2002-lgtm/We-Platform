package com.businessplatform.hr.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.calendar.CalendarService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.hr.domain.AttendanceRecord;
import com.businessplatform.hr.domain.EmployeeProfile;
import com.businessplatform.hr.domain.Holiday;
import com.businessplatform.hr.domain.LeaveRequest;
import com.businessplatform.hr.domain.OvertimeRequest;
import com.businessplatform.hr.domain.WorkSchedule;
import com.businessplatform.hr.payload.AttendancePayloads.AttendanceEdit;
import com.businessplatform.hr.payload.AttendancePayloads.BoardRow;
import com.businessplatform.hr.payload.AttendancePayloads.ClockRequest;
import com.businessplatform.hr.payload.AttendancePayloads.DayRow;
import com.businessplatform.hr.payload.AttendancePayloads.ScheduleView;
import com.businessplatform.hr.payload.AttendancePayloads.Timesheet;
import com.businessplatform.hr.payload.AttendancePayloads.Today;
import com.businessplatform.hr.payload.AttendancePayloads.Totals;
import com.businessplatform.hr.payload.HrPayloads.PersonRef;
import com.businessplatform.hr.repository.AttendanceRecordRepository;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.hr.repository.LeaveRequestRepository;
import com.businessplatform.hr.repository.OvertimeRequestRepository;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.RoleCatalogue;

import lombok.RequiredArgsConstructor;

/**
 * Attendance. Everyone clocks themselves in and out once a day (in the organisation's time zone). A day's
 * status comes from the person's schedule, public holidays and approved leave: present, late (after the
 * start plus the grace minutes), absent (a past working day with no record), on leave, a holiday or a day
 * off. The person, their manager and HR managers see a timesheet; HR managers see everyone's day and
 * correct records, always with a reason, which goes to the audit log.
 */
@Service
@RequiredArgsConstructor
public class TimekeepingService {

    /** How attendance records appear in the activity log. */
    public static final String RECORD = "attendance";
    /** A day this long or longer has its break taken off the worked time. */
    static final int BREAK_AFTER_MINUTES = 360;

    private final HrAccess hr;
    private final ProfileService profiles;
    private final EmployeeProfileRepository profileRows;
    private final ScheduleService schedules;
    private final HolidayService holidays;
    private final AttendanceRecordRepository records;
    private final LeaveRequestRepository leaves;
    private final OvertimeRequestRepository overtime;
    private final MembershipRepository memberships;
    private final ActivityService activity;
    private final Clock clock;

    // ── Me ───────────────────────────────────────────────────────────────────────────

    @Transactional
    public Today today(String slug) {
        OrgContext ctx = hr.require(slug);
        return today(ctx);
    }

    @Transactional
    public Today clockIn(String slug, ClockRequest in) {
        OrgContext ctx = hr.require(slug);
        profiles.profile(ctx, ctx.userId());
        // One clock-in a day, even when two taps arrive together.
        profileRows.lock(ctx.orgId(), ctx.userId());
        LocalDate day = LeaveService.today(clock);
        if (records.findByOrgIdAndUserIdAndWorkDate(ctx.orgId(), ctx.userId(), day).isPresent()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "You have clocked in today already", List.of());
        }
        AttendanceRecord r = new AttendanceRecord();
        r.setId(Ids.next());
        r.setOrgId(ctx.orgId());
        r.setUserId(ctx.userId());
        r.setWorkDate(day);
        r.setClockIn(clock.instant());
        r.setNote(ProfileService.clean(in == null ? null : in.note()));
        r.setCreatedAt(clock.instant());
        r.setUpdatedAt(r.getCreatedAt());
        records.saveAndFlush(r);
        return today(ctx);
    }

    @Transactional
    public Today clockOut(String slug, ClockRequest in) {
        OrgContext ctx = hr.require(slug);
        AttendanceRecord r = records.findByOrgIdAndUserIdAndWorkDate(ctx.orgId(), ctx.userId(), LeaveService.today(clock))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "Clock in first", List.of()));
        if (r.getClockOut() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "You have clocked out today already", List.of());
        }
        r.setClockOut(clock.instant());
        String note = ProfileService.clean(in == null ? null : in.note());
        if (note != null) {
            r.setNote(r.getNote() == null ? note : abbreviate(r.getNote() + " · " + note));
        }
        r.setUpdatedAt(clock.instant());
        records.saveAndFlush(r);
        return today(ctx);
    }

    // ── Timesheets and the board ─────────────────────────────────────────────────────

    /** One person's month: themselves, their manager or an HR manager; {@code userId} null = mine. */
    @Transactional
    public Timesheet timesheet(String slug, UUID userId, YearMonth month) {
        OrgContext ctx = hr.require(slug);
        UUID who = userId == null ? ctx.userId() : userId;
        if (!mayView(ctx, who)) {
            throw BusinessException.forbidden();
        }
        EmployeeProfile p = profiles.profile(ctx, who);
        WorkSchedule s = scheduleOf(ctx, p);
        List<DayRow> days = days(ctx, p, s, month.atDay(1), month.atEndOfMonth());
        return new Timesheet(profiles.persons(List.of(who)).get(who), schedules.view(ctx, s), month.toString(), days,
                totals(days, s), HrAccess.managesPerson(ctx, who));
    }

    /** HR managers: everyone's {@code date} (default today), by name; a unit's HR manager sees their units' people. */
    @Transactional
    public List<BoardRow> board(String slug, LocalDate date) {
        OrgContext ctx = hr.requireTeam(slug);
        Set<UUID> scope = ctx.scopeOf(RoleCatalogue.HR);
        LocalDate day = date == null ? LeaveService.today(clock) : date;
        profileRows.createMissing(ctx.orgId());
        Set<UUID> active = memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST)
                .map(Membership::getUserId).collect(Collectors.toSet());
        List<EmployeeProfile> people = profileRows.findByOrgId(ctx.orgId()).stream()
                .filter(p -> active.contains(p.getUserId()) && (scope == null || scope.contains(p.getUserId()))).toList();
        Map<UUID, WorkSchedule> scheduleOf = schedules.forPeople(ctx, people);
        Map<UUID, PersonRef> refs = profiles.persons(people.stream().map(EmployeeProfile::getUserId).toList());
        Inputs inputs = inputs(ctx, null, day, day);
        Map<UUID, ScheduleView> views = new HashMap<>();
        return people.stream().map(p -> {
            WorkSchedule s = scheduleOf.get(p.getUserId());
            ScheduleView view = views.computeIfAbsent(s.getId(), k -> schedules.view(ctx, s));
            return new BoardRow(refs.get(p.getUserId()), p.getDepartment(), view,
                    row(day, p, s, inputs, joined(ctx, p)));
        }).sorted(Comparator.comparing(r -> r.person().name(), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /**
     * HR managers: sets one person's clock-in and clock-out on a day (not in the future), or removes the day's
     * record when {@code clockIn} is empty. The reason is kept on the record and in the audit log.
     */
    @Transactional
    public DayRow correct(String slug, UUID userId, LocalDate date, AttendanceEdit in) {
        OrgContext ctx = hr.requireTeam(slug);
        if (!HrAccess.managesPerson(ctx, userId)) {
            throw BusinessException.forbidden();
        }
        EmployeeProfile p = profiles.profile(ctx, userId);
        if (date.isAfter(LeaveService.today(clock))) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "date", "This day hasn't happened yet");
        }
        String reason = in.reason().strip();
        var existing = records.findByOrgIdAndUserIdAndWorkDate(ctx.orgId(), userId, date);
        Map<String, Object> data = new HashMap<>(Map.of("date", date.toString(), "reason", reason));
        if (in.clockIn() == null) {
            existing.ifPresent(r -> {
                records.delete(r);
                activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, r.getId(), ctx.userId(), "attendance.removed", data);
            });
        } else {
            if (in.clockOut() != null && !in.clockOut().isAfter(in.clockIn())) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "clockOut", "Clock-out is before clock-in");
            }
            AttendanceRecord r = existing.orElseGet(() -> {
                AttendanceRecord n = new AttendanceRecord();
                n.setId(Ids.next());
                n.setOrgId(ctx.orgId());
                n.setUserId(userId);
                n.setWorkDate(date);
                n.setCreatedAt(clock.instant());
                return n;
            });
            r.setClockIn(at(date, in.clockIn()));
            r.setClockOut(in.clockOut() == null ? null : at(date, in.clockOut()));
            r.setNote(ProfileService.clean(in.note()));
            r.setEditedBy(ctx.userId());
            r.setEditReason(reason);
            r.setUpdatedAt(clock.instant());
            records.saveAndFlush(r);
            data.put("in", in.clockIn().toString());
            if (in.clockOut() != null) {
                data.put("out", in.clockOut().toString());
            }
            activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, r.getId(), ctx.userId(), "attendance.corrected", data);
        }
        WorkSchedule s = scheduleOf(ctx, p);
        return days(ctx, p, s, date, date).get(0);
    }

    // ── Days ─────────────────────────────────────────────────────────────────────────

    /** The person, their manager, or an HR manager. */
    boolean mayView(OrgContext ctx, UUID who) {
        return who.equals(ctx.userId()) || HrAccess.managesPerson(ctx, who)
                || profiles.managerOf(ctx.orgId(), who).map(ctx.userId()::equals).orElse(false);
    }

    private Today today(OrgContext ctx) {
        EmployeeProfile p = profiles.profile(ctx, ctx.userId());
        WorkSchedule s = scheduleOf(ctx, p);
        LocalDate day = LeaveService.today(clock);
        DayRow row = days(ctx, p, s, day, day).get(0);
        return new Today(row, schedules.view(ctx, s), row.recordId() == null, row.recordId() != null && row.clockOut() == null);
    }

    private WorkSchedule scheduleOf(OrgContext ctx, EmployeeProfile p) {
        return schedules.forPeople(ctx, List.of(p)).get(p.getUserId());
    }

    /** What decides a day: records, approved leave and overtime, holidays (for one person, or all when null). */
    private record Inputs(Map<String, AttendanceRecord> records, List<LeaveRequest> leave, Map<LocalDate, Holiday> holidays,
            Map<String, Integer> overtime, LocalDate today) {

        static String key(UUID user, LocalDate day) {
            return user + "|" + day;
        }
    }

    private Inputs inputs(OrgContext ctx, UUID userId, LocalDate from, LocalDate to) {
        List<AttendanceRecord> rs = userId == null
                ? (from.equals(to) ? records.findByOrgIdAndWorkDate(ctx.orgId(), from) : List.of())
                : records.findByOrgIdAndUserIdAndWorkDateBetweenOrderByWorkDate(ctx.orgId(), userId, from, to);
        List<LeaveRequest> ls = leaves.approvedBetween(ctx.orgId(), from, to).stream()
                .filter(l -> userId == null || l.getUserId().equals(userId)).toList();
        List<OvertimeRequest> os = userId == null ? List.of()
                : overtime.findByOrgIdAndUserIdAndWorkDateBetween(ctx.orgId(), userId, from, to);
        return new Inputs(
                rs.stream().collect(Collectors.toMap(r -> Inputs.key(r.getUserId(), r.getWorkDate()), Function.identity())),
                ls,
                holidays.between(ctx, from, to).stream().collect(Collectors.toMap(Holiday::getDate, Function.identity(), (a, b) -> a)),
                os.stream().filter(o -> OvertimeRequest.APPROVED.equals(o.getStatus()))
                        .collect(Collectors.toMap(o -> Inputs.key(o.getUserId(), o.getWorkDate()), o -> (int) o.getMinutes(), Integer::sum)),
                LeaveService.today(clock));
    }

    private List<DayRow> days(OrgContext ctx, EmployeeProfile p, WorkSchedule s, LocalDate from, LocalDate to) {
        Inputs inputs = inputs(ctx, p.getUserId(), from, to);
        LocalDate joined = joined(ctx, p);
        List<DayRow> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            out.add(row(d, p, s, inputs, joined));
        }
        return out;
    }

    /** The first day they count as working here: their start date, else when they joined the organisation. */
    private LocalDate joined(OrgContext ctx, EmployeeProfile p) {
        if (p.getStartDate() != null) {
            return p.getStartDate();
        }
        return memberships.findByOrgIdAndUserId(ctx.orgId(), p.getUserId())
                .map(m -> LocalDate.ofInstant(m.getCreatedAt(), CalendarService.ZONE)).orElse(LocalDate.MIN);
    }

    private DayRow row(LocalDate d, EmployeeProfile p, WorkSchedule s, Inputs in, LocalDate joined) {
        AttendanceRecord r = in.records().get(Inputs.key(p.getUserId(), d));
        Holiday h = in.holidays().get(d);
        boolean working = s.worksOn(d.getDayOfWeek()) && h == null;
        boolean onLeave = in.leave().stream().anyMatch(l -> l.getUserId().equals(p.getUserId())
                && !d.isBefore(l.getStartDate()) && !d.isAfter(l.getEndDate()));
        int ot = in.overtime().getOrDefault(Inputs.key(p.getUserId(), d), 0);
        String status;
        int late = 0;
        int worked = 0;
        if (r != null) {
            LocalTime start = LocalTime.ofInstant(r.getClockIn(), CalendarService.ZONE);
            long after = Duration.between(s.getStartTime(), start).toMinutes();
            late = working && after > s.getGraceMinutes() ? (int) after : 0;
            status = late > 0 ? "LATE" : "PRESENT";
            if (r.getClockOut() != null) {
                long span = Duration.between(r.getClockIn(), r.getClockOut()).toMinutes();
                worked = (int) Math.max(0, span >= BREAK_AFTER_MINUTES ? span - s.getBreakMinutes() : span);
            }
        } else if (d.isBefore(joined)) {
            status = "DAY_OFF";
        } else if (h != null) {
            status = "HOLIDAY";
        } else if (!working) {
            status = "DAY_OFF";
        } else if (onLeave) {
            status = "LEAVE";
        } else if (d.isAfter(in.today())) {
            status = "UPCOMING";
        } else if (d.equals(in.today())) {
            status = "NOT_IN";
        } else {
            status = "ABSENT";
        }
        return new DayRow(d, status, r == null ? null : r.getId(), r == null ? null : r.getClockIn(),
                r == null ? null : r.getClockOut(), late, worked, ot, r == null ? null : r.getNote(),
                r != null && r.getEditedBy() != null, r == null ? null : r.getEditReason(),
                h == null ? null : h.getNameEn(), h == null ? null : h.getNameKm());
    }

    private Totals totals(List<DayRow> days, WorkSchedule s) {
        int present = 0, late = 0, absent = 0, leave = 0, worked = 0, expected = 0, ot = 0;
        for (DayRow d : days) {
            switch (d.status()) {
                case "PRESENT" -> present++;
                case "LATE" -> { present++; late++; }
                case "ABSENT" -> absent++;
                case "LEAVE" -> leave++;
                default -> { }
            }
            boolean counts = switch (d.status()) {
                case "ABSENT", "NOT_IN" -> true;
                case "PRESENT", "LATE" -> s.worksOn(d.date().getDayOfWeek()) && d.holidayEn() == null;
                default -> false;
            };
            if (counts) {
                expected += s.dayMinutes();
            }
            worked += d.workedMinutes();
            ot += d.overtimeMinutes();
        }
        return new Totals(present, late, absent, leave, worked, expected, ot);
    }

    private static Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(CalendarService.ZONE).toInstant();
    }

    private static String abbreviate(String s) {
        return s.length() <= 300 ? s : s.substring(0, 300);
    }
}
