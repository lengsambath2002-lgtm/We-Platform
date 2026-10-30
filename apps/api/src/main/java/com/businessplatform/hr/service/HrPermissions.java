package com.businessplatform.hr.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.hr.domain.JobOpening;
import com.businessplatform.hr.repository.RecruitmentRepositories.InterviewPanelRepository;
import com.businessplatform.hr.repository.RecruitmentRepositories.JobOpeningRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.PermissionSource;

import lombok.RequiredArgsConstructor;

/**
 * {@code hr.recruit}: HR managers, hiring managers of an opening still running, and interviewers — the people who
 * see Recruitment. Reads the repositories directly: the core's OrgService uses this, and RecruitmentService uses
 * OrgService (to invite hires).
 */
@Component
@RequiredArgsConstructor
public class HrPermissions implements PermissionSource {

    private final JobOpeningRepository jobs;
    private final InterviewPanelRepository panels;

    @Override
    @Transactional(readOnly = true)
    public List<String> permissions(OrgContext ctx, Set<String> enabledModules) {
        if (!enabledModules.contains(HrAccess.MODULE) || ctx.isGuest()) {
            return List.of();
        }
        boolean involved = HrAccess.recruits(ctx)
                || jobs.existsByOrgIdAndHiringManagerIdAndStatusNot(ctx.orgId(), ctx.userId(), JobOpening.CLOSED)
                || panels.onAnyPanel(ctx.orgId(), ctx.userId());
        if (!involved) {
            return List.of();
        }
        // hr.recruit.manage: every opening, the onboarding template and hiring (not just the caller's own openings).
        return HrAccess.recruits(ctx) ? List.of("hr.recruit", "hr.recruit.manage") : List.of("hr.recruit");
    }
}
