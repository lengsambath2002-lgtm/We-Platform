package com.businessplatform.org.payload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.businessplatform.org.domain.OrgRole;

/** Organisations, members, invitations and teams. */
public final class OrgPayloads {

    private OrgPayloads() {
    }

    public record CreateOrgRequest(@NotBlank @Size(min = 2, max = 120) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9]([a-z0-9-]{1,38}[a-z0-9])$",
                    message = "3–40 lowercase letters, numbers or dashes") String slug) {
    }

    public record UpdateOrgRequest(@NotBlank @Size(min = 2, max = 120) String name) {
    }

    public record OrgSummary(UUID id, String slug, String name, OrgRole role) {
    }

    public record ModuleState(String key, String nameEn, String nameKm, String kind, boolean available,
            boolean enabled) {
    }

    /** Everything the shell needs: who I am here, what I may do, which modules are on. */
    public record OrgContextResponse(OrgSummary org, List<String> permissions, List<ModuleState> modules) {
    }

    public record MemberResponse(UUID membershipId, UUID userId, String name, String email, String avatarUrl,
            OrgRole role, String status, String title, Instant joinedAt) {
    }

    public record UpdateMemberRequest(OrgRole role, @Pattern(regexp = "ACTIVE|SUSPENDED") String status,
            @Size(max = 80) String title) {
    }

    public record InviteRequest(@NotBlank @Email @Size(max = 254) String email, @NotNull OrgRole role) {
    }

    /** {@code link} is shown to the inviter to copy: it only works for an account signed in with {@code email}. */
    public record InvitationResponse(UUID id, String email, OrgRole role, Instant expiresAt, String link) {
    }

    public record InvitationPreview(String orgName, String email, OrgRole role, boolean expired) {
    }

    public record TeamRequest(@NotBlank @Size(min = 2, max = 80) String name, List<UUID> membershipIds) {
    }

    public record TeamResponse(UUID id, String name, List<UUID> membershipIds) {
    }
}
