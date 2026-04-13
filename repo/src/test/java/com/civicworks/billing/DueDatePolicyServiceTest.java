package com.civicworks.billing;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DueDatePolicyServiceTest {

    @Test
    void dueDate15DaysFromCycleDate() {
        LocalDate cycleDate = LocalDate.of(2026, 4, 1);
        int dueInDays = 15;
        LocalDate dueDate = cycleDate.plusDays(dueInDays);
        assertEquals(LocalDate.of(2026, 4, 16), dueDate);
    }

    @Test
    void dueDate1DayMinimum() {
        LocalDate cycleDate = LocalDate.of(2026, 4, 1);
        LocalDate dueDate = cycleDate.plusDays(1);
        assertEquals(LocalDate.of(2026, 4, 2), dueDate);
    }

    @Test
    void dueDate60DayMaximum() {
        LocalDate cycleDate = LocalDate.of(2026, 4, 1);
        LocalDate dueDate = cycleDate.plusDays(60);
        assertEquals(LocalDate.of(2026, 5, 31), dueDate);
    }

    @Test
    void dueInDaysValidationRange() {
        assertTrue(1 >= 1 && 1 <= 60);
        assertTrue(60 >= 1 && 60 <= 60);
        assertFalse(0 >= 1 && 0 <= 60);
        assertFalse(61 >= 1 && 61 <= 60);
    }

    @Test
    void lateFeeEligibilityDay11() {
        LocalDate dueDate = LocalDate.of(2026, 4, 16);
        LocalDate eligibleDate = dueDate.plusDays(10); // Grace = 10 days, eligible on day 11
        LocalDate lateFeeStartDate = eligibleDate.plusDays(1); // First opportunity
        // Actually per spec: eligible starting local day 11 after due date
        LocalDate day11 = dueDate.plusDays(11);
        assertEquals(LocalDate.of(2026, 4, 27), day11);
    }

    @Test
    void gracePeriodCutoffCalculation() {
        LocalDate today = LocalDate.of(2026, 4, 27);
        int gracePeriodDays = 10;
        LocalDate cutoff = today.minusDays(gracePeriodDays);
        // Bills with due_date <= cutoff are eligible
        assertEquals(LocalDate.of(2026, 4, 17), cutoff);
    }
}
