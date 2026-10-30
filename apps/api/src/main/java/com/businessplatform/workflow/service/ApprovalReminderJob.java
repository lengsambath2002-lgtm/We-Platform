package com.businessplatform.workflow.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Hourly: approval reminders and escalation ({@code platform.approvals.reminder-cron}). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalReminderJob {

    private final ApprovalService approvals;

    @Scheduled(cron = "${platform.approvals.reminder-cron:0 20 * * * *}", zone = "Asia/Phnom_Penh")
    public void run() {
        int[] done = approvals.remindAndEscalate();
        if (done[0] + done[1] > 0) {
            log.info("Approvals: {} reminded, {} escalated", done[0], done[1]);
        }
    }
}
