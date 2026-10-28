package com.businessplatform.document.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.document.payload.DocumentPayloads.*;
import com.businessplatform.document.service.DocumentService;
import com.businessplatform.document.service.FolderService;
import com.businessplatform.file.FileService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orgs/{org}/documents")
@RequiredArgsConstructor
@Tag(name = "Documents")
public class DocumentController {

    private final FolderService folders;
    private final DocumentService documents;
    private final FileService files;

    // ── Folders ──────────────────────────────────────────────────────────────────────

    /** A folder's contents with its path; without {@code folder}, the top level. */
    @GetMapping
    public ResponseEntity<ApiResponse<FolderContents>> contents(@PathVariable String org,
            @RequestParam(required = false) UUID folder) {
        return ResponseEntity.ok(ApiResponse.ok(folders.contents(org, folder)));
    }

    @GetMapping("/folders")
    public ResponseEntity<ApiResponse<List<FolderOption>>> folderOptions(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(folders.options(org)));
    }

    @PostMapping("/folders")
    public ResponseEntity<ApiResponse<FolderRef>> createFolder(@PathVariable String org,
            @Valid @RequestBody CreateFolderRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(folders.create(org, in)));
    }

    @PatchMapping("/folders/{id}")
    public ResponseEntity<ApiResponse<FolderRef>> renameFolder(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody RenameFolderRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(folders.rename(org, id, in)));
    }

    @PostMapping("/folders/{id}/move")
    public ResponseEntity<ApiResponse<FolderRef>> moveFolder(@PathVariable String org, @PathVariable UUID id,
            @RequestBody MoveRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(folders.move(org, id, in)));
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFolder(@PathVariable String org, @PathVariable UUID id) {
        folders.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/folders/{id}/restore")
    public ResponseEntity<ApiResponse<FolderRef>> restoreFolder(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(folders.restore(org, id)));
    }

    // ── Documents ────────────────────────────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<SearchHit>>> search(@PathVariable String org, @RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.ok(documents.search(org, q)));
    }

    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<List<TrashItem>>> trash(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(documents.trash(org)));
    }

    /** Uploads a file (at most 20 MB, common document and image types) into {@code folderId} or the top level. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(@PathVariable String org,
            @RequestParam("file") MultipartFile file, @RequestParam(required = false) UUID folderId,
            @RequestParam(required = false) String description) {
        return ResponseEntity.status(201).body(ApiResponse.ok(documents.upload(org, folderId, description, file)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentDetail>> get(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(documents.get(org, id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentResponse>> update(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(documents.update(org, id, in)));
    }

    @PostMapping("/{id}/move")
    public ResponseEntity<ApiResponse<DocumentResponse>> move(@PathVariable String org, @PathVariable UUID id,
            @RequestBody MoveRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(documents.move(org, id, in)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String org, @PathVariable UUID id) {
        documents.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<DocumentResponse>> restore(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(documents.restore(org, id)));
    }

    /** A new version; the earlier ones stay downloadable. */
    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> newVersion(@PathVariable String org, @PathVariable UUID id,
            @RequestParam("file") MultipartFile file, @RequestParam(required = false) String note) {
        return ResponseEntity.status(201).body(ApiResponse.ok(documents.newVersion(org, id, note, file)));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String org, @PathVariable UUID id) {
        return send(documents.download(org, id, null));
    }

    @GetMapping("/{id}/versions/{version}/download")
    public ResponseEntity<byte[]> downloadVersion(@PathVariable String org, @PathVariable UUID id,
            @PathVariable int version) {
        return send(documents.download(org, id, version));
    }

    private ResponseEntity<byte[]> send(DocumentService.Download d) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.file().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(files.read(d.file()));
    }
}
