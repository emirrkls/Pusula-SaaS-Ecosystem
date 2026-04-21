package com.pusula.backend.job;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.DailyClosing;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.DailyClosingRepository;
import com.pusula.backend.service.FinanceService;

@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final FinanceService financeService;
    private final CompanyRepository companyRepository;
    private final DailyClosingRepository dailyClosingRepository;

    public ScheduledJobs(FinanceService financeService,
            CompanyRepository companyRepository,
            DailyClosingRepository dailyClosingRepository) {
        this.financeService = financeService;
        this.companyRepository = companyRepository;
        this.dailyClosingRepository = dailyClosingRepository;
    }

    /**
     * Automatic daily closing at 23:59 every day
     * Closes the current day for all companies if not already closed
     */
    @Scheduled(cron = "0 59 23 * * *") // Every day at 23:59
    public void automaticDailyClosing() {
        log.info("Running automatic daily closing...");

        LocalDate today = LocalDate.now();
        List<Company> companies = companyRepository.findAll();

        for (Company company : companies) {
            try {
                // Check if already closed
                boolean alreadyClosed = dailyClosingRepository.existsByCompanyIdAndDateAndStatus(
                        company.getId(), today, DailyClosing.ClosingStatus.CLOSED);

                if (!alreadyClosed) {
                    financeService.closeDay(company.getId(), today, null); // null for system user
                    log.info("Day closed for company {} on {}", company.getId(), today);
                }
            } catch (Exception e) {
                log.error("Failed to close day for company {}: {}", company.getId(), e.getMessage());
            }
        }

        log.info("Automatic daily closing completed.");
    }

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
