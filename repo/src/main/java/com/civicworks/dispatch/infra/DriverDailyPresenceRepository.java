package com.civicworks.dispatch.infra;

import com.civicworks.dispatch.domain.DriverDailyPresence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface DriverDailyPresenceRepository extends JpaRepository<DriverDailyPresence, Long> {
    Optional<DriverDailyPresence> findByDriverIdAndPresenceDate(Long driverId, LocalDate date);
}
