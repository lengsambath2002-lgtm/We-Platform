package com.businessplatform.document.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.document.domain.DocumentVersion;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByDocumentIdOrderByVersionDesc(UUID documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersion(UUID documentId, int version);
}
