package com.civicworks.dispatch.infra;

import com.civicworks.dispatch.domain.ZoneCapacityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ZoneCapacityRuleRepository extends JpaRepository<ZoneCapacityRule, Long> {
    Optional<ZoneCapacityRule> findByZoneId(Long zoneId);
}
