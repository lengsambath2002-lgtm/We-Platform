package com.businessplatform.hr.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Someone applying for a job opening, at one stage of the pipeline. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "candidate")
public class Candidate {

    public static final List<String> STAGES = List.of("NEW", "SCREENING", "INTERVIEW", "OFFER", "HIRED", "REJECTED");
    public static final List<String> SOURCES = List.of("REFERRAL", "JOB_BOARD", "WEBSITE", "WALK_IN", "AGENCY", "OTHER");
    public static final String HIRED = "HIRED";
    public static final String REJECTED = "REJECTED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private String name;

    private String email;

    private String phone;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String stage;

    private Short rating;

    private String notes;

    @Column(name = "cv_file_id")
    private UUID cvFileId;

    @Column(name = "rejected_reason")
    private String rejectedReason;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "invitation_id")
    private UUID invitationId;

    @Column(name = "hired_at")
    private Instant hiredAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean closed() {
        return HIRED.equals(stage) || REJECTED.equals(stage);
    }
}
