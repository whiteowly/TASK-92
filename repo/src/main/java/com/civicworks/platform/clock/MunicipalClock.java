package com.civicworks.platform.clock;

import com.civicworks.platform.config.SystemConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;

/**
 * Single source of truth for "what time is it in this municipality".
 *
 * <p>Timezone is read at each call from {@link SystemConfigService} (key
 * {@code timezone}) so that a SYSTEM_ADMIN edit via
 * {@code PUT /api/v1/admin/system-config} takes effect immediately on the
 * next clock consumer without a restart. The {@code @Value} default is
 * retained as a fallback for the initial boot window before any admin
 * edit lands, and for misconfigured/invalid zone values in {@code system_config}.
 *
 * <p>Parsed {@link ZoneId} instances are cached per-value to avoid
 * re-parsing the IANA zone string on every call.
 *
 * <p>Scheduled Quartz triggers are built at bean creation against the
 * boot-time timezone; rotating the cron timezone itself requires a
 * restart. Runtime clock consumers (e.g. {@code today()} used by the late-
 * fee eligibility calculation) do pick up the new zone immediately.
 */
@Component
public class MunicipalClock {

    private final SystemConfigService systemConfig;
    private final String defaultTimezone;

    // Cache: last observed raw config string → parsed ZoneId. Avoids re-parsing
    // on every clock read while still honoring runtime edits the moment the
    // string changes in system_config.
    private volatile String cachedKey;
    private volatile ZoneId cachedZone;

    public MunicipalClock(SystemConfigService systemConfig,
                          @Value("${civicworks.timezone:America/New_York}") String defaultTimezone) {
        this.systemConfig = systemConfig;
        this.defaultTimezone = defaultTimezone;
        this.cachedKey = defaultTimezone;
        this.cachedZone = parseOrDefault(defaultTimezone);
    }

    public ZoneId zone() {
        String raw = systemConfig != null
                ? systemConfig.getOrDefault(SystemConfigService.KEY_TIMEZONE, defaultTimezone)
                : defaultTimezone;
        if (!raw.equals(cachedKey)) {
            // Only re-parse when the string actually changes.
            cachedZone = parseOrDefault(raw);
            cachedKey = raw;
        }
        return cachedZone;
    }

    public LocalDate today() {
        return LocalDate.now(zone());
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zone());
    }

    public Instant instant() {
        return Instant.now();
    }

    public ZonedDateTime zonedNow() {
        return ZonedDateTime.now(zone());
    }

    private ZoneId parseOrDefault(String raw) {
        try {
            return ZoneId.of(raw);
        } catch (RuntimeException ex) {
            // Invalid IANA zone in system_config: fall back to the boot default.
            return ZoneId.of(defaultTimezone);
        }
    }
}
