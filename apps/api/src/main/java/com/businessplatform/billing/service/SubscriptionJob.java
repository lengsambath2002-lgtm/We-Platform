package com.businessplatform.billing.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Hourly: ended trials and cancelled paid periods move to Free ({@code platform.billing.cron}). */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionJob {

    private final SubscriptionService subscriptions;
    private final PlatformInvoiceService invoices;

    @Scheduled(cron = "${platform.billing.cron:0 10 * * * *}", zone = "Asia/Phnom_Penh")
    public void run() {
        int n = subscriptions.expire();
        int[] r = invoices.run();
        if (n + r[0] + r[1] + r[2] > 0) {
            log.info("Billing: {} trial/cancelled to Free, {} renewed, {} late, {} unpaid to Free", n, r[0], r[1], r[2]);
        }
    }
}
