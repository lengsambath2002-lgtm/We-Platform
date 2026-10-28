package com.businessplatform.business.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.business.domain.DocLine;
import com.businessplatform.business.domain.Product;
import com.businessplatform.business.domain.Totals;
import com.businessplatform.business.payload.BusinessPayloads.LineInput;
import com.businessplatform.business.payload.BusinessPayloads.LineView;
import com.businessplatform.business.repository.DocLineRepository;
import com.businessplatform.business.repository.ProductRepository;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Quote and invoice lines: built from the client's input (a product of this organisation, or free text) and priced by {@link Totals}. */
@Component
@RequiredArgsConstructor
public class Lines {

    private final DocLineRepository lines;
    private final ProductRepository products;

    /** New lines (not saved) from {@code in}; a product's name and unit fill empty ones. */
    @Transactional(readOnly = true)
    public List<DocLine> build(OrgContext ctx, List<LineInput> in) {
        Map<UUID, Product> byId = new HashMap<>();
        List<UUID> ids = in.stream().map(LineInput::productId).filter(java.util.Objects::nonNull).distinct().toList();
        products.findAllById(ids).stream().filter(p -> p.getOrgId().equals(ctx.orgId())).forEach(p -> byId.put(p.getId(), p));
        List<DocLine> out = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            LineInput x = in.get(i);
            String productField = "lines[" + i + "].productId";
            String descriptionField = "lines[" + i + "].description";
            Product p = null;
            if (x.productId() != null) {
                p = byId.get(x.productId());
                if (p == null) {
                    throw BusinessException.field(ErrorCode.VALIDATION_FAILED, productField, "Choose a product");
                }
            }
            String description = BizPeople.clean(x.description());
            if (description == null && p != null) {
                description = p.getNameEn();
            }
            if (description == null) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, descriptionField,
                        "Describe the line or choose a product");
            }
            DocLine l = new DocLine();
            l.setId(Ids.next());
            l.setOrgId(ctx.orgId());
            l.setPosition((short) (i + 1));
            l.setProductId(p == null ? null : p.getId());
            l.setDescription(description);
            String unit = BizPeople.clean(x.unit());
            l.setUnit(unit == null && p != null ? p.getUnit() : unit);
            l.setQuantity(x.quantity().setScale(3, java.math.RoundingMode.HALF_UP));
            l.setUnitPrice(Totals.cents(x.unitPrice()));
            l.setDiscountPct(x.discountPct() == null ? BigDecimal.ZERO.setScale(2) : x.discountPct().setScale(2, java.math.RoundingMode.HALF_UP));
            l.setVat(x.vat());
            out.add(l);
        }
        return out;
    }

    /** Copies of {@code from} (for converting a quote into an invoice). */
    public static List<DocLine> copy(List<DocLine> from) {
        return from.stream().map(f -> {
            DocLine l = new DocLine();
            l.setId(Ids.next());
            l.setOrgId(f.getOrgId());
            l.setPosition(f.getPosition());
            l.setProductId(f.getProductId());
            l.setDescription(f.getDescription());
            l.setUnit(f.getUnit());
            l.setQuantity(f.getQuantity());
            l.setUnitPrice(f.getUnitPrice());
            l.setDiscountPct(f.getDiscountPct());
            l.setVat(f.isVat());
            l.setDiscount(f.getDiscount());
            l.setNet(f.getNet());
            return l;
        }).toList();
    }

    /** Replaces a quote's lines. */
    @Transactional
    public void saveForQuote(UUID quoteId, List<DocLine> ls) {
        lines.deleteByQuote(quoteId);
        lines.flush();
        ls.forEach(l -> l.setQuoteId(quoteId));
        lines.saveAll(ls);
    }

    /** Replaces an invoice's lines. */
    @Transactional
    public void saveForInvoice(UUID invoiceId, List<DocLine> ls) {
        lines.deleteByInvoice(invoiceId);
        lines.flush();
        ls.forEach(l -> l.setInvoiceId(invoiceId));
        lines.saveAll(ls);
    }

    public List<DocLine> ofQuote(UUID quoteId) {
        return lines.findByQuoteIdOrderByPosition(quoteId);
    }

    public List<DocLine> ofInvoice(UUID invoiceId) {
        return lines.findByInvoiceIdOrderByPosition(invoiceId);
    }

    public static List<LineView> views(List<DocLine> ls) {
        return ls.stream().map(l -> new LineView(l.getId(), l.getPosition(), l.getProductId(), l.getDescription(),
                l.getUnit(), l.getQuantity(), l.getUnitPrice(), l.getDiscountPct(), l.isVat(), l.getDiscount(), l.getNet()))
                .toList();
    }
}
