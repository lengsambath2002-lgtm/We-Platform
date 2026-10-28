package com.businessplatform.project.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.project.domain.Project;
import com.businessplatform.project.domain.ProjectMember;
import com.businessplatform.project.domain.ProjectRole;
import com.businessplatform.project.repository.ProjectMemberRepository;
import com.businessplatform.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

/**
 * Who may do what with a project (docs/02-architecture.md §5), checked on every request:
 * view — org Owner/Admin, a project member, or (ORG visibility) any non-guest member;
 * edit tasks — org Owner/Admin, project Lead or Member; manage — org Owner/Admin or project Lead.
 * A project the person may not see answers NOT_FOUND. An ARCHIVED project is read-only: nobody may change
 * its tasks or comments (INVALID_STATE) until a manager restores it.
 */
@Component
@RequiredArgsConstructor
public class ProjectAccess {

    private final OrgAccess orgAccess;
    private final ModuleService modules;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;

    /** The organisation context with the Projects module switched on. */
    public OrgContext org(String slug) {
        OrgContext ctx = orgAccess.require(slug);
        modules.require(ctx.orgId(), Module.PROJECTS);
        return ctx;
    }

    public record Access(Project project, ProjectRole role, boolean canEdit, boolean canManage) {
    }

    @Transactional(readOnly = true)
    public Access view(OrgContext ctx, String key) {
        Project p = projects.findByOrgIdAndKey(ctx.orgId(), key).orElseThrow(BusinessException::notFound);
        return check(ctx, p).orElseThrow(BusinessException::notFound);
    }

    @Transactional(readOnly = true)
    public Access view(OrgContext ctx, UUID projectId) {
        Project p = projects.findById(projectId).filter(x -> x.getOrgId().equals(ctx.orgId()))
                .orElseThrow(BusinessException::notFound);
        return check(ctx, p).orElseThrow(BusinessException::notFound);
    }

    /** Empty when {@code ctx}'s person may not see {@code p}. */
    public Optional<Access> check(OrgContext ctx, Project p) {
        ProjectRole role = members.findByProjectIdAndUserId(p.getId(), ctx.userId()).map(ProjectMember::getRole)
                .orElse(null);
        boolean visible = ctx.isAdmin() || role != null || ("ORG".equals(p.getVisibility()) && !ctx.isGuest());
        if (!visible) {
            return Optional.empty();
        }
        boolean manage = ctx.isAdmin() || role == ProjectRole.LEAD;
        boolean edit = !archived(p) && (manage || role == ProjectRole.MEMBER);
        return Optional.of(new Access(p, role, edit, manage));
    }

    public Access requireEdit(OrgContext ctx, UUID projectId) {
        Access a = view(ctx, projectId);
        requireOpen(a.project());
        if (!a.canEdit()) {
            throw BusinessException.forbidden();
        }
        return a;
    }

    public Access requireManage(OrgContext ctx, String key) {
        Access a = view(ctx, key);
        if (!a.canManage()) {
            throw BusinessException.forbidden();
        }
        return a;
    }

    public static boolean archived(Project p) {
        return "ARCHIVED".equals(p.getStatus());
    }

    /** Refuses changes to the tasks of an archived project. */
    public static void requireOpen(Project p) {
        if (archived(p)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This project is archived", List.of());
        }
    }
}
