package com.businessplatform.ops.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.PermissionSource;

import lombok.RequiredArgsConstructor;

/**
 * The module's permission codes for the client (the services re-check them): {@code service.view} for anyone with
 * a role, {@code service.manage} for Admins and dispatchers, {@code service.admin} for Admins, and
 * {@code service.technician} for people who take work orders.
 */
@Component
@RequiredArgsConstructor
public class OpsPermissions implements PermissionSource {

    private final OpsAccess access;

    @Override
    public List<String> permissions(OrgContext ctx, Set<String> enabledModules) {
        if (!enabledModules.contains(OpsAccess.MODULE)) {
            return List.of();
        }
        return access.who(ctx).map(w -> {
            List<String> p = new ArrayList<>(List.of("service.view"));
            if (w.manager()) {
                p.add("service.manage");
            }
            if (w.admin()) {
                p.add("service.admin");
            }
            if (w.technician()) {
                p.add("service.technician");
            }
            return p;
        }).orElse(List.of());
    }
}
