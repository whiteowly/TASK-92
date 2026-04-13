package com.civicworks.searchanalytics.application;

import com.civicworks.billing.domain.Bill;
import com.civicworks.billing.infra.BillRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.clock.MunicipalClock;
import com.civicworks.searchanalytics.domain.AnomalyFlag;
import com.civicworks.searchanalytics.domain.KpiSnapshot;
import com.civicworks.searchanalytics.infra.AnomalyFlagRepository;
import com.civicworks.searchanalytics.infra.KpiSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily KPI snapshot + anomaly detection. Triggered by a Quartz job after
 * the daily billing/late-fee jobs have completed (early-morning local time).
 *
 * <p>Anomaly rule from the planning docs: arrears growth &gt; 15% week-over-week
 * raises an {@code arrearsGrowthHigh} flag.
 */
@Service
public class AnalyticsAggregationService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAggregationService.class);
    private static final BigDecimal ARREARS_GROWTH_THRESHOLD = new BigDecimal("0.15");

    private final KpiSnapshotRepository kpiRepo;
    private final AnomalyFlagRepository anomalyRepo;
    private final BillRepository billRepo;
    private final AuditService auditService;
    private final MunicipalClock clock;

    public AnalyticsAggregationService(KpiSnapshotRepository kpiRepo,
                                       AnomalyFlagRepository anomalyRepo,
                                       BillRepository billRepo,
                                       AuditService auditService,
                                       MunicipalClock clock) {
        this.kpiRepo = kpiRepo;
        this.anomalyRepo = anomalyRepo;
        this.billRepo = billRepo;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public Map<String, BigDecimal> runDailyAggregation() {
        LocalDate today = clock.today();
        Map<String, BigDecimal> kpis = computeKpis();

        kpis.forEach((name, value) -> {
            KpiSnapshot snap = new KpiSnapshot();
            snap.setSnapshotDate(today);
            snap.setMetricName(name);
            snap.setMetricValue(value);
            kpiRepo.save(snap);
        });

        detectAnomalies(today, kpis);

        auditService.log(null, "SYSTEM", "KPI_AGGREGATION", "kpi_snapshot",
                today.toString(), "metrics=" + kpis.keySet());
        log.info("KPI aggregation: persisted {} metrics for {}", kpis.size(), today);
        return kpis;
    }

    private Map<String, BigDecimal> computeKpis() {
        Map<String, BigDecimal> out = new HashMap<>();

        List<Bill> all = billRepo.findAll();
        long total = all.size();
        BigDecimal totalAr = all.stream()
                .map(Bill::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal arrears = all.stream()
                .filter(b -> b.getStatus() == Bill.BillStatus.OVERDUE)
                .map(Bill::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        long overdueCount = all.stream()
                .filter(b -> b.getStatus() == Bill.BillStatus.OVERDUE).count();

        out.put("totalBills", BigDecimal.valueOf(total));
        out.put("openArBalance", totalAr.setScale(2, RoundingMode.HALF_UP));
        out.put("arrearsBalance", arrears.setScale(2, RoundingMode.HALF_UP));
        out.put("overdueBillCount", BigDecimal.valueOf(overdueCount));
        return out;
    }

    private void detectAnomalies(LocalDate today, Map<String, BigDecimal> todayKpis) {
        BigDecimal todayArrears = todayKpis.getOrDefault("arrearsBalance", BigDecimal.ZERO);
        LocalDate weekAgo = today.minusDays(7);

        BigDecimal weekAgoArrears = kpiRepo.findAll().stream()
                .filter(s -> "arrearsBalance".equals(s.getMetricName())
                        && weekAgo.equals(s.getSnapshotDate()))
                .map(KpiSnapshot::getMetricValue)
                .findFirst().orElse(null);

        if (weekAgoArrears == null || weekAgoArrears.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal growth = todayArrears.subtract(weekAgoArrears)
                .divide(weekAgoArrears, 4, RoundingMode.HALF_UP);

        if (growth.compareTo(ARREARS_GROWTH_THRESHOLD) > 0) {
            AnomalyFlag flag = new AnomalyFlag();
            flag.setFlagType("arrearsGrowthHigh");
            flag.setDescription("Arrears WoW growth " + growth.movePointRight(2)
                    + "% > " + ARREARS_GROWTH_THRESHOLD.movePointRight(2) + "% threshold");
            flag.setMetricValue(growth);
            flag.setThresholdValue(ARREARS_GROWTH_THRESHOLD);
            anomalyRepo.save(flag);
            log.warn("Anomaly flagged: arrearsGrowthHigh growth={}", growth);
        }
    }
}
