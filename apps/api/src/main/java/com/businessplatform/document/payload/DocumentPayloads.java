package com.businessplatform.document.payload;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Requests and responses of the document library. */
public final class DocumentPayloads {

    private DocumentPayloads() {
    }

    // ── Requests ─────────────────────────────────────────────────────────────────────

    public record CreateFolderRequest(@NotBlank @Size(max = 120) String name, UUID parentId) {
    }

    public record RenameFolderRequest(@NotBlank @Size(max = 120) String name) {
    }

    /** Where to move a folder or document; {@code folderId} null = the top level. */
    public record MoveRequest(UUID folderId) {
    }

    /** Fields left null stay as they are; an empty description clears it. */
    public record UpdateDocumentRequest(@Size(min = 1, max = 255) String name, @Size(max = 2000) String description) {
    }

    // ── Responses ────────────────────────────────────────────────────────────────────

    public record PersonRef(UUID id, String name, String avatarUrl) {
    }

    public record FolderRef(UUID id, String name) {
    }

    /** A folder for the move picker; the client builds the tree from {@code parentId}. */
    public record FolderOption(UUID id, String name, UUID parentId) {
    }

    public record FolderResponse(UUID id, String name, UUID parentId, int items, PersonRef createdBy, Instant createdAt,
            Instant updatedAt, boolean canManage) {
    }

    public record DocumentResponse(UUID id, String name, String description, UUID folderId, String contentType, long size,
            int version, PersonRef uploadedBy, PersonRef updatedBy, Instant createdAt, Instant updatedAt,
            boolean canManage) {
    }

    /** One folder's contents ({@code folder} null = the top level), with the path from the top. */
    public record FolderContents(FolderRef folder, List<FolderRef> path, List<FolderResponse> folders,
            List<DocumentResponse> documents, boolean canUpload, boolean canManage) {
    }

    public record SearchHit(DocumentResponse document, List<FolderRef> path) {
    }

    public record VersionResponse(int version, String fileName, String contentType, long size, String note,
            PersonRef uploadedBy, Instant createdAt, boolean current) {
    }

    public record ActivityView(UUID id, PersonRef actor, String verb, Map<String, Object> data, Instant at) {
    }

    public record DocumentDetail(DocumentResponse document, List<FolderRef> path, List<VersionResponse> versions,
            List<ActivityView> activity) {
    }

    /** Something in the trash: {@code kind} FOLDER or DOCUMENT. */
    public record TrashItem(String kind, UUID id, String name, String contentType, Long size, PersonRef deletedBy,
            Instant deletedAt) {
    }
}
