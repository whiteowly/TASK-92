package com.civicworks.platform.config;

import com.civicworks.platform.config.jobs.ScheduledJobBeans;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;
import java.util.TimeZone;

/**
 * Registers Quartz jobs and cron triggers using the configured municipal
 * timezone. Persisted via the JDBC job store (LocalDataSourceJobStore +
 * PostgreSQLDelegate, tablePrefix QRTZ_, useProperties=true so JobDataMaps
 * are stored as String properties — avoiding the postgres bytea/blob
 * read mismatch).
 *
 * <p>Disabled in the "test" profile so unit/MockMvc tests do not require
 * a database to start.
 */
@Configuration
@Profile("!test")
public class QuartzJobConfig {

    private final String timezone;

    public QuartzJobConfig(@Value("${civicworks.timezone:America/New_York}") String timezone) {
        this.timezone = timezone;
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource,
                                                     org.springframework.context.ApplicationContext ctx) {
        AutowiringSpringBeanJobFactory autowiringFactory = new AutowiringSpringBeanJobFactory();
        autowiringFactory.setApplicationContext(ctx);

        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setSchedulerName("CivicWorksScheduler");
        factory.setDataSource(dataSource);
        factory.setJobFactory(autowiringFactory);
        factory.setOverwriteExistingJobs(true);
        factory.setWaitForJobsToCompleteOnShutdown(true);
        // Programmatic Quartz properties — set explicitly here so the
        // PostgreSQL delegate and useProperties=true take effect (Spring
        // Boot's relaxed YAML binding mangles camelCase Quartz property
        // names, falling back to StdJDBCDelegate which mis-reads bytea).
        factory.setQuartzProperties(quartzProperties());
        factory.setJobDetails(
                jobDetail(ScheduledJobBeans.ContentPublicationJob.class,    "contentPublicationJob"),
                jobDetail(ScheduledJobBeans.BillingCycleRunJob.class,       "billingCycleRunJob"),
                jobDetail(ScheduledJobBeans.LateFeeBillingJob.class,        "lateFeeBillingJob"),
                jobDetail(ScheduledJobBeans.ReminderDeliveryJob.class,      "reminderDeliveryJob"),
                jobDetail(ScheduledJobBeans.SearchHistoryRetentionJob.class,"searchHistoryRetentionJob"),
                jobDetail(ScheduledJobBeans.DailyBackupJob.class,           "dailyBackupJob"),
                jobDetail(ScheduledJobBeans.KpiAggregationJob.class,        "kpiAggregationJob")
        );
        factory.setTriggers(
                cron("contentPublicationJob",    "0 */5 * * * ?"),  // every 5 minutes
                cron("billingCycleRunJob",       "0 5 0 * * ?"),    // 12:05 AM local — automatic cycle run
                cron("lateFeeBillingJob",        "0 5 0 * * ?"),    // 12:05 AM local — late fees
                cron("reminderDeliveryJob",      "0 */10 * * * ?"), // every 10 minutes
                cron("searchHistoryRetentionJob","0 0 2 * * ?"),    // 2:00 AM local
                cron("dailyBackupJob",           "0 0 3 * * ?"),    // 3:00 AM local
                cron("kpiAggregationJob",        "0 30 0 * * ?")    // 12:30 AM local (after billing)
        );
        return factory;
    }

    private Properties quartzProperties() {
        Properties p = new Properties();
        p.setProperty("org.quartz.scheduler.instanceName", "CivicWorksScheduler");
        p.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        p.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        p.setProperty("org.quartz.threadPool.threadCount", "5");
        p.setProperty("org.quartz.threadPool.threadPriority", "5");
        p.setProperty("org.quartz.jobStore.class",
                "org.springframework.scheduling.quartz.LocalDataSourceJobStore");
        p.setProperty("org.quartz.jobStore.driverDelegateClass",
                "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
        p.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        p.setProperty("org.quartz.jobStore.isClustered", "false");
        p.setProperty("org.quartz.jobStore.misfireThreshold", "60000");
        // Crucial: store JobDataMap as String properties in QRTZ_SIMPROP_TRIGGERS
        // instead of as a serialized blob — sidesteps the postgres bytea/Blob OID
        // read mismatch.
        p.setProperty("org.quartz.jobStore.useProperties", "true");
        return p;
    }

    private JobDetail jobDetail(Class<? extends Job> jobClass, String name) {
        return JobBuilder.newJob(jobClass)
                .withIdentity(name, "civicworks")
                .storeDurably(true)
                .requestRecovery(true)
                .build();
    }

    private Trigger cron(String jobName, String cron) {
        return TriggerBuilder.newTrigger()
                .forJob(jobName, "civicworks")
                .withIdentity(jobName + "-trigger", "civicworks")
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .inTimeZone(TimeZone.getTimeZone(timezone)))
                .build();
    }
}
