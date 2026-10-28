package com.businessplatform.business.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.business.domain.Expense;
import com.businessplatform.business.payload.BusinessPayloads.PersonRef;
import com.businessplatform.business.repository.ExpenseCategoryRepository;

import lombok.RequiredArgsConstructor;

/** An expense's title for the approval engine, the audit log and notifications (repositories only, so the core's hooks can use it). */
@Component
@RequiredArgsConstructor
public class ExpenseTitles {

    private final ExpenseCategoryRepository categories;
    private final BizPeople people;

    /** "Expense · Transport and fuel · $12.50 · Dara Sok". */
    @Transactional(readOnly = true)
    public String title(Expense e) {
        PersonRef who = people.one(e.getUserId());
        String category = categories.findById(e.getCategoryId()).map(c -> c.getNameEn()).orElse("");
        return "Expense · " + category + " · $" + e.getAmount().toPlainString() + (who == null ? "" : " · " + who.name());
    }
}
