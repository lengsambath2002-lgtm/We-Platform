package com.businessplatform.hr.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Messages;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.hr.domain.EmployeePay;
import com.businessplatform.hr.domain.EmployeeProfile;
import com.businessplatform.hr.domain.PayChange;
import com.businessplatform.hr.domain.LeaveRequest;
import com.businessplatform.hr.domain.LeaveType;
import com.businessplatform.hr.domain.OvertimeRequest;
import com.businessplatform.hr.domain.PayRun;
import com.businessplatform.hr.domain.PayrollSettings;
import com.businessplatform.hr.domain.Payslip;
import com.businessplatform.hr.domain.WorkSchedule;
import com.businessplatform.hr.payload.HrPayloads.PersonRef;
import com.businessplatform.hr.payload.PayrollPayloads.*;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.hr.repository.LeaveRequestRepository;
import com.businessplatform.hr.repository.OvertimeRequestRepository;
import com.businessplatform.hr.repository.PayrollRepositories.EmployeePayRepository;
import com.businessplatform.hr.repository.PayrollRepositories.PayRunRepository;
import com.businessplatform.hr.repository.PayrollRepositories.PayrollSettingsRepository;
import com.businessplatform.hr.repository.PayrollRepositories.PayslipRepository;
import com.businessplatform.hr.repository.RecordRepositories.PayChanges;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.mail.MailService;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Payroll, for HR managers (each person also reads their own paid payslips). A month's run is prepared as a
 * DRAFT for everyone with a salary set up: salary, approved unpaid leave, approved overtime, HR's adjustments,
 * NSSF and Tax on Salary (see {@link PayrollCalculator}). A draft can be recalculated as often as needed;
 * approving locks it; marking it paid shows the payslips to their people, with a notification and an email.
 */
@Service
@RequiredArgsConstructor
public class PayrollService {

    /** How pay runs and pay set-ups appear in the activity log. */
    public static final String RUN = "payrun";
    public static final String PAY = "pay";

    private final HrAccess hr;
    private final ProfileService profiles;
    private final ScheduleService schedules;
    private final HolidayService holidays;
    private final LeaveTypeService leaveTypes;
    private final PayrollSettingsRepository settingsRows;
    private final EmployeePayRepository pays;
    private final PayRunRepository runs;
    private final PayslipRepository slips;
    private final EmployeeProfileRepository profileRows;
    private final LeaveRequestRepository leaves;
    private final OvertimeRequestRepository overtime;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final MailService mail;
    private final Messages messages;
    private final Environment env;
    private final PayChanges payChanges;
    private final Clock clock;

    // ── Settings ─────────────────────────────────────────────────────────────────────

    @Transactional
    public SettingsView settings(String slug) {
        return view(settings(hr.requirePayroll(slug)));
    }

