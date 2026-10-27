package com.businessplatform.org.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.org.domain.Team;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByOrgIdOrderByName(UUID orgId);

    Optional<Team> findByIdAndOrgId(UUID id, UUID orgId);

    @Query(value = "SELECT membership_id FROM team_member WHERE team_id = :teamId", nativeQuery = true)
    List<UUID> memberIds(UUID teamId);

    @Modifying
    @Query(value = "DELETE FROM team_member WHERE team_id = :teamId", nativeQuery = true)
    void clearMembers(UUID teamId);

    @Modifying
    @Query(value = "INSERT INTO team_member (team_id, membership_id) VALUES (:teamId, :membershipId)", nativeQuery = true)
    void addMember(UUID teamId, UUID membershipId);
}
