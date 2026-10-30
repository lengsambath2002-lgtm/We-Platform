package com.businessplatform.hr.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.file.FileService;
import com.businessplatform.file.StoredFile;
import com.businessplatform.file.StoredFileRepository;
import com.businessplatform.hr.domain.EmployeeContract;
import com.businessplatform.hr.domain.EmployeeDocument;
import com.businessplatform.hr.domain.EmployeeProfile;
import com.businessplatform.hr.domain.PayChange;
import com.businessplatform.hr.payload.HrPayloads.PersonRef;
import com.businessplatform.hr.payload.RecordPayloads.*;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.hr.repository.RecordRepositories.Contracts;
import com.businessplatform.hr.repository.RecordRepositories.Documents;
import com.businessplatform.hr.repository.RecordRepositories.PayChanges;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.RoleCatalogue;

import lombok.RequiredArgsConstructor;

/**
 * Employee records: personal details, contracts and documents, seen by the person and their HR managers (HR managers
 * of their unit included); HR managers change them, people their own contacts and documents. Salary history is
 * payroll's. "Expiring soon" lists contracts, probations, IDs and documents ending within a window.
 */
@Service
@RequiredArgsConstructor
public class RecordService {

    private static final Set<String> DOC_CATEGORIES = Set.of("ID", "CONTRACT", "CERTIFICATE", "MEDICAL", "OTHER");

    private final HrAccess hr;
    private final ProfileService profiles;
    private final EmployeeProfileRepository profileRows;
    private final Contracts contracts;
    private final Documents documents;
    private final PayChanges payChanges;
    private final MembershipRepository memberships;
    private final FileService fileService;
    private final StoredFileRepository files;
    private final ActivityService activity;
    private final Clock clock;

    // ── The record ───────────────────────────────────────────────────────────────────

    @Transactional
    public RecordsView get(String slug, UUID userId) {
        OrgContext ctx = hr.require(slug);
        return view(ctx, profiles.profile(ctx, userId));
    }

