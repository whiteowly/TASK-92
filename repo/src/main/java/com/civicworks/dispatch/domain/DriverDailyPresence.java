package com.civicworks.dispatch.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "driver_daily_presence", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"driver_id", "presence_date"})
})
public class DriverDailyPresence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "driver_id", nullable = false)
    private Long driverId;
    @Column(name = "presence_date", nullable = false)
    private LocalDate presenceDate;
    @Column(name = "minutes_online", nullable = false)
    private int minutesOnline = 0;

    public Long getId() { return id; }
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public LocalDate getPresenceDate() { return presenceDate; }
    public void setPresenceDate(LocalDate presenceDate) { this.presenceDate = presenceDate; }
    public int getMinutesOnline() { return minutesOnline; }
    public void setMinutesOnline(int minutesOnline) { this.minutesOnline = minutesOnline; }
}
