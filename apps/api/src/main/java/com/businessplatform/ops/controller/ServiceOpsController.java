package com.businessplatform.ops.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.file.FileService;
import com.businessplatform.file.StoredFile;
import com.businessplatform.ops.payload.OpsPayloads.*;
import com.businessplatform.ops.service.AssetService;
import com.businessplatform.ops.service.MaintenanceService;
import com.businessplatform.ops.service.OpsCustomerService;
import com.businessplatform.ops.service.OpsOverviewService;
import com.businessplatform.ops.service.OpsReportService;
import com.businessplatform.ops.service.OpsSettingsService;
import com.businessplatform.ops.service.StaffService;
import com.businessplatform.ops.service.TicketService;
import com.businessplatform.ops.service.WorkOrderService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Service &amp; Operations: staff, SLA, customers and sites, assets, tickets, work orders, maintenance plans, the
 * dashboard and reports. Who may do what is described on {@code OpsAccess}; lists are paged ({@code page} from 0,
 * {@code size} up to 100). Bookings stay in VirtualBiz (the web app links there through Retail &amp; Commerce).
 */
@RestController
@RequestMapping("/api/orgs/{org}/service")
@RequiredArgsConstructor
@Tag(name = "Service & Operations")
public class ServiceOpsController {

    private final OpsSettingsService settings;
    private final StaffService staff;
    private final OpsCustomerService customers;
    private final AssetService assets;
    private final TicketService tickets;
    private final WorkOrderService workOrders;
    private final MaintenanceService maintenance;
    private final OpsOverviewService overview;
    private final OpsReportService reports;
    private final FileService files;

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> created(T body) {
        return ResponseEntity.status(201).body(ApiResponse.ok(body));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeView>> me(@PathVariable String org) {
        return ok(settings.me(org));
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Overview>> overview(@PathVariable String org) {
        return ok(overview.overview(org));
    }

    /** Days, inclusive; default the last 30. */
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<Report>> report(@PathVariable String org,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(reports.report(org, from, to));
    }

    // ── Settings, SLA, staff ─────────────────────────────────────────────────────────

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsView>> settings(@PathVariable String org) {
        return ok(settings.get(org));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsView>> updateSettings(@PathVariable String org, @Valid @RequestBody SettingsRequest in) {
        return ok(settings.update(org, in));
    }

    @GetMapping("/sla")
    public ResponseEntity<ApiResponse<List<SlaView>>> sla(@PathVariable String org) {
        return ok(settings.sla(org));
    }

    @PutMapping("/sla")
    public ResponseEntity<ApiResponse<List<SlaView>>> updateSla(@PathVariable String org, @Valid @RequestBody List<@Valid SlaRequest> in) {
        return ok(settings.updateSla(org, in));
    }

    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<StaffView>>> staff(@PathVariable String org) {
        return ok(staff.list(org));
    }

    @PostMapping("/staff")
    public ResponseEntity<ApiResponse<StaffView>> addStaff(@PathVariable String org, @Valid @RequestBody StaffRequest in) {
        return created(staff.create(org, in));
    }

    @PutMapping("/staff/{id}")
    public ResponseEntity<ApiResponse<StaffView>> updateStaff(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody StaffRequest in) {
        return ok(staff.update(org, id, in));
    }

    // ── Customers and sites ──────────────────────────────────────────────────────────

    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<PageResponse<CustomerView>>> customers(@PathVariable String org,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ok(customers.list(org, q, archived, page, size));
    }

    @PostMapping("/customers")
    public ResponseEntity<ApiResponse<CustomerView>> createCustomer(@PathVariable String org, @Valid @RequestBody CustomerRequest in) {
        return created(customers.create(org, in));
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<ApiResponse<CustomerDetail>> customer(@PathVariable String org, @PathVariable UUID id) {
        return ok(customers.get(org, id));
    }

    @PutMapping("/customers/{id}")
    public ResponseEntity<ApiResponse<CustomerView>> updateCustomer(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CustomerRequest in) {
        return ok(customers.update(org, id, in));
    }

    @PostMapping("/customers/{id}/archive")
    public ResponseEntity<ApiResponse<CustomerView>> archive(@PathVariable String org, @PathVariable UUID id) {
        return ok(customers.archive(org, id, true));
    }

    @PostMapping("/customers/{id}/restore")
    public ResponseEntity<ApiResponse<CustomerView>> restore(@PathVariable String org, @PathVariable UUID id) {
        return ok(customers.archive(org, id, false));
    }

    @PostMapping("/customers/{id}/sites")
    public ResponseEntity<ApiResponse<SiteView>> addSite(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody SiteRequest in) {
        return created(customers.addSite(org, id, in));
    }

    @PutMapping("/sites/{id}")
    public ResponseEntity<ApiResponse<SiteView>> updateSite(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody SiteRequest in) {
        return ok(customers.updateSite(org, id, in));
    }

    @DeleteMapping("/sites/{id}")
    public ResponseEntity<ApiResponse<Void>> removeSite(@PathVariable String org, @PathVariable UUID id) {
        customers.removeSite(org, id);
        return ok(null);
    }

    // ── Assets and maintenance ───────────────────────────────────────────────────────

    @GetMapping("/assets")
    public ResponseEntity<ApiResponse<PageResponse<AssetView>>> assets(@PathVariable String org,
            @RequestParam(required = false) String q, @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Boolean warranty,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ok(assets.list(org, q, customerId, status, warranty, page, size));
    }

    @PostMapping("/assets")
    public ResponseEntity<ApiResponse<AssetView>> createAsset(@PathVariable String org, @Valid @RequestBody AssetRequest in) {
        return created(assets.create(org, in));
    }

    @GetMapping("/assets/{id}")
    public ResponseEntity<ApiResponse<AssetDetail>> asset(@PathVariable String org, @PathVariable UUID id) {
        return ok(assets.get(org, id));
    }

    @PutMapping("/assets/{id}")
    public ResponseEntity<ApiResponse<AssetView>> updateAsset(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody AssetRequest in) {
        return ok(assets.update(org, id, in));
    }

    @GetMapping("/maintenance")
    public ResponseEntity<ApiResponse<List<PlanView>>> plans(@PathVariable String org, @RequestParam(required = false) UUID assetId) {
        return ok(maintenance.list(org, assetId));
    }

    @PostMapping("/maintenance")
    public ResponseEntity<ApiResponse<PlanView>> createPlan(@PathVariable String org, @Valid @RequestBody PlanRequest in) {
        return created(maintenance.create(org, in));
    }

    @PutMapping("/maintenance/{id}")
    public ResponseEntity<ApiResponse<PlanView>> updatePlan(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody PlanRequest in) {
        return ok(maintenance.update(org, id, in));
    }

    /** Generates the work orders of the plans that are due (the hourly job does the same). Safe to repeat. */
    @PostMapping("/maintenance/generate")
    public ResponseEntity<ApiResponse<GenerateResult>> generate(@PathVariable String org) {
        return ok(maintenance.generateNow(org));
    }

    // ── Tickets ──────────────────────────────────────────────────────────────────────

    /** {@code status}: a status or OPEN; {@code assignee}: me, none or a user id; {@code sla}: BREACHED or AT_RISK. */
    @GetMapping("/tickets")
    public ResponseEntity<ApiResponse<PageResponse<TicketSummary>>> tickets(@PathVariable String org,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority, @RequestParam(required = false) String category,
            @RequestParam(required = false) String assignee, @RequestParam(required = false) String sla,
            @RequestParam(required = false) UUID customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ok(tickets.list(org, q, status, priority, category, assignee, sla, customerId, page, size));
    }

    @PostMapping("/tickets")
    public ResponseEntity<ApiResponse<TicketView>> createTicket(@PathVariable String org, @Valid @RequestBody TicketRequest in) {
        return created(tickets.create(org, in));
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<ApiResponse<TicketView>> ticket(@PathVariable String org, @PathVariable UUID id) {
        return ok(tickets.get(org, id));
    }

    @PutMapping("/tickets/{id}")
    public ResponseEntity<ApiResponse<TicketView>> updateTicket(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody TicketRequest in) {
        return ok(tickets.update(org, id, in));
    }

    @PostMapping("/tickets/{id}/status")
    public ResponseEntity<ApiResponse<TicketView>> ticketStatus(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody StatusRequest in) {
        return ok(tickets.status(org, id, in.status()));
    }

    @PostMapping("/tickets/{id}/assign")
    public ResponseEntity<ApiResponse<TicketView>> assign(@PathVariable String org, @PathVariable UUID id,
            @RequestBody AssignRequest in) {
        return ok(tickets.assign(org, id, in.assigneeId()));
    }

    @GetMapping("/tickets/{id}/comments")
    public ResponseEntity<ApiResponse<List<CommentView>>> comments(@PathVariable String org, @PathVariable UUID id) {
        return ok(tickets.comments(org, id));
    }

    @PostMapping("/tickets/{id}/comments")
    public ResponseEntity<ApiResponse<CommentView>> comment(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CommentRequest in) {
        return created(tickets.comment(org, id, in.body()));
    }

    @PostMapping(value = "/tickets/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TicketView>> attach(@PathVariable String org, @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return ok(tickets.attach(org, id, file));
    }

    @DeleteMapping("/tickets/{id}/attachments/{fileId}")
    public ResponseEntity<ApiResponse<TicketView>> detach(@PathVariable String org, @PathVariable UUID id, @PathVariable UUID fileId) {
        return ok(tickets.detach(org, id, fileId));
    }

    @GetMapping("/tickets/{id}/attachments/{fileId}")
    public ResponseEntity<byte[]> file(@PathVariable String org, @PathVariable UUID id, @PathVariable UUID fileId) {
        StoredFile f = tickets.file(org, id, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(files.read(f));
    }

    @GetMapping("/tickets/{id}/activity")
    public ResponseEntity<ApiResponse<List<ActivityView>>> ticketHistory(@PathVariable String org, @PathVariable UUID id) {
        return ok(tickets.history(org, id));
    }

    // ── Work orders ──────────────────────────────────────────────────────────────────

    /** {@code status}: a status or OPEN; {@code technician}: me or a user id; {@code from}/{@code to}: scheduled days. */
    @GetMapping("/work-orders")
    public ResponseEntity<ApiResponse<PageResponse<WorkOrderSummary>>> workOrders(@PathVariable String org,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) String technician, @RequestParam(required = false) UUID ticketId,
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ok(workOrders.list(org, q, status, technician, ticketId, assetId, from, to, page, size));
    }

    @PostMapping("/work-orders")
    public ResponseEntity<ApiResponse<WorkOrderView>> createWorkOrder(@PathVariable String org,
            @Valid @RequestBody WorkOrderRequest in) {
        return created(workOrders.create(org, in));
    }

    @GetMapping("/work-orders/{id}")
    public ResponseEntity<ApiResponse<WorkOrderView>> workOrder(@PathVariable String org, @PathVariable UUID id) {
        return ok(workOrders.get(org, id));
    }

    @PutMapping("/work-orders/{id}")
    public ResponseEntity<ApiResponse<WorkOrderView>> updateWorkOrder(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody WorkOrderRequest in) {
        return ok(workOrders.update(org, id, in));
    }

    /** Schedules a draft, or sends it for approval first when its total is above the threshold. */
    @PostMapping("/work-orders/{id}/schedule")
    public ResponseEntity<ApiResponse<WorkOrderView>> schedule(@PathVariable String org, @PathVariable UUID id) {
        return ok(workOrders.schedule(org, id));
    }

    @PostMapping("/work-orders/{id}/start")
    public ResponseEntity<ApiResponse<WorkOrderView>> start(@PathVariable String org, @PathVariable UUID id) {
        return ok(workOrders.start(org, id));
    }

    @PostMapping("/work-orders/{id}/hold")
    public ResponseEntity<ApiResponse<WorkOrderView>> hold(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody ReasonRequest in) {
        return ok(workOrders.hold(org, id, in.reason()));
    }

    @PostMapping("/work-orders/{id}/complete")
    public ResponseEntity<ApiResponse<WorkOrderView>> complete(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CompleteRequest in) {
        return ok(workOrders.complete(org, id, in));
    }

    @PostMapping("/work-orders/{id}/cancel")
    public ResponseEntity<ApiResponse<WorkOrderView>> cancel(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody ReasonRequest in) {
        return ok(workOrders.cancel(org, id, in.reason()));
    }

    @PostMapping("/work-orders/{id}/checklist/{itemId}")
    public ResponseEntity<ApiResponse<WorkOrderView>> check(@PathVariable String org, @PathVariable UUID id,
            @PathVariable UUID itemId, @RequestBody CheckRequest in) {
        return ok(workOrders.check(org, id, itemId, in.done()));
    }

    @PutMapping("/work-orders/{id}/lines")
    public ResponseEntity<ApiResponse<WorkOrderView>> lines(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody LinesRequest in) {
        return ok(workOrders.setLines(org, id, in.lines()));
    }

    @GetMapping("/work-orders/{id}/activity")
    public ResponseEntity<ApiResponse<List<ActivityView>>> workOrderHistory(@PathVariable String org, @PathVariable UUID id) {
        return ok(workOrders.history(org, id));
    }
}
