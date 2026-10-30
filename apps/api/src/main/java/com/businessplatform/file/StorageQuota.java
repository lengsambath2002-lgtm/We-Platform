package com.businessplatform.file;

import java.util.UUID;

/** Refuses a file that would take an organisation past its storage (implemented by billing; none = no limit). */
public interface StorageQuota {

    void require(UUID orgId, long bytes);
}
