package com.businessplatform.hr.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.calendar.CalendarService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.file.FileService;
import com.businessplatform.file.StoredFile;
import com.businessplatform.file.StoredFileRepository;
import com.businessplatform.hr.domain.Candidate;
import com.businessplatform.hr.domain.Interview;
import com.businessplatform.hr.domain.InterviewPanel;
import com.businessplatform.hr.domain.JobOpening;
import com.businessplatform.hr.domain.OnboardingItem;
import com.businessplatform.hr.domain.OnboardingTemplate;
import com.businessplatform.hr.payload.HrPayloads.PersonRef;
import com.businessplatform.hr.payload.RecruitmentPayloads.*;
import com.businessplatform.hr.repository.RecruitmentRepositories.CandidateRepository;
import com.businessplatform.hr.repository.RecruitmentRepositories.InterviewPanelRepository;
import com.businessplatform.hr.repository.RecruitmentRepositories.InterviewRepository;
import com.businessplatform.hr.repository.RecruitmentRepositories.JobOpeningRepository;
import com.businessplatform.hr.repository.RecruitmentRepositories.OnboardingItemRepository;
import com.businessplatform.hr.repository.RecruitmentRepositories.OnboardingTemplateRepository;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.payload.OrgPayloads.InviteRequest;
import com.businessplatform.org.repository.InvitationRepository;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.OrgService;

import lombok.RequiredArgsConstructor;

/**
 * Recruitment and onboarding. HR managers run it all; a job's hiring manager follows that job's candidates (moves
 * them along, rates them, schedules interviews) but doesn't hire; interviewers see the candidates they interview
 * and give feedback — seeing their colleagues' feedback only once they have given their own. Hiring sets a start
 * date, can invite the candidate into the organisation, and copies the onboarding checklist onto the hire.
 */
@Service
@RequiredArgsConstructor
public class RecruitmentService {

    /** How jobs and candidates appear in the activity log. */
    public static final String JOB = "job";
    public static final String CANDIDATE = "candidate";

    private final HrAccess hr;
    private final ProfileService profiles;
    private final JobOpeningRepository jobs;
    private final CandidateRepository candidates;
    private final InterviewRepository interviews;
    private final InterviewPanelRepository panels;
    private final OnboardingTemplateRepository templates;
    private final OnboardingItemRepository items;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final OrgService orgs;
    private final FileService fileService;
    private final StoredFileRepository files;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    // ── Who may do what ──────────────────────────────────────────────────────────────

    private static boolean manages(OrgContext ctx, JobOpening j) {
        return HrAccess.manages(ctx) || ctx.userId().equals(j.getHiringManagerId());
    }

    /** HR, the job's hiring manager, or someone on one of the candidate's interview panels. */
    private boolean sees(OrgContext ctx, JobOpening j, Candidate c) {
        if (manages(ctx, j)) {
            return true;
        }
        List<UUID> ids = interviews.findByCandidateIdOrderByStartsAt(c.getId()).stream().map(Interview::getId).toList();
        return !ids.isEmpty() && panels.existsByUserIdAndInterviewIdIn(ctx.userId(), ids);
    }

    // ── Jobs ─────────────────────────────────────────────────────────────────────────

    /** HR: every opening; hiring managers: theirs. Newest first. */
    @Transactional(readOnly = true)
    public List<JobView> jobs(String slug) {
        OrgContext ctx = hr.require(slug);
        List<JobOpening> found = jobs.findByOrgIdOrderByCreatedAtDesc(ctx.orgId()).stream().filter(j -> manages(ctx, j)).toList();
        return jobViews(ctx, found);
    }

    @Transactional(readOnly = true)
    public JobView job(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        JobOpening j = job(ctx, id);
        if (!manages(ctx, j)) {
            throw BusinessException.notFound();
        }
        return jobViews(ctx, List.of(j)).get(0);
    }

