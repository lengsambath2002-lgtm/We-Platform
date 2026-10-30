package com.businessplatform.hr.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.RecordLabeler;
import com.businessplatform.hr.repository.AttendanceRecordRepository;
import com.businessplatform.hr.repository.DevelopmentRepositories.ReviewCycleRepository;
import com.businessplatform.hr.repository.DevelopmentRepositories.ReviewRepository;
import com.businessplatform.hr.repository.DevelopmentRepositories.TrainingCourseRepository;
import com.businessplatform.hr.repository.OvertimeRequestRepository;
import com.businessplatform.hr.repository.PayrollRepositories.PayRunRepository;
import com.businessplatform.hr.repository.RecruitmentRepositories.CandidateRepository;
import com.businessplatform.hr.repository.RecruitmentRepositories.JobOpeningRepository;
import com.businessplatform.hr.repository.WorkScheduleRepository;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Names and links of attendance records, overtime requests, work schedules and pay runs for the audit log. */
@Component
@RequiredArgsConstructor
public class AttendanceRecordLabeler implements RecordLabeler {

    private final AttendanceRecordRepository records;
    private final OvertimeRequestRepository overtime;
    private final WorkScheduleRepository schedules;
    private final PayRunRepository runs;
    private final JobOpeningRepository jobs;
    private final CandidateRepository candidates;
    private final ReviewCycleRepository cycles;
    private final ReviewRepository reviews;
    private final TrainingCourseRepository courses;
    private final UserRepository users;

    @Override
    public boolean handles(String recordType) {
        return TimekeepingService.RECORD.equals(recordType) || OvertimeService.RECORD.equals(recordType)
                || ScheduleService.RECORD.equals(recordType) || PayrollService.RUN.equals(recordType)
                || RecruitmentService.JOB.equals(recordType) || RecruitmentService.CANDIDATE.equals(recordType)
                || ReviewService.CYCLE.equals(recordType) || ReviewService.REVIEW.equals(recordType)
                || TrainingService.COURSE.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids) {
        Map<UUID, Label> out = new HashMap<>();
        String base = "/" + ctx.slug() + "/hr";
        switch (recordType) {
            case TimekeepingService.RECORD -> {
                var found = records.findAllById(ids).stream().filter(r -> r.getOrgId().equals(ctx.orgId())).toList();
                Map<UUID, AppUser> people = people(found.stream().map(r -> r.getUserId()).toList());
                found.forEach(r -> out.put(r.getId(), new Label(people.get(r.getUserId()).getName() + " · " + r.getWorkDate(),
                        base + "/attendance/" + r.getUserId() + "?month=" + r.getWorkDate().toString().substring(0, 7))));
            }
            case OvertimeService.RECORD -> {
                var found = overtime.findAllById(ids).stream().filter(o -> o.getOrgId().equals(ctx.orgId())).toList();
                Map<UUID, AppUser> people = people(found.stream().map(o -> o.getUserId()).toList());
                found.forEach(o -> out.put(o.getId(), new Label(OvertimeService.title(o, people.get(o.getUserId())),
                        OvertimeService.link(ctx))));
            }
            case PayrollService.RUN -> {
                runs.findAllById(ids).stream().filter(r -> r.getOrgId().equals(ctx.orgId())).forEach(r -> out.put(r.getId(),
                        new Label("Payroll · " + PayrollService.monthName(r.getMonth()), base + "/payroll/" + r.getId())));
                // Settings changes are recorded against the organisation itself.
                ids.stream().filter(ctx.orgId()::equals).forEach(id -> out.put(id, new Label("Payroll settings", base + "/payroll/settings")));
            }
            case RecruitmentService.JOB -> {
                jobs.findAllById(ids).stream().filter(j -> j.getOrgId().equals(ctx.orgId()))
                        .forEach(j -> out.put(j.getId(), new Label(j.getTitle(), RecruitmentService.jobLink(ctx, j.getId()))));
                // Onboarding template changes are recorded against the organisation itself.
                ids.stream().filter(ctx.orgId()::equals).forEach(id -> out.put(id, new Label("Onboarding steps", base + "/recruitment?tab=onboarding")));
            }
            case RecruitmentService.CANDIDATE -> candidates.findAllById(ids).stream().filter(c -> c.getOrgId().equals(ctx.orgId()))
                    .forEach(c -> out.put(c.getId(), new Label(c.getName(), RecruitmentService.candidateLink(ctx, c.getId()))));
            case ReviewService.CYCLE -> cycles.findAllById(ids).stream().filter(c -> c.getOrgId().equals(ctx.orgId()))
                    .forEach(c -> out.put(c.getId(), new Label(c.getName(), base + "/reviews/cycles/" + c.getId())));
            case ReviewService.REVIEW -> {
                var found = reviews.findAllById(ids).stream().filter(r -> r.getOrgId().equals(ctx.orgId())).toList();
                Map<UUID, AppUser> people = people(found.stream().map(r -> r.getEmployeeId()).toList());
                found.forEach(r -> out.put(r.getId(), new Label(people.get(r.getEmployeeId()).getName(), base + "/reviews/" + r.getId())));
            }
            case TrainingService.COURSE -> courses.findAllById(ids).stream().filter(c -> c.getOrgId().equals(ctx.orgId()))
                    .forEach(c -> out.put(c.getId(), new Label(c.getTitle(), base + "/training/" + c.getId())));
            default -> schedules.findAllById(ids).stream().filter(s -> s.getOrgId().equals(ctx.orgId()))
                    .forEach(s -> out.put(s.getId(), new Label(s.getName(), base + "/settings")));
        }
        return out;
    }

    private Map<UUID, AppUser> people(Collection<UUID> ids) {
        Map<UUID, AppUser> out = new HashMap<>();
        if (!ids.isEmpty()) {
            users.findByIdIn(ids.stream().distinct().toList()).forEach(u -> out.put(u.getId(), u));
        }
        return out;
    }
}
