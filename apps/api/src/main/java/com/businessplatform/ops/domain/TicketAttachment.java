package com.businessplatform.ops.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A file on a ticket (stored by the platform's FileService). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "svc_ticket_attachment")
public class TicketAttachment {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
