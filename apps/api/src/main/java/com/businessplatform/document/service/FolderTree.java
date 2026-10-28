package com.businessplatform.document.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.businessplatform.document.domain.DocFolder;
import com.businessplatform.document.payload.DocumentPayloads.FolderRef;

/**
 * All folders of one organisation, trashed ones included, walked in memory. A folder is <em>live</em>
 * when neither it nor any folder above it is in the trash; {@code null} stands for the top level.
 */
final class FolderTree {

    /** Guards the walks against a cycle that the service never creates but the data could hold. */
    private static final int MAX_DEPTH = 200;

    private final Map<UUID, DocFolder> byId = new HashMap<>();

    FolderTree(List<DocFolder> folders) {
        folders.forEach(f -> byId.put(f.getId(), f));
    }

    Optional<DocFolder> find(UUID id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    /** The folder if it and every folder above it are out of the trash. */
    Optional<DocFolder> live(UUID id) {
        return find(id).filter(f -> isLive(f.getId()));
    }

    boolean isLive(UUID id) {
        UUID at = id;
        for (int i = 0; at != null && i < MAX_DEPTH; i++) {
            DocFolder f = byId.get(at);
            if (f == null || f.getDeletedAt() != null) {
                return false;
            }
            at = f.getParentId();
        }
        return at == null;
    }

    /** From the top level down to {@code id} (empty for the top level). */
    List<FolderRef> path(UUID id) {
        List<FolderRef> out = new ArrayList<>();
        UUID at = id;
        for (int i = 0; at != null && i < MAX_DEPTH; i++) {
            DocFolder f = byId.get(at);
            if (f == null) {
                break;
            }
            out.add(new FolderRef(f.getId(), f.getName()));
            at = f.getParentId();
        }
        Collections.reverse(out);
        return out;
    }

    /** Is {@code id} the folder {@code ancestor} or somewhere inside it? */
    boolean within(UUID id, UUID ancestor) {
        UUID at = id;
        for (int i = 0; at != null && i < MAX_DEPTH; i++) {
            if (at.equals(ancestor)) {
                return true;
            }
            DocFolder f = byId.get(at);
            at = f == null ? null : f.getParentId();
        }
        return false;
    }

    /** The folders directly in {@code parentId} that are not in the trash themselves, by name. */
    List<DocFolder> children(UUID parentId) {
        return byId.values().stream()
                .filter(f -> Objects.equals(f.getParentId(), parentId) && f.getDeletedAt() == null)
                .sorted(Comparator.comparing(DocFolder::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** {@code id} and every folder below it, in any state. */
    Set<UUID> subtree(UUID id) {
        Set<UUID> out = new LinkedHashSet<>();
        for (DocFolder f : byId.values()) {
            if (within(f.getId(), id)) {
                out.add(f.getId());
            }
        }
        return out;
    }

    List<DocFolder> allLive() {
        return byId.values().stream().filter(f -> isLive(f.getId()))
                .sorted(Comparator.comparing(DocFolder::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** Trashed folders whose own parent is live: the top of the trash (what's inside goes back with them). */
    List<DocFolder> trashedTop() {
        return byId.values().stream().filter(f -> f.getDeletedAt() != null && isLive(f.getParentId())).toList();
    }

    /** {@code name}, or "name (2)", "name (3)" … if a live folder in {@code parentId} already has it. */
    String freeName(UUID parentId, String name, UUID except) {
        String candidate = name;
        for (int n = 2; taken(parentId, candidate, except); n++) {
            String suffix = " (" + n + ")";
            candidate = name.substring(0, Math.min(name.length(), 120 - suffix.length())) + suffix;
        }
        return candidate;
    }

    boolean taken(UUID parentId, String name, UUID except) {
        return children(parentId).stream()
                .anyMatch(f -> !f.getId().equals(except) && f.getName().equalsIgnoreCase(name));
    }
}
