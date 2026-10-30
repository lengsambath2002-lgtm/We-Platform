package com.businessplatform.ops.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A part or a labour line of a work order: {@code amount = quantity × unit price}, half-up to cents. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "svc_wo_line")
public class WoLine {

    public static final String PART = "PART";
    public static final String LABOUR = "LABOUR";
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(nullable = false)
    private short position;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal amount;

    /** Rounds half-up to cents. */
    public static BigDecimal cents(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Computes each line's amount and the work order's parts, labour and total (only here; clients never set them). */
    public static void totals(WorkOrder w, List<WoLine> lines) {
        BigDecimal parts = ZERO;
        BigDecimal labour = ZERO;
        for (WoLine l : lines) {
            l.setAmount(cents(l.getQuantity().multiply(l.getUnitPrice())));
            if (LABOUR.equals(l.getKind())) {
                labour = labour.add(l.getAmount());
            } else {
                parts = parts.add(l.getAmount());
            }
        }
        w.setPartsTotal(parts);
        w.setLabourTotal(labour);
        w.setTotal(parts.add(labour));
    }
}
