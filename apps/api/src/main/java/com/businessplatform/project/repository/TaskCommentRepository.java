package com.businessplatform.project.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.project.domain.TaskComment;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    List<TaskComment> findByTaskIdOrderByCreatedAt(UUID taskId);

    Optional<TaskComment> findByIdAndTaskIdAndOrgId(UUID id, UUID taskId, UUID orgId);
}
