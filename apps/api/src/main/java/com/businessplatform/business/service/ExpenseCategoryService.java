package com.businessplatform.business.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.business.domain.ExpenseCategory;
import com.businessplatform.business.payload.BusinessPayloads.CategoryRequest;
import com.businessplatform.business.payload.BusinessPayloads.CategoryView;
import com.businessplatform.business.repository.ExpenseCategoryRepository;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Expense categories, kept by Admins. The first use of the module adds editable defaults a small Cambodian
 * company needs; categories are switched off rather than deleted, so old expenses keep theirs.
 */
@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    public static final String RECORD = "expense_category";

    private final BusinessAccess access;
    private final ExpenseCategoryRepository categories;
    private final ActivityService activity;
    private final Clock clock;

    /** Active categories; Admins also see switched-off ones. */
    @Transactional
    public List<CategoryView> list(String slug) {
        OrgContext ctx = access.require(slug);
        return all(ctx).stream().filter(c -> c.isActive() || BusinessAccess.manages(ctx)).map(ExpenseCategoryService::view).toList();
    }

    @Transactional
    public CategoryView save(String slug, UUID id, CategoryRequest in) {
        OrgContext ctx = access.requireManager(slug);
        ExpenseCategory c;
        if (id == null) {
            c = new ExpenseCategory();
            c.setId(Ids.next());
            c.setOrgId(ctx.orgId());
            c.setCreatedAt(clock.instant());
            c.setSortOrder((short) (all(ctx).size() * 10 + 10));
        } else {
            c = categories.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        }
        String name = in.nameEn().strip();
        if (categories.existsByOrgIdAndNameEnIgnoreCaseAndIdNot(ctx.orgId(), name, c.getId())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "nameEn", "Another category has this name");
        }
        c.setNameEn(name);
        c.setNameKm(in.nameKm().strip());
        c.setActive(in.active() == null || in.active());
        categories.save(c);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, c.getId(), ctx.userId(),
                id == null ? "business.category.created" : "business.category.updated", Map.of("name", c.getNameEn()));
        return view(c);
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    /** Every category of the organisation (adding the defaults on first use). */
    @Transactional
    public List<ExpenseCategory> all(OrgContext ctx) {
        if (categories.countByOrgId(ctx.orgId()) == 0) {
            seed(ctx, "Office supplies", "សម្ភារៈការិយាល័យ", 10);
            seed(ctx, "Rent", "ថ្លៃជួល", 20);
            seed(ctx, "Utilities", "ទឹក ភ្លើង និងអ៊ីនធឺណិត", 30);
            seed(ctx, "Transport and fuel", "ការធ្វើដំណើរ និងប្រេងឥន្ធនៈ", 40);
            seed(ctx, "Meals and entertainment", "អាហារ និងការទទួលភ្ញៀវ", 50);
            seed(ctx, "Marketing", "ទីផ្សារ", 60);
            seed(ctx, "Equipment", "ឧបករណ៍", 70);
            seed(ctx, "Other", "ផ្សេងៗ", 80);
        }
        return categories.findByOrgIdOrderBySortOrderAscNameEnAsc(ctx.orgId());
    }

    @Transactional(readOnly = true)
    public ExpenseCategory find(OrgContext ctx, UUID id) {
        return categories.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private void seed(OrgContext ctx, String en, String km, int order) {
        categories.seed(ctx.orgId(), en, km, (short) order);
    }

    public static CategoryView view(ExpenseCategory c) {
        return new CategoryView(c.getId(), c.getNameEn(), c.getNameKm(), c.isActive());
    }
}
