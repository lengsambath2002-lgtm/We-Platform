package com.businessplatform.education.service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.education.domain.AcademicYear;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Teacher;
import com.businessplatform.education.payload.EducationPayloads.TeacherRequest;
import com.businessplatform.education.payload.EducationPayloads.TeacherView;
import com.businessplatform.education.repository.CourseRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.TeacherRepository;
import com.businessplatform.education.service.EducationAccess.Edu;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;

import lombok.RequiredArgsConstructor;

/**
 * Teachers: organisation members (not guests) with a teacher profile, added and edited by admins. A switched-off
 * profile keeps its history but no longer gives access. Everyone in the module sees the staff list.
 */
@Service
@RequiredArgsConstructor
public class TeacherService {

    public static final String RECORD = "edu_teacher";

    private final EducationAccess access;
    private final StructureService structure;
    private final EduRefs refs;
    private final TeacherRepository teachers;
    private final SchoolClassRepository classes;
    private final CourseRepository courses;
    private final MembershipRepository memberships;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    /** By name; teachers see active colleagues only. */
    @Transactional(readOnly = true)
    public List<TeacherView> list(String slug) {
        Edu edu = access.require(slug);
        List<Teacher> found = teachers.findByOrgId(edu.orgId()).stream().filter(t -> t.isActive() || edu.admin()).toList();
        return views(edu, found);
    }

    @Transactional
    public TeacherView save(String slug, UUID id, TeacherRequest in) {
        Edu edu = access.requireAdmin(slug);
        Teacher t;
        if (id == null) {
            if (in.userId() == null) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "userId", "Choose a member");
            }
            Membership m = memberships.findByOrgIdAndUserId(edu.orgId(), in.userId())
                    .filter(x -> Membership.ACTIVE.equals(x.getStatus()))
                    .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "userId", "Choose a member"));
            if (m.getRole() == OrgRole.GUEST) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "userId", "A guest can't be a teacher");
            }
            t = teachers.findByOrgIdAndUserId(edu.orgId(), in.userId()).orElse(null);
            if (t != null && t.isActive()) {
                throw BusinessException.field(ErrorCode.CONFLICT, "userId", "This member is already a teacher");
            }
            if (t == null) {
                t = new Teacher();
                t.setId(Ids.next());
                t.setOrgId(edu.orgId());
                t.setUserId(in.userId());
                t.setCreatedAt(clock.instant());
            }
            t.setActive(true);
        } else {
            t = find(edu, id);
            if (in.active() != null) {
                t.setActive(in.active());
            }
        }
        t.setPhone(EduRefs.clean(in.phone()));
        t.setSpecialty(EduRefs.clean(in.specialty()));
        teachers.saveAndFlush(t);
        AppUser u = refs.users(List.of(t.getUserId())).get(t.getUserId());
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, t.getId(), edu.userId(),
                id == null ? "education.teacher.added" : "education.teacher.updated",
                Map.of("name", u == null ? "" : u.getName(), "active", t.isActive()));
        if (id == null) {
            notifications.send(edu.orgId(), List.of(t.getUserId()), edu.userId(), "education.teacher.added",
                    Text.of("You were added as a teacher in {org}", Map.of("org", edu.ctx().orgName())), null,
                    "/" + edu.slug() + "/education");
        }
        return views(edu, List.of(t)).get(0);
    }

    public Teacher find(Edu edu, UUID id) {
        return teachers.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    /** An active teacher of the organisation, or a refusal on {@code field}. */
    public Teacher usable(Edu edu, UUID id, String field) {
        return teachers.findByIdAndOrgId(id, edu.orgId()).filter(Teacher::isActive)
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, field, "Choose an active teacher"));
    }

    private List<TeacherView> views(Edu edu, List<Teacher> found) {
        Map<UUID, AppUser> us = refs.users(found.stream().map(Teacher::getUserId).toList());
        UUID year = structure.currentYear(edu.orgId()).map(AcademicYear::getId).orElse(null);
        List<SchoolClass> current = year == null ? List.of() : classes.findByOrgIdAndYearIdOrderByName(edu.orgId(), year);
        var currentIds = current.stream().map(SchoolClass::getId).toList();
        var courseList = currentIds.isEmpty() ? List.<com.businessplatform.education.domain.Course>of() : courses.findByClassIdIn(currentIds);
        return found.stream().map(t -> {
            AppUser u = us.get(t.getUserId());
            List<String> homerooms = current.stream().filter(c -> t.getId().equals(c.getHomeroomTeacherId())).map(SchoolClass::getName).toList();
            int n = (int) courseList.stream().filter(c -> c.getTeacherId().equals(t.getId())).count();
            return new TeacherView(t.getId(), t.getUserId(), u == null ? "" : u.getName(), u == null ? null : u.getEmail(),
                    u == null ? null : u.getAvatarUrl(), t.getPhone(), t.getSpecialty(), t.isActive(), homerooms, n);
        }).sorted(Comparator.comparing(TeacherView::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
