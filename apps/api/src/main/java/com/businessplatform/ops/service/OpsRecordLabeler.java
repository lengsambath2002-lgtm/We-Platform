package com.businessplatform.ops.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.RecordLabeler;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.ops.repository.AssetRepository;
import com.businessplatform.ops.repository.MaintenancePlanRepository;
import com.businessplatform.ops.repository.OpsCustomerRepository;
import com.businessplatform.ops.repository.StaffRepository;
import com.businessplatform.ops.repository.TicketRepository;
import com.businessplatform.ops.repository.WorkOrderRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Names and links of tickets, work orders, customers, assets, plans and staff for the audit log. */
@Component
@RequiredArgsConstructor
public class OpsRecordLabeler implements RecordLabeler {

    private static final Set<String> TYPES = Set.of(TicketService.RECORD, WorkOrderService.RECORD, OpsCustomerService.RECORD,
            AssetService.RECORD, MaintenanceService.RECORD, StaffService.RECORD, OpsSettingsService.RECORD);

    private final TicketRepository tickets;
    private final WorkOrderRepository workOrders;
    private final OpsCustomerRepository customers;
    private final AssetRepository assets;
    private final MaintenancePlanRepository plans;
    private final StaffRepository staff;
    private final UserRepository users;

    @Override
    public boolean handles(String recordType) {
        return TYPES.contains(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids) {
        Map<UUID, Label> out = new HashMap<>();
        String base = "/" + ctx.slug() + "/service";
        switch (recordType) {
            case TicketService.RECORD -> tickets.findAllById(ids).stream().filter(t -> t.getOrgId().equals(ctx.orgId()))
                    .forEach(t -> out.put(t.getId(), new Label(t.getNumber() + " · " + t.getTitle(), TicketService.link(ctx.slug(), t.getId()))));
            case WorkOrderService.RECORD -> workOrders.findAllById(ids).stream().filter(o -> o.getOrgId().equals(ctx.orgId()))
                    .forEach(o -> out.put(o.getId(), new Label(o.getNumber() + " · " + o.getTitle(), WorkOrderService.link(ctx.slug(), o.getId()))));
            case OpsCustomerService.RECORD -> customers.findAllById(ids).stream().filter(c -> c.getOrgId().equals(ctx.orgId()))
                    .forEach(c -> out.put(c.getId(), new Label(c.getName(), base + "/customers/" + c.getId())));
            case AssetService.RECORD -> assets.findAllById(ids).stream().filter(a -> a.getOrgId().equals(ctx.orgId()))
                    .forEach(a -> out.put(a.getId(), new Label(a.getName(), base + "/assets/" + a.getId())));
            case MaintenanceService.RECORD -> plans.findAllById(ids).stream().filter(p -> p.getOrgId().equals(ctx.orgId()))
                    .forEach(p -> out.put(p.getId(), new Label(p.getTitle(), base + "/maintenance")));
            case StaffService.RECORD -> staff.findAllById(ids).stream().filter(s -> s.getOrgId().equals(ctx.orgId()))
                    .forEach(s -> users.findById(s.getUserId())
                            .ifPresent(u -> out.put(s.getId(), new Label(u.getName(), base + "/technicians"))));
            default -> ids.stream().filter(id -> id.equals(ctx.orgId()))
                    .forEach(id -> out.put(id, new Label(ctx.orgName(), base + "/settings")));
        }
        return out;
    }
}
