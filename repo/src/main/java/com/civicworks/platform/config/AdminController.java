package com.civicworks.platform.config;

import com.civicworks.platform.audit.AuditLog;
import com.civicworks.platform.audit.AuditLogRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final SystemConfigRepository configRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    public AdminController(SystemConfigRepository configRepository,
                           AuditLogRepository auditLogRepository,
                           AuditService auditService) {
        this.configRepository = configRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
    }

    @GetMapping("/system-config")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, String>> getConfig() {
        var configs = configRepository.findAll();
        Map<String, String> result = new java.util.HashMap<>();
        configs.forEach(c -> result.put(c.getConfigKey(), c.getConfigValue()));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/system-config")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, String>> updateConfig(@Valid @RequestBody SystemConfigRequest request) {
        if (request.timezone() != null) {
            ZoneId.of(request.timezone()); // validates IANA zone
            upsertConfig("timezone", request.timezone());
        }
        if (request.sessionTtlMinutes() != null) {
            upsertConfig("sessionTtlMinutes", String.valueOf(request.sessionTtlMinutes()));
        }
        if (request.searchHistoryRetentionDays() != null) {
            upsertConfig("searchHistoryRetentionDays", String.valueOf(request.searchHistoryRetentionDays()));
        }
        if (request.emailChannelEnabled() != null) {
            upsertConfig("emailChannelEnabled", String.valueOf(request.emailChannelEnabled()));
        }
        if (request.smsChannelEnabled() != null) {
            upsertConfig("smsChannelEnabled", String.valueOf(request.smsChannelEnabled()));
        }
        if (request.imChannelEnabled() != null) {
            upsertConfig("imChannelEnabled", String.valueOf(request.imChannelEnabled()));
        }

        auditService.log(SecurityUtils.currentUserId(), "SYSTEM_ADMIN", "SYSTEM_CONFIG_UPDATE",
                "system_config", null, request.toString());

        return getConfig();
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'AUDITOR')")
    public ResponseEntity<Page<AuditLog>> getAuditLog(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        auditService.log(SecurityUtils.currentUserId(), null, "AUDIT_LOG_ACCESS",
                "audit_log", null, null);

        Page<AuditLog> result = auditLogRepository.findFiltered(
                actorId, action, entityType, from, to,
                PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(result);
    }

    private void upsertConfig(String key, String value) {
        SystemConfig config = configRepository.findByConfigKey(key)
                .orElseGet(() -> {
                    SystemConfig c = new SystemConfig();
                    c.setConfigKey(key);
                    return c;
                });
        config.setConfigValue(value);
        config.setUpdatedAt(Instant.now());
        configRepository.save(config);
    }

    public record SystemConfigRequest(
            String timezone,
            @Min(60) @Max(1440) Integer sessionTtlMinutes,
            @Min(1) @Max(3650) Integer searchHistoryRetentionDays,
            Boolean emailChannelEnabled,
            Boolean smsChannelEnabled,
            Boolean imChannelEnabled
    ) {}
}
