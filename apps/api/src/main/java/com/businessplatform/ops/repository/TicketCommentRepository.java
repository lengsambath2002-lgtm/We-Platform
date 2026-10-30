package com.businessplatform.ops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.ops.domain.TicketComment;

public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

    List<TicketComment> findByOrgIdAndTicketIdOrderByCreatedAt(UUID orgId, UUID ticketId);
}
