package com.pusula.backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledJobs {

    // private final ServiceTicketRepository ticketRepository;

    @Scheduled(cron = "0 0 9 * * *") // Every day at 9 AM
    public void checkAnnualMaintenance() {
        log.info("Checking for annual maintenance reminders...");
        // Logic: Find tickets completed 1 year ago, create new reminder ticket or
        // notify.
    }

    @Scheduled(cron = "0 0 10 * * *") // Every day at 10 AM
    public void checkWarrantyExpirations() {
        log.info("Checking for warranty expirations...");
        // Logic: Find devices/installations with warranty expiring soon.
    }
}
