package com.businessplatform.document.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.document.domain.DocFolder;

public interface DocFolderRepository extends JpaRepository<DocFolder, UUID> {

    /** Every folder of the organisation, trashed ones included (the tree is small and walked in memory). */
    List<DocFolder> findByOrgId(UUID orgId);
}
