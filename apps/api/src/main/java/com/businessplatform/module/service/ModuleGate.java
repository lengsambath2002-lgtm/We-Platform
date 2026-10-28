package com.businessplatform.module.service;

/**
 * Lets a module that needs server configuration (e.g. Retail &amp; Commerce needs the VirtualBiz address) say
 * whether it can be used on this server. A module with a gate is available only when the registry says so
 * and its gate is ready; switching it on otherwise is refused with the gate's reason.
 */
public interface ModuleGate {

    String moduleKey();

    boolean ready();

    /** Throws the refusal (a {@code BusinessException} with a clear message) when not ready. */
    void requireReady();
}
