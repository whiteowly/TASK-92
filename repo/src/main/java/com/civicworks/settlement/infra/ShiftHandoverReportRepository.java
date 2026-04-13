package com.civicworks.settlement.infra;

import com.civicworks.settlement.domain.ShiftHandoverReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftHandoverReportRepository extends JpaRepository<ShiftHandoverReport, Long> {
}
