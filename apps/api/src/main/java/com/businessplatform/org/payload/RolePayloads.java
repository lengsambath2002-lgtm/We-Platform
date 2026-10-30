package com.businessplatform.org.payload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.businessplatform.org.payload.StructurePayloads.PersonBrief;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Custom roles and who holds them. */
public final class RolePayloads {

    private RolePayloads() {
    }

    public record RoleRequest(@NotBlank @Size(max = 60) String name, @Size(max = 300) String description,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 40) String> permissions) {
    }

    /** {@code unitId}: limit the role to that unit and the units below it (empty = the whole organisation). */
    public record GrantRequest(@NotNull UUID membershipId, UUID unitId) {
    }

    /** {@code available}: its module is switched on (core permissions always are). */
    public record CatalogueEntry(String code, String module, boolean scoped, boolean available) {
    }

    /** {@code scopable}: it has a permission that can be limited to a unit. */
    public record RoleView(UUID id, String name, String description, List<String> permissions, boolean scopable,
            int holders) {
    }

    public record GrantView(UUID id, UUID roleId, PersonBrief person, UUID unitId, String unitName, Instant createdAt) {
    }

    public record RolesView(List<CatalogueEntry> catalogue, List<RoleView> roles, List<GrantView> grants) {
    }
}
