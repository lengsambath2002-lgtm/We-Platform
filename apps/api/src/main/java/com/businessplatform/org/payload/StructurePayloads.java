package com.businessplatform.org.payload;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The organisation structure: units, positions and who holds which. */
public final class StructurePayloads {

    private StructurePayloads() {
    }

    public static final String KIND = "BRANCH|DIVISION|DEPARTMENT|CENTER|SECTION|TEAM";
    private static final String CODE = "^[A-Za-z0-9][A-Za-z0-9._-]{0,19}$";

    /** {@code parentId}: empty for a top-level unit. {@code headUserId}: the unit's head, an active member. */
    public record UnitRequest(@NotBlank @Size(max = 120) String name, @NotNull @Pattern(regexp = KIND) String kind,
            UUID parentId, @Size(max = 20) @Pattern(regexp = CODE, message = "Letters, numbers, dot, dash or underscore") String code,
            UUID headUserId, @Size(max = 300) String address, @Size(max = 30) String phone) {
    }

    /** {@code headcount}: how many people the position is planned for (0 = frozen). */
    public record PositionRequest(@NotBlank @Size(max = 80) String title, UUID unitId,
            @Size(max = 20) @Pattern(regexp = CODE, message = "Letters, numbers, dot, dash or underscore") String code,
            @Size(max = 20) String grade, UUID reportsToId, @Min(0) @Max(10000) int headcount,
            @Size(max = 2000) String description) {
    }

    /** A place in the structure: a unit, a position or both (a position in a unit implies that unit). */
    public record Placement(UUID unitId, UUID positionId) {
    }

    /** Replaces the member's places: {@code primary} (or none) and any additional ones. */
    public record PlacementsRequest(@Valid Placement primary, @Size(max = 10) List<@Valid @NotNull Placement> additional) {
    }

    public record PersonBrief(UUID userId, UUID membershipId, String name, String avatarUrl) {
    }

    /** {@code people}: active members placed directly in the unit (sub-units not counted). */
    public record UnitView(UUID id, UUID parentId, String kind, String name, String code, PersonBrief head,
            String address, String phone, boolean archived, int people) {
    }

    /** {@code filled}: active members holding it; {@code headcount - filled} are vacancies when positive. */
    public record PositionView(UUID id, UUID unitId, String title, String code, String grade, UUID reportsToId,
            int headcount, int filled, String description, boolean archived) {
    }

    public record AssignmentView(UUID id, PersonBrief person, String title, UUID unitId, UUID positionId,
            boolean primary) {
    }

    /** Everything the structure pages need; archived units and positions are included, flagged. */
    public record StructureView(List<UnitView> units, List<PositionView> positions, List<AssignmentView> assignments,
            boolean canManage) {
    }
}
