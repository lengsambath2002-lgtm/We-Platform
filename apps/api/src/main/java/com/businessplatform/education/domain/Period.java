package com.businessplatform.education.domain;

import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A weekly lesson of a course: weekday 1 (Monday) … 6 (Saturday), from {@code startsAt} to {@code endsAt}. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_period")
public class Period {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false)
    private short weekday;

    @Column(name = "starts_at", nullable = false)
    private LocalTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalTime endsAt;

    private String room;

    /** Same weekday and the times overlap (touching ends don't). */
    public boolean overlaps(short day, LocalTime start, LocalTime end) {
        return weekday == day && startsAt.isBefore(end) && start.isBefore(endsAt);
    }
}
