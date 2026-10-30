package com.businessplatform.ops.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.ops.domain.WorkOrder;
import com.businessplatform.ops.repository.WoTechnicianRepository;
import com.businessplatform.ops.repository.WorkOrderRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.workflow.service.RecordResolver;

import lombok.RequiredArgsConstructor;

/**
 * Lets the approval engine check work orders: visible to dispatchers, Admins and its technicians (the engine itself
 * shows a request only to its requester, approvers and Admins); a dispatcher or Admin asks for approval of a
 * draft (the module does it when scheduling one above the threshold). Nothing while the module is switched off.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderRecordResolver implements RecordResolver {

    private final OpsAccess access;
    private final WorkOrderRepository workOrders;
    private final WoTechnicianRepository technicians;

    @Override
    public String module() {
        return OpsAccess.MODULE;
    }

    @Override
    public boolean handles(String recordType) {
        return WorkOrderService.RECORD.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordView> view(OrgContext ctx, String recordType, UUID recordId) {
        Optional<OpsAccess.Who> who = access.usable(ctx);
        if (who.isEmpty()) {
            return Optional.empty();
        }
        OpsAccess.Who w = who.get();
        return workOrders.findByIdAndOrgId(recordId, ctx.orgId())
                .filter(o -> w.manager() || technicians.existsByWorkOrderIdAndUserId(o.getId(), w.userId()))
                .map(o -> new RecordView(title(o), WorkOrderService.link(ctx.slug(), o.getId()),
                        w.manager() && WorkOrder.DRAFT.equals(o.getStatus())));
    }

    @Override
    public Optional<UUID> leadOf(OrgContext ctx, String recordType, UUID recordId) {
        return Optional.empty();
    }

    /** "Work order WO-0003 · Replace compressor · $820.00". */
    static String title(WorkOrder o) {
        return "Work order " + o.getNumber() + " · " + o.getTitle() + " · $" + o.getTotal().toPlainString();
    }
}
