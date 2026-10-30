package com.businessplatform.ops.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.ops.domain.TicketAttachment;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

    List<TicketAttachment> findByOrgIdAndTicketIdOrderByCreatedAt(UUID orgId, UUID ticketId);

    Optional<TicketAttachment> findByOrgIdAndTicketIdAndFileId(UUID orgId, UUID ticketId, UUID fileId);
}