    @Transactional
    public SettingsView updateSettings(String slug, SettingsRequest in) {
        OrgContext ctx = hr.requirePayroll(slug);
        List<BracketInput> b = in.taxBrackets();
        for (int i = 0; i < b.size(); i++) {
            boolean last = i == b.size() - 1;
            if (last != (b.get(i).upToKhr() == null)) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "taxBrackets", "Only the last bracket has no upper limit");
            }
            if (i > 0 && !last && b.get(i).upToKhr() <= b.get(i - 1).upToKhr()) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "taxBrackets", "Each bracket must end above the one before");
            }
        }
        if (in.nssfCapKhr() < in.nssfFloorKhr()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "nssfCapKhr", "The cap is below the floor");
        }
        PayrollSettings s = settings(ctx);
        s.setTaxBrackets(b.stream().map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("upToKhr", x.upToKhr());
            m.put("ratePct", x.ratePct());
            return m;
        }).toList());
        s.setDependantAllowanceKhr(in.dependantAllowanceKhr());
        s.setNssfFloorKhr(in.nssfFloorKhr());
        s.setNssfCapKhr(in.nssfCapKhr());
        s.setNssfPensionEmployeePct(in.nssfPensionEmployeePct());
        s.setNssfPensionEmployerPct(in.nssfPensionEmployerPct());
        s.setNssfHealthEmployerPct(in.nssfHealthEmployerPct());
        s.setNssfRiskEmployerPct(in.nssfRiskEmployerPct());
        s.setOvertimeRatePct(in.overtimeRatePct());
        s.setUpdatedBy(ctx.userId());
        s.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), HrAccess.MODULE, RUN, ctx.orgId(), ctx.userId(), "payroll.settings.updated", Map.of());
        return view(s);
    }

    /**
     * The organisation's settings, created with Cambodia's rates when this was written: Tax on Salary 0 % to
     * 1,500,000 ៛, 5 % to 2,000,000, 10 % to 8,500,000, 15 % to 12,500,000, then 20 %; 150,000 ៛ per dependant;
     * NSSF on 400,000–1,200,000 ៛: pension 2 % + 2 %, health care 2.6 % and occupational risk 0.8 % (employer);
     * overtime 150 %.
     */
    @Transactional
    PayrollSettings settings(OrgContext ctx) {
        return settingsRows.findById(ctx.orgId()).orElseGet(() -> {
            PayrollSettings s = new PayrollSettings();
            s.setOrgId(ctx.orgId());
            s.setTaxBrackets(List.of(bracket(1_500_000L, "0"), bracket(2_000_000L, "5"), bracket(8_500_000L, "10"),
                    bracket(12_500_000L, "15"), bracket(null, "20")));
            s.setDependantAllowanceKhr(150_000);
            s.setNssfFloorKhr(400_000);
            s.setNssfCapKhr(1_200_000);
            s.setNssfPensionEmployeePct(new BigDecimal("2.00"));
            s.setNssfPensionEmployerPct(new BigDecimal("2.00"));
            s.setNssfHealthEmployerPct(new BigDecimal("2.60"));
            s.setNssfRiskEmployerPct(new BigDecimal("0.80"));
            s.setOvertimeRatePct(new BigDecimal("150.0"));
            s.setUpdatedAt(clock.instant());
            return settingsRows.saveAndFlush(s);
        });
    }

    private static Map<String, Object> bracket(Long upTo, String rate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("upToKhr", upTo);
        m.put("ratePct", new BigDecimal(rate));
        return m;
    }

    private static List<BracketInput> brackets(PayrollSettings s) {
        return s.getTaxBrackets().stream().map(m -> new BracketInput(
                m.get("upToKhr") == null ? null : ((Number) m.get("upToKhr")).longValue(),
                new BigDecimal(m.get("ratePct").toString()))).toList();
    }

    private static SettingsView view(PayrollSettings s) {
        return new SettingsView(brackets(s), s.getDependantAllowanceKhr(), s.getNssfFloorKhr(), s.getNssfCapKhr(),
                s.getNssfPensionEmployeePct(), s.getNssfPensionEmployerPct(), s.getNssfHealthEmployerPct(),
                s.getNssfRiskEmployerPct(), s.getOvertimeRatePct(), s.getUpdatedAt());
    }

    // ── Pay set-up ───────────────────────────────────────────────────────────────────

    /** Everyone active (not guests) with what they are paid, by name. */
    @Transactional
    public List<PayView> people(String slug) {
        OrgContext ctx = hr.requirePayroll(slug);
        List<EmployeeProfile> people = activePeople(ctx);
        Map<UUID, EmployeePay> byUser = pays.findByOrgId(ctx.orgId()).stream()
                .collect(Collectors.toMap(EmployeePay::getUserId, Function.identity()));
        Map<UUID, PersonRef> refs = profiles.persons(people.stream().map(EmployeeProfile::getUserId).toList());
        return people.stream().map(p -> payView(refs.get(p.getUserId()), p, byUser.get(p.getUserId())))
                .sorted(Comparator.comparing(v -> v.person().name(), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional
    public PayView setPay(String slug, UUID userId, PayRequest in) {
        OrgContext ctx = hr.requirePayroll(slug);
        EmployeeProfile p = profiles.profile(ctx, userId);
        EmployeePay pay = pays.findByOrgIdAndUserId(ctx.orgId(), userId).orElseGet(() -> {
            EmployeePay n = new EmployeePay();
            n.setId(Ids.next());
            n.setOrgId(ctx.orgId());
            n.setUserId(userId);
            return n;
        });
        BigDecimal before = pay.getMonthlySalary();
        pay.setMonthlySalary(PayrollCalculator.money(in.monthlySalary()));
        pay.setDependants((short) in.dependants());
        pay.setNssfEnrolled(in.nssfEnrolled());
        pay.setBankName(ProfileService.clean(in.bankName()));
        pay.setBankAccount(ProfileService.clean(in.bankAccount()));
        pay.setUpdatedBy(ctx.userId());
        pay.setUpdatedAt(clock.instant());
        pays.save(pay);
        if (before == null || before.compareTo(pay.getMonthlySalary()) != 0) {
            PayChange c = new PayChange();
            c.setId(Ids.next());
            c.setOrgId(ctx.orgId());
            c.setUserId(userId);
            c.setMonthlySalary(pay.getMonthlySalary());
            c.setPrevious(before);
            c.setEffectiveOn(LeaveService.today(clock));
            c.setReason(ProfileService.clean(in.reason()));
            c.setChangedBy(ctx.userId());
            c.setCreatedAt(clock.instant());
            payChanges.save(c);
        }
        // The amount itself stays out of the audit log, which more people than payroll may read.
        activity.record(ctx.orgId(), HrAccess.MODULE, ProfileService.RECORD, p.getId(), ctx.userId(),
                before == null ? "employee.pay.set" : "employee.pay.changed", Map.of());
        return payView(profiles.persons(List.of(userId)).get(userId), p, pay);
    }

    private static PayView payView(PersonRef person, EmployeeProfile p, EmployeePay pay) {
        return pay == null ? new PayView(person, p.getDepartment(), null, 0, true, null, null)
                : new PayView(person, p.getDepartment(), pay.getMonthlySalary(), pay.getDependants(), pay.isNssfEnrolled(),
                        pay.getBankName(), pay.getBankAccount());
    }

    // ── Pay runs ─────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RunSummary> runs(String slug) {
        OrgContext ctx = hr.requirePayroll(slug);
        return runs.findByOrgIdOrderByMonthDesc(ctx.orgId()).stream().map(r -> summary(r, slips.findByPayRunId(r.getId()))).toList();
    }

    /** Prepares a month's DRAFT for everyone with a salary set up. */
    @Transactional
    public RunDetail create(String slug, RunRequest in) {
        OrgContext ctx = hr.requirePayroll(slug);
        if (runs.existsByOrgIdAndMonth(ctx.orgId(), in.month())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "month", "There is already a pay run for this month");
        }
        PayRun r = new PayRun();
        r.setId(Ids.next());
        r.setOrgId(ctx.orgId());
        r.setMonth(in.month());
        r.setExchangeRate(in.exchangeRate());
        r.setNote(ProfileService.clean(in.note()));
        r.setStatus(PayRun.DRAFT);
        r.setCreatedBy(ctx.userId());
        r.setCreatedAt(clock.instant());
        r.setUpdatedAt(r.getCreatedAt());
        runs.saveAndFlush(r);
        calculate(ctx, r);
        activity.record(ctx.orgId(), HrAccess.MODULE, RUN, r.getId(), ctx.userId(), "payroll.run.created", Map.of("month", r.getMonth()));
        return detail(ctx, r);
    }

    // Not read-only: listing who isn't set up may create missing employee profiles.
    @Transactional
    public RunDetail get(String slug, UUID id) {
        OrgContext ctx = hr.requirePayroll(slug);
        return detail(ctx, run(ctx, id));
    }

    /** A DRAFT's exchange rate and note; everything is recalculated. */
    @Transactional
    public RunDetail update(String slug, UUID id, RunUpdate in) {
        OrgContext ctx = hr.requirePayroll(slug);
        PayRun r = draft(ctx, id);
        r.setExchangeRate(in.exchangeRate());
        r.setNote(ProfileService.clean(in.note()));
        r.setUpdatedAt(clock.instant());
        calculate(ctx, r);
        return detail(ctx, r);
    }

    /** Recalculates a DRAFT from today's salaries, leave and overtime (HR's adjustments are kept). */
    @Transactional
    public RunDetail recalculate(String slug, UUID id) {
        OrgContext ctx = hr.requirePayroll(slug);
        PayRun r = draft(ctx, id);
        calculate(ctx, r);
        return detail(ctx, r);
    }

    @Transactional
    public RunDetail setAdjustments(String slug, UUID payslipId, AdjustmentsRequest in) {
        OrgContext ctx = hr.requirePayroll(slug);
        Payslip p = slips.findByIdAndOrgId(payslipId, ctx.orgId()).orElseThrow(BusinessException::notFound);
        PayRun r = draft(ctx, p.getPayRunId());
        p.setAdjustments(in.adjustments().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", a.label().strip());
            m.put("amount", PayrollCalculator.money(a.amount()));
            return m;
        }).toList());
        calculate(ctx, r);
        return detail(ctx, r);
    }

    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = hr.requirePayroll(slug);
        PayRun r = draft(ctx, id);
        activity.record(ctx.orgId(), HrAccess.MODULE, RUN, r.getId(), ctx.userId(), "payroll.run.deleted", Map.of("month", r.getMonth()));
        runs.delete(r);
    }

    /** Locks a DRAFT; nothing changes after this. */
    @Transactional
    public RunDetail approve(String slug, UUID id) {
        OrgContext ctx = hr.requirePayroll(slug);
        PayRun r = draft(ctx, id);
        if (slips.findByPayRunId(r.getId()).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Set up someone's salary before approving", List.of());
        }
        r.setStatus(PayRun.APPROVED);
        r.setApprovedBy(ctx.userId());
        r.setApprovedAt(clock.instant());
        r.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), HrAccess.MODULE, RUN, r.getId(), ctx.userId(), "payroll.run.approved", Map.of("month", r.getMonth()));
        return detail(ctx, r);
    }

    /** Back to DRAFT, while not paid yet. */
    @Transactional
    public RunDetail reopen(String slug, UUID id) {
        OrgContext ctx = hr.requirePayroll(slug);
        PayRun r = run(ctx, id);
        if (!PayRun.APPROVED.equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only an approved run that isn't paid can be reopened", List.of());
        }
        r.setStatus(PayRun.DRAFT);
        r.setApprovedBy(null);
        r.setApprovedAt(null);
        r.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), HrAccess.MODULE, RUN, r.getId(), ctx.userId(), "payroll.run.reopened", Map.of("month", r.getMonth()));
        return detail(ctx, r);
    }

    /** Records the payment: everyone in the run sees their payslip and gets a notification and an email. */
    @Transactional
    public RunDetail markPaid(String slug, UUID id) {
        OrgContext ctx = hr.requirePayroll(slug);
        PayRun r = run(ctx, id);
        if (!PayRun.APPROVED.equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Approve the run first", List.of());
        }
        r.setStatus(PayRun.PAID);
        r.setPaidBy(ctx.userId());
        r.setPaidAt(clock.instant());
        r.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), HrAccess.MODULE, RUN, r.getId(), ctx.userId(), "payroll.run.paid", Map.of("month", r.getMonth()));
        List<Payslip> found = slips.findByPayRunId(r.getId());
        String month = monthName(r.getMonth());
        Map<UUID, AppUser> people = users.findByIdIn(found.stream().map(Payslip::getUserId).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        String base = env.getProperty("platform.app-url", "http://localhost:3300");
        for (Payslip p : found) {
            String link = "/" + ctx.slug() + "/hr/payslips/" + p.getId();
            notifications.send(ctx.orgId(), List.of(p.getUserId()), ctx.userId(), "payroll.paid",
                    Text.of("Your payslip for {month} is ready", Map.of("month", month)), null, link);
            AppUser u = people.get(p.getUserId());
            if (u != null) {
                String lang = u.getLocale();
                Map<String, String> args = Map.of("month", month, "org", ctx.orgName());
                mail.queue(MailService.compose(u.getEmail(),
                        messages.in(lang, "Your payslip for {month} from {org}", args),
                        List.of(messages.in(lang, "{org} has paid your salary for {month}. Your payslip is ready to view.", args)),
                        messages.in(lang, "View your payslip", Map.of()), base + link,
                        messages.in(lang, "Payslips are private: only you and your organisation's HR can see them.", Map.of())));
            }
        }
        return detail(ctx, r);
    }

    /** The bank transfer list of a run: name, bank, account and net pay. */
    // Not read-only: listing who isn't set up may create missing employee profiles.
    @Transactional
    public String csv(String slug, UUID id) {
        OrgContext ctx = hr.requirePayroll(slug);
        PayRun r = run(ctx, id);
        StringBuilder out = new StringBuilder("Name,Email,Bank,Account,Net pay (USD)\n");
        detail(ctx, r).payslips().forEach(p -> {
            String email = users.findById(p.person().id()).map(AppUser::getEmail).orElse("");
            out.append(cell(p.person().name())).append(',').append(cell(email)).append(',').append(cell(p.bankName()))
                    .append(',').append(cell(p.bankAccount())).append(',').append(p.net().toPlainString()).append('\n');
        });
        return out.toString();
    }

    private static String cell(String v) {
        if (v == null) {
            return "";
        }
        String s = v.startsWith("=") || v.startsWith("+") || v.startsWith("-") || v.startsWith("@") ? "'" + v : v;
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    // ── Payslips ─────────────────────────────────────────────────────────────────────

    /** Mine, from paid runs, newest first. */
    @Transactional(readOnly = true)
    public List<PayslipView> mine(String slug) {
        OrgContext ctx = hr.require(slug);
        Map<UUID, PayRun> paid = runs.findByOrgIdOrderByMonthDesc(ctx.orgId()).stream()
                .filter(r -> PayRun.PAID.equals(r.getStatus())).collect(Collectors.toMap(PayRun::getId, Function.identity()));
        return slips.findByOrgIdAndUserId(ctx.orgId(), ctx.userId()).stream().filter(p -> paid.containsKey(p.getPayRunId()))
                .sorted(Comparator.comparing((Payslip p) -> paid.get(p.getPayRunId()).getMonth()).reversed())
                .map(p -> slip(ctx, paid.get(p.getPayRunId()), p, profiles.persons(List.of(p.getUserId())))).toList();
    }

    /** One payslip: the person once paid, or an HR manager at any time. */
    @Transactional(readOnly = true)
    public PayslipView payslip(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        Payslip p = slips.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        PayRun r = runs.findById(p.getPayRunId()).orElseThrow();
        boolean own = p.getUserId().equals(ctx.userId()) && PayRun.PAID.equals(r.getStatus());
        if (!own && !HrAccess.runsPayroll(ctx)) {
            throw BusinessException.notFound();
        }
        return slip(ctx, r, p, profiles.persons(List.of(p.getUserId())));
    }

    // ── Calculation ──────────────────────────────────────────────────────────────────

    /** (Re)builds a DRAFT's payslips: one per active person with a salary, keeping HR's adjustments. */
    private void calculate(OrgContext ctx, PayRun r) {
        YearMonth ym = YearMonth.parse(r.getMonth());
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        PayrollSettings settings = settings(ctx);
        PayrollCalculator.Rules rules = new PayrollCalculator.Rules(
                brackets(settings).stream().map(b -> new PayrollCalculator.Bracket(b.upToKhr(), b.ratePct())).toList(),
                settings.getDependantAllowanceKhr(), settings.getNssfFloorKhr(), settings.getNssfCapKhr(),
                settings.getNssfPensionEmployeePct(), settings.getNssfPensionEmployerPct().add(settings.getNssfHealthEmployerPct())
                        .add(settings.getNssfRiskEmployerPct()), settings.getOvertimeRatePct());
        List<EmployeeProfile> people = activePeople(ctx);
        Map<UUID, EmployeePay> payOf = pays.findByOrgId(ctx.orgId()).stream()
                .collect(Collectors.toMap(EmployeePay::getUserId, Function.identity()));
        Map<UUID, WorkSchedule> scheduleOf = schedules.forPeople(ctx, people);
        Set<LocalDate> holidayDates = holidays.dates(ctx, from, to);
        Set<UUID> unpaidTypes = leaveTypes.all(ctx).stream().filter(t -> !t.isPaid()).map(LeaveType::getId).collect(Collectors.toSet());
        List<LeaveRequest> approved = leaves.approvedBetween(ctx.orgId(), from, to);
        Map<UUID, Payslip> existing = slips.findByPayRunId(r.getId()).stream()
                .collect(Collectors.toMap(Payslip::getUserId, Function.identity()));
        Set<UUID> kept = new HashSet<>();
        for (EmployeeProfile p : people) {
            EmployeePay pay = payOf.get(p.getUserId());
            if (pay == null) {
                continue;
            }
            WorkSchedule s = scheduleOf.get(p.getUserId());
            int workingDays = 0;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (s.worksOn(d.getDayOfWeek()) && !holidayDates.contains(d)) {
                    workingDays++;
                }
            }
            BigDecimal unpaidDays = BigDecimal.ZERO;
            for (LeaveRequest l : approved) {
                if (l.getUserId().equals(p.getUserId()) && unpaidTypes.contains(l.getLeaveTypeId())) {
                    LocalDate a = l.getStartDate().isBefore(from) ? from : l.getStartDate();
                    LocalDate b = l.getEndDate().isAfter(to) ? to : l.getEndDate();
                    unpaidDays = unpaidDays.add(LeaveDays.count(a, b, l.isHalfStart() && a.equals(l.getStartDate()),
                            l.isHalfEnd() && b.equals(l.getEndDate()), holidayDates));
                }
            }
            int otMinutes = overtime.findByOrgIdAndUserIdAndWorkDateBetween(ctx.orgId(), p.getUserId(), from, to).stream()
                    .filter(o -> OvertimeRequest.APPROVED.equals(o.getStatus())).mapToInt(OvertimeRequest::getMinutes).sum();
            Payslip slip = existing.get(p.getUserId());
            if (slip == null) {
                slip = new Payslip();
                slip.setId(Ids.next());
                slip.setOrgId(ctx.orgId());
                slip.setPayRunId(r.getId());
                slip.setUserId(p.getUserId());
                slip.setAdjustments(List.of());
                slip.setCreatedAt(clock.instant());
            }
            BigDecimal allowances = BigDecimal.ZERO;
            BigDecimal deductions = BigDecimal.ZERO;
            for (Map<String, Object> a : slip.getAdjustments()) {
                BigDecimal amount = new BigDecimal(a.get("amount").toString());
                if (amount.signum() > 0) {
                    allowances = allowances.add(amount);
                } else {
                    deductions = deductions.add(amount.negate());
                }
            }
            PayrollCalculator.Result x = PayrollCalculator.calculate(new PayrollCalculator.Input(pay.getMonthlySalary(),
                    workingDays, s.dayMinutes(), unpaidDays, otMinutes, allowances, deductions, pay.getDependants(),
                    pay.isNssfEnrolled(), r.getExchangeRate()), rules);
            slip.setMonthlySalary(pay.getMonthlySalary());
            slip.setWorkingDays((short) workingDays);
            slip.setUnpaidLeaveDays(unpaidDays);
            slip.setUnpaidLeaveAmount(x.unpaidAmount());
            slip.setOvertimeMinutes(otMinutes);
            slip.setOvertimeAmount(x.overtimeAmount());
            slip.setGross(x.gross());
            slip.setDependants(pay.getDependants());
            slip.setTaxableKhr(x.taxableKhr());
            slip.setTaxKhr(x.taxKhr());
            slip.setTax(x.tax());
            slip.setNssfEmployee(x.nssfEmployee());
            slip.setNssfEmployer(x.nssfEmployer());
            slip.setNet(x.net());
            slip.setBankName(pay.getBankName());
            slip.setBankAccount(pay.getBankAccount());
            slip.setUpdatedAt(clock.instant());
            slips.save(slip);
            kept.add(p.getUserId());
        }
        // People who left, or whose salary was removed, drop out of a draft.
        existing.values().stream().filter(s -> !kept.contains(s.getUserId())).forEach(slips::delete);
        slips.flush();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private List<EmployeeProfile> activePeople(OrgContext ctx) {
        profileRows.createMissing(ctx.orgId());
        Set<UUID> active = memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST)
                .map(Membership::getUserId).collect(Collectors.toSet());
        return profileRows.findByOrgId(ctx.orgId()).stream().filter(p -> active.contains(p.getUserId())).toList();
    }

    private PayRun run(OrgContext ctx, UUID id) {
        return runs.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private PayRun draft(OrgContext ctx, UUID id) {
        PayRun r = run(ctx, id);
        if (!PayRun.DRAFT.equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This pay run is approved; reopen it to change it", List.of());
        }
        return r;
    }

    private RunDetail detail(OrgContext ctx, PayRun r) {
        List<Payslip> found = slips.findByPayRunId(r.getId());
        Set<UUID> in = found.stream().map(Payslip::getUserId).collect(Collectors.toSet());
        List<EmployeeProfile> people = activePeople(ctx);
        Map<UUID, PersonRef> refs = profiles.persons(people.stream().map(EmployeeProfile::getUserId).collect(Collectors.toSet()));
        refs.putAll(profiles.persons(in));
        List<PayslipView> views = found.stream().map(p -> slip(ctx, r, p, refs))
                .sorted(Comparator.comparing(v -> v.person().name(), String.CASE_INSENSITIVE_ORDER)).toList();
        List<PersonRef> notSetUp = PayRun.DRAFT.equals(r.getStatus()) ? people.stream()
                .filter(p -> !in.contains(p.getUserId()) && pays.findByOrgIdAndUserId(ctx.orgId(), p.getUserId()).isEmpty())
                .map(p -> refs.get(p.getUserId())).filter(Objects::nonNull)
                .sorted(Comparator.comparing(PersonRef::name, String.CASE_INSENSITIVE_ORDER)).toList() : List.of();
        return new RunDetail(summary(r, found), views, notSetUp);
    }

    private RunSummary summary(PayRun r, List<Payslip> found) {
        BigDecimal gross = sum(found, Payslip::getGross);
        BigDecimal nssfEmployer = sum(found, Payslip::getNssfEmployer);
        Map<UUID, PersonRef> who = profiles.persons(java.util.stream.Stream.of(r.getApprovedBy(), r.getPaidBy())
                .filter(Objects::nonNull).toList());
        return new RunSummary(r.getId(), r.getMonth(), r.getExchangeRate(), r.getStatus(), r.getNote(),
                new RunTotals(found.size(), gross, sum(found, Payslip::getTax), sum(found, Payslip::getNssfEmployee),
                        nssfEmployer, sum(found, Payslip::getNet), gross.add(nssfEmployer)),
                r.getCreatedAt(), r.getApprovedBy() == null ? null : who.get(r.getApprovedBy()), r.getApprovedAt(),
                r.getPaidBy() == null ? null : who.get(r.getPaidBy()), r.getPaidAt());
    }

    private PayslipView slip(OrgContext ctx, PayRun r, Payslip p, Map<UUID, PersonRef> refs) {
        Membership m = memberships.findByOrgIdAndUserId(ctx.orgId(), p.getUserId()).orElse(null);
        String department = profileRows.findByOrgIdAndUserId(ctx.orgId(), p.getUserId()).map(EmployeeProfile::getDepartment).orElse(null);
        List<Adjustment> adjustments = p.getAdjustments().stream()
                .map(a -> new Adjustment(String.valueOf(a.get("label")), new BigDecimal(a.get("amount").toString()))).toList();
        return new PayslipView(p.getId(), r.getId(), r.getMonth(), r.getStatus(), ctx.orgName(),
                refs.getOrDefault(p.getUserId(), profiles.persons(List.of(p.getUserId())).get(p.getUserId())), department,
                m == null ? null : m.getTitle(), p.getMonthlySalary(), p.getWorkingDays(), p.getUnpaidLeaveDays(),
                p.getUnpaidLeaveAmount(), p.getOvertimeMinutes(), p.getOvertimeAmount(), adjustments, p.getGross(),
                p.getDependants(), r.getExchangeRate(), p.getTaxableKhr(), p.getTaxKhr(), p.getTax(), p.getNssfEmployee(),
                p.getNssfEmployer(), p.getNet(), p.getBankName(), p.getBankAccount(), r.getPaidAt());
    }

    private static BigDecimal sum(List<Payslip> found, Function<Payslip, BigDecimal> f) {
        return found.stream().map(f).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** "November 2026". */
    static String monthName(String month) {
        YearMonth ym = YearMonth.parse(month);
        return ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + ym.getYear();
    }
}
