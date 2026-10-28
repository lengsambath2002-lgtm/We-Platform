package com.businessplatform.workflow.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.repository.ModuleRepository;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.workflow.domain.WorkflowDefinition;
import com.businessplatform.workflow.domain.WorkflowStepDef;
import com.businessplatform.workflow.payload.WorkflowPayloads.*;
import com.businessplatform.workflow.repository.WorkflowRepositories;

import lombok.RequiredArgsConstructor;

/**
 * Approval routes. Every organisation starts with two (project lead sign-off, admin sign-off); Owners
 * and Admins add or change routes; everyone can list the active ones to choose from.
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final OrgAccess access;
    private final WorkflowRepositories.Definitions definitions;
    private final WorkflowRepositories.StepDefs stepDefs;
    private final ModuleRepository modules;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final Clock clock;

    @Transactional
    public List<WorkflowResponse> list(String slug) {
        OrgContext ctx = access.require(slug);
        ensureDefaults(ctx);
        return definitions.findByOrgIdOrderByName(ctx.orgId()).stream()
                .filter(w -> w.isActive() || ctx.isAdmin()).map(this::toResponse).toList();
    }

    @Transactional
    public WorkflowResponse save(String slug, UUID id, WorkflowRequest in) {
        OrgContext ctx = access.requireAdmin(slug);
        modules.findById(in.moduleKey()).orElseThrow(() ->
                BusinessException.field(ErrorCode.VALIDATION_FAILED, "moduleKey", "Unknown module"));
        WorkflowDefinition w;
        if (id == null) {
            if (definitions.existsByOrgIdAndName(ctx.orgId(), in.name().strip())) {
                throw BusinessException.field(ErrorCode.CONFLICT, "name", "Another workflow has this name");
            }
            w = new WorkflowDefinition();
            w.setId(Ids.next());
            w.setOrgId(ctx.orgId());
            w.setCreatedBy(ctx.userId());
            w.setCreatedAt(clock.instant());
        } else {
            w = definitions.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        }
        w.setName(in.name().strip());
        w.setModuleKey(in.moduleKey());
        w.setActive(in.active() == null || in.active());
        definitions.saveAndFlush(w);
        stepDefs.deleteByWorkflowId(w.getId());
        stepDefs.flush();
        if (!Module.PROJECTS.equals(in.moduleKey()) && in.steps().stream().anyMatch(s -> "PROJECT_LEAD".equals(s.type()))) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "steps", "Only project workflows can ask the project lead");
        }
        short order = 1;
        for (StepInput s : in.steps()) {
            stepDefs.save(step(ctx, w.getId(), order++, s));
        }
        return toResponse(w);
    }

    private WorkflowStepDef step(OrgContext ctx, UUID workflowId, short order, StepInput s) {
        WorkflowStepDef d = new WorkflowStepDef();
        d.setId(Ids.next());
        d.setWorkflowId(workflowId);
        d.setStepOrder(order);
        d.setApproverType(s.type());
        switch (s.type()) {
            case "USER" -> {
                boolean member = s.userId() != null && memberships.findByOrgIdAndUserId(ctx.orgId(), s.userId())
                        .filter(m -> Membership.ACTIVE.equals(m.getStatus())).isPresent();
                if (!member) {
                    throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "steps", "Choose a member of the organisation");
                }
                d.setApproverUser(s.userId());
            }
            case "ORG_ROLE" -> {
                if (s.role() == null) {
                    throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "steps", "Choose Owner or Admin");
                }
                d.setApproverRole(s.role());
            }
            default -> { }
        }
        return d;
    }

    /**
     * The workflow a module sends its records through without asking (e.g. leave requests): the active
     * one of {@code module} named {@code name}, else any active one of {@code module}, else {@code name}
     * created with one {@code step} (Admins may change it later in Admin → Workflows).
     */
    @Transactional
    public UUID ensure(OrgContext ctx, String module, String name, StepInput step) {
        ensureDefaults(ctx);
        List<WorkflowDefinition> mine = definitions.findByOrgIdOrderByName(ctx.orgId()).stream()
                .filter(w -> w.isActive() && w.getModuleKey().equals(module)).toList();
        return mine.stream().filter(w -> w.getName().equals(name)).findFirst().or(() -> mine.stream().findFirst())
                .map(WorkflowDefinition::getId).orElseGet(() -> {
                    String free = definitions.existsByOrgIdAndName(ctx.orgId(), name) ? name + " (" + module + ")" : name;
                    return create(ctx, module, free, step);
                });
    }

    private void ensureDefaults(OrgContext ctx) {
        if (definitions.countByOrgId(ctx.orgId()) > 0) {
            return;
        }
        create(ctx, Module.PROJECTS, "Project lead sign-off", new StepInput("PROJECT_LEAD", null, null));
        create(ctx, Module.PROJECTS, "Admin sign-off", new StepInput("ORG_ROLE", null, "ADMIN"));
    }

    private UUID create(OrgContext ctx, String module, String name, StepInput step) {
        WorkflowDefinition w = new WorkflowDefinition();
        w.setId(Ids.next());
        w.setOrgId(ctx.orgId());
        w.setModuleKey(module);
        w.setName(name);
        w.setCreatedBy(ctx.userId());
        w.setCreatedAt(clock.instant());
        definitions.saveAndFlush(w);
        stepDefs.save(step(ctx, w.getId(), (short) 1, step));
        return w.getId();
    }

    WorkflowResponse toResponse(WorkflowDefinition w) {
        List<WorkflowStepDef> steps = stepDefs.findByWorkflowIdOrderByStepOrder(w.getId());
        Map<UUID, String> names = users.findByIdIn(steps.stream().map(WorkflowStepDef::getApproverUser)
                .filter(java.util.Objects::nonNull).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(AppUser::getId, AppUser::getName));
        return new WorkflowResponse(w.getId(), w.getName(), w.getModuleKey(), w.isActive(),
                steps.stream().map(s -> new StepView(s.getStepOrder(), s.getApproverType(), s.getApproverUser(),
                        s.getApproverUser() == null ? null : names.get(s.getApproverUser()), s.getApproverRole()))
                        .toList());
    }
}
