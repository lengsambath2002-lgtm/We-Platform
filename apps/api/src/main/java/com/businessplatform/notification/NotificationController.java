package com.businessplatform.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** My notifications in an organisation. */
@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/orgs/{org}/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;
    private final OrgAccess access;

    public record Item(UUID id, String type, String title, String body, String link, UUID actorId, Instant readAt,
            Instant createdAt) {
    }

    public record Inbox(List<Item> items, long unread) {
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Inbox>> list(@PathVariable String org) {
        OrgContext ctx = access.require(org);
        List<Item> items = notifications.list(ctx.userId(), ctx.orgId(), 50).stream()
                .map(n -> new Item(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(), n.getActorId(),
                        n.getReadAt(), n.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(new Inbox(items, notifications.unread(ctx.userId(), ctx.orgId()))));
    }

    /** Marks one ({@code id}) or all as read. */
    @PostMapping("/read")
    public ResponseEntity<ApiResponse<Void>> read(@PathVariable String org, @RequestParam(required = false) UUID id) {
        OrgContext ctx = access.require(org);
        notifications.markRead(ctx.userId(), ctx.orgId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
