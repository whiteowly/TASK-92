package com.civicworks.searchanalytics.api;

import com.civicworks.billing.infra.BillRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.security.SecurityUtils;
import com.civicworks.searchanalytics.domain.AnomalyFlag;
import com.civicworks.searchanalytics.domain.KpiSnapshot;
import com.civicworks.searchanalytics.infra.AnomalyFlagRepository;
import com.civicworks.searchanalytics.infra.KpiSnapshotRepository;
import com.civicworks.settlement.infra.PaymentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final KpiSnapshotRepository kpiRepo;
    private final AnomalyFlagRepository anomalyRepo;
    private final BillRepository billRepo;
    private final PaymentRepository paymentRepo;
    private final AuditService auditService;

    public ReportController(KpiSnapshotRepository kpiRepo, AnomalyFlagRepository anomalyRepo,
                            BillRepository billRepo, PaymentRepository paymentRepo,
                            AuditService auditService) {
        this.kpiRepo = kpiRepo;
        this.anomalyRepo = anomalyRepo;
        this.billRepo = billRepo;
        this.paymentRepo = paymentRepo;
        this.auditService = auditService;
    }

    @GetMapping("/financial")
    @PreAuthorize("hasAnyRole('AUDITOR', 'SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> financialReport() {
        auditService.log(SecurityUtils.currentUserId(), null, "REPORT_ACCESS",
                "report", "financial", null);
        Map<String, Object> report = new HashMap<>();
        report.put("totalBills", billRepo.count());
        report.put("totalPayments", paymentRepo.count());
        return ResponseEntity.ok(report);
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('AUDITOR', 'SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> activityReport() {
        auditService.log(SecurityUtils.currentUserId(), null, "REPORT_ACCESS",
                "report", "activity", null);
        Map<String, Object> report = new HashMap<>();
        report.put("totalBills", billRepo.count());
        return ResponseEntity.ok(report);
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('AUDITOR', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<KpiSnapshot>> kpis() {
        auditService.log(SecurityUtils.currentUserId(), null, "REPORT_ACCESS",
                "report", "kpis", null);
        return ResponseEntity.ok(kpiRepo.findAllByOrderBySnapshotDateDesc());
    }

    @GetMapping("/anomalies")
    @PreAuthorize("hasAnyRole('AUDITOR', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<AnomalyFlag>> anomalies() {
        auditService.log(SecurityUtils.currentUserId(), null, "REPORT_ACCESS",
                "report", "anomalies", null);
        return ResponseEntity.ok(anomalyRepo.findByAcknowledgedFalseOrderByCreatedAtDesc());
    }
}
