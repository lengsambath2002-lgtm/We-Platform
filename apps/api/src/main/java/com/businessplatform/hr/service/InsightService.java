package com.businessplatform.hr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
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

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.hr.domain.Candidate;
import com.businessplatform.hr.domain.EmployeeProfile;
import com.businessplatform.hr.domain.JobOpening;
import com.businessplatform.hr.domain.LeaveRequest;
import com.businessplatform.hr.domain.LeaveType;
import com.businessplatform.hr.domain.OnboardingItem;
import com.businessplatform.hr.domain.OvertimeRequest;
import com.businessplatform.hr.domain.Review;
import com.businessplatform.hr.domain.ReviewCycle;
import com.businessplatform.hr.domain.TrainingEnrolment;
import com.businessplatform.hr.payload.AttendancePayloads.BoardRow;
import com.businessplatform.hr.payload.AttendancePayloads.Timesheet;
import com.businessplatform.hr.payload.HrPayloads.PersonSummary;
import com.businessplatform.hr.payload.InsightPayloads.*;
import com.businessplatform.hr.payload.PayrollPayloads.RunSummary;
import com.businessplatform.hr.repository.DevelopmentRepositories.ReviewCycleRepository;
import com.businessplatform.hr.repository.DevelopmentRepositories.ReviewRepository;
import com.businessplatform.hr.repository.DevelopmentRepositories.TrainingEnrolmentRepository;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.hr.repository.LeaveRequestRepository;
import com.businessplatform.hr.repository.OvertimeRequestRepository;
import com.businessplatform.hr.repository.RecruitmentRepositories.CandidateRepository;
import com.businessplatform.hr.repository.RecruitmentRepositories.JobOpeningRepository;
import com.businessplatform.hr.repository.RecruitmentRepositories.OnboardingItemRepository;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * The HR dashboard (HR managers), the org chart (everyone in HR) and the HR reports as CSV (HR managers): the
 * employee list, a month's attendance per person and a year's leave taken per person and type.
 */
@Service
@RequiredArgsConstructor
public class InsightService {

    private final HrAccess hr;
    private final ProfileService profiles;
    private final TimekeepingService timekeeping;
    private final PayrollService payroll;
    private final LeaveTypeService leaveTypes;
    private final EmployeeProfileRepository profileRows;
    private final MembershipRepository memberships;
    private final LeaveRequestRepository leaves;
    private final OvertimeRequestRepository overtime;
    private final JobOpeningRepository jobs;
    private final CandidateRepository candidates;
    private final OnboardingItemRepository onboarding;
    private final ReviewCycleRepository cycles;
    private final ReviewRepository reviews;
    private final TrainingEnrolmentRepository enrolments;
    private final Clock clock;

