package com.civicworks.dispatch.infra;

import com.civicworks.dispatch.domain.DispatchAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface DispatchAttemptRepository extends JpaRepository<DispatchAttempt, Long> {

    @Query("SELECT COUNT(a) > 0 FROM DispatchAttempt a WHERE a.orderId = :orderId AND a.driverId = :driverId " +
            "AND a.forced = true AND a.attemptedAt > :since")
    boolean existsForcedAttemptSince(@Param("orderId") Long orderId,
                                     @Param("driverId") Long driverId,
                                     @Param("since") Instant since);
}
