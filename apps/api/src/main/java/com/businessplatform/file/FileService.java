package com.businessplatform.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;

/**
 * Stores uploads on disk under {@code platform.files.dir} (an S3-compatible store can replace it).
 * Only common document and image types; the name is kept for downloads, never used as a path.
 */
@Service
public class FileService {

    static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf",
            "text/plain", "text/csv", "application/zip",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/msword", "application/vnd.ms-excel");

    private final StoredFileRepository files;
    private final Clock clock;
    private final Path root;

    public FileService(StoredFileRepository files, Clock clock, @Value("${platform.files.dir}") String dir) {
        this.files = files;
        this.clock = clock;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Transactional
    public StoredFile store(UUID orgId, UUID ownerId, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "file", "Choose a file");
        }
        String type = upload.getContentType() == null ? "" : upload.getContentType().toLowerCase();
        if (!ALLOWED.contains(type)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "file", "This file type is not allowed");
        }
        StoredFile f = new StoredFile();
        f.setId(Ids.next());
        f.setOrgId(orgId);
        f.setOwnerId(ownerId);
        String name = upload.getOriginalFilename() == null ? "file" : upload.getOriginalFilename();
        f.setName(name.replaceAll("[\\\\/\\r\\n]", "_").substring(0, Math.min(255, name.length())));
        f.setContentType(type);
        f.setSizeBytes(upload.getSize());
        f.setStorageKey(orgId + "/" + f.getId());
        f.setCreatedAt(clock.instant());
        try {
            Path target = root.resolve(f.getStorageKey()).normalize();
            Files.createDirectories(target.getParent());
            upload.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files.save(f);
    }

    public byte[] read(StoredFile f) {
        try {
            return Files.readAllBytes(root.resolve(f.getStorageKey()).normalize());
        } catch (IOException e) {
            throw BusinessException.notFound();
        }
    }
}
