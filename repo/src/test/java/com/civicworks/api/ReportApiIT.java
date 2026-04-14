package com.civicworks.api;

import com.civicworks.platform.security.Role;
import com.civicworks.searchanalytics.domain.AnomalyFlag;
import com.civicworks.searchanalytics.domain.KpiSnapshot;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportApiIT extends BaseApiIT {

    private String adminToken;
    private String auditorToken;
    private String editorToken;

    @BeforeAll
    void setup() {
        adminToken = login("admin", "admin123");
        auditorToken = createUserAndLogin("rpt_auditor", "pass123", Role.AUDITOR);
        editorToken = createUserAndLogin("rpt_editor", "pass123", Role.CONTENT_EDITOR);

        // Seed KPI and anomaly data
        KpiSnapshot kpi = new KpiSnapshot();
        kpi.setSnapshotDate(LocalDate.now());
        kpi.setMetricName("total_revenue");
        kpi.setMetricValue(new BigDecimal("12345.6789"));
        kpiSnapshotRepository.saveAndFlush(kpi);

        AnomalyFlag flag = new AnomalyFlag();
        flag.setFlagType("REVENUE_SPIKE");
        flag.setDescription("Revenue exceeded threshold");
        flag.setMetricValue(new BigDecimal("50000"));
        flag.setThresholdValue(new BigDecimal("40000"));
        flag.setAcknowledged(false);
        anomalyFlagRepository.saveAndFlush(flag);
    }

    // ── GET /api/v1/reports/financial ──

    @Test
    void financialReport_asAdmin_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/reports/financial", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("totalBills");
        assertThat(resp.getBody()).containsKey("totalPayments");
    }

    @Test
    void financialReport_asAuditor_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/reports/financial", auditorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void financialReport_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/reports/financial", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void financialReport_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/reports/financial");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /api/v1/reports/activity ──

    @Test
    void activityReport_asAdmin_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/reports/activity", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("totalBills");
    }

    @Test
    void activityReport_asAuditor_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/reports/activity", auditorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void activityReport_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/reports/activity", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/reports/kpis ──

    @Test
    void kpiReport_asAdmin_returns200() {
        ResponseEntity<Object[]> resp = rest.exchange("/api/v1/reports/kpis",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(adminToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().length).isGreaterThanOrEqualTo(1);
    }

    @Test
    void kpiReport_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/reports/kpis", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void kpiReport_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/reports/kpis");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /api/v1/reports/anomalies ──

    @Test
    void anomaliesReport_asAdmin_returns200() {
        ResponseEntity<Object[]> resp = rest.exchange("/api/v1/reports/anomalies",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(adminToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().length).isGreaterThanOrEqualTo(1);
    }

    @Test
    void anomaliesReport_asAuditor_returns200() {
        ResponseEntity<Object[]> resp = rest.exchange("/api/v1/reports/anomalies",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(auditorToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anomaliesReport_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/reports/anomalies", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
