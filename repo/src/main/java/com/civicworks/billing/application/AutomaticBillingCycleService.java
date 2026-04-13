package com.civicworks.billing.application;

import com.civicworks.billing.domain.BillingRun;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.clock.MunicipalClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;

/**
 * Triggers the automatic billing cycle run. Fires once daily at 12:05 AM
 * local time (via Quartz). On the configured monthly/quarterly cycle dates
 * it kicks off a billing run for the corresponding cycle type.
 *
 * <p>Defaults: monthly run on day 1; quarterly run on day 1 of Jan/Apr/Jul/Oct.
 */
@Service
public class AutomaticBillingCycleService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticBillingCycleService.class);

    private final BillingService billingService;
    private final AuditService auditService;
    private final MunicipalClock clock;
    private final int monthlyCycleDay;
    private final int quarterlyCycleDay;

    public AutomaticBillingCycleService(BillingService billingService,
                                        AuditService auditService,
                                        MunicipalClock clock,
                                        @Value("${civicworks.billing.monthly-cycle-day:1}") int monthlyCycleDay,
                                        @Value("${civicworks.billing.quarterly-cycle-day:1}") int quarterlyCycleDay) {
        this.billingService = billingService;
        this.auditService = auditService;
        this.clock = clock;
        this.monthlyCycleDay = monthlyCycleDay;
        this.quarterlyCycleDay = quarterlyCycleDay;
    }

    public void runIfCycleDay() {
        LocalDate today = clock.today();

        if (today.getDayOfMonth() == monthlyCycleDay) {
            runCycle(today, "MONTHLY");
        }
        if (today.getDayOfMonth() == quarterlyCycleDay && isQuarterStart(today.getMonth())) {
            runCycle(today, "QUARTERLY");
        }
    }

    private void runCycle(LocalDate cycleDate, String cycleType) {
        try {
            BillingRun run = billingService.executeBillingRun(cycleDate, cycleType, null);
            log.info("Automatic {} billing cycle run id={} bills={} total={}",
                    cycleType, run.getId(), run.getBillsGenerated(), run.getTotalAmount());
            auditService.log(null, "SYSTEM", "AUTOMATIC_BILLING_RUN",
                    "billing_run", run.getId().toString(),
                    "cycleType=" + cycleType + ",cycleDate=" + cycleDate);
        } catch (Exception e) {
            log.error("Automatic {} billing cycle run failed for {}", cycleType, cycleDate, e);
        }
    }

    private boolean isQuarterStart(Month m) {
        return m == Month.JANUARY || m == Month.APRIL || m == Month.JULY || m == Month.OCTOBER;
    }
}
