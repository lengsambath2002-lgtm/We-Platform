package com.businessplatform.org.service;

import java.util.List;
import java.util.Optional;

import com.businessplatform.module.domain.Module;

/**
 * The permissions a custom role can grant. Each is something Owners and Admins can always do; a role hands one of
 * them to members. {@code module}: the module it belongs to (null = the shared core). {@code scoped}: it can be
 * limited to the people in one unit and the units below it; the others always apply organisation-wide.
 */
public final class RoleCatalogue {

    private RoleCatalogue() {
    }

    public record Entry(String code, String module, boolean scoped) {
    }

    public static final String STRUCTURE = "org.structure.manage";
    public static final String BILLING = "billing.manage";
    public static final String DOCUMENTS = "documents.manage";
    public static final String AUDIT = "activity.view";
    public static final String BUSINESS = "business.manage";
    public static final String RETAIL = "retail.manage";
    public static final String EDUCATION = "education.manage";
    public static final String SERVICE = "service.manage";
    public static final String HR = "hr.manage";
    public static final String PAYROLL = "hr.payroll";
    public static final String RECRUIT = "hr.recruit";

    public static final List<Entry> ENTRIES = List.of(
            new Entry(STRUCTURE, null, false),
            new Entry(BILLING, null, false),
            new Entry(DOCUMENTS, null, false),
            new Entry(AUDIT, null, false),
            new Entry(HR, Module.HR, true),
            new Entry(PAYROLL, Module.HR, false),
            new Entry(RECRUIT, Module.HR, false),
            new Entry(BUSINESS, Module.BUSINESS, false),
            new Entry(RETAIL, Module.RETAIL, false),
            new Entry(EDUCATION, Module.EDUCATION, false),
            new Entry(SERVICE, Module.SERVICE, false));

    public static Optional<Entry> find(String code) {
        return ENTRIES.stream().filter(e -> e.code().equals(code)).findFirst();
    }
}
