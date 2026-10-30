package com.businessplatform.ops.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.ops.domain.Asset;
import com.businessplatform.ops.payload.OpsPayloads.AssetDetail;
import com.businessplatform.ops.payload.OpsPayloads.AssetRequest;
import com.businessplatform.ops.payload.OpsPayloads.AssetView;
import com.businessplatform.ops.repository.AssetRepository;
import com.businessplatform.ops.repository.MaintenancePlanRepository;
import com.businessplatform.ops.repository.TicketRepository;
import com.businessplatform.ops.repository.WorkOrderRepository;

import lombok.RequiredArgsConstructor;

/** Assets at customers (serial, model, warranty, status) with their work order and ticket history. Dispatchers and Admins. */
@Service
@RequiredArgsConstructor
public class AssetService {

    public static final String RECORD = "service_asset";

    private final OpsAccess access;
    private final OpsCustomerService customers;
    private final AssetRepository assets;
    private final WorkOrderRepository workOrders;
    private final TicketRepository tickets;
    private final MaintenancePlanRepository plans;
    private final OpsViews views;
    private final ActivityService activity;
    private final Clock clock;

    /** {@code warranty}: true = under warranty only. */
    @Transactional(readOnly = true)
    public PageResponse<AssetView> list(String slug, String q, UUID customerId, String status, Boolean warranty, int page, int size) {
        OpsAccess.Who w = access.requireManager(slug);
        Specification<Asset> spec = (a, query, cb) -> cb.equal(a.get("orgId"), w.orgId());
        if (customerId != null) {
            spec = spec.and((a, query, cb) -> cb.equal(a.get("customerId"), customerId));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((a, query, cb) -> cb.equal(a.get("status"), status));
        }
        if (Boolean.TRUE.equals(warranty)) {
            spec = spec.and((a, query, cb) -> cb.greaterThanOrEqualTo(a.get("warrantyEnd"), access.today()));
        }
        if (q != null && !q.isBlank()) {
            String like = OpsPeople.like(q);
            spec = spec.and((a, query, cb) -> cb.or(cb.like(cb.lower(a.get("name")), like, '\\'),
                    cb.like(cb.lower(a.get("serial")), like, '\\'), cb.like(cb.lower(a.get("model")), like, '\\')));
        }
        Page<Asset> found = assets.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), OpsCustomerService.MAX_PAGE),
                Sort.by(Sort.Order.asc("name").ignoreCase())));
        return PageResponse.of(new PageImpl<>(views.assets(w.orgId(), found.getContent(), access.today()), found.getPageable(),
                found.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public AssetDetail get(String slug, UUID id) {
        OpsAccess.Who w = access.requireManager(slug);
        Asset a = find(w.orgId(), id);
        return new AssetDetail(views.assets(w.orgId(), List.of(a), access.today()).get(0),
                views.workOrders(w.orgId(), workOrders.findTop50ByOrgIdAndAssetIdOrderByCreatedAtDesc(w.orgId(), id)),
                views.tickets(w.orgId(), tickets.findTop20ByOrgIdAndAssetIdOrderByCreatedAtDesc(w.orgId(), id)),
                views.plans(w.orgId(), plans.findByOrgIdAndAssetIdOrderByNextDue(w.orgId(), id)));
    }

    @Transactional
    public AssetView create(String slug, AssetRequest in) {
        OpsAccess.Who w = access.requireManager(slug);
        Asset a = new Asset();
        a.setId(Ids.next());
        a.setOrgId(w.orgId());
        a.setCreatedAt(clock.instant());
        apply(w, a, in, false);
        assets.saveAndFlush(a);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, a.getId(), w.userId(), "service.asset.created",
                Map.of("name", a.getName(), "serial", a.getSerial() == null ? "" : a.getSerial()));
        return views.assets(w.orgId(), List.of(a), access.today()).get(0);
    }

    @Transactional
    public AssetView update(String slug, UUID id, AssetRequest in) {
        OpsAccess.Who w = access.requireManager(slug);
        Asset a = find(w.orgId(), id);
        String before = a.getStatus();
        apply(w, a, in, in.customerId().equals(a.getCustomerId()));
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, a.getId(), w.userId(), "service.asset.updated",
                Map.of("name", a.getName(), "status", a.getStatus(), "from", before));
        return views.assets(w.orgId(), List.of(a), access.today()).get(0);
    }

    Asset find(UUID orgId, UUID id) {
        return assets.findByIdAndOrgId(id, orgId).orElseThrow(BusinessException::notFound);
    }

    /** The asset for a ticket or work order: it must belong to the customer given (when one is). */
    Asset checkLink(UUID orgId, UUID assetId, UUID customerId) {
        if (assetId == null) {
            return null;
        }
        Asset a = assets.findByIdAndOrgId(assetId, orgId)
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "assetId", "Choose an asset"));
        if (customerId != null && !a.getCustomerId().equals(customerId)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "assetId", "This asset belongs to another customer");
        }
        return a;
    }

    private void apply(OpsAccess.Who w, Asset a, AssetRequest in, boolean sameCustomer) {
        customers.checkLink(w.orgId(), in.customerId(), in.siteId(), sameCustomer);
        String serial = OpsPeople.clean(in.serial());
        if (serial != null && assets.serialTaken(w.orgId(), serial, a.getId())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "serial", "Another asset has this serial number");
        }
        if (in.installedOn() != null && in.warrantyEnd() != null && in.warrantyEnd().isBefore(in.installedOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "warrantyEnd", "The warranty ends before the installation");
        }
        a.setCustomerId(in.customerId());
        a.setSiteId(in.siteId());
        a.setName(in.name().strip());
        a.setSerial(serial);
        a.setModel(OpsPeople.clean(in.model()));
        a.setManufacturer(OpsPeople.clean(in.manufacturer()));
        a.setInstalledOn(in.installedOn());
        a.setWarrantyEnd(in.warrantyEnd());
        a.setStatus(in.status() == null ? (a.getStatus() == null ? Asset.ACTIVE : a.getStatus()) : in.status());
        a.setNotes(OpsPeople.clean(in.notes()));
        a.setUpdatedAt(clock.instant());
    }
}
