package com.businessplatform.notification;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** My notifications in an organisation, and which kinds I get. */
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

    /** {@code unread}: how many are unread in all; {@code total}: how many match the filter. */
    public record Inbox(List<Item> items, long unread, int page, int size, long total) {
    }

    public record Kind(String kind, boolean inApp) {
    }

    public record Preferences(List<Kind> kinds) {
    }

    public record PreferencesRequest(@NotNull @Size(max = 20) List<String> muted) {
    }

    /** Newest first, a page at a time ({@code unread=true}: only unread ones). */
    @GetMapping
    public ResponseEntity<ApiResponse<Inbox>> list(@PathVariable String org, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, @RequestParam(defaultValue = "false") boolean unread) {
        OrgContext ctx = access.require(org);
        Page<Notification> found = notifications.page(ctx.userId(), ctx.orgId(), unread, page, size);
        List<Item> items = found.getContent().stream()
                .map(n -> new Item(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(), n.getActorId(),
                        n.getReadAt(), n.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(new Inbox(items, notifications.unread(ctx.userId(), ctx.orgId()),
                found.getNumber(), found.getSize(), found.getTotalElements())));
    }

    /** Marks one ({@code id}) or all as read. */
    @PostMapping("/read")
    public ResponseEntity<ApiResponse<Void>> read(@PathVariable String org, @RequestParam(required = false) UUID id) {
        OrgContext ctx = access.require(org);
        notifications.markRead(ctx.userId(), ctx.orgId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<Preferences>> preferences(@PathVariable String org) {
        OrgContext ctx = access.require(org);
        return ResponseEntity.ok(ApiResponse.ok(preferences(notifications.muted(ctx.membershipId()))));
    }

    /** Replaces the kinds I don't want in this organisation. */
    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<Preferences>> setPreferences(@PathVariable String org,
            @Valid @RequestBody PreferencesRequest in) {
        OrgContext ctx = access.require(org);
        notifications.setMuted(ctx.membershipId(), in.muted());
        return ResponseEntity.ok(ApiResponse.ok(preferences(notifications.muted(ctx.membershipId()))));
    }

    private static Preferences preferences(Set<String> muted) {
        return new Preferences(NotificationService.KINDS.stream().map(k -> new Kind(k, !muted.contains(k))).toList());
    }
}
