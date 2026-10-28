package com.businessplatform.notification;

import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;

import lombok.RequiredArgsConstructor;

/**
 * In-app notifications. The actor never notifies themselves, and nobody gets a kind they switched off
 * in this organisation ({@link #KINDS}).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** The kinds a person can switch on or off; every notification type belongs to one. */
    public static final List<String> KINDS = List.of("task.assigned", "task.mentioned", "task.commented", "task.status",
            "project.added", "event.invited", "approval.requested", "approval.decided");
    static final int MAX_PAGE = 100;

    private final NotificationRepository notifications;
    private final Clock clock;

    /** The kind of a notification type (approvals group their steps). */
    public static String kindOf(String type) {
        return switch (type) {
            case "approval.requested", "approval.resubmitted" -> "approval.requested";
            case "approval.approved", "approval.rejected", "approval.changes_requested", "approval.step.approved" ->
                "approval.decided";
            default -> type;
        };
    }

    @Transactional
    public void send(UUID orgId, Collection<UUID> to, UUID actorId, String type, String title, String body, String link) {
        List<UUID> wanted = to.stream().distinct().filter(u -> u != null && !u.equals(actorId)).toList();
        if (wanted.isEmpty()) {
            return;
        }
        Set<UUID> muting = new HashSet<>(notifications.muting(orgId, kindOf(type), wanted));
        wanted.stream().filter(u -> !muting.contains(u)).forEach(u -> {
            Notification n = new Notification();
            n.setId(Ids.next());
            n.setOrgId(orgId);
            n.setUserId(u);
            n.setActorId(actorId);
            n.setType(type);
            n.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
            n.setBody(body == null ? null : body.length() > 500 ? body.substring(0, 500) : body);
            n.setLink(link);
            n.setCreatedAt(clock.instant());
            notifications.save(n);
        });
    }

    @Transactional(readOnly = true)
    public List<Notification> list(UUID userId, UUID orgId, int limit) {
        return notifications.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId, PageRequest.of(0, limit));
    }

    /** One page, newest first; {@code unreadOnly} leaves out what was read. */
    @Transactional(readOnly = true)
    public Page<Notification> page(UUID userId, UUID orgId, boolean unreadOnly, int page, int size) {
        PageRequest p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return unreadOnly ? notifications.findByUserIdAndOrgIdAndReadAtIsNull(userId, orgId, p)
                : notifications.findByUserIdAndOrgId(userId, orgId, p);
    }

    @Transactional(readOnly = true)
    public long unread(UUID userId, UUID orgId) {
        return notifications.countByUserIdAndOrgIdAndReadAtIsNull(userId, orgId);
    }

    @Transactional
    public void markRead(UUID userId, UUID orgId, UUID id) {
        notifications.markRead(userId, orgId, id, clock.instant());
    }

    /** The kinds this member switched off. */
    @Transactional(readOnly = true)
    public Set<String> muted(UUID membershipId) {
        return Set.copyOf(notifications.mutedKinds(membershipId));
    }

    @Transactional
    public void setMuted(UUID membershipId, Collection<String> kinds) {
        if (!KINDS.containsAll(kinds)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "muted", "Unknown kind of notification");
        }
        notifications.clearMutes(membershipId);
        kinds.stream().distinct().forEach(k -> notifications.mute(membershipId, k));
    }
}
