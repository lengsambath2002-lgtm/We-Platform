package com.businessplatform.project.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.project.domain.ProjectMember;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMember.Key> {

    List<ProjectMember> findByProjectId(UUID projectId);

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    List<ProjectMember> findByUserIdAndProjectIdIn(UUID userId, List<UUID> projectIds);
}
