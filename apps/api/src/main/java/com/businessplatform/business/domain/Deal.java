package com.businessplatform.business.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A sales opportunity: for a customer, or a lead (company / contact) not yet a customer. Stages LEAD → QUALIFIED →
 * PROPOSAL → NEGOTIATION, then WON or LOST (closed).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_deal")
public class Deal {

    public static final List<String> STAGES = List.of("LEAD", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "WON", "LOST");
    public static final String WON = "WON";
    public static final String LOST = "LOST";
    /** How likely an open deal at each stage is to be won, in percent (for the weighted forecast). */
    public static final Map<String, Integer> PROBABILITY = Map.of("LEAD", 10, "QUALIFIED", 25, "PROPOSAL", 50,
            "NEGOTIATION", 75, "WON", 100, "LOST", 0);

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String title;

    @Column(name = "customer_id")
    private UUID customerId;

    private String company;

    @Column(name = "contact_name")
    private String contactName;

    private String email;

    private String phone;

    @Column(nullable = false)
    private BigDecimal value;

    @Column(nullable = false)
    private String stage;

    @Column(nullable = false)
    private String source;

    @Column(name = "expected_close")
    private LocalDate expectedClose;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "next_step")
    private String nextStep;

    @Column(name = "next_step_date")
    private LocalDate nextStepDate;

    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "lost_reason")
    private String lostReason;

    @Column(name = "closed_at")
    private Instant closedAt;

    private String notes;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean closed() {
        return WON.equals(stage) || LOST.equals(stage);
    }
}
