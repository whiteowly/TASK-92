package com.civicworks.platform.config;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Runtime accessor for {@code system_config} values persisted by
 * {@link AdminController}. Services that need admin-editable values (channel
 * enables, retention windows, timezone, session TTL) should consume this
 * service rather than static {@code @Value} reads so that a SYSTEM_ADMIN
 * edit via {@code PUT /api/v1/admin/system-config} is picked up at the next
 * call without a restart.
 *
 * <p>Reads are per-call through the repository (no caching). Under the
 * project's offline, single-node deployment the {@code system_config} table
 * is small and hot in the Postgres buffer cache; adding a cache would need
 * an invalidation path and is not justified here.
 */
@Service
public class SystemConfigService {

    /** Keys persisted by the admin config contract — kept here to avoid stringly typing. */
    public static final String KEY_TIMEZONE = "timezone";
    public static final String KEY_SESSION_TTL_MIN = "sessionTtlMinutes";
    public static final String KEY_SEARCH_HISTORY_RETENTION_DAYS = "searchHistoryRetentionDays";
    public static final String KEY_EMAIL_CHANNEL_ENABLED = "emailChannelEnabled";
    public static final String KEY_SMS_CHANNEL_ENABLED = "smsChannelEnabled";
    public static final String KEY_IM_CHANNEL_ENABLED = "imChannelEnabled";

    private final SystemConfigRepository repo;

    public SystemConfigService(SystemConfigRepository repo) {
        this.repo = repo;
    }

    public Optional<String> get(String key) {
        return repo.findByConfigKey(key).map(SystemConfig::getConfigValue);
    }

    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return get(key).map(v -> "true".equalsIgnoreCase(v.trim())).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return get(key).map(v -> {
            try { return Integer.parseInt(v.trim()); }
            catch (NumberFormatException e) { return defaultValue; }
        }).orElse(defaultValue);
    }

    /** Channel names used by both system_config toggles and notification_outbox rows. */
    public boolean isChannelEnabled(String channel) {
        if (channel == null) return false;
        return switch (channel.trim().toUpperCase()) {
            case "EMAIL" -> getBoolean(KEY_EMAIL_CHANNEL_ENABLED, false);
            case "SMS" -> getBoolean(KEY_SMS_CHANNEL_ENABLED, false);
            case "IM" -> getBoolean(KEY_IM_CHANNEL_ENABLED, false);
            // IN_APP is always "enabled" — it's the only in-process channel.
            case "IN_APP" -> true;
            default -> false;
        };
    }
}
