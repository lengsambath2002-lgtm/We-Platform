package com.businessplatform.project.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.project.domain.Task;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndOrgId(UUID id, UUID orgId);

    List<Task> findByProjectIdAndParentIdIsNullOrderByStatusAscPositionAsc(UUID projectId);

    List<Task> findByParentIdOrderByNumber(UUID parentId);

    List<Task> findByProjectIdAndStatusAndParentIdIsNullOrderByPositionAsc(UUID projectId, String status);

    @Query("SELECT t FROM Task t WHERE t.orgId = :orgId AND t.assigneeId = :userId AND t.projectId IN :projectIds"
            + " ORDER BY CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END, t.dueDate ASC NULLS LAST, t.updatedAt DESC")
    List<Task> myTasks(UUID orgId, UUID userId, Collection<UUID> projectIds);

    @Query("SELECT t FROM Task t WHERE t.orgId = :orgId AND t.projectId IN :projectIds AND t.dueDate BETWEEN :from AND :to"
            + " AND (:userId IS NULL OR t.assigneeId = :userId)")
    List<Task> dueBetween(UUID orgId, Collection<UUID> projectIds, LocalDate from, LocalDate to, UUID userId);

    @Query("SELECT t.projectId, t.status, COUNT(t) FROM Task t WHERE t.projectId IN :projectIds AND t.parentId IS NULL"
            + " GROUP BY t.projectId, t.status")
    List<Object[]> countsByStatus(Collection<UUID> projectIds);

    @Query("SELECT t.parentId, COUNT(t), SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END) FROM Task t"
            + " WHERE t.parentId IN :parentIds GROUP BY t.parentId")
    List<Object[]> subtaskCounts(Collection<UUID> parentIds);

    @Query(value = "SELECT file_id FROM task_attachment WHERE task_id = :taskId", nativeQuery = true)
    List<UUID> attachmentIds(UUID taskId);

    @Modifying
    @Query(value = "INSERT INTO task_attachment (task_id, file_id) VALUES (:taskId, :fileId)", nativeQuery = true)
    void attach(UUID taskId, UUID fileId);

    @Modifying
    @Query(value = "DELETE FROM task_attachment WHERE task_id = :taskId AND file_id = :fileId", nativeQuery = true)
    int detach(UUID taskId, UUID fileId);

    @Query(value = "SELECT task_id, COUNT(*) FROM task_comment WHERE task_id IN :taskIds GROUP BY task_id",
            nativeQuery = true)
    List<Object[]> commentCounts(Collection<UUID> taskIds);
}
