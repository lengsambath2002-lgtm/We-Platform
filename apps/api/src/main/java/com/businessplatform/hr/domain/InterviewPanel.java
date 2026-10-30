package com.businessplatform.hr.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One interviewer on an interview, with their recommendation once they give it. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "interview_panel")
@IdClass(InterviewPanel.Key.class)
public class InterviewPanel {

    @Id
    @Column(name = "interview_id")
    private UUID interviewId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    private String recommendation;

    private String notes;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID interviewId;
        private UUID userId;
    }
}
