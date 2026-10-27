package com.businessplatform.activity;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.persistence.Ids;

import lombok.RequiredArgsConstructor;

/** Writes and reads the activity history. Callers check access to the record first. */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activities;
    private final Clock clock;

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
}
