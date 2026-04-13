package com.civicworks.dispatch.infra;

import com.civicworks.dispatch.domain.DispatchOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DispatchOrderRepository extends JpaRepository<DispatchOrder, Long> {

    @Query("SELECT COUNT(o) FROM DispatchOrder o WHERE o.zoneId = :zoneId AND o.status IN ('ASSIGNED', 'ACCEPTED')")
    int countActiveInZone(@Param("zoneId") Long zoneId);

    @Query("SELECT o FROM DispatchOrder o WHERE o.assignedDriverId = :driverId AND o.status IN ('ASSIGNED', 'ACCEPTED')")
    Page<DispatchOrder> findActiveByDriver(@Param("driverId") Long driverId, Pageable pageable);

    Page<DispatchOrder> findAll(Pageable pageable);
}
