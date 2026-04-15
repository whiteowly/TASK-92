package com.civicworks.searchanalytics;

import com.civicworks.billing.domain.Bill;
import com.civicworks.billing.domain.Bill.BillStatus;
import com.civicworks.billing.infra.BillRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.clock.MunicipalClock;
import com.civicworks.searchanalytics.application.AnalyticsAggregationService;
import com.civicworks.searchanalytics.domain.AnomalyFlag;
import com.civicworks.searchanalytics.domain.KpiSnapshot;
import com.civicworks.searchanalytics.infra.AnomalyFlagRepository;
import com.civicworks.searchanalytics.infra.KpiSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct service-level coverage for daily KPI aggregation + WoW arrears
 * anomaly detection. The HTTP layer (ReportApiIT) only covers the GET
 * read path; the actual computation is otherwise untested.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsAggregationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 15);
    private static final LocalDate WEEK_AGO = TODAY.minusDays(7);

    @Mock private KpiSnapshotRepository kpiRepo;
    @Mock private AnomalyFlagRepository anomalyRepo;
    @Mock private BillRepository billRepo;
    @Mock private AuditService auditService;
    @Mock private MunicipalClock clock;

    private AnalyticsAggregationService svc;

    @BeforeEach
    void setUp() {
        svc = new AnalyticsAggregationService(kpiRepo, anomalyRepo, billRepo, auditService, clock);
        when(clock.today()).thenReturn(TODAY);
    }

    private static Bill bill(BigDecimal balance, BillStatus status) {
        Bill b = new Bill();
        b.setBalance(balance);
        b.setStatus(status);
        return b;
    }

    private static KpiSnapshot arrearsSnapshot(LocalDate date, BigDecimal value) {
        KpiSnapshot s = new KpiSnapshot();
        s.setSnapshotDate(date);
        s.setMetricName("arrearsBalance");
        s.setMetricValue(value);
        return s;
    }

    @Test
    void runDailyAggregation_persistsKpiSnapshotForEveryMetric_withTodayDate() {
        when(billRepo.findAll()).thenReturn(List.of(
                bill(new BigDecimal("100.00"), BillStatus.OPEN),
                bill(new BigDecimal("250.00"), BillStatus.OVERDUE),
                bill(new BigDecimal("75.00"), BillStatus.OVERDUE)
        ));
        // No prior snapshots → no anomaly evaluation.
        when(kpiRepo.findAll()).thenReturn(List.of());

        Map<String, BigDecimal> kpis = svc.runDailyAggregation();

        assertThat(kpis).containsOnlyKeys(
                "totalBills", "openArBalance", "arrearsBalance", "overdueBillCount");
        assertThat(kpis.get("totalBills")).isEqualByComparingTo("3");
        assertThat(kpis.get("openArBalance")).isEqualByComparingTo("425.00");
        // arrears = sum(balance where status == OVERDUE)
        assertThat(kpis.get("arrearsBalance")).isEqualByComparingTo("325.00");
        assertThat(kpis.get("overdueBillCount")).isEqualByComparingTo("2");

        ArgumentCaptor<KpiSnapshot> snapCap = ArgumentCaptor.forClass(KpiSnapshot.class);
        verify(kpiRepo, times(4)).save(snapCap.capture());
        assertThat(snapCap.getAllValues())
                .extracting(KpiSnapshot::getSnapshotDate)
                .containsOnly(TODAY);
        assertThat(snapCap.getAllValues())
                .extracting(KpiSnapshot::getMetricName)
                .containsExactlyInAnyOrder(
                        "totalBills", "openArBalance", "arrearsBalance", "overdueBillCount");

        verify(auditService).log(null, "SYSTEM", "KPI_AGGREGATION", "kpi_snapshot",
                TODAY.toString(), "metrics=" + kpis.keySet());
    }

    @Test
    void runDailyAggregation_emitsArrearsGrowthHighFlag_whenWoWGrowthExceeds15Pct() {
        // Today's arrears = 200; week-ago arrears = 100 → growth = 100% (> 15%).
        when(billRepo.findAll()).thenReturn(List.of(
                bill(new BigDecimal("200.00"), BillStatus.OVERDUE)
        ));
        when(kpiRepo.findAll()).thenReturn(List.of(
                arrearsSnapshot(WEEK_AGO, new BigDecimal("100.00"))
        ));

        svc.runDailyAggregation();

        ArgumentCaptor<AnomalyFlag> cap = ArgumentCaptor.forClass(AnomalyFlag.class);
        verify(anomalyRepo).save(cap.capture());
        AnomalyFlag flag = cap.getValue();
        assertThat(flag.getFlagType()).isEqualTo("arrearsGrowthHigh");
        assertThat(flag.getThresholdValue()).isEqualByComparingTo("0.15");
        // Growth ratio = (200 - 100) / 100 = 1.0000 (4-scale HALF_UP).
        assertThat(flag.getMetricValue()).isEqualByComparingTo("1.0000");
        assertThat(flag.getDescription()).containsIgnoringCase("arrears");
        assertThat(flag.getDescription()).contains("WoW");
    }

    @Test
    void runDailyAggregation_doesNotFlag_whenWoWGrowthIsWithinThreshold() {
        // Today's arrears = 110; week-ago = 100 → growth = 10% (≤ 15%, no flag).
        when(billRepo.findAll()).thenReturn(List.of(
                bill(new BigDecimal("110.00"), BillStatus.OVERDUE)
        ));
        when(kpiRepo.findAll()).thenReturn(List.of(
                arrearsSnapshot(WEEK_AGO, new BigDecimal("100.00"))
        ));

        svc.runDailyAggregation();

        verify(anomalyRepo, never()).save(any(AnomalyFlag.class));
    }

    @Test
    void runDailyAggregation_doesNotFlag_whenNoPriorWeekAgoSnapshotExists() {
        // Even with a huge arrears balance today, the absence of a prior
        // snapshot means we have no baseline to compute a growth ratio against.
        when(billRepo.findAll()).thenReturn(List.of(
                bill(new BigDecimal("9999.00"), BillStatus.OVERDUE)
        ));
        when(kpiRepo.findAll()).thenReturn(List.of()); // no historical data

        svc.runDailyAggregation();

        verify(anomalyRepo, never()).save(any(AnomalyFlag.class));
    }

    @Test
    void runDailyAggregation_doesNotFlag_whenWeekAgoArrearsWasZero() {
        // Division-by-zero guard: a zero baseline must not raise an anomaly,
        // even if today's arrears is non-zero (this is a "from-nothing" case
        // that should be reflected in the snapshot, not as an anomaly).
        when(billRepo.findAll()).thenReturn(List.of(
                bill(new BigDecimal("500.00"), BillStatus.OVERDUE)
        ));
        when(kpiRepo.findAll()).thenReturn(List.of(
                arrearsSnapshot(WEEK_AGO, BigDecimal.ZERO)
        ));

        svc.runDailyAggregation();

        verify(anomalyRepo, never()).save(any(AnomalyFlag.class));
    }
}
