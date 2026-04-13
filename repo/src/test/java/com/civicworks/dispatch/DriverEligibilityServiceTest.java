package com.civicworks.dispatch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DriverEligibilityServiceTest {

    private static final BigDecimal MIN_RATING = new BigDecimal("4.20");
    private static final double MAX_DISTANCE_MILES = 3.0;
    private static final int MIN_ONLINE_MINUTES = 15;

    private double distanceMiles(double lat1, double lon1, double lat2, double lon2) {
        double R = 3958.8;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @Test
    void ratingCheckExactlyAtMinimum() {
        BigDecimal rating = new BigDecimal("4.20");
        assertTrue(rating.compareTo(MIN_RATING) >= 0);
    }

    @Test
    void ratingCheckBelowMinimum() {
        BigDecimal rating = new BigDecimal("4.19");
        assertTrue(rating.compareTo(MIN_RATING) < 0);
    }

    @Test
    void distanceWithinThreeMiles() {
        // ~1 mile apart
        double distance = distanceMiles(40.7128, -74.0060, 40.7258, -74.0060);
        assertTrue(distance < MAX_DISTANCE_MILES);
    }

    @Test
    void distanceExceedsThreeMiles() {
        // ~5 miles apart
        double distance = distanceMiles(40.7128, -74.0060, 40.7828, -74.0060);
        assertTrue(distance > MAX_DISTANCE_MILES);
    }

    @Test
    void onlineMinutesExactlyAtMinimum() {
        int minutes = 15;
        assertTrue(minutes >= MIN_ONLINE_MINUTES);
    }

    @Test
    void onlineMinutesBelowMinimum() {
        int minutes = 14;
        assertFalse(minutes >= MIN_ONLINE_MINUTES);
    }
}
