package com.businessplatform.education.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.PermissionSource;

import lombok.RequiredArgsConstructor;

/** {@code education.view} and {@code education.teach} for members with an active teacher profile (admins get theirs from the core). */
@Component
@RequiredArgsConstructor
public class EducationPermissions implements PermissionSource {

    private final EducationAccess access;

    @Override
    public List<String> permissions(OrgContext ctx, Set<String> enabledModules) {
        List<String> out = new ArrayList<>();
        if (!enabledModules.contains(EducationAccess.MODULE)) {
            return out;
        }
        access.of(ctx).filter(e -> e.teacherId() != null).ifPresent(e -> {
            out.add("education.view");
            out.add("education.teach");
        });
        return out;
    }
}
