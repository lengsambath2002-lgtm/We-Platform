package com.businessplatform.retail.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.calendar.CalendarService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.retail.domain.PoLine;
import com.businessplatform.retail.domain.PoReceipt;
import com.businessplatform.retail.domain.PurchaseOrder;
import com.businessplatform.retail.domain.ShopLink;
import com.businessplatform.retail.domain.Supplier;
import com.businessplatform.retail.domain.SupplierPayment;
import com.businessplatform.retail.payload.PurchasingPayloads.*;
import com.businessplatform.retail.repository.PurchasingRepositories.PoLineRepository;
import com.businessplatform.retail.repository.PurchasingRepositories.PoReceiptRepository;
import com.businessplatform.retail.repository.PurchasingRepositories.PurchaseOrderRepository;
import com.businessplatform.retail.repository.PurchasingRepositories.SupplierPaymentRepository;
import com.businessplatform.retail.repository.PurchasingRepositories.SupplierRepository;
import com.businessplatform.retail.repository.ShopLinkRepository;

import lombok.RequiredArgsConstructor;

/**
 * Suppliers and purchase orders. Everyone in Retail &amp; Commerce (not guests) sees them; Owners and Admins
 * ({@code retail.manage}) add suppliers, order, receive deliveries and record payments.
 * <p>
 * Stock stays in VirtualBiz, the one stock count: a delivery here records what arrived against the order, and the
 * person then puts it into stock in VirtualBiz (as "Received") and ticks the delivery off. An order is paid against
 * its total; what is unpaid is owed to the supplier, due the supplier's payment terms after the order date.
 */
@Service
@RequiredArgsConstructor
public class PurchasingService {

    /** How suppliers and orders appear in the activity log. */
    public static final String SUPPLIER = "supplier";
    public static final String ORDER = "purchase_order";

    private final OrgAccess access;
    private final ModuleService modules;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;
    private final PoLineRepository lines;
    private final PoReceiptRepository receipts;
    private final SupplierPaymentRepository payments;
    private final ShopLinkRepository shops;
    private final UserRepository users;
    private final ActivityService activity;
    private final Clock clock;

    // ── Access ───────────────────────────────────────────────────────────────────────

    OrgContext view(String slug) {
        OrgContext ctx = access.require(slug);
        modules.require(ctx.orgId(), Module.RETAIL);
        if (ctx.isGuest()) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    OrgContext manage(String slug) {
        OrgContext ctx = view(slug);
        if (!ctx.isAdmin()) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(CalendarService.ZONE));
    }

