package com.businessplatform.document.service;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.document.domain.DocFolder;
import com.businessplatform.document.domain.Document;
import com.businessplatform.document.payload.DocumentPayloads.PersonRef;
import com.businessplatform.document.repository.DocFolderRepository;
import com.businessplatform.document.repository.DocumentRepository;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Who may do what in the document library. Every active member except guests may view and upload;
 * organisation admins and the person who added a folder or file may rename, move, delete and restore it.
 */
@Component
@RequiredArgsConstructor
public class DocumentAccess {

    /** The module key and record types written to the activity log. */
    public static final String MODULE = "documents";
    public static final String DOCUMENT = "document";
    public static final String FOLDER = "doc_folder";

    private final OrgAccess orgAccess;
    private final DocFolderRepository folders;
    private final DocumentRepository documents;
    private final UserRepository users;

    /** A member of the organisation who isn't a guest; NOT_FOUND for non-members, FORBIDDEN for guests. */
    public OrgContext require(String slug) {
        OrgContext ctx = orgAccess.require(slug);
        if (ctx.isGuest()) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    public static boolean canManage(OrgContext ctx, UUID addedBy) {
        return ctx.isAdmin() || ctx.userId().equals(addedBy);
    }

    static void requireManage(OrgContext ctx, UUID addedBy) {
        if (!canManage(ctx, addedBy)) {
            throw BusinessException.forbidden();
        }
    }

    FolderTree tree(OrgContext ctx) {
        return new FolderTree(folders.findByOrgId(ctx.orgId()));
    }

    /** A folder out of the trash (with everything above it); NOT_FOUND otherwise. */
    DocFolder liveFolder(FolderTree tree, UUID id) {
        return tree.live(id).orElseThrow(BusinessException::notFound);
    }

    /** A document of this organisation that is out of the trash, in a live folder; NOT_FOUND otherwise. */
    Document liveDocument(OrgContext ctx, FolderTree tree, UUID id) {
        return documents.findByIdAndOrgId(id, ctx.orgId())
                .filter(d -> d.getDeletedAt() == null && tree.isLive(d.getFolderId()))
                .orElseThrow(BusinessException::notFound);
    }

    @Transactional(readOnly = true)
    public Map<UUID, PersonRef> people(Collection<UUID> ids) {
        var wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return users.findByIdIn(wanted).stream().collect(Collectors.toMap(AppUser::getId,
                u -> new PersonRef(u.getId(), u.getName(), u.getAvatarUrl()), (a, b) -> a));
    }
}
