package com.businessplatform.module.service;

import java.util.UUID;

/** Whether an organisation's plan includes a module (implemented by billing; without it, every module is allowed). */
public interface ModuleEntitlements {

    boolean allows(UUID orgId, String moduleKey);
}
