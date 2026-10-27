package com.businessplatform.file;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    List<StoredFile> findByIdInAndOrgId(Collection<UUID> ids, UUID orgId);
}
