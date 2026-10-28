package com.businessplatform.document.service;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.document.domain.DocFolder;
import com.businessplatform.document.domain.Document;
import com.businessplatform.document.payload.DocumentPayloads.*;
import com.businessplatform.document.repository.DocFolderRepository;
import com.businessplatform.document.repository.DocumentRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Folders of the document library: browse, create, rename, move and delete (to the trash). */
@Service
@RequiredArgsConstructor
public class FolderService {

    private final DocumentAccess access;
    private final DocFolderRepository folders;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final ActivityService activity;
    private final Clock clock;

    /** The folders and files directly in {@code folderId} (null = the top level), folders first, by name. */
    @Transactional(readOnly = true)
    public FolderContents contents(String slug, UUID folderId) {
        OrgContext ctx = access.require(slug);
        FolderTree tree = access.tree(ctx);
        DocFolder folder = folderId == null ? null : access.liveFolder(tree, folderId);
        List<DocFolder> subfolders = tree.children(folderId);
        List<Document> docs = folderId == null ? documents.findByOrgIdAndFolderIdIsNullAndDeletedAtIsNull(ctx.orgId())
                : documents.findByOrgIdAndFolderIdAndDeletedAtIsNull(ctx.orgId(), folderId);
        docs = docs.stream().sorted(Comparator.comparing(Document::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        Map<UUID, Long> counts = new HashMap<>();
        documents.countByFolder(ctx.orgId()).forEach(r -> counts.put((UUID) r[0], (Long) r[1]));
        Map<UUID, PersonRef> people = access.people(subfolders.stream().map(DocFolder::getCreatedBy).toList());
        List<FolderResponse> folderRows = subfolders.stream().map(f -> new FolderResponse(f.getId(), f.getName(),
                f.getParentId(), tree.children(f.getId()).size() + counts.getOrDefault(f.getId(), 0L).intValue(),
                people.get(f.getCreatedBy()), f.getCreatedAt(), f.getUpdatedAt(),
                DocumentAccess.canManage(ctx, f.getCreatedBy()))).toList();
        return new FolderContents(folder == null ? null : new FolderRef(folder.getId(), folder.getName()),
                tree.path(folderId), folderRows, documentService.responses(ctx, docs), true,
                folder == null ? ctx.isAdmin() : DocumentAccess.canManage(ctx, folder.getCreatedBy()));
    }

    /** Every folder out of the trash, for the "move to" picker. */
    @Transactional(readOnly = true)
    public List<FolderOption> options(String slug) {
        OrgContext ctx = access.require(slug);
        return access.tree(ctx).allLive().stream().map(f -> new FolderOption(f.getId(), f.getName(), f.getParentId()))
                .toList();
    }

    @Transactional
    public FolderRef create(String slug, CreateFolderRequest in) {
        OrgContext ctx = access.require(slug);
        FolderTree tree = access.tree(ctx);
        if (in.parentId() != null) {
            access.liveFolder(tree, in.parentId());
        }
        String name = in.name().strip();
        checkFree(tree, in.parentId(), name, null);
        DocFolder f = new DocFolder();
        f.setId(Ids.next());
        f.setOrgId(ctx.orgId());
        f.setParentId(in.parentId());
        f.setName(name);
        f.setCreatedBy(ctx.userId());
        f.setCreatedAt(clock.instant());
        f.setUpdatedAt(f.getCreatedAt());
        folders.save(f);
        activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.FOLDER, f.getId(), ctx.userId(),
                "folder.created", Map.of("name", name));
        return new FolderRef(f.getId(), f.getName());
    }

    @Transactional
    public FolderRef rename(String slug, UUID id, RenameFolderRequest in) {
        OrgContext ctx = access.require(slug);
        FolderTree tree = access.tree(ctx);
        DocFolder f = access.liveFolder(tree, id);
        DocumentAccess.requireManage(ctx, f.getCreatedBy());
        String name = in.name().strip();
        if (!name.equals(f.getName())) {
            checkFree(tree, f.getParentId(), name, f.getId());
            activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.FOLDER, f.getId(), ctx.userId(),
                    "folder.renamed", Map.of("from", f.getName(), "to", name));
            f.setName(name);
            f.setUpdatedAt(clock.instant());
        }
        return new FolderRef(f.getId(), f.getName());
    }

    @Transactional
    public FolderRef move(String slug, UUID id, MoveRequest in) {
        OrgContext ctx = access.require(slug);
        FolderTree tree = access.tree(ctx);
        DocFolder f = access.liveFolder(tree, id);
        DocumentAccess.requireManage(ctx, f.getCreatedBy());
        UUID target = in.folderId();
        if (Objects.equals(target, f.getParentId())) {
            return new FolderRef(f.getId(), f.getName());
        }
        if (target != null) {
            access.liveFolder(tree, target);
            if (tree.within(target, f.getId())) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "folderId",
                        "A folder can't be moved into itself");
            }
        }
        checkFree(tree, target, f.getName(), f.getId());
        f.setParentId(target);
        f.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.FOLDER, f.getId(), ctx.userId(),
                "folder.moved", Map.of("name", f.getName(), "to", target == null ? "" : tree.find(target).orElseThrow().getName()));
        return new FolderRef(f.getId(), f.getName());
    }

    /**
     * Moves the folder, and everything in it, to the trash. A member who isn't an admin may only do so
     * when everything inside was added by them.
     */
    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        FolderTree tree = access.tree(ctx);
        DocFolder f = access.liveFolder(tree, id);
        DocumentAccess.requireManage(ctx, f.getCreatedBy());
        if (!ctx.isAdmin()) {
            Set<UUID> inside = tree.subtree(f.getId());
            boolean others = Stream.concat(
                    inside.stream().map(x -> tree.find(x).orElseThrow().getCreatedBy()),
                    documents.findByOrgIdAndFolderIdIn(ctx.orgId(), inside).stream().map(Document::getUploadedBy))
                    .anyMatch(u -> !u.equals(ctx.userId()));
            if (others) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "This folder holds other people's files; ask an admin to delete it", List.of());
            }
        }
        f.setDeletedAt(clock.instant());
        f.setDeletedBy(ctx.userId());
        activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.FOLDER, f.getId(), ctx.userId(),
                "folder.deleted", Map.of("name", f.getName()));
    }

    /** Back out of the trash; to the top level when the folder it was in is gone. */
    @Transactional
    public FolderRef restore(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        FolderTree tree = access.tree(ctx);
        DocFolder f = tree.find(id).filter(x -> x.getDeletedAt() != null).orElseThrow(BusinessException::notFound);
        if (!DocumentAccess.canManage(ctx, f.getCreatedBy()) && !ctx.userId().equals(f.getDeletedBy())) {
            throw BusinessException.forbidden();
        }
        if (!tree.isLive(f.getParentId())) {
            f.setParentId(null);
        }
        f.setName(tree.freeName(f.getParentId(), f.getName(), f.getId()));
        f.setDeletedAt(null);
        f.setDeletedBy(null);
        f.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), DocumentAccess.MODULE, DocumentAccess.FOLDER, f.getId(), ctx.userId(),
                "folder.restored", Map.of("name", f.getName()));
        return new FolderRef(f.getId(), f.getName());
    }

    private static void checkFree(FolderTree tree, UUID parentId, String name, UUID except) {
        if (tree.taken(parentId, name, except)) {
            throw BusinessException.field(ErrorCode.CONFLICT, "name", "A folder with this name is already here");
        }
    }
}
