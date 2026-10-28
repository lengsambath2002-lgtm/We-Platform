package com.businessplatform.document.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.activity.Activity;
import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.document.domain.DocFolder;
import com.businessplatform.document.domain.Document;
import com.businessplatform.document.domain.DocumentVersion;
import com.businessplatform.document.payload.DocumentPayloads.*;
import com.businessplatform.document.repository.DocumentRepository;
import com.businessplatform.document.repository.DocumentVersionRepository;
import com.businessplatform.file.FileService;
import com.businessplatform.file.StoredFile;
import com.businessplatform.file.StoredFileRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Files of the document library: upload, new versions (older ones stay downloadable), rename, move,
 * delete to the trash and restore, search and download. Every change lands in the activity log.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int SEARCH_LIMIT = 50;

    private final DocumentAccess access;
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final StoredFileRepository files;
    private final FileService fileService;
    private final ActivityService activity;
    private final Clock clock;

    /** A file to send back, with the name to save it under. */
    public record Download(StoredFile file, String name) {
    }

    // ── Read ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DocumentDetail get(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        FolderTree tree = access.tree(ctx);
        Document d = access.liveDocument(ctx, tree, id);
        List<DocumentVersion> vs = versions.findByDocumentIdOrderByVersionDesc(d.getId());
        Map<UUID, StoredFile> fs = files(ctx, vs.stream().map(DocumentVersion::getFileId).toList());
        List<Activity> as = activity.forRecord(ctx.orgId(), DocumentAccess.DOCUMENT, d.getId());
        Map<UUID, PersonRef> people = access.people(Stream.concat(vs.stream().map(DocumentVersion::getUploadedBy),
                as.stream().map(Activity::getActorId)).toList());
        List<VersionResponse> versionRows = vs.stream().map(v -> {
            StoredFile f = fs.get(v.getFileId());
            return new VersionResponse(v.getVersion(), f == null ? d.getName() : f.getName(),
                    f == null ? "" : f.getContentType(), f == null ? 0 : f.getSizeBytes(), v.getNote(),
                    people.get(v.getUploadedBy()), v.getCreatedAt(), v.getVersion() == d.getVersion());
        }).toList();
        List<ActivityView> history = as.stream().map(a -> new ActivityView(a.getId(), people.get(a.getActorId()),
                a.getVerb(), a.getData(), a.getCreatedAt())).toList().reversed();
        return new DocumentDetail(responses(ctx, List.of(d)).get(0), tree.path(d.getFolderId()), versionRows, history);
    }

    /** Files whose name contains {@code q}, anywhere in the library, most recently changed first. */
    @Transactional(readOnly = true)
    public List<SearchHit> search(String slug, String q) {
        OrgContext ctx = access.require(slug);
        String term = q == null ? "" : q.strip();
        if (term.isEmpty()) {
            return List.of();
        }
        FolderTree tree = access.tree(ctx);
        List<Document> found = documents.findByOrgIdAndDeletedAtIsNullAndNameContainingIgnoreCaseOrderByUpdatedAtDesc(
                ctx.orgId(), term, PageRequest.of(0, SEARCH_LIMIT)).stream()
                .filter(d -> tree.isLive(d.getFolderId())).toList();
        List<DocumentResponse> rows = responses(ctx, found);
        List<SearchHit> out = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            out.add(new SearchHit(rows.get(i), tree.path(found.get(i).getFolderId())));
        }
        return out;
    }

    /** The current version ({@code version} null) or an older one, under a name worth saving. */
    @Transactional(readOnly = true)
    public Download download(String slug, UUID id, Integer version) {
        OrgContext ctx = access.require(slug);
        Document d = access.liveDocument(ctx, access.tree(ctx), id);
        UUID fileId = version == null || version == d.getVersion() ? d.getFileId()
                : versions.findByDocumentIdAndVersion(d.getId(), version).orElseThrow(BusinessException::notFound)
                        .getFileId();
        StoredFile f = files.findById(fileId).filter(x -> x.getOrgId().equals(ctx.orgId()))
                .orElseThrow(BusinessException::notFound);
        return new Download(f, version == null ? withExtension(d.getName(), f.getName()) : f.getName());
    }

    // ── Write ────────────────────────────────────────────────────────────────────────

    @Transactional
    public DocumentResponse upload(String slug, UUID folderId, String description, MultipartFile upload) {
        OrgContext ctx = access.require(slug);
        FolderTree tree = access.tree(ctx);
        if (folderId != null) {
            access.liveFolder(tree, folderId);
        }
        String desc = description(description);
        StoredFile f = fileService.store(ctx.orgId(), ctx.userId(), upload);
        Document d = new Document();
        d.setId(Ids.next());
        d.setOrgId(ctx.orgId());
        d.setFolderId(folderId);
        d.setName(f.getName());
        d.setDescription(desc);
        d.setVersion(1);
        d.setFileId(f.getId());
        d.setUploadedBy(ctx.userId());
        d.setUpdatedBy(ctx.userId());
        d.setCreatedAt(clock.instant());
        d.setUpdatedAt(d.getCreatedAt());
        documents.save(d);
        saveVersion(ctx, d, f, null);
        activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.DOCUMENT, d.getId(), ctx.userId(),
                "document.uploaded", Map.of("name", d.getName()));
        return responses(ctx, List.of(d)).get(0);
    }

    /** Any member who can see the file may upload a new version; the earlier ones are kept. */
    @Transactional
    public DocumentResponse newVersion(String slug, UUID id, String note, MultipartFile upload) {
        OrgContext ctx = access.require(slug);
        Document d = access.liveDocument(ctx, access.tree(ctx), id);
        String n = note == null || note.isBlank() ? null : note.strip();
        if (n != null && n.length() > 500) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "note", "Keep the note under 500 characters");
        }
        StoredFile f = fileService.store(ctx.orgId(), ctx.userId(), upload);
        Document locked = documents.lock(d.getId()).orElseThrow();
        locked.setVersion(locked.getVersion() + 1);
        locked.setFileId(f.getId());
        locked.setUpdatedBy(ctx.userId());
        locked.setUpdatedAt(clock.instant());
        saveVersion(ctx, locked, f, n);
        Map<String, Object> data = new LinkedHashMap<>(Map.of("version", locked.getVersion(), "file", f.getName()));
        if (n != null) {
            data.put("note", n);
        }
        activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.DOCUMENT, d.getId(), ctx.userId(),
                "document.versioned", data);
        return responses(ctx, List.of(locked)).get(0);
    }

    @Transactional
    public DocumentResponse update(String slug, UUID id, UpdateDocumentRequest in) {
        OrgContext ctx = access.require(slug);
        Document d = access.liveDocument(ctx, access.tree(ctx), id);
        DocumentAccess.requireManage(ctx, d.getUploadedBy());
        if (in.name() != null && !in.name().isBlank() && !in.name().strip().equals(d.getName())) {
            String name = in.name().strip().replaceAll("[\\\\/\\r\\n]", "_");
            activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.DOCUMENT, d.getId(), ctx.userId(),
                    "document.renamed", Map.of("from", d.getName(), "to", name));
            d.setName(name);
            touch(ctx, d);
        }
        if (in.description() != null && !Objects.equals(description(in.description()), d.getDescription())) {
            d.setDescription(description(in.description()));
            activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.DOCUMENT, d.getId(), ctx.userId(),
                    "document.described", Map.of());
            touch(ctx, d);
        }
        return responses(ctx, List.of(d)).get(0);
    }

    @Transactional
    public DocumentResponse move(String slug, UUID id, MoveRequest in) {
        OrgContext ctx = access.require(slug);
        FolderTree tree = access.tree(ctx);
        Document d = access.liveDocument(ctx, tree, id);
        DocumentAccess.requireManage(ctx, d.getUploadedBy());
        if (!Objects.equals(in.folderId(), d.getFolderId())) {
            DocFolder target = in.folderId() == null ? null : access.liveFolder(tree, in.folderId());
            d.setFolderId(in.folderId());
            touch(ctx, d);
            activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.DOCUMENT, d.getId(), ctx.userId(),
                    "document.moved", Map.of("to", target == null ? "" : target.getName()));
        }
        return responses(ctx, List.of(d)).get(0);
    }

    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Document d = access.liveDocument(ctx, access.tree(ctx), id);
        DocumentAccess.requireManage(ctx, d.getUploadedBy());
        d.setDeletedAt(clock.instant());
        d.setDeletedBy(ctx.userId());
        activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.DOCUMENT, d.getId(), ctx.userId(),
                "document.deleted", Map.of("name", d.getName()));
    }

    /** Back out of the trash; to the top level when the folder it was in is gone. */
    @Transactional
    public DocumentResponse restore(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        FolderTree tree = access.tree(ctx);
        Document d = documents.findByIdAndOrgId(id, ctx.orgId()).filter(x -> x.getDeletedAt() != null)
                .orElseThrow(BusinessException::notFound);
        if (!DocumentAccess.canManage(ctx, d.getUploadedBy()) && !ctx.userId().equals(d.getDeletedBy())) {
            throw BusinessException.forbidden();
        }
        if (!tree.isLive(d.getFolderId())) {
            d.setFolderId(null);
        }
        d.setDeletedAt(null);
        d.setDeletedBy(null);
        touch(ctx, d);
        activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.DOCUMENT, d.getId(), ctx.userId(),
                "document.restored", Map.of("name", d.getName()));
        return responses(ctx, List.of(d)).get(0);
    }

    /**
     * The top of the trash: deleted files in live folders and deleted folders in live folders, newest
     * first. Admins see everything; others what they added or deleted.
     */
    @Transactional(readOnly = true)
    public List<TrashItem> trash(String slug) {
        OrgContext ctx = access.require(slug);
        FolderTree tree = access.tree(ctx);
        List<Document> docs = documents.findByOrgIdAndDeletedAtIsNotNull(ctx.orgId()).stream()
                .filter(d -> tree.isLive(d.getFolderId()))
                .filter(d -> DocumentAccess.canManage(ctx, d.getUploadedBy()) || ctx.userId().equals(d.getDeletedBy()))
                .toList();
        List<DocFolder> fs = tree.trashedTop().stream()
                .filter(f -> DocumentAccess.canManage(ctx, f.getCreatedBy()) || ctx.userId().equals(f.getDeletedBy()))
                .toList();
        Map<UUID, StoredFile> stored = files(ctx, docs.stream().map(Document::getFileId).toList());
        Map<UUID, PersonRef> people = access.people(Stream.concat(docs.stream().map(Document::getDeletedBy),
                fs.stream().map(DocFolder::getDeletedBy)).toList());
        List<TrashItem> out = new ArrayList<>();
        fs.forEach(f -> out.add(new TrashItem("FOLDER", f.getId(), f.getName(), null, null,
                people.get(f.getDeletedBy()), f.getDeletedAt())));
        docs.forEach(d -> {
            StoredFile f = stored.get(d.getFileId());
            out.add(new TrashItem("DOCUMENT", d.getId(), d.getName(), f == null ? null : f.getContentType(),
                    f == null ? null : f.getSizeBytes(), people.get(d.getDeletedBy()), d.getDeletedAt()));
        });
        out.sort(Comparator.comparing(TrashItem::deletedAt).reversed());
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    /** Rows for the table, in the given order (one query for files, one for people). */
    List<DocumentResponse> responses(OrgContext ctx, List<Document> docs) {
        Map<UUID, StoredFile> fs = files(ctx, docs.stream().map(Document::getFileId).toList());
        Map<UUID, PersonRef> people = access.people(docs.stream()
                .flatMap(d -> Stream.of(d.getUploadedBy(), d.getUpdatedBy())).toList());
        return docs.stream().map(d -> {
            StoredFile f = fs.get(d.getFileId());
            return new DocumentResponse(d.getId(), d.getName(), d.getDescription(), d.getFolderId(),
                    f == null ? "" : f.getContentType(), f == null ? 0 : f.getSizeBytes(), d.getVersion(),
                    people.get(d.getUploadedBy()), people.get(d.getUpdatedBy()), d.getCreatedAt(), d.getUpdatedAt(),
                    DocumentAccess.canManage(ctx, d.getUploadedBy()));
        }).toList();
    }

    private Map<UUID, StoredFile> files(OrgContext ctx, List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return files.findByIdInAndOrgId(ids, ctx.orgId()).stream()
                .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
    }

    private void saveVersion(OrgContext ctx, Document d, StoredFile f, String note) {
        DocumentVersion v = new DocumentVersion();
        v.setId(Ids.next());
        v.setOrgId(ctx.orgId());
        v.setDocumentId(d.getId());
        v.setVersion(d.getVersion());
        v.setFileId(f.getId());
        v.setNote(note);
        v.setUploadedBy(ctx.userId());
        v.setCreatedAt(clock.instant());
        versions.save(v);
    }

    private void touch(OrgContext ctx, Document d) {
        d.setUpdatedBy(ctx.userId());
        d.setUpdatedAt(clock.instant());
    }

    private static String description(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String d = raw.strip();
        if (d.length() > 2000) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "description",
                    "Keep the description under 2000 characters");
        }
        return d;
    }

    /** "Budget" + "budget-v3.xlsx" → "Budget.xlsx", so a renamed file still opens with the right program. */
    static String withExtension(String name, String original) {
        int dot = original.lastIndexOf('.');
        if (dot <= 0 || dot == original.length() - 1) {
            return name;
        }
        String ext = original.substring(dot);
        return name.toLowerCase().endsWith(ext.toLowerCase()) ? name : name + ext;
    }
}
