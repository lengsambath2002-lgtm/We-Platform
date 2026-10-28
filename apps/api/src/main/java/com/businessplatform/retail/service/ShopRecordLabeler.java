package com.businessplatform.retail.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.RecordLabeler;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.retail.repository.ShopLinkRepository;

import lombok.RequiredArgsConstructor;

/** Names linked shops in the audit log (an unlinked shop falls back to the name the entry recorded). */
@Component
@RequiredArgsConstructor
public class ShopRecordLabeler implements RecordLabeler {

    private final ShopLinkRepository links;

    @Override
    public boolean handles(String recordType) {
        return ShopLinkService.RECORD.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids) {
        Map<UUID, Label> out = new HashMap<>();
        links.findAllById(ids).stream().filter(l -> l.getOrgId().equals(ctx.orgId()))
                .forEach(l -> out.put(l.getBusinessId(), new Label(l.getBusinessName(), "/" + ctx.slug() + "/retail")));
        return out;
    }
}
