package com.businessplatform.hr.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.businessplatform.hr.payload.HrPayloads.PersonRef;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Employee records: personal details, contracts, documents, salary history and what expires soon. */
public final class RecordPayloads {

    private RecordPayloads() {
    }

    private static final String PHONE = "^$|^[0-9+()\\-. ]{6,30}$";

    /**
     * Personal details; empty clears. HR managers change all of it; people their own address, personal email and
     * phone (the rest of what they send is ignored).
     */
    public record PersonalRequest(@Size(max = 40) String nickname, @Size(max = 120) String duty, @Size(max = 60) String category,
            @Pattern(regexp = "^$|MALE|FEMALE|OTHER") String gender, LocalDate dateOfBirth, @Size(max = 60) String nationality,
            @Pattern(regexp = "^$|NATIONAL_ID|PASSPORT|OTHER") String idType, @Size(max = 40) String idNumber,
            LocalDate idExpiresOn, @Size(max = 30) String nssfNumber, @Size(max = 300) String homeAddress,
            @Email @Size(max = 254) String personalEmail,
            @Pattern(regexp = PHONE, message = "Use digits, spaces and + ( ) - only") String personalPhone) {
    }

    /** {@code nickname}, {@code duty} and {@code category} are for everyone; the rest is null unless shown. */
    public record PersonalView(String nickname, String duty, String category, String gender, LocalDate dateOfBirth,
            String nationality, String idType, String idNumber, LocalDate idExpiresOn, String nssfNumber, String homeAddress,
            String personalEmail, String personalPhone) {
    }

    public record ContractRequest(@NotNull @Pattern(regexp = "PROBATION|FIXED_TERM|UNLIMITED|INTERNSHIP|OTHER") String kind,
            @NotNull LocalDate startsOn, LocalDate endsOn, LocalDate probationEndsOn,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal monthlySalary, @Size(max = 1000) String note) {
    }

    /** {@code status}: UPCOMING, ACTIVE or ENDED today; {@code monthlySalary} only for payroll and the person. */
    public record ContractView(UUID id, String kind, LocalDate startsOn, LocalDate endsOn, LocalDate probationEndsOn,
            BigDecimal monthlySalary, String note, String fileName, String status, PersonRef createdBy, Instant createdAt) {
    }

    public record DocumentView(UUID id, String category, String title, LocalDate expiresOn, String fileName, long sizeBytes,
            PersonRef uploadedBy, Instant createdAt, boolean canDelete) {
    }

    /**
     * {@code shown}: private details, contracts and documents are visible (the person and their HR managers);
     * {@code canEdit}: HR may change everything; {@code self}: the person (own contacts, own documents).
     */
    public record RecordsView(PersonalView personal, List<ContractView> contracts, List<DocumentView> documents, boolean shown,
            boolean canEdit, boolean self) {
    }

    /** {@code kind}: CONTRACT_END, PROBATION_END, ID_EXPIRY or DOCUMENT_EXPIRY; {@code days}: left (negative = past). */
    public record ExpiringItem(String kind, PersonRef person, LocalDate date, long days, String label) {
    }

    public record PayChangeView(UUID id, BigDecimal monthlySalary, BigDecimal previous, LocalDate effectiveOn, String reason,
            PersonRef changedBy, Instant createdAt) {
    }
}
