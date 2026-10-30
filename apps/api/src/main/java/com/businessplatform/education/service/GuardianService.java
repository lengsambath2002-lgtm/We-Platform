package com.businessplatform.education.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.education.domain.Guardian;
import com.businessplatform.education.domain.Student;
import com.businessplatform.education.domain.StudentGuardian;
import com.businessplatform.education.payload.EducationPayloads.GuardianRequest;
import com.businessplatform.education.payload.EducationPayloads.GuardianView;
import com.businessplatform.education.payload.EducationPayloads.StudentRef;
import com.businessplatform.education.repository.GuardianRepository;
import com.businessplatform.education.repository.StudentGuardianRepository;
import com.businessplatform.education.repository.StudentRepository;
import com.businessplatform.education.service.EducationAccess.Edu;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * Parents and guardians: contacts linked to students. Admins add and edit them; teachers see those of the
 * students in their classes (to reach a family), read only.
 */
@Service
@RequiredArgsConstructor
public class GuardianService {

    public static final String RECORD = "edu_guardian";
    static final int MAX_PAGE = 100;

    private final EducationAccess access;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository links;
    private final StudentRepository students;
    private final ActivityService activity;
    private final Clock clock;

    /** {@code q}: name, phone or email. By name. */
    @Transactional(readOnly = true)
    public PageResponse<GuardianView> list(String slug, String q, int page, int size) {
        Edu edu = access.require(slug);
        Specification<Guardian> spec = (g, query, cb) -> cb.equal(g.get("orgId"), edu.orgId());
        Set<UUID> visible = access.visibleStudents(edu);
        if (visible != null) {
            if (visible.isEmpty()) {
                return new PageResponse<>(List.of(), page, size, 0);
            }
            spec = spec.and((g, query, cb) -> {
                Subquery<UUID> linked = query.subquery(UUID.class);
                Root<StudentGuardian> l = linked.from(StudentGuardian.class);
                linked.select(l.get("guardianId")).where(l.get("studentId").in(visible));
                return g.get("id").in(linked);
            });
        }
        if (q != null && !q.isBlank()) {
            String like = EduRefs.like(q);
            spec = spec.and((g, query, cb) -> cb.or(cb.like(cb.lower(g.get("name")), like, '\\'),
                    cb.like(cb.lower(g.get("phone")), like, '\\'), cb.like(cb.lower(g.get("email")), like, '\\')));
        }
        Page<Guardian> found = guardians.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE),
                Sort.by("name", "id")));
        return PageResponse.of(new PageImpl<>(views(edu, found.getContent()), found.getPageable(), found.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public GuardianView get(String slug, UUID id) {
        Edu edu = access.require(slug);
        Guardian g = find(edu, id);
        if (!edu.admin() && links.findByGuardianId(g.getId()).stream().noneMatch(l -> access.sees(edu, l.getStudentId()))) {
            throw BusinessException.notFound();
        }
        return views(edu, List.of(g)).get(0);
    }

    @Transactional
    public GuardianView save(String slug, UUID id, GuardianRequest in) {
        Edu edu = access.requireAdmin(slug);
        Guardian g = id == null ? null : find(edu, id);
        g = apply(edu, g, in);
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, g.getId(), edu.userId(),
                id == null ? "education.guardian.created" : "education.guardian.updated", Map.of("name", g.getName()));
        return views(edu, List.of(g)).get(0);
    }

    /** Creates ({@code g} null) or updates a guardian; needs a phone or an email. */
    Guardian apply(Edu edu, Guardian g, GuardianRequest in) {
        String phone = EduRefs.clean(in.phone());
        String email = EduRefs.clean(in.email());
        if (phone == null && email == null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "phone", "Give a phone number or an email");
        }
        if (g == null) {
            g = new Guardian();
            g.setId(Ids.next());
            g.setOrgId(edu.orgId());
            g.setCreatedAt(clock.instant());
        }
        g.setName(in.name().strip());
        g.setPhone(phone);
        g.setEmail(email);
        g.setOccupation(EduRefs.clean(in.occupation()));
        g.setAddress(EduRefs.clean(in.address()));
        g.setUpdatedAt(clock.instant());
        return guardians.saveAndFlush(g);
    }

    Guardian find(Edu edu, UUID id) {
        return guardians.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    private List<GuardianView> views(Edu edu, List<Guardian> found) {
        List<StudentGuardian> ls = found.isEmpty() ? List.of() : links.findByGuardianIdIn(found.stream().map(Guardian::getId).toList());
        Set<UUID> visible = access.visibleStudents(edu);
        Map<UUID, Student> ss = new HashMap<>();
        students.findByIdInAndOrgId(ls.stream().map(StudentGuardian::getStudentId).distinct().toList(), edu.orgId())
                .forEach(s -> ss.put(s.getId(), s));
        return found.stream().map(g -> {
            List<StudentRef> children = ls.stream().filter(l -> l.getGuardianId().equals(g.getId()))
                    .filter(l -> visible == null || visible.contains(l.getStudentId()))
                    .map(l -> EduRefs.student(ss.get(l.getStudentId()))).filter(java.util.Objects::nonNull).toList();
            return new GuardianView(g.getId(), g.getName(), g.getPhone(), g.getEmail(), g.getOccupation(), g.getAddress(), children);
        }).toList();
    }
}
