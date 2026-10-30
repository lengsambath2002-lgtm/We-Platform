package com.businessplatform.ops.service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.ops.domain.OpsSettings;
import com.businessplatform.ops.domain.SlaPolicy;
import com.businessplatform.ops.payload.OpsPayloads.MeView;
import com.businessplatform.ops.payload.OpsPayloads.SettingsRequest;
import com.businessplatform.ops.payload.OpsPayloads.SettingsView;
import com.businessplatform.ops.payload.OpsPayloads.SlaRequest;
import com.businessplatform.ops.payload.OpsPayloads.SlaView;
import com.businessplatform.ops.repository.OpsSettingsRepository;
import com.businessplatform.ops.repository.SlaPolicyRepository;

import lombok.RequiredArgsConstructor;

/**
 * The organisation's service settings (approval threshold, labour rate), its SLA targets per priority (defaults:
 * urgent 1 h / 4 h, high 4 h / 1 day, medium 8 h / 2 days, low 1 day / 5 days) and the TCK- / WO- numbers.
 */
@Service
@RequiredArgsConstructor
public class OpsSettingsService {

    public static final String RECORD = "service_settings";
    static final List<String> PRIORITIES = List.of("URGENT", "HIGH", "MEDIUM", "LOW");

    private final OpsAccess access;
    private final OpsSettingsRepository settings;
    private final SlaPolicyRepository policies;
    private final ActivityService activity;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MeView me(String slug) {
        OpsAccess.Who w = access.require(slug);
        return new MeView(w.role(), w.manager(), w.admin(), w.technician(), w.userId());
    }

    @Transactional(readOnly = true)
    public SettingsView get(String slug) {
        OpsAccess.Who w = access.require(slug);
        OpsSettings s = of(w.orgId());
        return new SettingsView(s.getApprovalThreshold(), s.getLabourRate());
    }

    @Transactional
    public SettingsView update(String slug, SettingsRequest in) {
        OpsAccess.Who w = access.requireAdmin(slug);
        OpsSettings s = stored(w.orgId());
        s.setApprovalThreshold(in.approvalThreshold() == null ? null : in.approvalThreshold().setScale(2));
        s.setLabourRate(in.labourRate().setScale(2));
        s.setUpdatedBy(w.userId());
        s.setUpdatedAt(clock.instant());
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, w.orgId(), w.userId(), "service.settings.updated",
                Map.of("threshold", s.getApprovalThreshold() == null ? "" : s.getApprovalThreshold().toPlainString(),
                        "rate", s.getLabourRate().toPlainString()));
        return new SettingsView(s.getApprovalThreshold(), s.getLabourRate());
    }

    @Transactional
    public List<SlaView> sla(String slug) {
        // Writes the defaults on first use.
        OpsAccess.Who w = access.require(slug);
        return slaViews(w);
    }

    /** Replaces the targets of the priorities given (Admins). */
    @Transactional
    public List<SlaView> updateSla(String slug, List<SlaRequest> in) {
        OpsAccess.Who w = access.requireAdmin(slug);
        settings.ensureSla(w.orgId());
        for (SlaRequest r : in) {
            if (r.resolutionMinutes() < r.responseMinutes()) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "resolutionMinutes",
                        "The resolution target can't be shorter than the response target");
            }
            SlaPolicy p = policies.findById(new SlaPolicy.Key(w.orgId(), r.priority())).orElseThrow();
            p.setResponseMinutes(r.responseMinutes());
            p.setResolutionMinutes(r.resolutionMinutes());
        }
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, w.orgId(), w.userId(), "service.sla.updated", Map.of());
        return slaViews(w);
    }

    /** New tickets' targets for {@code priority}. */
    @Transactional
    public SlaPolicy policy(java.util.UUID orgId, String priority) {
        settings.ensureSla(orgId);
        return policies.findById(new SlaPolicy.Key(orgId, priority)).orElseThrow();
    }

    /** The organisation's settings; until first saved, the defaults (nothing is written, so reads can use it). */
    @Transactional(readOnly = true)
    public OpsSettings of(java.util.UUID orgId) {
        return settings.findById(orgId).orElseGet(() -> {
            OpsSettings d = new OpsSettings();
            d.setOrgId(orgId);
            d.setApprovalThreshold(new java.math.BigDecimal("500.00"));
            d.setLabourRate(new java.math.BigDecimal("15.00"));
            return d;
        });
    }

    private OpsSettings stored(java.util.UUID orgId) {
        settings.ensure(orgId);
        return settings.findById(orgId).orElseThrow();
    }

    /** TCK-0001 / WO-0001: the organisation's next number of the kind. */
    @Transactional
    public String nextNumber(java.util.UUID orgId, String kind) {
        int n = settings.next(orgId, kind);
        return ("TICKET".equals(kind) ? "TCK-" : "WO-") + String.format("%04d", n);
    }

    private List<SlaView> slaViews(OpsAccess.Who w) {
        settings.ensureSla(w.orgId());
        return policies.findByOrgId(w.orgId()).stream()
                .sorted(Comparator.comparingInt(p -> PRIORITIES.indexOf(p.getPriority())))
                .map(p -> new SlaView(p.getPriority(), p.getResponseMinutes(), p.getResolutionMinutes())).toList();
    }
}
