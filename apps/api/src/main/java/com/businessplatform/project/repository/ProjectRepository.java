package com.businessplatform.project.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.project.domain.Project;

import jakarta.persistence.LockModeType;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByOrgIdAndKey(UUID orgId, String key);

    boolean existsByOrgIdAndKey(UUID orgId, String key);

    List<Project> findByOrgIdOrderByName(UUID orgId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id")
    Optional<Project> lock(UUID id);
}
