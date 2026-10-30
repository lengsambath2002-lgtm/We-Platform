package com.businessplatform.org.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Published (inside the transaction) after a bulk import, for the HR details the core doesn't keep: each person's
 * manager, 2nd approver, employment type and start date (null = leave as it is).
 */
public record PeopleImported(UUID orgId, List<Person> people) {

    public record Person(UUID userId, UUID managerId, UUID secondManagerId, String employmentType, LocalDate startDate) {
    }
}
