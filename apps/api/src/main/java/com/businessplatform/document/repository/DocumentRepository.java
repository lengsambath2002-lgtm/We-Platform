package com.businessplatform.document.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.document.domain.Document;

import jakarta.persistence.LockModeType;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndOrgId(UUID id, UUID orgId);

    /** The row, locked until the end of the transaction (new versions are numbered one at a time). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id = :id")
    Optional<Document> lock(UUID id);

    List<Document> findByOrgIdAndFolderIdAndDeletedAtIsNull(UUID orgId, UUID folderId);

    List<Document> findByOrgIdAndFolderIdIsNullAndDeletedAtIsNull(UUID orgId);

    List<Document> findByOrgIdAndDeletedAtIsNullAndNameContainingIgnoreCaseOrderByUpdatedAtDesc(UUID orgId, String q,
            Pageable page);

    List<Document> findByOrgIdAndDeletedAtIsNotNull(UUID orgId);

    List<Document> findByOrgIdAndFolderIdIn(UUID orgId, Collection<UUID> folderIds);

    /** Live documents per folder: {@code [folderId, count]}. */
    @Query("select d.folderId, count(d) from Document d where d.orgId = :orgId and d.deletedAt is null"
            + " and d.folderId is not null group by d.folderId")
    List<Object[]> countByFolder(UUID orgId);
}