    // ── Suppliers ────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SupplierView> suppliers(String slug, boolean archived) {
        OrgContext ctx = view(slug);
        List<PurchaseOrder> all = orders.findByOrgIdOrderByCreatedAtDesc(ctx.orgId());
        return suppliers.findByOrgIdOrderByNameAsc(ctx.orgId()).stream().filter(s -> s.isArchived() == archived)
                .map(s -> supplierView(s, all)).toList();
    }

    @Transactional(readOnly = true)
    public SupplierView supplier(String slug, UUID id) {
        OrgContext ctx = view(slug);
        return supplierView(supplier(ctx, id), orders.findByOrgIdAndSupplierIdOrderByCreatedAtDesc(ctx.orgId(), id));
    }

    @Transactional
    public SupplierView saveSupplier(String slug, UUID id, SupplierRequest in) {
        OrgContext ctx = manage(slug);
        Supplier s = id == null ? new Supplier() : supplier(ctx, id);
        if (id == null) {
            s.setId(Ids.next());
            s.setOrgId(ctx.orgId());
            s.setCreatedBy(ctx.userId());
            s.setCreatedAt(clock.instant());
        }
        s.setName(in.name().strip());
        s.setContactName(clean(in.contactName()));
        s.setEmail(clean(in.email()));
        s.setPhone(clean(in.phone()));
        s.setAddress(clean(in.address()));
        s.setTaxId(clean(in.taxId()));
        s.setPaymentTermsDays((short) in.paymentTermsDays());
        s.setNotes(clean(in.notes()));
        s.setUpdatedAt(clock.instant());
        suppliers.saveAndFlush(s);
        activity.record(ctx.orgId(), Module.RETAIL, SUPPLIER, s.getId(), ctx.userId(), id == null ? "retail.supplier.created" : "retail.supplier.updated",
                Map.of("name", s.getName()));
        return supplierView(s, orders.findByOrgIdAndSupplierIdOrderByCreatedAtDesc(ctx.orgId(), s.getId()));
    }

    @Transactional
    public SupplierView archiveSupplier(String slug, UUID id, boolean archived) {
        OrgContext ctx = manage(slug);
        Supplier s = supplier(ctx, id);
        s.setArchived(archived);
        s.setUpdatedAt(clock.instant());
        return supplierView(s, orders.findByOrgIdAndSupplierIdOrderByCreatedAtDesc(ctx.orgId(), id));
    }

    private Supplier supplier(OrgContext ctx, UUID id) {
        return suppliers.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private SupplierView supplierView(Supplier s, List<PurchaseOrder> all) {
        List<PurchaseOrder> mine = all.stream().filter(o -> o.getSupplierId().equals(s.getId())).toList();
        int year = today().getYear();
        BigDecimal owed = money(mine.stream().filter(PurchaseOrder::committed).map(o -> o.getTotal().subtract(o.getPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal spent = money(mine.stream().filter(o -> o.committed() && o.getOrderDate() != null && o.getOrderDate().getYear() == year)
                .map(PurchaseOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        int open = (int) mine.stream().filter(o -> PurchaseOrder.ORDERED.equals(o.getStatus()) || PurchaseOrder.PARTIAL.equals(o.getStatus())).count();
        return new SupplierView(s.getId(), s.getName(), s.getContactName(), s.getEmail(), s.getPhone(), s.getAddress(), s.getTaxId(),
                s.getPaymentTermsDays(), s.getNotes(), s.isArchived(), owed, spent, open);
    }

    // ── Orders ───────────────────────────────────────────────────────────────────────

    /** Newest first; {@code status} and {@code supplierId} narrow it. */
    @Transactional(readOnly = true)
    public List<OrderSummary> orders(String slug, String status, UUID supplierId) {
        OrgContext ctx = view(slug);
        List<PurchaseOrder> found = (supplierId == null ? orders.findByOrgIdOrderByCreatedAtDesc(ctx.orgId())
                : orders.findByOrgIdAndSupplierIdOrderByCreatedAtDesc(ctx.orgId(), supplierId)).stream()
                .filter(o -> status == null || status.isBlank() || status.equals(o.getStatus())).toList();
        return summaries(ctx, found);
    }

    @Transactional(readOnly = true)
    public OrderView order(String slug, UUID id) {
        OrgContext ctx = view(slug);
        return view(ctx, order(ctx, id));
    }

    @Transactional
    public OrderView create(String slug, OrderRequest in) {
        OrgContext ctx = manage(slug);
        PurchaseOrder o = new PurchaseOrder();
        o.setId(Ids.next());
        o.setOrgId(ctx.orgId());
        o.setStatus(PurchaseOrder.DRAFT);
        o.setPaid(BigDecimal.ZERO.setScale(2));
        o.setCreatedBy(ctx.userId());
        o.setCreatedAt(clock.instant());
        apply(ctx, o, in);
        orders.saveAndFlush(o);
        writeLines(o, in.lines());
        activity.record(ctx.orgId(), Module.RETAIL, ORDER, o.getId(), ctx.userId(), "retail.po.created",
                Map.of("supplier", supplier(ctx, o.getSupplierId()).getName(), "total", o.getTotal().toPlainString()));
        return view(ctx, o);
    }

    /** A DRAFT's supplier, dates, shop, notes and lines. */
    @Transactional
    public OrderView update(String slug, UUID id, OrderRequest in) {
        OrgContext ctx = manage(slug);
        PurchaseOrder o = locked(ctx, id);
        if (!PurchaseOrder.DRAFT.equals(o.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a draft order can be changed", List.of());
        }
        apply(ctx, o, in);
        lines.deleteByPoId(o.getId());
        lines.flush();
        writeLines(o, in.lines());
        return view(ctx, o);
    }

    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = manage(slug);
        PurchaseOrder o = locked(ctx, id);
        if (!PurchaseOrder.DRAFT.equals(o.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a draft order can be deleted; cancel it instead", List.of());
        }
        orders.delete(o);
    }

    /** Sends a DRAFT to the supplier: it gets its number and order date, and counts as owed. */
    @Transactional
    public OrderView place(String slug, UUID id) {
        OrgContext ctx = manage(slug);
        PurchaseOrder o = locked(ctx, id);
        if (!PurchaseOrder.DRAFT.equals(o.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This order is placed already", List.of());
        }
        if (supplier(ctx, o.getSupplierId()).isArchived()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "supplierId", "This supplier is archived");
        }
        o.setNumber(String.format("PO-%04d", orders.nextNumber(ctx.orgId())));
        o.setStatus(PurchaseOrder.ORDERED);
        o.setOrderDate(today());
        o.setOrderedAt(clock.instant());
        o.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), Module.RETAIL, ORDER, o.getId(), ctx.userId(), "retail.po.ordered",
                Map.of("number", o.getNumber(), "total", o.getTotal().toPlainString()));
        return view(ctx, o);
    }

    /** Calls off an order nothing has arrived for. */
    @Transactional
    public OrderView cancel(String slug, UUID id, CancelRequest in) {
        OrgContext ctx = manage(slug);
        PurchaseOrder o = locked(ctx, id);
        if (!PurchaseOrder.ORDERED.equals(o.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only an order nothing has arrived for can be cancelled", List.of());
        }
        if (o.getPaid().signum() > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This order has payments; it can't be cancelled", List.of());
        }
        o.setStatus(PurchaseOrder.CANCELLED);
        o.setCancelledAt(clock.instant());
        o.setCancelReason(in.reason().strip());
        o.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), Module.RETAIL, ORDER, o.getId(), ctx.userId(), "retail.po.cancelled",
                Map.of("number", o.getNumber(), "reason", o.getCancelReason()));
        return view(ctx, o);
    }

    /** Records a delivery: what arrived of each line (never more than is still to come). */
    @Transactional
    public OrderView receive(String slug, UUID id, ReceiveRequest in) {
        OrgContext ctx = manage(slug);
        PurchaseOrder o = locked(ctx, id);
        if (!PurchaseOrder.ORDERED.equals(o.getStatus()) && !PurchaseOrder.PARTIAL.equals(o.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only an order that is waiting for goods can receive them", List.of());
        }
        LocalDate on = in.receivedOn() == null ? today() : in.receivedOn();
        if (on.isAfter(today())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "receivedOn", "Goods can't arrive in the future");
        }
        Map<UUID, PoLine> byId = lines.findByPoIdOrderBySortOrder(o.getId()).stream().collect(Collectors.toMap(PoLine::getId, Function.identity()));
        List<Map<String, Object>> got = new ArrayList<>();
        for (ReceiveLine r : in.lines()) {
            PoLine l = byId.get(r.lineId());
            if (l == null) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "lines", "A line isn't on this order");
            }
            if (r.quantity().signum() == 0) {
                continue;
            }
            if (l.getReceived().add(r.quantity()).compareTo(l.getQuantity()) > 0) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "lines", "More arrived than was ordered");
            }
            l.setReceived(l.getReceived().add(r.quantity()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineId", l.getId().toString());
            m.put("quantity", r.quantity());
            got.add(m);
        }
        if (got.isEmpty()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "lines", "Enter what arrived");
        }
        PoReceipt receipt = new PoReceipt();
        receipt.setId(Ids.next());
        receipt.setOrgId(ctx.orgId());
        receipt.setPoId(o.getId());
        receipt.setReceivedOn(on);
        receipt.setLines(got);
        receipt.setNote(clean(in.note()));
        receipt.setReceivedBy(ctx.userId());
        receipt.setCreatedAt(clock.instant());
        receipts.save(receipt);
        boolean all = byId.values().stream().allMatch(l -> l.getReceived().compareTo(l.getQuantity()) == 0);
        o.setStatus(all ? PurchaseOrder.RECEIVED : PurchaseOrder.PARTIAL);
        if (all) {
            o.setReceivedAt(clock.instant());
        }
        o.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), Module.RETAIL, ORDER, o.getId(), ctx.userId(), all ? "retail.po.received" : "retail.po.part_received",
                Map.of("number", o.getNumber()));
        return view(ctx, o);
    }

    /** Ticks a delivery off as put into stock in VirtualBiz (or back). */
    @Transactional
    public OrderView stockRecorded(String slug, UUID receiptId, boolean recorded) {
        OrgContext ctx = manage(slug);
        PoReceipt r = receipts.findByIdAndOrgId(receiptId, ctx.orgId()).orElseThrow(BusinessException::notFound);
        r.setStockRecorded(recorded);
        return view(ctx, order(ctx, r.getPoId()));
    }

    @Transactional
    public OrderView pay(String slug, UUID id, PaymentRequest in) {
        OrgContext ctx = manage(slug);
        PurchaseOrder o = locked(ctx, id);
        if (!o.committed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a placed order can be paid", List.of());
        }
        BigDecimal amount = money(in.amount());
        if (o.getPaid().add(amount).compareTo(o.getTotal()) > 0) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "amount", "More than is left to pay");
        }
        SupplierPayment p = new SupplierPayment();
        p.setId(Ids.next());
        p.setOrgId(ctx.orgId());
        p.setPoId(o.getId());
        p.setPaidOn(in.paidOn());
        p.setAmount(amount);
        p.setMethod(in.method());
        p.setReference(clean(in.reference()));
        p.setRecordedBy(ctx.userId());
        p.setCreatedAt(clock.instant());
        payments.save(p);
        o.setPaid(money(o.getPaid().add(amount)));
        o.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), Module.RETAIL, ORDER, o.getId(), ctx.userId(), "retail.po.paid",
                Map.of("number", o.getNumber(), "amount", amount.toPlainString()));
        return view(ctx, o);
    }

    /** What is owed (and overdue), deliveries awaited or not yet in stock, this month's orders, this year's spend by supplier. */
    @Transactional(readOnly = true)
    public PurchasingOverview overview(String slug) {
        OrgContext ctx = view(slug);
        List<PurchaseOrder> all = orders.findByOrgIdOrderByCreatedAtDesc(ctx.orgId());
        Map<UUID, Supplier> byId = suppliers.findByOrgIdOrderByNameAsc(ctx.orgId()).stream().collect(Collectors.toMap(Supplier::getId, Function.identity()));
        LocalDate today = today();
        BigDecimal owed = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        for (PurchaseOrder o : all) {
            if (o.committed()) {
                BigDecimal left = o.getTotal().subtract(o.getPaid());
                owed = owed.add(left);
                LocalDate due = due(o, byId.get(o.getSupplierId()));
                if (due != null && due.isBefore(today)) {
                    overdue = overdue.add(left);
                }
            }
        }
        int awaiting = (int) all.stream().filter(o -> PurchaseOrder.ORDERED.equals(o.getStatus()) || PurchaseOrder.PARTIAL.equals(o.getStatus())).count();
        List<UUID> withReceipts = all.stream().filter(o -> PurchaseOrder.PARTIAL.equals(o.getStatus()) || PurchaseOrder.RECEIVED.equals(o.getStatus()))
                .map(PurchaseOrder::getId).toList();
        int notInStock = (int) withReceipts.stream().flatMap(id -> receipts.findByPoIdOrderByReceivedOnDescCreatedAtDesc(id).stream())
                .filter(r -> !r.isStockRecorded()).count();
        BigDecimal month = money(all.stream().filter(o -> o.committed() && o.getOrderDate() != null
                && o.getOrderDate().getYear() == today.getYear() && o.getOrderDate().getMonth() == today.getMonth())
                .map(PurchaseOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        Map<UUID, List<PurchaseOrder>> thisYear = all.stream().filter(o -> o.committed() && o.getOrderDate() != null
                && o.getOrderDate().getYear() == today.getYear()).collect(Collectors.groupingBy(PurchaseOrder::getSupplierId));
        List<SpendBySupplier> spend = thisYear.entrySet().stream().map(e -> {
            BigDecimal ordered = money(e.getValue().stream().map(PurchaseOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal paid = money(e.getValue().stream().map(PurchaseOrder::getPaid).reduce(BigDecimal.ZERO, BigDecimal::add));
            Supplier s = byId.get(e.getKey());
            return new SpendBySupplier(e.getKey(), s == null ? "" : s.getName(), e.getValue().size(), ordered, paid, money(ordered.subtract(paid)));
        }).sorted(Comparator.comparing(SpendBySupplier::ordered).reversed()).toList();
        return new PurchasingOverview(money(owed), money(overdue), awaiting, notInStock, month, spend);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private PurchaseOrder order(OrgContext ctx, UUID id) {
        return orders.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private PurchaseOrder locked(OrgContext ctx, UUID id) {
        return orders.lock(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private void apply(OrgContext ctx, PurchaseOrder o, OrderRequest in) {
        Supplier s = suppliers.findByIdAndOrgId(in.supplierId(), ctx.orgId())
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "supplierId", "Choose a supplier"));
        if (s.isArchived() && !s.getId().equals(o.getSupplierId())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "supplierId", "This supplier is archived");
        }
        if (in.shopBusinessId() != null && shops.findById(in.shopBusinessId()).filter(l -> l.getOrgId().equals(ctx.orgId())).isEmpty()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "shopBusinessId", "Choose one of the organisation's shops");
        }
        o.setSupplierId(s.getId());
        o.setExpectedDate(in.expectedDate());
        o.setShopBusinessId(in.shopBusinessId());
        o.setNotes(clean(in.notes()));
        o.setTotal(money(in.lines().stream().map(l -> l.quantity().multiply(l.unitCost())).reduce(BigDecimal.ZERO, BigDecimal::add)));
        o.setUpdatedAt(clock.instant());
    }

    private void writeLines(PurchaseOrder o, List<LineInput> in) {
        short n = 0;
        for (LineInput l : in) {
            PoLine line = new PoLine();
            line.setId(Ids.next());
            line.setPoId(o.getId());
            line.setSortOrder(n++);
            line.setDescription(l.description().strip());
            line.setSku(clean(l.sku()));
            line.setQuantity(l.quantity());
            line.setUnitCost(money(l.unitCost()));
            line.setReceived(BigDecimal.ZERO);
            lines.save(line);
        }
        lines.flush();
    }

    private static LocalDate due(PurchaseOrder o, Supplier s) {
        return o.getOrderDate() == null || s == null ? null : o.getOrderDate().plusDays(s.getPaymentTermsDays());
    }

    private List<OrderSummary> summaries(OrgContext ctx, List<PurchaseOrder> found) {
        Map<UUID, Supplier> byId = suppliers.findAllById(found.stream().map(PurchaseOrder::getSupplierId).distinct().toList()).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
        Map<UUID, String> shopNames = shops.findByOrgIdOrderByBusinessNameAsc(ctx.orgId()).stream()
                .collect(Collectors.toMap(ShopLink::getBusinessId, ShopLink::getBusinessName));
        LocalDate today = today();
        return found.stream().map(o -> {
            Supplier s = byId.get(o.getSupplierId());
            LocalDate due = due(o, s);
            BigDecimal balance = o.committed() ? money(o.getTotal().subtract(o.getPaid())) : money(BigDecimal.ZERO);
            return new OrderSummary(o.getId(), o.getNumber(), o.getSupplierId(), s == null ? "" : s.getName(), o.getStatus(),
                    o.getOrderDate(), o.getExpectedDate(), due, due != null && due.isBefore(today) && balance.signum() > 0, o.getTotal(),
                    o.getPaid(), balance, o.getShopBusinessId() == null ? null : shopNames.get(o.getShopBusinessId()));
        }).toList();
    }

    private OrderView view(OrgContext ctx, PurchaseOrder o) {
        orders.flush();
        List<PoLine> ls = lines.findByPoIdOrderBySortOrder(o.getId());
        Map<UUID, PoLine> byId = ls.stream().collect(Collectors.toMap(PoLine::getId, Function.identity()));
        List<PoReceipt> rs = receipts.findByPoIdOrderByReceivedOnDescCreatedAtDesc(o.getId());
        List<SupplierPayment> ps = payments.findByPoIdOrderByPaidOnDescCreatedAtDesc(o.getId());
        Map<UUID, PersonRef> people = people(Stream.concat(Stream.of(o.getCreatedBy()),
                Stream.concat(rs.stream().map(PoReceipt::getReceivedBy), ps.stream().map(SupplierPayment::getRecordedBy))).toList());
        boolean admin = ctx.isAdmin();
        boolean waiting = PurchaseOrder.ORDERED.equals(o.getStatus()) || PurchaseOrder.PARTIAL.equals(o.getStatus());
        return new OrderView(summaries(ctx, List.of(o)).get(0), o.getShopBusinessId(), o.getNotes(),
                ls.stream().map(l -> new LineView(l.getId(), l.getDescription(), l.getSku(), l.getQuantity(), l.getUnitCost(), l.getReceived(),
                        money(l.getQuantity().multiply(l.getUnitCost())))).toList(),
                rs.stream().map(r -> new ReceiptView(r.getId(), r.getReceivedOn(), r.getLines().stream().map(m -> {
                    PoLine l = byId.get(UUID.fromString(String.valueOf(m.get("lineId"))));
                    return new ReceiptLine(l == null ? null : l.getId(), l == null ? "" : l.getDescription(), l == null ? null : l.getSku(),
                            new BigDecimal(String.valueOf(m.get("quantity"))));
                }).toList(), r.getNote(), r.isStockRecorded(), people.get(r.getReceivedBy()), r.getCreatedAt())).toList(),
                ps.stream().map(p -> new PaymentView(p.getId(), p.getPaidOn(), p.getAmount(), p.getMethod(), p.getReference(),
                        people.get(p.getRecordedBy()))).toList(),
                o.getCancelReason(), people.get(o.getCreatedBy()), o.getCreatedAt(),
                admin && PurchaseOrder.DRAFT.equals(o.getStatus()), admin && waiting,
                admin && o.committed() && o.getPaid().compareTo(o.getTotal()) < 0);
    }

    private Map<UUID, PersonRef> people(List<UUID> ids) {
        Map<UUID, PersonRef> out = new HashMap<>();
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!wanted.isEmpty()) {
            for (AppUser u : users.findByIdIn(wanted)) {
                out.put(u.getId(), new PersonRef(u.getId(), u.getName(), u.getAvatarUrl()));
            }
        }
        return out;
    }

    static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
