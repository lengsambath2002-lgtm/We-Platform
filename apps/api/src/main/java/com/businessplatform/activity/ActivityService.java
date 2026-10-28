package com.businessplatform.activity;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.api.PageResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Writes and reads the activity history. Callers check access to the record first; the organisation-wide
 * audit log is for Owners and Admins only.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    /** The organisation's time zone for the audit log's day filters. */
    static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");
    static final int MAX_PAGE = 100;

    private final ActivityRepository activities;
    private final OrgAccess access;
    private final UserRepository users;
    private final List<RecordLabeler> labelers;
    private final Clock clock;

    public record AuditFilter(UUID actorId, String module, String recordType, String verb, LocalDate from,
            LocalDate to) {
    }

    public record Actor(UUID id, String name, String avatarUrl) {
    }

    /** {@code record}: the record's current name and link, null when it is gone or has none. */
    public record AuditEntry(UUID id, Actor actor, String module, String recordType, UUID recordId, String verb,
            Map<String, Object> data, Instant at, RecordLabeler.Label record) {
    }

    @Transactional
    public void record(UUID orgId, String module, String recordType, UUID recordId, UUID actorId, String verb,
            Map<String, Object> data) {
        Activity a = new Activity();
        a.setId(Ids.next());
        a.setOrgId(orgId);
        a.setModuleKey(module);
        a.setRecordType(recordType);
        a.setRecordId(recordId);
        a.setActorId(actorId);
        a.setVerb(verb);
        a.setData(data == null ? Map.of() : data);
        a.setCreatedAt(clock.instant());
        activities.save(a);
    }

    @Transactional(readOnly = true)
    public List<Activity> forRecord(UUID orgId, String recordType, UUID recordId) {
        return activities.findByOrgIdAndRecordTypeAndRecordIdOrderByCreatedAtAsc(orgId, recordType, recordId);
    }

    @Transactional(readOnly = true)
    public List<Activity> recentFor(UUID orgId, List<UUID> recordIds, int limit) {
        if (recordIds.isEmpty()) {
            return List.of();
        }
        return activities.findByOrgIdAndRecordIdInOrderByCreatedAtDesc(orgId, recordIds, PageRequest.of(0, limit));
    }

    /** The organisation's audit log, newest first (Owners and Admins). */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> audit(String slug, AuditFilter f, int page, int size) {
        OrgContext ctx = access.requireAdmin(slug);
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "to", "The end is before the start");
        }
        Specification<Activity> spec = (a, q, cb) -> cb.equal(a.get("orgId"), ctx.orgId());
        if (f.actorId() != null) {
            spec = spec.and((a, q, cb) -> cb.equal(a.get("actorId"), f.actorId()));
        }
        if (f.module() != null && !f.module().isBlank()) {
            spec = spec.and((a, q, cb) -> cb.equal(a.get("moduleKey"), f.module()));
        }
        if (f.recordType() != null && !f.recordType().isBlank()) {
            spec = spec.and((a, q, cb) -> cb.equal(a.get("recordType"), f.recordType()));
        }
        if (f.verb() != null && !f.verb().isBlank()) {
            String prefix = f.verb().replace("\\", "").replace("%", "").replace("_", "\\_") + "%";
            spec = spec.and((a, q, cb) -> cb.like(a.get("verb"), prefix, '\\'));
        }
        if (f.from() != null) {
            Instant start = f.from().atStartOfDay(ZONE).toInstant();
            spec = spec.and((a, q, cb) -> cb.greaterThanOrEqualTo(a.get("createdAt"), start));
        }
        if (f.to() != null) {
            Instant end = f.to().plusDays(1).atStartOfDay(ZONE).toInstant();
            spec = spec.and((a, q, cb) -> cb.lessThan(a.get("createdAt"), end));
        }
        Page<Activity> found = activities.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<UUID, Actor> actors = new HashMap<>();
        List<UUID> actorIds = found.getContent().stream().map(Activity::getActorId).filter(Objects::nonNull).distinct()
                .toList();
        if (!actorIds.isEmpty()) {
            users.findByIdIn(actorIds).forEach(u -> actors.put(u.getId(), new Actor(u.getId(), u.getName(),
                    u.getAvatarUrl())));
        }
        Map<String, Map<UUID, RecordLabeler.Label>> labels = new HashMap<>();
        found.getContent().stream().collect(Collectors.groupingBy(Activity::getRecordType,
                Collectors.mapping(Activity::getRecordId, Collectors.toSet())))
                .forEach((type, ids) -> labelers.stream().filter(l -> l.handles(type)).findFirst()
                        .ifPresent(l -> labels.put(type, l.labels(ctx, type, ids))));
        return PageResponse.of(found.map(a -> new AuditEntry(a.getId(),
                a.getActorId() == null ? null : actors.get(a.getActorId()), a.getModuleKey(), a.getRecordType(),
                a.getRecordId(), a.getVerb(), a.getData(), a.getCreatedAt(),
                labels.getOrDefault(a.getRecordType(), Map.of()).get(a.getRecordId()))));
    }
}
