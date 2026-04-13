package com.civicworks.settlement.infra;

import com.civicworks.settlement.domain.DiscrepancyCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DiscrepancyCaseRepository extends JpaRepository<DiscrepancyCase, Long> {
    List<DiscrepancyCase> findByStatus(String status);
}