    @Transactional
    public RecordsView updatePersonal(String slug, UUID userId, PersonalRequest in) {
        OrgContext ctx = hr.require(slug);
        EmployeeProfile p = profiles.profile(ctx, userId);
        boolean hrm = HrAccess.managesPerson(ctx, userId);
        boolean self = userId.equals(ctx.userId());
        if (!hrm && !self) {
            throw BusinessException.forbidden();
        }
        if (in.dateOfBirth() != null && in.dateOfBirth().isAfter(LeaveService.today(clock))) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "dateOfBirth", "A birth date can't be in the future");
        }
        List<String> changed = new ArrayList<>();
        if (hrm) {
            set(changed, "nickname", p.getNickname(), clean(in.nickname()), p::setNickname);
            set(changed, "duty", p.getDuty(), clean(in.duty()), p::setDuty);
            set(changed, "category", p.getCategory(), clean(in.category()), p::setCategory);
            set(changed, "gender", p.getGender(), clean(in.gender()), p::setGender);
            set(changed, "dateOfBirth", p.getDateOfBirth(), in.dateOfBirth(), p::setDateOfBirth);
            set(changed, "nationality", p.getNationality(), clean(in.nationality()), p::setNationality);
            set(changed, "idType", p.getIdType(), clean(in.idType()), p::setIdType);
            set(changed, "idNumber", p.getIdNumber(), clean(in.idNumber()), p::setIdNumber);
            set(changed, "idExpiresOn", p.getIdExpiresOn(), in.idExpiresOn(), p::setIdExpiresOn);
            set(changed, "nssfNumber", p.getNssfNumber(), clean(in.nssfNumber()), p::setNssfNumber);
        }
        set(changed, "homeAddress", p.getHomeAddress(), clean(in.homeAddress()), p::setHomeAddress);
        set(changed, "personalEmail", p.getPersonalEmail(), clean(in.personalEmail()), p::setPersonalEmail);
        set(changed, "personalPhone", p.getPersonalPhone(), clean(in.personalPhone()), p::setPersonalPhone);
        if (!changed.isEmpty()) {
            p.setUpdatedAt(clock.instant());
            // Which fields changed, never their values (the audit log has more readers than HR).
            activity.record(ctx.orgId(), HrAccess.MODULE, ProfileService.RECORD, p.getId(), ctx.userId(), "employee.personal.updated",
                    Map.of("name", name(userId), "fields", changed));
        }
        return view(ctx, p);
    }

    // ── Contracts ────────────────────────────────────────────────────────────────────

    @Transactional
    public RecordsView addContract(String slug, UUID userId, ContractRequest in) {
        OrgContext ctx = manager(slug, userId);
        EmployeeProfile p = profiles.profile(ctx, userId);
        EmployeeContract c = new EmployeeContract();
        c.setId(Ids.next());
        c.setOrgId(ctx.orgId());
        c.setUserId(userId);
        c.setCreatedBy(ctx.userId());
        c.setCreatedAt(clock.instant());
        fill(ctx, c, in);
        contracts.save(c);
        activity.record(ctx.orgId(), HrAccess.MODULE, ProfileService.RECORD, p.getId(), ctx.userId(), "employee.contract.added",
                Map.of("name", name(userId), "kind", c.getKind(), "start", c.getStartsOn().toString()));
        return view(ctx, p);
    }

    @Transactional
    public RecordsView updateContract(String slug, UUID id, ContractRequest in) {
        OrgContext ctx = hr.require(slug);
        EmployeeContract c = contracts.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        requireManager(ctx, c.getUserId());
        fill(ctx, c, in);
        return view(ctx, profiles.profile(ctx, c.getUserId()));
    }

    @Transactional
    public RecordsView deleteContract(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        EmployeeContract c = contracts.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        requireManager(ctx, c.getUserId());
        contracts.delete(c);
        if (c.getFileId() != null) {
            fileService.discard(ctx.orgId(), List.of(c.getFileId()));
        }
        EmployeeProfile p = profiles.profile(ctx, c.getUserId());
        activity.record(ctx.orgId(), HrAccess.MODULE, ProfileService.RECORD, p.getId(), ctx.userId(), "employee.contract.removed",
                Map.of("name", name(c.getUserId()), "kind", c.getKind()));
        return view(ctx, p);
    }

    /** The signed contract (a scan or PDF); replaces an earlier file. */
    @Transactional
    public RecordsView attachContractFile(String slug, UUID id, MultipartFile upload) {
        OrgContext ctx = hr.require(slug);
        EmployeeContract c = contracts.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        requireManager(ctx, c.getUserId());
        StoredFile f = fileService.store(ctx.orgId(), ctx.userId(), upload);
        UUID old = c.getFileId();
        c.setFileId(f.getId());
        c.setUpdatedAt(clock.instant());
        contracts.saveAndFlush(c);
        if (old != null) {
            fileService.discard(ctx.orgId(), List.of(old));
        }
        return view(ctx, profiles.profile(ctx, c.getUserId()));
    }

    @Transactional(readOnly = true)
    public StoredFile contractFile(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        EmployeeContract c = contracts.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        if (!sees(ctx, c.getUserId()) || c.getFileId() == null) {
            throw BusinessException.notFound();
        }
        return file(ctx, c.getFileId());
    }

    private void fill(OrgContext ctx, EmployeeContract c, ContractRequest in) {
        if (in.endsOn() != null && in.endsOn().isBefore(in.startsOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endsOn", "The end is before the start");
        }
        if ("FIXED_TERM".equals(in.kind()) && in.endsOn() == null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endsOn", "A fixed-term contract needs an end date");
        }
        if (in.probationEndsOn() != null && in.probationEndsOn().isBefore(in.startsOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "probationEndsOn", "Probation ends after the start");
        }
        c.setKind(in.kind());
        c.setStartsOn(in.startsOn());
        c.setEndsOn("UNLIMITED".equals(in.kind()) ? null : in.endsOn());
        c.setProbationEndsOn(in.probationEndsOn());
        // Only payroll keeps salaries on contracts; others leave what is there.
        if (HrAccess.runsPayroll(ctx)) {
            c.setMonthlySalary(in.monthlySalary());
        }
        c.setNote(clean(in.note()));
        c.setUpdatedAt(clock.instant());
    }

    // ── Documents ────────────────────────────────────────────────────────────────────

    /** HR managers file any document; people their own (not an ID or a contract, which HR checks). */
    @Transactional
    public RecordsView addDocument(String slug, UUID userId, String category, String title, LocalDate expiresOn, MultipartFile upload) {
        OrgContext ctx = hr.require(slug);
        EmployeeProfile p = profiles.profile(ctx, userId);
        boolean hrm = HrAccess.managesPerson(ctx, userId);
        if (!hrm && !userId.equals(ctx.userId())) {
            throw BusinessException.forbidden();
        }
        String cat = category == null ? "OTHER" : category.strip().toUpperCase(java.util.Locale.ROOT);
        if (!DOC_CATEGORIES.contains(cat)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "category", "Choose a kind of document");
        }
        if (!hrm && Set.of("ID", "CONTRACT").contains(cat)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "category", "HR files IDs and contracts");
        }
        String t = clean(title);
        if (t == null || t.length() > 160) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "title", "Give the document a title (up to 160 characters)");
        }
        StoredFile f = fileService.store(ctx.orgId(), ctx.userId(), upload);
        EmployeeDocument d = new EmployeeDocument();
        d.setId(Ids.next());
        d.setOrgId(ctx.orgId());
        d.setUserId(userId);
        d.setCategory(cat);
        d.setTitle(t);
        d.setExpiresOn(expiresOn);
        d.setFileId(f.getId());
        d.setUploadedBy(ctx.userId());
        d.setCreatedAt(clock.instant());
        documents.save(d);
        activity.record(ctx.orgId(), HrAccess.MODULE, ProfileService.RECORD, p.getId(), ctx.userId(), "employee.document.added",
                Map.of("name", name(userId), "title", t));
        return view(ctx, p);
    }

    @Transactional(readOnly = true)
    public StoredFile documentFile(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        EmployeeDocument d = documents.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        if (!sees(ctx, d.getUserId())) {
            throw BusinessException.notFound();
        }
        return file(ctx, d.getFileId());
    }

    @Transactional
    public RecordsView deleteDocument(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        EmployeeDocument d = documents.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        if (!canDelete(ctx, d)) {
            throw BusinessException.forbidden();
        }
        documents.delete(d);
        documents.flush();
        fileService.discard(ctx.orgId(), List.of(d.getFileId()));
        EmployeeProfile p = profiles.profile(ctx, d.getUserId());
        activity.record(ctx.orgId(), HrAccess.MODULE, ProfileService.RECORD, p.getId(), ctx.userId(), "employee.document.removed",
                Map.of("name", name(d.getUserId()), "title", d.getTitle()));
        return view(ctx, p);
    }

    // ── Salary history and what expires ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PayChangeView> payHistory(String slug, UUID userId) {
        OrgContext ctx = hr.requirePayroll(slug);
        List<PayChange> found = payChanges.findByOrgIdAndUserIdOrderByEffectiveOnDescCreatedAtDesc(ctx.orgId(), userId);
        Map<UUID, PersonRef> by = profiles.persons(found.stream().map(PayChange::getChangedBy).toList());
        return found.stream().map(c -> new PayChangeView(c.getId(), c.getMonthlySalary(), c.getPrevious(), c.getEffectiveOn(),
                c.getReason(), by.get(c.getChangedBy()), c.getCreatedAt())).toList();
    }

    /**
     * Within {@code days} from today (and up to 30 days past): contracts and probations ending, IDs and documents
     * expiring, for the people the caller manages in HR.
     */
    @Transactional
    public List<ExpiringItem> expiring(String slug, int days) {
        OrgContext ctx = hr.requireTeam(slug);
        LocalDate today = LeaveService.today(clock);
        LocalDate from = today.minusDays(30);
        LocalDate to = today.plusDays(Math.max(1, Math.min(days, 365)));
        Set<UUID> scope = ctx.scopeOf(RoleCatalogue.HR);
        Set<UUID> active = new java.util.HashSet<>(memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST).map(Membership::getUserId).toList());
        java.util.function.Predicate<UUID> mine = u -> active.contains(u) && (scope == null || scope.contains(u));
        java.util.function.Predicate<LocalDate> soon = d -> d != null && !d.isBefore(from) && !d.isAfter(to);
        record Hit(String kind, UUID user, LocalDate date, String label) {
        }
        List<Hit> hits = new ArrayList<>();
        // Only each person's latest contract counts (a renewal replaces the old end date).
        Map<UUID, EmployeeContract> latest = new java.util.HashMap<>();
        contracts.findByOrgId(ctx.orgId()).forEach(c -> latest.merge(c.getUserId(), c, (a, b) -> a.getStartsOn().isAfter(b.getStartsOn()) ? a : b));
        latest.values().stream().filter(c -> mine.test(c.getUserId())).forEach(c -> {
            if (soon.test(c.getEndsOn())) {
                hits.add(new Hit("CONTRACT_END", c.getUserId(), c.getEndsOn(), c.getKind()));
            }
            if (soon.test(c.getProbationEndsOn())) {
                hits.add(new Hit("PROBATION_END", c.getUserId(), c.getProbationEndsOn(), c.getKind()));
            }
        });
        profileRows.findByOrgId(ctx.orgId()).stream().filter(p -> mine.test(p.getUserId()) && soon.test(p.getIdExpiresOn()))
                .forEach(p -> hits.add(new Hit("ID_EXPIRY", p.getUserId(), p.getIdExpiresOn(), p.getIdType())));
        documents.findByOrgIdAndExpiresOnIsNotNull(ctx.orgId()).stream().filter(d -> mine.test(d.getUserId()) && soon.test(d.getExpiresOn()))
                .forEach(d -> hits.add(new Hit("DOCUMENT_EXPIRY", d.getUserId(), d.getExpiresOn(), d.getTitle())));
        Map<UUID, PersonRef> people = profiles.persons(hits.stream().map(Hit::user).toList());
        return hits.stream().sorted(Comparator.comparing(Hit::date))
                .map(h -> new ExpiringItem(h.kind(), people.get(h.user()), h.date(), ChronoUnit.DAYS.between(today, h.date()), h.label()))
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private RecordsView view(OrgContext ctx, EmployeeProfile p) {
        UUID user = p.getUserId();
        boolean self = user.equals(ctx.userId());
        boolean hrm = HrAccess.managesPerson(ctx, user);
        boolean shown = self || hrm;
        PersonalView personal = new PersonalView(p.getNickname(), p.getDuty(), p.getCategory(),
                shown ? p.getGender() : null, shown ? p.getDateOfBirth() : null, shown ? p.getNationality() : null,
                shown ? p.getIdType() : null, shown ? p.getIdNumber() : null, shown ? p.getIdExpiresOn() : null,
                shown ? p.getNssfNumber() : null, shown ? p.getHomeAddress() : null, shown ? p.getPersonalEmail() : null,
                shown ? p.getPersonalPhone() : null);
        if (!shown) {
            return new RecordsView(personal, List.of(), List.of(), false, false, false);
        }
        LocalDate today = LeaveService.today(clock);
        List<EmployeeContract> cs = contracts.findByOrgIdAndUserIdOrderByStartsOnDesc(ctx.orgId(), user);
        List<EmployeeDocument> ds = documents.findByOrgIdAndUserIdOrderByCreatedAtDesc(ctx.orgId(), user);
        Map<UUID, StoredFile> fs = new java.util.HashMap<>();
        files.findAllById(Stream.concat(cs.stream().map(EmployeeContract::getFileId), ds.stream().map(EmployeeDocument::getFileId))
                .filter(Objects::nonNull).toList()).forEach(f -> fs.put(f.getId(), f));
        Map<UUID, PersonRef> by = profiles.persons(Stream.concat(cs.stream().map(EmployeeContract::getCreatedBy),
                ds.stream().map(EmployeeDocument::getUploadedBy)).filter(Objects::nonNull).toList());
        boolean salary = self || HrAccess.runsPayroll(ctx);
        List<ContractView> contractViews = cs.stream().map(c -> new ContractView(c.getId(), c.getKind(), c.getStartsOn(), c.getEndsOn(),
                c.getProbationEndsOn(), salary ? c.getMonthlySalary() : null, c.getNote(),
                c.getFileId() == null || !fs.containsKey(c.getFileId()) ? null : fs.get(c.getFileId()).getName(),
                c.getStartsOn().isAfter(today) ? "UPCOMING" : c.getEndsOn() != null && c.getEndsOn().isBefore(today) ? "ENDED" : "ACTIVE",
                by.get(c.getCreatedBy()), c.getCreatedAt())).toList();
        List<DocumentView> documentViews = ds.stream().map(d -> {
            StoredFile f = fs.get(d.getFileId());
            return new DocumentView(d.getId(), d.getCategory(), d.getTitle(), d.getExpiresOn(), f == null ? null : f.getName(),
                    f == null ? 0 : f.getSizeBytes(), by.get(d.getUploadedBy()), d.getCreatedAt(), canDelete(ctx, d));
        }).toList();
        return new RecordsView(personal, contractViews, documentViews, true, hrm, self);
    }

    private boolean sees(OrgContext ctx, UUID userId) {
        return userId.equals(ctx.userId()) || HrAccess.managesPerson(ctx, userId);
    }

    private static boolean canDelete(OrgContext ctx, EmployeeDocument d) {
        return HrAccess.managesPerson(ctx, d.getUserId()) || (d.getUserId().equals(ctx.userId()) && ctx.userId().equals(d.getUploadedBy()));
    }

    private OrgContext manager(String slug, UUID userId) {
        OrgContext ctx = hr.require(slug);
        requireManager(ctx, userId);
        return ctx;
    }

    private static void requireManager(OrgContext ctx, UUID userId) {
        if (!HrAccess.managesPerson(ctx, userId)) {
            throw BusinessException.forbidden();
        }
    }

    private StoredFile file(OrgContext ctx, UUID id) {
        return files.findById(id).filter(f -> f.getOrgId().equals(ctx.orgId())).orElseThrow(BusinessException::notFound);
    }

    private String name(UUID userId) {
        PersonRef r = profiles.persons(List.of(userId)).get(userId);
        return r == null ? "" : r.name();
    }

    private static <T> void set(List<String> changed, String field, T before, T after, Consumer<T> apply) {
        if (!Objects.equals(before, after)) {
            apply.accept(after);
            changed.add(field);
        }
    }

    private static String clean(String s) {
        return ProfileService.clean(s);
    }
}
