package com.businessplatform.org.payload;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bulk import of people and the organisation structure (a spreadsheet's rows, parsed by the client). */
public final class ImportPayloads {

    private ImportPayloads() {
    }

    /**
     * One person. {@code unitPath}: "Mobile Center > Mobile" (created when missing); {@code unitKinds}: their types,
     * e.g. "CENTER > TEAM" (new units only; default Department, then Team). {@code position}: a title in that unit.
     * {@code managerEmail} / {@code secondManagerEmail}: people in the same file or already members.
     * {@code unitHead}: they head their unit. Empty values leave what is there unchanged.
     */
    public record ImportRow(@Size(max = 80) String name, @Size(max = 254) String email, @Size(max = 10) String role,
            @Size(max = 600) String unitPath, @Size(max = 200) String unitKinds, @Size(max = 80) String position,
            @Size(max = 254) String managerEmail, @Size(max = 254) String secondManagerEmail,
            @Size(max = 10) String employmentType, @Size(max = 10) String startDate, Boolean unitHead) {
    }

    public record ImportRequest(@NotNull @Size(min = 1, max = 2000) List<@NotNull @Valid ImportRow> rows) {
    }

    /**
     * {@code action}: CREATE (a new account, emailed a link to choose a password), INVITE (has an account already:
     * invited, and placed by importing again once they accept), UPDATE (already a member), ERROR (see
     * {@code problems}; nothing is imported while any row has one). {@code notes}: what will be left out.
     */
    public record RowResult(int row, String email, String name, String action, List<String> problems, List<String> notes,
            String unit, String position, String manager) {
    }

    public record ImportResult(boolean applied, List<RowResult> rows, int creates, int invites, int updates, int errors,
            List<String> newUnits, List<String> newPositions) {
    }
}