    // Not read-only: the directory and the board may create missing profiles and seed defaults.
    @Transactional
    public Dashboard dashboard(String slug) {
        OrgContext ctx = hr.requireManager(slug);
        List<PersonSummary> people = profiles.directory(slug);
        LocalDate today = LeaveService.today(clock);
        Map<UUID, Membership> joinedAt = memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                .collect(Collectors.toMap(Membership::getUserId, Function.identity()));
        Map<UUID, LocalDate> starts = new HashMap<>();
        profileRows.findByOrgId(ctx.orgId()).forEach(p -> starts.put(p.getUserId(), p.getStartDate()));
        long joined = people.stream().filter(p -> {
            LocalDate start = starts.get(p.person().id());
            if (start == null && joinedAt.containsKey(p.person().id())) {
                start = LocalDate.ofInstant(joinedAt.get(p.person().id()).getCreatedAt(), CalendarService.ZONE);
            }
            return start != null && !start.isBefore(today.minusDays(90)) && !start.isAfter(today);
        }).count();
        Map<String, Long> byDept = people.stream().collect(Collectors.groupingBy(
                p -> p.department() == null ? "" : p.department(), Collectors.counting()));
        List<Count> departments = byDept.entrySet().stream().map(e -> new Count(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(Count::count).reversed().thenComparing(Count::label)).toList();

        List<BoardRow> board = timekeeping.board(slug, today);
        Map<String, Long> status = board.stream().collect(Collectors.groupingBy(r -> r.day().status(), Collectors.counting()));
        Today now = new Today(status.getOrDefault("PRESENT", 0L) + status.getOrDefault("LATE", 0L), status.getOrDefault("LATE", 0L),
                status.getOrDefault("ABSENT", 0L), status.getOrDefault("LEAVE", 0L), status.getOrDefault("NOT_IN", 0L),
                status.getOrDefault("HOLIDAY", 0L) + status.getOrDefault("DAY_OFF", 0L));

        List<JobOpening> open = jobs.findByOrgIdOrderByCreatedAtDesc(ctx.orgId()).stream()
                .filter(j -> JobOpening.OPEN.equals(j.getStatus())).toList();
        long active = candidates.findByOrgIdAndJobIdIn(ctx.orgId(), open.stream().map(JobOpening::getId).toList()).stream()
                .filter(c -> !c.closed()).count();
        List<Candidate> hires = candidates.findByOrgIdAndStageOrderByHiredAtDesc(ctx.orgId(), Candidate.HIRED);
        Map<UUID, List<OnboardingItem>> steps = onboarding.findByCandidateIdIn(hires.stream().map(Candidate::getId).toList())
                .stream().collect(Collectors.groupingBy(OnboardingItem::getCandidateId));
        long onboardingNow = hires.stream().filter(c -> steps.getOrDefault(c.getId(), List.of()).stream()
                .anyMatch(s -> s.getDoneAt() == null)).count();

        List<RunSummary> runs = payroll.runs(slug);
        PayrollSnapshot pay = runs.isEmpty() ? null : new PayrollSnapshot(runs.get(0).id(), runs.get(0).month(), runs.get(0).status(),
                runs.get(0).totals().net(), runs.get(0).totals().employerCost(), runs.get(0).totals().people());

        List<ReviewCycle> openCycles = cycles.findByOrgIdOrderByCreatedAtDesc(ctx.orgId()).stream()
                .filter(c -> ReviewCycle.OPEN.equals(c.getStatus())).toList();
        Map<UUID, List<Review>> byCycle = reviews.findByCycleIdIn(openCycles.stream().map(ReviewCycle::getId).toList()).stream()
                .collect(Collectors.groupingBy(Review::getCycleId));
        List<ReviewSnapshot> reviewSnaps = openCycles.stream().map(c -> {
            List<Review> rs = byCycle.getOrDefault(c.getId(), List.of());
            return new ReviewSnapshot(c.getId(), c.getName(), rs.size(), (int) rs.stream().filter(r -> r.getManagerSubmittedAt() != null).count());
        }).toList();

        int year = today.getYear();
        long trained = enrolments.countByOrgIdAndStatusAndCompletedAtGreaterThanEqual(ctx.orgId(), TrainingEnrolment.COMPLETED,
                LocalDate.of(year, 1, 1).atStartOfDay(CalendarService.ZONE).toInstant());

        return new Dashboard(people.size(), joined, departments, now, leaves.countByOrgIdAndStatus(ctx.orgId(), LeaveRequest.PENDING),
                overtime.countByOrgIdAndStatus(ctx.orgId(), OvertimeRequest.PENDING), new Hiring(open.size(), active, onboardingNow),
                pay, reviewSnaps, trained);
    }

    /** Everyone in HR, with their manager, for the org chart. */
    @Transactional
    public List<ChartNode> orgChart(String slug) {
        hr.require(slug);
        List<PersonSummary> people = profiles.directory(slug);
        Set<UUID> in = people.stream().map(p -> p.person().id()).collect(Collectors.toSet());
        return people.stream().map(p -> new ChartNode(p.person(), p.title(), p.department(),
                p.manager() != null && in.contains(p.manager().id()) ? p.manager().id() : null)).toList();
    }

    // ── Reports (CSV) ────────────────────────────────────────────────────────────────

    @Transactional
    public String peopleCsv(String slug) {
        OrgContext ctx = hr.requireManager(slug);
        Map<UUID, EmployeeProfile> byUser = profileRows.findByOrgId(ctx.orgId()).stream()
                .collect(Collectors.toMap(EmployeeProfile::getUserId, Function.identity()));
        StringBuilder out = new StringBuilder("Name,Email,Job title,Department,Manager,Employment,Start date,Work phone\n");
        for (PersonSummary p : profiles.directory(slug)) {
            var profile = byUser.get(p.person().id());
            row(out, p.person().name(), p.email(), p.title(), p.department(), p.manager() == null ? null : p.manager().name(),
                    profile == null ? null : profile.getEmploymentType(),
                    profile == null || profile.getStartDate() == null ? null : profile.getStartDate().toString(), p.workPhone());
        }
        return out.toString();
    }

    /** One line per person: the month's days present, late, absent and on leave, time worked and approved overtime. */
    @Transactional
    public String attendanceCsv(String slug, YearMonth month) {
        hr.requireManager(slug);
        StringBuilder out = new StringBuilder("Name,Department,Month,Days present,Days late,Days absent,Days on leave,Hours worked,Hours expected,Overtime hours\n");
        for (PersonSummary p : profiles.directory(slug)) {
            Timesheet t = timekeeping.timesheet(slug, p.person().id(), month);
            var x = t.totals();
            row(out, p.person().name(), p.department(), month.toString(), String.valueOf(x.presentDays()), String.valueOf(x.lateDays()),
                    String.valueOf(x.absentDays()), String.valueOf(x.leaveDays()), hours(x.workedMinutes()), hours(x.expectedMinutes()),
                    hours(x.overtimeMinutes()));
        }
        return out.toString();
    }

    /** One line per person and leave type: approved days taken that start in {@code year}. */
    @Transactional
    public String leaveCsv(String slug, Year year) {
        OrgContext ctx = hr.requireManager(slug);
        Map<UUID, LeaveType> types = leaveTypes.all(ctx).stream().collect(Collectors.toMap(LeaveType::getId, Function.identity()));
        Map<UUID, Map<UUID, BigDecimal>> taken = new HashMap<>();
        leaves.approvedBetween(ctx.orgId(), year.atDay(1), year.atMonth(12).atEndOfMonth()).stream()
                .filter(l -> l.getStartDate().getYear() == year.getValue())
                .forEach(l -> taken.computeIfAbsent(l.getUserId(), k -> new HashMap<>()).merge(l.getLeaveTypeId(), l.getDays(), BigDecimal::add));
        StringBuilder out = new StringBuilder("Name,Department,Year,Leave type,Paid,Days taken\n");
        for (PersonSummary p : profiles.directory(slug)) {
            Map<UUID, BigDecimal> mine = taken.getOrDefault(p.person().id(), Map.of());
            List<Map.Entry<UUID, BigDecimal>> lines = new ArrayList<>(mine.entrySet());
            lines.sort(Comparator.comparing(e -> types.get(e.getKey()).getSortOrder()));
            for (var e : lines) {
                LeaveType type = types.get(e.getKey());
                row(out, p.person().name(), p.department(), year.toString(), type.getNameEn(), type.isPaid() ? "Yes" : "No",
                        e.getValue().stripTrailingZeros().toPlainString());
            }
        }
        return out.toString();
    }

    private static String hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void row(StringBuilder out, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells[i]));
        }
        out.append('\n');
    }

    /** Quoted when needed; a leading = + - @ is neutralised so a spreadsheet won't run it as a formula. */
    static String cell(String v) {
        if (v == null) {
            return "";
        }
        String s = !v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 ? "'" + v : v;
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
