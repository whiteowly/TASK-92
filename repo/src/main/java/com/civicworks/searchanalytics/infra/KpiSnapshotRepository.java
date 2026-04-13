package com.civicworks.searchanalytics.infra;

import com.civicworks.searchanalytics.domain.KpiSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KpiSnapshotRepository extends JpaRepository<KpiSnapshot, Long> {
    List<KpiSnapshot> findAllByOrderBySnapshotDateDesc();
}
