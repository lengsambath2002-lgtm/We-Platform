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

    @Scheduled(cron = "${platform.billing.cron:0 10 * * * *}", zone = "Asia/Phnom_Penh")
    public void run() {
        int n = subscriptions.expire();
        if (n > 0) {
            log.info("Billing: {} subscription(s) moved to Free", n);
        }
    }
}
