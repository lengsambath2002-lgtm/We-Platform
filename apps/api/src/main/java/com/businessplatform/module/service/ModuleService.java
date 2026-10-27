package com.businessplatform.module.service;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.domain.OrgModule;
import com.businessplatform.module.repository.ModuleRepository;
import com.businessplatform.module.repository.OrgModuleRepository;

import lombok.RequiredArgsConstructor;

/** The module registry and each organisation's switched-on modules. */
@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleRepository modules;
    private final OrgModuleRepository orgModules;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Module> registry() {
        return modules.findAllByOrderBySortOrder();
    }

    @Transactional(readOnly = true)
    public Set<String> enabled(UUID orgId) {
        return orgModules.findByOrgId(orgId).stream().filter(OrgModule::isEnabled)
                .map(OrgModule::getModuleKey).collect(Collectors.toSet());
    }

    /** MODULE_DISABLED unless the organisation has {@code key} switched on. */
    @Transactional(readOnly = true)
    public void require(UUID orgId, String key) {
        if (!enabled(orgId).contains(key)) {
            throw new BusinessException(ErrorCode.MODULE_DISABLED);
        }
    }

    @Transactional
    public void set(UUID orgId, String key, boolean on, UUID by) {
        Module m = modules.findById(key).orElseThrow(BusinessException::notFound);
        if (on && !m.available()) {
            throw BusinessException.field(ErrorCode.INVALID_STATE, "module", "This module is not available yet");
        }
        OrgModule om = orgModules.findById(new OrgModule.Key(orgId, key)).orElseGet(() -> {
            OrgModule n = new OrgModule();
            n.setOrgId(orgId);
            n.setModuleKey(key);
            return n;
        });
        om.setEnabled(on);
        om.setChangedBy(by);
        om.setChangedAt(clock.instant());
        orgModules.save(om);
    }
}
