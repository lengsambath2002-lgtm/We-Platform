package com.businessplatform.notification;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Messages;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.identity.repository.UserRepository;

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
    private final UserRepository users;
    private final Messages messages;
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

    /** Sends to each person in their own language (title and body are translated per recipient). */
    @Transactional
    public void send(UUID orgId, Collection<UUID> to, UUID actorId, String type, Text title, Text body, String link) {
        List<UUID> wanted = to.stream().distinct().filter(u -> u != null && !u.equals(actorId)).toList();
        if (wanted.isEmpty()) {
            return;
        }
        Set<UUID> muting = new HashSet<>(notifications.muting(orgId, kindOf(type), wanted));
        Map<UUID, String> language = new HashMap<>();
        users.findAllById(wanted).forEach(u -> language.put(u.getId(), Messages.language(Locale.forLanguageTag(u.getLocale()))));
        wanted.stream().filter(u -> !muting.contains(u)).forEach(u -> {
            String lang = language.getOrDefault(u, "en");
            String heading = messages.in(lang, title);
            String text = messages.in(lang, body);
            Notification n = new Notification();
            n.setId(Ids.next());
            n.setOrgId(orgId);
            n.setUserId(u);
            n.setActorId(actorId);
            n.setType(type);
            n.setTitle(heading.length() > 200 ? heading.substring(0, 200) : heading);
            n.setBody(text == null ? null : text.length() > 500 ? text.substring(0, 500) : text);
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
