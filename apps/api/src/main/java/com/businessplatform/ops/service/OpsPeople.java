package com.businessplatform.ops.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.ops.payload.OpsPayloads.PersonRef;

import lombok.RequiredArgsConstructor;

/** Names and avatars of the people on service records. */
@Component
@RequiredArgsConstructor
public class OpsPeople {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public Map<UUID, PersonRef> of(Collection<UUID> ids) {
        Map<UUID, PersonRef> out = new HashMap<>();
        var wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!wanted.isEmpty()) {
            users.findByIdIn(wanted).forEach(u -> out.put(u.getId(), new PersonRef(u.getId(), u.getName(), u.getAvatarUrl())));
        }
        return out;
    }

    public PersonRef one(UUID id) {
        return id == null ? null : of(List.of(id)).get(id);
    }

    /** Trimmed text, or null when empty. */
    public static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** A LIKE pattern for a search, escaped. */
    public static String like(String q) {
        return "%" + q.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
