package com.businessplatform.ops.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Service &amp; Operations: staff, SLA, customers, sites, assets, tickets, work orders, maintenance and reports. */
public final class OpsPayloads {

    private OpsPayloads() {
    }

    private static final String PHONE = "^$|^[0-9+()\\-. ]{6,30}$";
    private static final String PHONE_MESSAGE = "Use digits, spaces and + ( ) - only";
    private static final String CENTS = "Use at most 2 decimals";
    private static final String PRIORITY = "URGENT|HIGH|MEDIUM|LOW";

    // ── Requests ─────────────────────────────────────────────────────────────────────

    /** {@code approvalThreshold} null: work orders never need approval. */
    public record SettingsRequest(
            @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2, message = CENTS) BigDecimal approvalThreshold,
            @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") @Digits(integer = 10, fraction = 2, message = CENTS) BigDecimal labourRate) {
    }

    public record SlaRequest(@NotNull @Pattern(regexp = PRIORITY) String priority,
            @NotNull @Min(5) @Max(525600) Integer responseMinutes, @NotNull @Min(5) @Max(525600) Integer resolutionMinutes) {
    }

    public record StaffRequest(UUID userId, @NotNull @Pattern(regexp = "DISPATCHER|TECHNICIAN") String role,
            @Size(max = 12) List<@NotBlank @Size(max = 40) String> skills,
            @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone, Boolean active) {
    }

    public record CustomerRequest(@NotBlank @Size(max = 200) String name, @Size(max = 120) String contactName,
            @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone, @Email @Size(max = 160) String email,
            @Size(max = 500) String address, @Size(max = 2000) String notes) {
    }

    public record SiteRequest(@NotBlank @Size(max = 160) String name, @Size(max = 500) String address,
            @Size(max = 1000) String notes) {
    }

    public record AssetRequest(@NotNull UUID customerId, UUID siteId, @NotBlank @Size(max = 160) String name,
            @Size(max = 80) String serial, @Size(max = 120) String model, @Size(max = 120) String manufacturer,
            LocalDate installedOn, LocalDate warrantyEnd, @Pattern(regexp = "ACTIVE|IN_REPAIR|RETIRED") String status,
            @Size(max = 2000) String notes) {
    }

    public record TicketRequest(@NotBlank @Size(max = 200) String title, @Size(max = 4000) String description,
            UUID customerId, UUID siteId, UUID assetId, @Size(max = 120) String requesterName,
            @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String requesterPhone,
            @NotNull @Pattern(regexp = "REPAIR|INSTALLATION|MAINTENANCE|INSPECTION|COMPLAINT|OTHER") String category,
            @NotNull @Pattern(regexp = PRIORITY) String priority, UUID assigneeId) {
    }

    public record StatusRequest(@NotNull @Pattern(regexp = "NEW|TRIAGED|IN_PROGRESS|WAITING|RESOLVED|CLOSED") String status) {
    }

    /** {@code assigneeId} null: unassigned. */
    public record AssignRequest(UUID assigneeId) {
    }

    public record CommentRequest(@NotBlank @Size(max = 4000) String body) {
    }

    public record LineInput(@NotNull @Pattern(regexp = "PART|LABOUR") String kind, @NotBlank @Size(max = 300) String description,
            @NotNull @DecimalMin(value = "0.001") @DecimalMax("999999.999") @Digits(integer = 9, fraction = 3, message = "Use at most 3 decimals") BigDecimal quantity,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999.99") @Digits(integer = 10, fraction = 2, message = CENTS) BigDecimal unitPrice) {
    }

    public record WorkOrderRequest(@NotBlank @Size(max = 200) String title, @Size(max = 4000) String description,
            @NotNull @Pattern(regexp = PRIORITY) String priority, UUID ticketId, UUID customerId, UUID siteId, UUID assetId,
            Instant scheduledStart, Instant scheduledEnd, @Size(max = 10) List<@NotNull UUID> technicianIds,
            @Size(max = 50) List<@NotBlank @Size(max = 200) String> checklist, @Size(max = 100) List<@Valid @NotNull LineInput> lines) {
    }

    public record LinesRequest(@NotNull @Size(max = 100) List<@Valid @NotNull LineInput> lines) {
    }

    public record CheckRequest(boolean done) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record CompleteRequest(@Size(max = 4000) String notes, @NotBlank @Size(max = 120) String signedBy) {
    }

    public record PlanRequest(@NotNull UUID assetId, @NotBlank @Size(max = 200) String title,
            @NotNull @Min(1) @Max(366) Integer intervalCount, @NotNull @Pattern(regexp = "DAYS|MONTHS") String intervalUnit,
            @NotNull LocalDate nextDue, @Min(0) @Max(90) Integer leadDays, UUID technicianId,
            @Size(max = 50) List<@NotBlank @Size(max = 200) String> checklist, Boolean active) {
    }

    // ── Views ────────────────────────────────────────────────────────────────────────

    public record PersonRef(UUID id, String name, String avatarUrl) {
    }

    public record Ref(UUID id, String name) {
    }

    public record SettingsView(BigDecimal approvalThreshold, BigDecimal labourRate) {
    }

    public record SlaView(String priority, int responseMinutes, int resolutionMinutes) {
    }

    /** {@code role}: ADMIN for Owners and Admins, else DISPATCHER or TECHNICIAN. */
    public record MeView(String role, boolean manager, boolean admin, boolean technician, UUID userId) {
    }

    public record StaffView(UUID id, PersonRef person, String email, String orgRole, String role, List<String> skills,
            String phone, boolean active, long openWorkOrders) {
    }

    public record CustomerView(UUID id, String name, String contactName, String phone, String email, String address,
            String notes, boolean archived, int sites, int assets, int openTickets, Instant createdAt) {
    }

    public record SiteView(UUID id, UUID customerId, String name, String address, String notes) {
    }

    public record CustomerDetail(CustomerView customer, List<SiteView> sites, List<AssetView> assets,
            List<TicketSummary> tickets, List<WorkOrderSummary> workOrders) {
    }

    /** {@code underWarranty}: warranty end today or later. */
    public record AssetView(UUID id, Ref customer, Ref site, String name, String serial, String model,
            String manufacturer, LocalDate installedOn, LocalDate warrantyEnd, boolean underWarranty, String status,
            String notes, LocalDate nextMaintenance, Instant createdAt) {
    }

    public record AssetDetail(AssetView asset, List<WorkOrderSummary> workOrders, List<TicketSummary> tickets,
            List<PlanView> plans) {
    }

    /** {@code sla}: ON_TRACK, AT_RISK, BREACHED or MET (derived on the server, at the time of the request). */
    public record TicketSummary(UUID id, String number, String title, String category, String priority, String status,
            Ref customer, PersonRef assignee, String sla, Instant responseDueAt, Instant resolutionDueAt,
            Instant createdAt, Instant updatedAt) {
    }

    public record FileRef(UUID id, String name, String contentType, long size, Instant createdAt, PersonRef addedBy) {
    }

    public record CommentView(UUID id, PersonRef author, String body, Instant createdAt) {
    }

    public record TicketView(TicketSummary summary, String description, Ref site, Ref asset, String requesterName,
            String requesterPhone, PersonRef createdBy, String responseSla, String resolutionSla, Instant respondedAt,
            Instant resolvedAt, Instant closedAt, List<String> nextStatuses, List<FileRef> attachments,
            List<WorkOrderSummary> workOrders, boolean canEdit, boolean canAssign, boolean canCreateWorkOrder) {
    }

    public record LineView(UUID id, int position, String kind, String description, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal amount) {
    }

    public record CheckView(UUID id, int position, String label, boolean done, PersonRef doneBy, Instant doneAt) {
    }

    public record WorkOrderSummary(UUID id, String number, String title, String priority, String status,
            String approvalState, Ref customer, Ref asset, List<PersonRef> technicians, Instant scheduledStart,
            Instant scheduledEnd, BigDecimal total, boolean preventive, int checklistDone, int checklistTotal,
            Instant createdAt) {
    }

    /**
     * {@code needsApproval}: scheduling it now would go through approval (total above the threshold and not approved
     * for at least this total). {@code approvalStatus}: the engine's status of the request, while there is one.
     */
    public record WorkOrderView(WorkOrderSummary summary, String description, Ref site, Ref ticket, String ticketNumber,
            Ref plan, LocalDate planDue, BigDecimal partsTotal, BigDecimal labourTotal, UUID approvalId,
            String approvalStatus, BigDecimal approvedTotal, BigDecimal approvalThreshold, boolean needsApproval,
            String holdReason, Instant startedAt, Instant completedAt, PersonRef completedBy, String completionNotes,
            String signedBy, String cancelReason, PersonRef createdBy, List<CheckView> checklist, List<LineView> lines,
            boolean canEdit, boolean canSchedule, boolean canStart, boolean canHold, boolean canComplete,
            boolean canCancel, boolean canEditLines, boolean canCheck) {
    }

    public record PlanView(UUID id, Ref asset, Ref customer, String title, int intervalCount, String intervalUnit,
            LocalDate nextDue, int leadDays, LocalDate generateOn, PersonRef technician, List<String> checklist,
            boolean active, Instant lastGeneratedAt) {
    }

    public record GenerateResult(int created, List<WorkOrderSummary> workOrders) {
    }

    public record ActivityView(UUID id, PersonRef actor, String verb, Map<String, Object> data, Instant at) {
    }

    public record WeekCount(LocalDate week, long opened, long resolved) {
    }

    /** The dashboard. Technicians see their own tickets and work orders; managers everyone's. */
    public record Overview(String scope, Map<String, Long> ticketsByStatus, long openTickets, long breached, long atRisk,
            List<TicketSummary> breachedTickets, List<WorkOrderSummary> workOrdersThisWeek, long awaitingApproval,
            List<WeekCount> weeks, List<PlanView> upcomingMaintenance) {
    }

    public record TechnicianReport(PersonRef technician, long completed, BigDecimal labourHours, BigDecimal labour,
            BigDecimal parts, BigDecimal total) {
    }

    public record SlaReport(String priority, long tickets, long responseMet, long responseBreached, long resolutionMet,
            long resolutionBreached, long open) {
    }

    public record CategoryCount(String category, long tickets) {
    }

    public record Report(LocalDate from, LocalDate to, long ticketsOpened, long ticketsResolved, long workOrdersCompleted,
            BigDecimal completedValue, List<TechnicianReport> technicians, List<SlaReport> sla,
            List<CategoryCount> categories) {
    }
}
