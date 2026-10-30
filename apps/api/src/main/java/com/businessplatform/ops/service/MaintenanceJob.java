package com.businessplatform.ops.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates due preventive work orders every hour ({@code platform.service.maintenance-cron}, default minute 5 of
 * every hour; {@code "-"} switches it off, as the tests do — they call {@link #run()} themselves).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceJob {

    private final MaintenanceService maintenance;

    @Scheduled(cron = "${platform.service.maintenance-cron:0 5 * * * *}", zone = "Asia/Phnom_Penh")
    public void run() {
        int n = maintenance.generateAll();
        if (n > 0) {
            log.info("Generated {} preventive work order(s)", n);
        }
    }

    /** Scheduling on, unless {@code platform.scheduling.enabled=false}. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "platform.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {
    }
}
