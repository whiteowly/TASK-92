package com.civicworks.platform.clock;

import com.civicworks.platform.config.SystemConfigService;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Timezone is an admin-editable runtime knob. A SYSTEM_ADMIN edit via
 * PUT /api/v1/admin/system-config must take effect on the next clock
 * consumer (e.g. late-fee eligibility, search-history retention query)
 * without a restart. These tests pin that contract.
 */
class MunicipalClockRuntimeConfigTest {

    @Test
    void zone_readsFromSystemConfigAtCallTime_notOnlyAtBoot() {
        SystemConfigService cfg = mock(SystemConfigService.class);
        // Boot time default is America/New_York. Later, an admin edits the
        // system_config.timezone to UTC. The clock must pick that up on the
        // next call without requiring a re-instantiation.
        when(cfg.getOrDefault(SystemConfigService.KEY_TIMEZONE, "America/New_York"))
                .thenReturn("America/New_York", "UTC");

        MunicipalClock clock = new MunicipalClock(cfg, "America/New_York");

        assertEquals(ZoneId.of("America/New_York"), clock.zone());
        // The second call reflects the admin edit.
        assertEquals(ZoneId.of("UTC"), clock.zone());
    }

    @Test
    void invalidZoneInSystemConfig_fallsBackToDefault() {
        SystemConfigService cfg = mock(SystemConfigService.class);
        when(cfg.getOrDefault(SystemConfigService.KEY_TIMEZONE, "America/New_York"))
                .thenReturn("Not/A_Real_Zone");

        MunicipalClock clock = new MunicipalClock(cfg, "America/New_York");

        assertEquals(ZoneId.of("America/New_York"), clock.zone());
    }

    @Test
    void cachedZone_avoidsReParseWhenConfigUnchanged() {
        SystemConfigService cfg = mock(SystemConfigService.class);
        when(cfg.getOrDefault(SystemConfigService.KEY_TIMEZONE, "America/New_York"))
                .thenReturn("Europe/Berlin");
        MunicipalClock clock = new MunicipalClock(cfg, "America/New_York");

        ZoneId first = clock.zone();
        ZoneId second = clock.zone();
        assertSame(first, second, "same config value → same ZoneId instance (cached)");
    }

    @Test
    void nullSystemConfigService_usesStaticDefault() {
        MunicipalClock clock = new MunicipalClock(null, "UTC");
        assertEquals(ZoneId.of("UTC"), clock.zone());
    }
}
