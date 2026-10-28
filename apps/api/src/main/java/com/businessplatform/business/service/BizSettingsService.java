package com.businessplatform.business.service;

import java.time.Clock;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.business.domain.BizSettings;
import com.businessplatform.business.domain.Totals;
import com.businessplatform.business.payload.BusinessPayloads.SettingsRequest;
import com.businessplatform.business.payload.BusinessPayloads.SettingsView;
import com.businessplatform.business.repository.BizSettingsRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * The organisation's business settings (Admins change them; everyone in the module reads them, since invoices
 * print them) and its document numbers: Q-0001 for quotes, INV-0001 for invoices, counted per organisation.
 */
@Service
@RequiredArgsConstructor
public class BizSettingsService {

    public static final String RECORD = "business_settings";

    private final BusinessAccess access;
    private final BizSettingsRepository settings;
    private final ActivityService activity;
    private final Clock clock;

    @Transactional
    public SettingsView get(String slug) {
        OrgContext ctx = access.require(slug);
        return view(ctx, settings(ctx));
    }

    @Transactional
    public SettingsView update(String slug, SettingsRequest in) {
        OrgContext ctx = access.requireManager(slug);
        BizSettings s = settings(ctx);
        s.setLegalName(BizPeople.clean(in.legalName()));
        s.setAddress(BizPeople.clean(in.address()));
        s.setTaxId(BizPeople.clean(in.taxId()));
        s.setPhone(BizPeople.clean(in.phone()));
        s.setEmail(BizPeople.clean(in.email()));
        s.setInvoiceFooter(BizPeople.clean(in.invoiceFooter()));
        s.setPaymentTermsDays(in.paymentTermsDays().shortValue());
        s.setVatDefault(in.vatDefault());
        s.setUpdatedBy(ctx.userId());
        s.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, ctx.orgId(), ctx.userId(), "business.settings.updated",
                Map.of("terms", s.getPaymentTermsDays(), "vat", s.isVatDefault()));
        return view(ctx, s);
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    /** The organisation's settings (created with the defaults on first use). */
    @Transactional
    public BizSettings settings(OrgContext ctx) {
        settings.ensure(ctx.orgId());
        return settings.findById(ctx.orgId()).orElseThrow();
    }

    /** The next quote number, "Q-0001". */
    @Transactional
    public String nextQuoteNumber(OrgContext ctx) {
        return String.format("Q-%04d", settings.next(ctx.orgId(), "QUOTE"));
    }

    /** The next invoice number, "INV-0001". */
    @Transactional
    public String nextInvoiceNumber(OrgContext ctx) {
        return String.format("INV-%04d", settings.next(ctx.orgId(), "INVOICE"));
    }

    static SettingsView view(OrgContext ctx, BizSettings s) {
        return new SettingsView(s.getLegalName(), s.getAddress(), s.getTaxId(), s.getPhone(), s.getEmail(),
                s.getInvoiceFooter(), s.getPaymentTermsDays(), s.isVatDefault(), Totals.VAT_RATE, ctx.orgName());
    }
}