    @Transactional
    public JobView saveJob(String slug, UUID id, JobRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        JobOpening j = id == null ? new JobOpening() : job(ctx, id);
        if (in.hiringManagerId() != null && !activeMember(ctx, in.hiringManagerId())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "hiringManagerId", "Choose a member of the organisation");
        }
        if (id == null) {
            j.setId(Ids.next());
            j.setOrgId(ctx.orgId());
            j.setCreatedBy(ctx.userId());
            j.setCreatedAt(clock.instant());
        }
        UUID before = j.getHiringManagerId();
        j.setTitle(in.title().strip());
        j.setDepartment(ProfileService.clean(in.department()));
        j.setEmploymentType(ProfileService.clean(in.employmentType()));
        j.setLocation(ProfileService.clean(in.location()));
        j.setDescription(ProfileService.clean(in.description()));
        j.setOpenings((short) in.openings());
        j.setHiringManagerId(in.hiringManagerId());
        j.setStatus(in.status() == null ? JobOpening.OPEN : in.status());
        j.setUpdatedAt(clock.instant());
        jobs.saveAndFlush(j);
        activity.record(ctx.orgId(), HrAccess.MODULE, JOB, j.getId(), ctx.userId(), id == null ? "job.created" : "job.updated",
                Map.of("title", j.getTitle(), "status", j.getStatus()));
        if (j.getHiringManagerId() != null && !j.getHiringManagerId().equals(before)) {
            notifications.send(ctx.orgId(), List.of(j.getHiringManagerId()), ctx.userId(), "job.hiring_manager",
                    Text.of("You are the hiring manager for {title}", Map.of("title", j.getTitle())), null, jobLink(ctx, j.getId()));
        }
        return jobViews(ctx, List.of(j)).get(0);
    }

    // ── Candidates ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CandidateSummary> candidates(String slug, UUID jobId) {
        OrgContext ctx = hr.require(slug);
        JobOpening j = job(ctx, jobId);
        if (!manages(ctx, j)) {
            throw BusinessException.notFound();
        }
        return summaries(candidates.findByOrgIdAndJobIdOrderByCreatedAtDesc(ctx.orgId(), jobId));
    }

    @Transactional
    public CandidateDetail addCandidate(String slug, UUID jobId, CandidateRequest in) {
        OrgContext ctx = hr.require(slug);
        JobOpening j = job(ctx, jobId);
        if (!manages(ctx, j)) {
            throw BusinessException.notFound();
        }
        if (JobOpening.CLOSED.equals(j.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This opening is closed", List.of());
        }
        Candidate c = new Candidate();
        c.setId(Ids.next());
        c.setOrgId(ctx.orgId());
        c.setJobId(jobId);
        c.setStage("NEW");
        c.setCreatedBy(ctx.userId());
        c.setCreatedAt(clock.instant());
        c.setUpdatedAt(c.getCreatedAt());
        apply(c, in);
        candidates.saveAndFlush(c);
        activity.record(ctx.orgId(), HrAccess.MODULE, CANDIDATE, c.getId(), ctx.userId(), "candidate.added",
                Map.of("name", c.getName(), "job", j.getTitle()));
        return detail(ctx, j, c);
    }

    @Transactional(readOnly = true)
    public CandidateDetail candidate(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        Candidate c = candidate(ctx, id);
        JobOpening j = job(ctx, c.getJobId());
        if (!sees(ctx, j, c)) {
            throw BusinessException.notFound();
        }
        return detail(ctx, j, c);
    }

    @Transactional
    public CandidateDetail updateCandidate(String slug, UUID id, CandidateRequest in) {
        OrgContext ctx = hr.require(slug);
        Candidate c = candidate(ctx, id);
        JobOpening j = managed(ctx, c);
        apply(c, in);
        c.setUpdatedAt(clock.instant());
        return detail(ctx, j, c);
    }

    /** Moves an open candidate along (or back) the pipeline; rejecting needs a reason. */
    @Transactional
    public CandidateDetail move(String slug, UUID id, StageRequest in) {
        OrgContext ctx = hr.require(slug);
        Candidate c = candidate(ctx, id);
        JobOpening j = managed(ctx, c);
        if (Candidate.HIRED.equals(c.getStage())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This candidate is hired already", List.of());
        }
        String reason = ProfileService.clean(in.reason());
        if (Candidate.REJECTED.equals(in.stage()) && reason == null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "reason", "Say why the candidate isn't going ahead");
        }
        String from = c.getStage();
        c.setStage(in.stage());
        c.setRejectedReason(Candidate.REJECTED.equals(in.stage()) ? reason : null);
        c.setUpdatedAt(clock.instant());
        Map<String, Object> data = new HashMap<>(Map.of("name", c.getName(), "from", from, "to", in.stage()));
        if (reason != null) {
            data.put("reason", reason);
        }
        activity.record(ctx.orgId(), HrAccess.MODULE, CANDIDATE, c.getId(), ctx.userId(), "candidate.moved", data);
        return detail(ctx, j, c);
    }

    /** HR managers: removes a candidate and their CV (e.g. on request); hires stay as the record of the hire. */
    @Transactional
    public void deleteCandidate(String slug, UUID id) {
        OrgContext ctx = hr.requireManager(slug);
        Candidate c = candidate(ctx, id);
        if (Candidate.HIRED.equals(c.getStage())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "A hire can't be removed", List.of());
        }
        if (c.getCvFileId() != null) {
            fileService.discard(ctx.orgId(), List.of(c.getCvFileId()));
        }
        activity.record(ctx.orgId(), HrAccess.MODULE, CANDIDATE, c.getId(), ctx.userId(), "candidate.removed", Map.of("name", c.getName()));
        candidates.delete(c);
    }

    @Transactional
    public CandidateDetail attachCv(String slug, UUID id, MultipartFile upload) {
        OrgContext ctx = hr.require(slug);
        Candidate c = candidate(ctx, id);
        JobOpening j = managed(ctx, c);
        StoredFile f = fileService.store(ctx.orgId(), ctx.userId(), upload);
        UUID old = c.getCvFileId();
        c.setCvFileId(f.getId());
        c.setUpdatedAt(clock.instant());
        candidates.saveAndFlush(c);
        if (old != null) {
            fileService.discard(ctx.orgId(), List.of(old));
        }
        return detail(ctx, j, c);
    }

    /** The CV, for anyone who sees the candidate. */
    @Transactional(readOnly = true)
    public StoredFile cv(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        Candidate c = candidate(ctx, id);
        if (!sees(ctx, job(ctx, c.getJobId()), c) || c.getCvFileId() == null) {
            throw BusinessException.notFound();
        }
        return files.findById(c.getCvFileId()).filter(f -> f.getOrgId().equals(ctx.orgId())).orElseThrow(BusinessException::notFound);
    }

    // ── Interviews ───────────────────────────────────────────────────────────────────

    @Transactional
    public CandidateDetail schedule(String slug, UUID candidateId, InterviewRequest in) {
        OrgContext ctx = hr.require(slug);
        Candidate c = candidate(ctx, candidateId);
        JobOpening j = managed(ctx, c);
        if (c.closed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This candidate's application is closed", List.of());
        }
        List<UUID> panel = in.panel().stream().distinct().toList();
        if (panel.stream().anyMatch(u -> !activeMember(ctx, u))) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "panel", "Choose members of the organisation");
        }
        Interview i = new Interview();
        i.setId(Ids.next());
        i.setOrgId(ctx.orgId());
        i.setCandidateId(c.getId());
        i.setStartsAt(in.startsAt());
        i.setDurationMinutes((short) in.durationMinutes());
        i.setLocation(ProfileService.clean(in.location()));
        i.setCreatedBy(ctx.userId());
        i.setCreatedAt(clock.instant());
        interviews.saveAndFlush(i);
        panel.forEach(u -> {
            InterviewPanel p = new InterviewPanel();
            p.setInterviewId(i.getId());
            p.setUserId(u);
            panels.save(p);
        });
        if (List.of("NEW", "SCREENING").contains(c.getStage())) {
            c.setStage("INTERVIEW");
            c.setUpdatedAt(clock.instant());
        }
        activity.record(ctx.orgId(), HrAccess.MODULE, CANDIDATE, c.getId(), ctx.userId(), "candidate.interview",
                Map.of("name", c.getName(), "at", i.getStartsAt().toString()));
        notifications.send(ctx.orgId(), panel, ctx.userId(), "interview.scheduled",
                Text.of("Interview with {name} for {title}", Map.of("name", c.getName(), "title", j.getTitle())), null,
                candidateLink(ctx, c.getId()));
        return detail(ctx, j, c);
    }

    @Transactional
    public CandidateDetail cancelInterview(String slug, UUID interviewId) {
        OrgContext ctx = hr.require(slug);
        Interview i = interviews.findByIdAndOrgId(interviewId, ctx.orgId()).orElseThrow(BusinessException::notFound);
        Candidate c = candidate(ctx, i.getCandidateId());
        JobOpening j = managed(ctx, c);
        interviews.delete(i);
        return detail(ctx, j, c);
    }

    /** A panelist's recommendation; they may change it until the candidate is hired or rejected. */
    @Transactional
    public CandidateDetail feedback(String slug, UUID interviewId, FeedbackRequest in) {
        OrgContext ctx = hr.require(slug);
        Interview i = interviews.findByIdAndOrgId(interviewId, ctx.orgId()).orElseThrow(BusinessException::notFound);
        InterviewPanel p = panels.findById(new InterviewPanel.Key(i.getId(), ctx.userId()))
                .orElseThrow(BusinessException::forbidden);
        Candidate c = candidate(ctx, i.getCandidateId());
        if (c.closed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This candidate's application is closed", List.of());
        }
        p.setRecommendation(in.recommendation());
        p.setNotes(ProfileService.clean(in.notes()));
        p.setSubmittedAt(clock.instant());
        JobOpening j = job(ctx, c.getJobId());
        activity.record(ctx.orgId(), HrAccess.MODULE, CANDIDATE, c.getId(), ctx.userId(), "candidate.feedback",
                Map.of("name", c.getName(), "recommendation", in.recommendation()));
        Set<UUID> to = new HashSet<>();
        if (j.getHiringManagerId() != null) {
            to.add(j.getHiringManagerId());
        }
        to.add(i.getCreatedBy());
        notifications.send(ctx.orgId(), to, ctx.userId(), "interview.feedback",
                Text.of("Interview feedback on {name}", Map.of("name", c.getName())), null, candidateLink(ctx, c.getId()));
        return detail(ctx, j, c);
    }

    /** Interviews I sit on, from today. */
    @Transactional(readOnly = true)
    public List<InterviewView> myInterviews(String slug) {
        OrgContext ctx = hr.require(slug);
        Instant since = LeaveService.today(clock).atStartOfDay(CalendarService.ZONE).toInstant();
        return interviewViews(ctx, interviews.forPanelist(ctx.orgId(), ctx.userId(), since), false);
    }

    // ── Hiring and onboarding ────────────────────────────────────────────────────────

    /**
     * HR managers: hires a candidate. The onboarding checklist is copied from the template (due dates from the
     * start date); with {@code invite}, the candidate's email is invited to the organisation as a member. The
     * opening closes once it has as many hires as openings.
     */
    @Transactional
    public CandidateDetail hire(String slug, UUID id, HireRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        Candidate c = candidate(ctx, id);
        JobOpening j = job(ctx, c.getJobId());
        if (c.closed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This candidate's application is closed", List.of());
        }
        if (in.invite() && c.getEmail() == null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "invite", "Add the candidate's email to invite them");
        }
        c.setStage(Candidate.HIRED);
        c.setStartDate(in.startDate());
        c.setHiredAt(clock.instant());
        c.setRejectedReason(null);
        c.setUpdatedAt(clock.instant());
        if (in.invite()) {
            try {
                c.setInvitationId(orgs.invite(slug, new InviteRequest(c.getEmail(), OrgRole.MEMBER)).id());
            } catch (BusinessException e) {
                // Someone who is a member already needs no invitation.
                if (e.getCode() != ErrorCode.CONFLICT) {
                    throw e;
                }
            }
        }
        short order = 0;
        for (OnboardingTemplate t : template(ctx)) {
            OnboardingItem item = new OnboardingItem();
            item.setId(Ids.next());
            item.setOrgId(ctx.orgId());
            item.setCandidateId(c.getId());
            item.setTitle(t.getTitle());
            item.setDueDate(in.startDate().plusDays(t.getDueDays()));
            item.setSortOrder(order++);
            item.setCreatedAt(clock.instant());
            items.save(item);
        }
        candidates.saveAndFlush(c);
        activity.record(ctx.orgId(), HrAccess.MODULE, CANDIDATE, c.getId(), ctx.userId(), "candidate.hired",
                Map.of("name", c.getName(), "job", j.getTitle(), "start", in.startDate().toString()));
        long hired = candidates.findByOrgIdAndJobIdOrderByCreatedAtDesc(ctx.orgId(), j.getId()).stream()
                .filter(x -> Candidate.HIRED.equals(x.getStage())).count();
        if (hired >= j.getOpenings() && !JobOpening.CLOSED.equals(j.getStatus())) {
            j.setStatus(JobOpening.CLOSED);
            j.setUpdatedAt(clock.instant());
            activity.record(ctx.orgId(), HrAccess.MODULE, JOB, j.getId(), ctx.userId(), "job.filled", Map.of("title", j.getTitle()));
        }
        return detail(ctx, j, c);
    }

    /** HR managers: recent hires with their onboarding progress. */
    @Transactional(readOnly = true)
    public List<HireView> hires(String slug) {
        OrgContext ctx = hr.requireManager(slug);
        List<Candidate> found = candidates.findByOrgIdAndStageOrderByHiredAtDesc(ctx.orgId(), Candidate.HIRED);
        Map<UUID, JobOpening> jobById = jobs.findAllById(found.stream().map(Candidate::getJobId).distinct().toList()).stream()
                .collect(Collectors.toMap(JobOpening::getId, Function.identity()));
        Map<UUID, List<OnboardingItem>> byCandidate = items.findByCandidateIdIn(found.stream().map(Candidate::getId).toList())
                .stream().collect(Collectors.groupingBy(OnboardingItem::getCandidateId));
        List<CandidateSummary> sums = summaries(found);
        List<HireView> out = new ArrayList<>();
        for (int k = 0; k < found.size(); k++) {
            Candidate c = found.get(k);
            List<OnboardingItem> its = byCandidate.getOrDefault(c.getId(), List.of());
            out.add(new HireView(sums.get(k), jobById.get(c.getJobId()).getTitle(), c.getStartDate(),
                    (int) its.stream().filter(x -> x.getDoneAt() != null).count(), its.size(), joined(c)));
        }
        return out;
    }

    @Transactional
    public CandidateDetail addItem(String slug, UUID candidateId, OnboardingItemRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        Candidate c = hired(ctx, candidateId);
        OnboardingItem item = new OnboardingItem();
        item.setId(Ids.next());
        item.setOrgId(ctx.orgId());
        item.setCandidateId(c.getId());
        item.setTitle(in.title().strip());
        item.setDueDate(in.dueDate());
        item.setSortOrder((short) items.findByCandidateIdOrderBySortOrder(c.getId()).size());
        item.setCreatedAt(clock.instant());
        items.save(item);
        return detail(ctx, job(ctx, c.getJobId()), c);
    }

    /** HR managers or the hiring manager: ticks a step off (or back on). */
    @Transactional
    public CandidateDetail setDone(String slug, UUID itemId, boolean done) {
        OrgContext ctx = hr.require(slug);
        OnboardingItem item = items.findByIdAndOrgId(itemId, ctx.orgId()).orElseThrow(BusinessException::notFound);
        Candidate c = candidate(ctx, item.getCandidateId());
        JobOpening j = managed(ctx, c);
        item.setDoneAt(done ? clock.instant() : null);
        item.setDoneBy(done ? ctx.userId() : null);
        return detail(ctx, j, c);
    }

    @Transactional
    public CandidateDetail removeItem(String slug, UUID itemId) {
        OrgContext ctx = hr.requireManager(slug);
        OnboardingItem item = items.findByIdAndOrgId(itemId, ctx.orgId()).orElseThrow(BusinessException::notFound);
        Candidate c = candidate(ctx, item.getCandidateId());
        items.delete(item);
        return detail(ctx, job(ctx, c.getJobId()), c);
    }

    // Not read-only: the first use seeds the standard steps.
    @Transactional
    public TemplateView templateView(String slug) {
        OrgContext ctx = hr.requireManager(slug);
        return new TemplateView(template(ctx).stream().map(t -> new TemplateItem(t.getTitle(), t.getDueDays())).toList());
    }

    @Transactional
    public TemplateView saveTemplate(String slug, TemplateRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        templates.deleteAll(template(ctx));
        templates.flush();
        short order = 0;
        for (TemplateItem i : in.items()) {
            OnboardingTemplate t = new OnboardingTemplate();
            t.setId(Ids.next());
            t.setOrgId(ctx.orgId());
            t.setTitle(i.title().strip());
            t.setDueDays((short) i.dueDays());
            t.setSortOrder(order++);
            templates.save(t);
        }
        activity.record(ctx.orgId(), HrAccess.MODULE, JOB, ctx.orgId(), ctx.userId(), "onboarding.template.updated",
                Map.of("steps", in.items().size()));
        return new TemplateView(in.items());
    }

    /** The template, seeded the first time with common steps. */
    private List<OnboardingTemplate> template(OrgContext ctx) {
        if (templates.countByOrgId(ctx.orgId()) == 0) {
            Map<String, Integer> seed = new LinkedHashMap<>();
            seed.put("Prepare a computer, email and accounts", -3);
            seed.put("Sign the employment contract", 0);
            seed.put("Welcome, introductions and a tour", 0);
            seed.put("Add to payroll and register with NSSF", 7);
            seed.put("First-week check-in with the manager", 7);
            seed.put("End-of-probation review", 90);
            short order = 0;
            for (var e : seed.entrySet()) {
                OnboardingTemplate t = new OnboardingTemplate();
                t.setId(Ids.next());
                t.setOrgId(ctx.orgId());
                t.setTitle(e.getKey());
                t.setDueDays(e.getValue().shortValue());
                t.setSortOrder(order++);
                templates.save(t);
            }
            templates.flush();
        }
        return templates.findByOrgIdOrderBySortOrder(ctx.orgId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private JobOpening job(OrgContext ctx, UUID id) {
        return jobs.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private Candidate candidate(OrgContext ctx, UUID id) {
        return candidates.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    /** The candidate's job, when the caller manages it (NOT_FOUND for those who can't see it, else FORBIDDEN). */
    private JobOpening managed(OrgContext ctx, Candidate c) {
        JobOpening j = job(ctx, c.getJobId());
        if (!manages(ctx, j)) {
            throw sees(ctx, j, c) ? BusinessException.forbidden() : BusinessException.notFound();
        }
        return j;
    }

    private Candidate hired(OrgContext ctx, UUID id) {
        Candidate c = candidate(ctx, id);
        if (!Candidate.HIRED.equals(c.getStage())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Onboarding starts when the candidate is hired", List.of());
        }
        return c;
    }

    private boolean activeMember(OrgContext ctx, UUID userId) {
        return memberships.findByOrgIdAndUserId(ctx.orgId(), userId)
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST).isPresent();
    }

    private boolean joined(Candidate c) {
        return c.getInvitationId() != null && invitations.findById(c.getInvitationId()).map(i -> i.getAcceptedAt() != null).orElse(false);
    }

    private static void apply(Candidate c, CandidateRequest in) {
        c.setName(in.name().strip());
        c.setEmail(ProfileService.clean(in.email()) == null ? null : in.email().strip().toLowerCase(Locale.ROOT));
        c.setPhone(ProfileService.clean(in.phone()));
        c.setSource(in.source());
        c.setRating(in.rating() == null ? null : in.rating().shortValue());
        c.setNotes(ProfileService.clean(in.notes()));
    }

    static String jobLink(OrgContext ctx, UUID jobId) {
        return "/" + ctx.slug() + "/hr/recruitment/jobs/" + jobId;
    }

    static String candidateLink(OrgContext ctx, UUID candidateId) {
        return "/" + ctx.slug() + "/hr/recruitment/candidates/" + candidateId;
    }

    private List<JobView> jobViews(OrgContext ctx, List<JobOpening> found) {
        Map<UUID, Map<String, Long>> stages = new HashMap<>();
        candidates.findByOrgIdAndJobIdIn(ctx.orgId(), found.stream().map(JobOpening::getId).toList()).forEach(c ->
                stages.computeIfAbsent(c.getJobId(), k -> new LinkedHashMap<>()).merge(c.getStage(), 1L, Long::sum));
        Map<UUID, PersonRef> people = profiles.persons(found.stream().map(JobOpening::getHiringManagerId).filter(Objects::nonNull).toList());
        return found.stream().map(j -> new JobView(j.getId(), j.getTitle(), j.getDepartment(), j.getEmploymentType(),
                j.getLocation(), j.getDescription(), j.getOpenings(),
                j.getHiringManagerId() == null ? null : people.get(j.getHiringManagerId()), j.getStatus(),
                stages.getOrDefault(j.getId(), Map.of()), j.getCreatedAt(), HrAccess.manages(ctx))).toList();
    }

    private List<CandidateSummary> summaries(List<Candidate> found) {
        Instant now = clock.instant();
        return found.stream().map(c -> new CandidateSummary(c.getId(), c.getJobId(), c.getName(), c.getEmail(), c.getPhone(),
                c.getSource(), c.getStage(), c.getRating() == null ? null : (int) c.getRating(), c.getCvFileId() != null,
                interviews.findByCandidateIdOrderByStartsAt(c.getId()).stream().map(Interview::getStartsAt)
                        .filter(t -> t.isAfter(now)).findFirst().orElse(null),
                c.getCreatedAt())).toList();
    }

    private List<InterviewView> interviewViews(OrgContext ctx, List<Interview> found, boolean seesAll) {
        Map<UUID, List<InterviewPanel>> byInterview = panels.findByInterviewIdIn(found.stream().map(Interview::getId).toList())
                .stream().collect(Collectors.groupingBy(InterviewPanel::getInterviewId));
        Map<UUID, PersonRef> people = profiles.persons(byInterview.values().stream().flatMap(List::stream)
                .map(InterviewPanel::getUserId).distinct().toList());
        Map<UUID, Candidate> cands = new HashMap<>();
        Map<UUID, String> titles = new HashMap<>();
        List<InterviewView> out = new ArrayList<>();
        for (Interview i : found) {
            Candidate c = cands.computeIfAbsent(i.getCandidateId(), id -> candidates.findById(id).orElseThrow());
            String jobTitle = titles.computeIfAbsent(c.getJobId(), id -> jobs.findById(id).map(JobOpening::getTitle).orElse(""));
            List<InterviewPanel> panel = byInterview.getOrDefault(i.getId(), List.of());
            Optional<InterviewPanel> mine = panel.stream().filter(p -> p.getUserId().equals(ctx.userId())).findFirst();
            boolean gave = mine.map(p -> p.getSubmittedAt() != null).orElse(false);
            List<PanelView> views = panel.stream().map(p -> {
                boolean show = seesAll || gave || p.getUserId().equals(ctx.userId());
                return new PanelView(people.get(p.getUserId()), show ? p.getRecommendation() : null, show ? p.getNotes() : null,
                        p.getSubmittedAt());
            }).sorted(Comparator.comparing(v -> v.person() == null ? "" : v.person().name(), String.CASE_INSENSITIVE_ORDER)).toList();
            out.add(new InterviewView(i.getId(), c.getId(), c.getName(), jobTitle, i.getStartsAt(), i.getDurationMinutes(),
                    i.getLocation(), views, mine.isPresent() && !c.closed()));
        }
        return out;
    }

    private CandidateDetail detail(OrgContext ctx, JobOpening j, Candidate c) {
        boolean canManage = manages(ctx, j);
        StoredFile cv = c.getCvFileId() == null ? null : files.findById(c.getCvFileId()).orElse(null);
        List<OnboardingItem> its = items.findByCandidateIdOrderBySortOrder(c.getId());
        Map<UUID, PersonRef> doneBy = profiles.persons(its.stream().map(OnboardingItem::getDoneBy).filter(Objects::nonNull).toList());
        return new CandidateDetail(summaries(List.of(c)).get(0), jobViews(ctx, List.of(j)).get(0), c.getNotes(),
                c.getRejectedReason(), c.getStartDate(), cv == null ? null : cv.getName(), cv == null ? null : cv.getSizeBytes(),
                c.getInvitationId() != null, joined(c),
                interviewViews(ctx, interviews.findByCandidateIdOrderByStartsAt(c.getId()), canManage),
                its.stream().map(x -> new OnboardingView(x.getId(), x.getTitle(), x.getDueDate(), x.getDoneAt() != null,
                        x.getDoneAt(), x.getDoneBy() == null ? null : doneBy.get(x.getDoneBy()))).toList(),
                canManage, HrAccess.manages(ctx) && !c.closed());
    }
}
