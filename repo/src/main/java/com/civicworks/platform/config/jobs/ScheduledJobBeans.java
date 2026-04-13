package com.civicworks.platform.config.jobs;

import com.civicworks.billing.application.AutomaticBillingCycleService;
import com.civicworks.billing.application.BillingService;
import com.civicworks.content.application.ContentService;
import com.civicworks.notifications.application.NotificationService;
import com.civicworks.ops.application.BackupService;
import com.civicworks.searchanalytics.application.AnalyticsAggregationService;
import com.civicworks.searchanalytics.application.SearchService;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Quartz JobBean wrappers around the existing service methods. The JobDetail
 * registrations live in {@link com.civicworks.platform.config.QuartzJobConfig}.
 *
 * <p>Each job implementation keeps the existing service logic but is now
 * dispatched by Quartz with cron triggers in the configured municipal
 * timezone, with persistence in QRTZ_* tables for restart safety.
 */
public final class ScheduledJobBeans {

    private ScheduledJobBeans() {}

    @DisallowConcurrentExecution
    public static class ContentPublicationJob implements Job {
        private static final Logger log = LoggerFactory.getLogger(ContentPublicationJob.class);
        @Autowired private ContentService contentService;
        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            try {
                contentService.processScheduledPublications();
            } catch (Exception e) {
                log.error("Content publication job failed", e);
                throw new JobExecutionException(e, false);
            }
        }
    }

    @DisallowConcurrentExecution
    public static class LateFeeBillingJob implements Job {
        private static final Logger log = LoggerFactory.getLogger(LateFeeBillingJob.class);
        @Autowired private BillingService billingService;
        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            log.info("Running late fee + billing eligibility job at scheduled local-tz fire");
            try {
                billingService.processLateFees();
            } catch (Exception e) {
                log.error("Late-fee billing job failed", e);
                throw new JobExecutionException(e, false);
            }
        }
    }

    @DisallowConcurrentExecution
    public static class ReminderDeliveryJob implements Job {
        private static final Logger log = LoggerFactory.getLogger(ReminderDeliveryJob.class);
        @Autowired private NotificationService notificationService;
        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            try {
                notificationService.processReminders();
            } catch (Exception e) {
                log.error("Reminder delivery job failed", e);
                throw new JobExecutionException(e, false);
            }
        }
    }

    @DisallowConcurrentExecution
    public static class SearchHistoryRetentionJob implements Job {
        private static final Logger log = LoggerFactory.getLogger(SearchHistoryRetentionJob.class);
        @Autowired private SearchService searchService;
        @Autowired private com.civicworks.platform.config.SystemConfigService systemConfig;
        // Fallback for initial boot before any admin edit lands in system_config.
        @Value("${civicworks.search.history-retention-days:90}")
        private int retentionDaysDefault;
        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            try {
                // Prefer runtime system_config value so SYSTEM_ADMIN edits via
                // PUT /api/v1/admin/system-config take effect on the next
                // scheduled run without a restart.
                int retentionDays = systemConfig.getInt(
                        com.civicworks.platform.config.SystemConfigService.KEY_SEARCH_HISTORY_RETENTION_DAYS,
                        retentionDaysDefault);
                int n = searchService.purgeOldHistory(retentionDays);
                log.info("Search history retention job purged {} entries (retentionDays={})", n, retentionDays);
            } catch (Exception e) {
                log.error("Search history retention job failed", e);
                throw new JobExecutionException(e, false);
            }
        }
    }

    @DisallowConcurrentExecution
    public static class DailyBackupJob implements Job {
        private static final Logger log = LoggerFactory.getLogger(DailyBackupJob.class);
        @Autowired private BackupService backupService;
        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            try {
                backupService.performBackup();
            } catch (Exception e) {
                log.error("Daily backup job failed", e);
                throw new JobExecutionException(e, false);
            }
        }
    }

    @DisallowConcurrentExecution
    public static class BillingCycleRunJob implements Job {
        private static final Logger log = LoggerFactory.getLogger(BillingCycleRunJob.class);
        @Autowired private AutomaticBillingCycleService automaticBillingCycleService;
        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            try {
                automaticBillingCycleService.runIfCycleDay();
            } catch (Exception e) {
                log.error("Automatic billing cycle run job failed", e);
                throw new JobExecutionException(e, false);
            }
        }
    }

    @DisallowConcurrentExecution
    public static class KpiAggregationJob implements Job {
        private static final Logger log = LoggerFactory.getLogger(KpiAggregationJob.class);
        @Autowired private AnalyticsAggregationService analyticsService;
        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            try {
                analyticsService.runDailyAggregation();
            } catch (Exception e) {
                log.error("KPI/anomaly aggregation job failed", e);
                throw new JobExecutionException(e, false);
            }
        }
    }
}
