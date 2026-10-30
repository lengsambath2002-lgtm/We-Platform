package com.businessplatform.retail.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.RecordLabeler;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.retail.repository.PurchasingRepositories.PurchaseOrderRepository;
import com.businessplatform.retail.repository.PurchasingRepositories.SupplierRepository;

import lombok.RequiredArgsConstructor;

/** Names and links of suppliers and purchase orders for the audit log. */
@Component
@RequiredArgsConstructor
public class PurchasingRecordLabeler implements RecordLabeler {

    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;

    @Override
    public boolean handles(String recordType) {
        return PurchasingService.SUPPLIER.equals(recordType) || PurchasingService.ORDER.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids) {
        Map<UUID, Label> out = new HashMap<>();
        String base = "/" + ctx.slug() + "/retail";
        if (PurchasingService.SUPPLIER.equals(recordType)) {
            suppliers.findAllById(ids).stream().filter(s -> s.getOrgId().equals(ctx.orgId()))
                    .forEach(s -> out.put(s.getId(), new Label(s.getName(), base + "/suppliers/" + s.getId())));
        } else {
            orders.findAllById(ids).stream().filter(o -> o.getOrgId().equals(ctx.orgId()))
                    .forEach(o -> out.put(o.getId(), new Label(o.getNumber() == null ? "Draft purchase order" : o.getNumber(),
                            base + "/purchase-orders/" + o.getId())));
        }
        return out;
    }
}
