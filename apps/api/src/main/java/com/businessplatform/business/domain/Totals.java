package com.businessplatform.business.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Money arithmetic for quotes and invoices, done only here (the API never trusts a client's totals). Each step
 * rounds half-up to cents:
 * <ul>
 * <li>a line: {@code amount = qty × unit price}, {@code discount = amount × discount% / 100},
 * {@code net = amount − discount};</li>
 * <li>the document: {@code subtotal = Σ net}, {@code VAT = (Σ net of VAT lines) × rate / 100} when the
 * document has VAT switched on, {@code total = subtotal + VAT}.</li>
 * </ul>
 * VAT is taken once on the document's taxable sum, so line roundings never add up to a cent too many.
 */
public final class Totals {

    /** Cambodia's standard VAT rate, in percent. */
    public static final BigDecimal VAT_RATE = new BigDecimal("10.00");
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED = new BigDecimal(100);

    private Totals() {
    }

    /** The totals a document stores. */
    public interface Doc {

        boolean isVatEnabled();

        BigDecimal getVatRate();

        void setSubtotal(BigDecimal v);

        void setDiscountTotal(BigDecimal v);

        void setVatTotal(BigDecimal v);

        void setTotal(BigDecimal v);
    }

    public static BigDecimal cents(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Sets the line's {@code discount} and {@code net} from its quantity, price and discount percentage. */
    public static void line(DocLine l) {
        BigDecimal amount = cents(l.getQuantity().multiply(l.getUnitPrice()));
        BigDecimal discount = cents(amount.multiply(l.getDiscountPct()).divide(HUNDRED));
        l.setDiscount(discount);
        l.setNet(amount.subtract(discount));
    }

    /** Computes every line and the document's subtotal, discount, VAT and total. */
    public static void document(Doc d, List<DocLine> lines) {
        BigDecimal subtotal = ZERO;
        BigDecimal discounts = ZERO;
        BigDecimal taxable = ZERO;
        for (DocLine l : lines) {
            line(l);
            subtotal = subtotal.add(l.getNet());
            discounts = discounts.add(l.getDiscount());
            if (l.isVat()) {
                taxable = taxable.add(l.getNet());
            }
        }
        BigDecimal vat = d.isVatEnabled() ? cents(taxable.multiply(d.getVatRate()).divide(HUNDRED)) : ZERO;
        d.setSubtotal(subtotal);
        d.setDiscountTotal(discounts);
        d.setVatTotal(vat);
        d.setTotal(subtotal.add(vat));
    }
}
