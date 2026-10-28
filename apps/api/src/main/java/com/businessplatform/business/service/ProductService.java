package com.businessplatform.business.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.business.domain.Product;
import com.businessplatform.business.domain.Totals;
import com.businessplatform.business.payload.BusinessPayloads.ProductRequest;
import com.businessplatform.business.payload.BusinessPayloads.ProductView;
import com.businessplatform.business.repository.ProductRepository;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Products and services with their USD price: everyone in the module picks them, Admins keep the list. */
@Service
@RequiredArgsConstructor
public class ProductService {

    public static final String RECORD = "product";

    private final BusinessAccess access;
    private final ProductRepository products;
    private final ActivityService activity;
    private final Clock clock;

    /** Active ones by name ({@code all}: switched-off ones too); {@code q} matches either name. */
    @Transactional(readOnly = true)
    public List<ProductView> list(String slug, String q, boolean all) {
        OrgContext ctx = access.require(slug);
        String needle = q == null || q.isBlank() ? null : q.strip().toLowerCase();
        return products.findByOrgIdOrderByNameEnAsc(ctx.orgId()).stream().filter(p -> all || p.isActive())
                .filter(p -> needle == null || p.getNameEn().toLowerCase().contains(needle)
                        || (p.getNameKm() != null && p.getNameKm().toLowerCase().contains(needle)))
                .map(ProductService::view).toList();
    }

    @Transactional
    public ProductView save(String slug, UUID id, ProductRequest in) {
        OrgContext ctx = access.requireManager(slug);
        Product p;
        if (id == null) {
            p = new Product();
            p.setId(Ids.next());
            p.setOrgId(ctx.orgId());
            p.setCreatedAt(clock.instant());
        } else {
            p = products.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        }
        String name = in.nameEn().strip();
        if (products.existsByOrgIdAndNameEnIgnoreCaseAndIdNot(ctx.orgId(), name, p.getId())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "nameEn", "Another product has this name");
        }
        p.setNameEn(name);
        p.setNameKm(BizPeople.clean(in.nameKm()));
        p.setUnit(in.unit().strip());
        p.setUnitPrice(Totals.cents(in.unitPrice()));
        p.setVat(in.vat());
        p.setActive(in.active() == null || in.active());
        p.setUpdatedAt(clock.instant());
        products.save(p);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, p.getId(), ctx.userId(),
                id == null ? "business.product.created" : "business.product.updated",
                Map.of("name", p.getNameEn(), "price", p.getUnitPrice().toPlainString()));
        return view(p);
    }

    static ProductView view(Product p) {
        return new ProductView(p.getId(), p.getNameEn(), p.getNameKm(), p.getUnit(), p.getUnitPrice(), p.isVat(),
                p.isActive());
    }
}
