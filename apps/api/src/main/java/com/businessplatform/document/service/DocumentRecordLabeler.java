package com.businessplatform.document.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.RecordLabeler;
import com.businessplatform.document.repository.DocFolderRepository;
import com.businessplatform.document.repository.DocumentRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Names and links of documents and folders for the audit log; items in the trash count as gone. */
@Component
@RequiredArgsConstructor
public class DocumentRecordLabeler implements RecordLabeler {

    private final DocumentRepository documents;
    private final DocFolderRepository folders;

    @Override
    public boolean handles(String recordType) {
        return DocumentAccess.DOCUMENT.equals(recordType) || DocumentAccess.FOLDER.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids) {
        Map<UUID, Label> out = new HashMap<>();
        String base = "/" + ctx.slug() + "/documents";
        if (DocumentAccess.DOCUMENT.equals(recordType)) {
            documents.findAllById(ids).stream()
                    .filter(d -> d.getOrgId().equals(ctx.orgId()) && d.getDeletedAt() == null)
                    .forEach(d -> out.put(d.getId(), new Label(d.getName(),
                            d.getFolderId() == null ? base : base + "?folder=" + d.getFolderId())));
        } else {
            folders.findAllById(ids).stream()
                    .filter(f -> f.getOrgId().equals(ctx.orgId()) && f.getDeletedAt() == null)
                    .forEach(f -> out.put(f.getId(), new Label(f.getName(), base + "?folder=" + f.getId())));
        }
        return out;
    }
}
