package com.businessplatform.business.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.business.payload.BusinessPayloads.PersonRef;
import com.businessplatform.identity.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Names and avatars of the people on business records. */
@Component
@RequiredArgsConstructor
public class BizPeople {

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
        return id == null ? null : of(java.util.List.of(id)).get(id);
    }

    /** Trimmed text, or null when empty. */
    public static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
