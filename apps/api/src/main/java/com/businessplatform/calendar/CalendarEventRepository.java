package com.businessplatform.calendar;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    Optional<CalendarEvent> findByIdAndOrgId(UUID id, UUID orgId);

    @Query("SELECT e FROM CalendarEvent e WHERE e.orgId = :orgId AND e.startsAt < :to AND e.endsAt >= :from ORDER BY e.startsAt")
    List<CalendarEvent> overlapping(UUID orgId, Instant from, Instant to);

    @Query(value = "SELECT user_id FROM calendar_attendee WHERE event_id = :eventId", nativeQuery = true)
    List<UUID> attendees(UUID eventId);

    @Modifying
    @Query(value = "DELETE FROM calendar_attendee WHERE event_id = :eventId", nativeQuery = true)
    void clearAttendees(UUID eventId);

    @Modifying
    @Query(value = "INSERT INTO calendar_attendee (event_id, user_id) VALUES (:eventId, :userId)", nativeQuery = true)
    void addAttendee(UUID eventId, UUID userId);
}
