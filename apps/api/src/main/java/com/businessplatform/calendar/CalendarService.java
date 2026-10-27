package com.businessplatform.calendar;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * The organisation calendar: events (organisation-wide, or tied to a project) plus every module's
 * deadlines. Who sees an event: its owner, attendees, Owners/Admins, and — when it is tied to a project —
 * people who can see that project, otherwise every non-guest member.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");
    static final Duration MAX_RANGE = Duration.ofDays(62);

    private final OrgAccess access;
    private final CalendarEventRepository events;
    private final MembershipRepository memberships;
    private final List<DeadlineSource> sources;
    private final NotificationService notifications;
    private final Clock clock;

    public record EventRequest(@NotBlank @Size(max = 200) String title, @Size(max = 5000) String description,
            @Size(max = 200) String location, @NotNull Instant startsAt, @NotNull Instant endsAt, boolean allDay,
            UUID projectId, List<UUID> attendeeIds) {
    }

    public record EventView(UUID id, String title, String description, String location, Instant startsAt,
            Instant endsAt, boolean allDay, UUID projectId, UUID ownerId, List<UUID> attendeeIds, boolean canEdit) {
    }

    public record CalendarView(List<EventView> events, List<DeadlineSource.Deadline> deadlines) {
    }

    @Transactional(readOnly = true)
    public CalendarView range(String slug, LocalDate from, LocalDate to, boolean mineOnly) {
        OrgContext ctx = access.require(slug);
        if (to.isBefore(from) || Duration.between(from.atStartOfDay(), to.atStartOfDay()).compareTo(MAX_RANGE) > 0) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "to", "At most 62 days");
        }
        Instant start = from.atStartOfDay(ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<EventView> evs = new ArrayList<>();
        for (CalendarEvent e : events.overlapping(ctx.orgId(), start, end)) {
            List<UUID> att = events.attendees(e.getId());
            if (canSee(ctx, e, att) && (!mineOnly || e.getOwnerId().equals(ctx.userId()) || att.contains(ctx.userId()))) {
                evs.add(view(ctx, e, att));
            }
        }
        List<DeadlineSource.Deadline> ds = new ArrayList<>();
        sources.forEach(s -> ds.addAll(s.deadlines(ctx, from, to, mineOnly)));
        return new CalendarView(evs, ds);
    }

    @Transactional
    public EventView create(String slug, EventRequest in) {
        OrgContext ctx = access.require(slug);
        if (ctx.isGuest()) {
            throw BusinessException.forbidden();
        }
        CalendarEvent e = new CalendarEvent();
        e.setId(Ids.next());
        e.setOrgId(ctx.orgId());
        e.setOwnerId(ctx.userId());
        e.setCreatedAt(clock.instant());
        apply(ctx, e, in);
        events.saveAndFlush(e);
        List<UUID> att = setAttendees(ctx, e, in.attendeeIds());
        notifications.send(ctx.orgId(), att, ctx.userId(), "event.invited", "Invitation: " + e.getTitle(), null,
                "/" + ctx.slug() + "/calendar?date=" + e.getStartsAt().atZone(ZONE).toLocalDate());
        return view(ctx, e, att);
    }

    @Transactional
    public EventView update(String slug, UUID id, EventRequest in) {
        OrgContext ctx = access.require(slug);
        CalendarEvent e = events.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        if (!canEdit(ctx, e)) {
            throw BusinessException.forbidden();
        }
        apply(ctx, e, in);
        return view(ctx, e, setAttendees(ctx, e, in.attendeeIds()));
    }

    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        CalendarEvent e = events.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        if (!canEdit(ctx, e)) {
            throw BusinessException.forbidden();
        }
        events.delete(e);
    }

    /** Upcoming events for the Overview (next {@code days} days, mine or org-wide visible). */
    @Transactional(readOnly = true)
    public List<EventView> upcoming(OrgContext ctx, int days) {
        Instant now = clock.instant();
        List<EventView> out = new ArrayList<>();
        for (CalendarEvent e : events.overlapping(ctx.orgId(), now, now.plus(Duration.ofDays(days)))) {
            List<UUID> att = events.attendees(e.getId());
            if (canSee(ctx, e, att)) {
                out.add(view(ctx, e, att));
            }
        }
        return out;
    }

    private void apply(OrgContext ctx, CalendarEvent e, EventRequest in) {
        if (in.endsAt().isBefore(in.startsAt())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endsAt", "Ends before it starts");
        }
        if (in.projectId() != null && sources.stream().noneMatch(s -> s.canSeeProject(ctx, in.projectId()))) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "projectId", "Unknown project");
        }
        e.setTitle(in.title().strip());
        e.setDescription(in.description() == null || in.description().isBlank() ? null : in.description().strip());
        e.setLocation(in.location() == null || in.location().isBlank() ? null : in.location().strip());
        e.setStartsAt(in.startsAt());
        e.setEndsAt(in.endsAt());
        e.setAllDay(in.allDay());
        e.setProjectId(in.projectId());
    }

    private List<UUID> setAttendees(OrgContext ctx, CalendarEvent e, List<UUID> ids) {
        List<UUID> wanted = ids == null ? List.of() : ids.stream().distinct().toList();
        Map<UUID, Membership> active = new java.util.HashMap<>();
        memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus())).forEach(m -> active.put(m.getUserId(), m));
        if (!active.keySet().containsAll(wanted)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "attendeeIds", "Not a member of the organisation");
        }
        events.clearAttendees(e.getId());
        wanted.forEach(u -> events.addAttendee(e.getId(), u));
        return wanted;
    }

    private boolean canSee(OrgContext ctx, CalendarEvent e, List<UUID> attendees) {
        if (ctx.isAdmin() || e.getOwnerId().equals(ctx.userId()) || attendees.contains(ctx.userId())) {
            return true;
        }
        if (e.getProjectId() != null) {
            return sources.stream().anyMatch(s -> s.canSeeProject(ctx, e.getProjectId()));
        }
        return !ctx.isGuest();
    }

    private static boolean canEdit(OrgContext ctx, CalendarEvent e) {
        return ctx.isAdmin() || e.getOwnerId().equals(ctx.userId());
    }

    private static EventView view(OrgContext ctx, CalendarEvent e, List<UUID> att) {
        return new EventView(e.getId(), e.getTitle(), e.getDescription(), e.getLocation(), e.getStartsAt(),
                e.getEndsAt(), e.isAllDay(), e.getProjectId(), e.getOwnerId(), att, canEdit(ctx, e));
    }
}
