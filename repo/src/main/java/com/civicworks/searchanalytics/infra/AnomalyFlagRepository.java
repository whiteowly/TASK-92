package com.civicworks.searchanalytics.infra;

import com.civicworks.searchanalytics.domain.AnomalyFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnomalyFlagRepository extends JpaRepository<AnomalyFlag, Long> {
    List<AnomalyFlag> findByAcknowledgedFalseOrderByCreatedAtDesc();
}
