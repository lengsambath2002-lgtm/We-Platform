package com.businessplatform.project.service;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.project.payload.ProjectPayloads.PersonRef;

import lombok.RequiredArgsConstructor;

/** Names and avatars for a batch of user ids (one query). */
@Component
@RequiredArgsConstructor
public class People {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public Map<UUID, PersonRef> of(Collection<UUID> ids) {
        var wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return users.findByIdIn(wanted).stream().collect(Collectors.toMap(AppUser::getId,
                u -> new PersonRef(u.getId(), u.getName(), u.getAvatarUrl()), (a, b) -> a));
    }

    public static <T> Function<UUID, PersonRef> lookup(Map<UUID, PersonRef> map) {
        return id -> id == null ? null : map.get(id);
    }
}
