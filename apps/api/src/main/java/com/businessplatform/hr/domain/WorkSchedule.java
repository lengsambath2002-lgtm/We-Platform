package com.businessplatform.hr.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A shift pattern: start and end of the day, the unpaid break, the weekdays worked ({@code workDays}, ISO
 * numbers "1,2,3,4,5") and how many minutes after the start still count as on time. One per organisation
 * is the default for people without their own.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "work_schedule")
public class WorkSchedule {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_minutes", nullable = false)
    private short breakMinutes;

    @Column(name = "grace_minutes", nullable = false)
    private short graceMinutes;

    @Column(name = "work_days", nullable = false)
    private String workDays;

    @Column(name = "is_default", nullable = false)
    private boolean defaultSchedule;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Set<DayOfWeek> days() {
        return Arrays.stream(workDays.split(",")).map(d -> DayOfWeek.of(Integer.parseInt(d))).collect(Collectors.toSet());
    }

    public boolean worksOn(DayOfWeek d) {
        return days().contains(d);
    }

    /** The minutes a full day is worth: start to end, less the break. */
    public int dayMinutes() {
        return (int) java.time.Duration.between(startTime, endTime).toMinutes() - breakMinutes;
    }
}
