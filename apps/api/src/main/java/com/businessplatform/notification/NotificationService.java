package com.businessplatform.notification;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.persistence.Ids;

import lombok.RequiredArgsConstructor;

/** In-app notifications. The actor never notifies themselves. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final Clock clock;

    @Transactional
    public void send(UUID orgId, Collection<UUID> to, UUID actorId, String type, String title, String body, String link) {
        to.stream().distinct().filter(u -> u != null && !u.equals(actorId)).forEach(u -> {
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

    @Transactional(readOnly = true)
    public long unread(UUID userId, UUID orgId) {
        return notifications.countByUserIdAndOrgIdAndReadAtIsNull(userId, orgId);
    }

    @Transactional
    public void markRead(UUID userId, UUID orgId, UUID id) {
        notifications.markRead(userId, orgId, id, clock.instant());
    }
}
