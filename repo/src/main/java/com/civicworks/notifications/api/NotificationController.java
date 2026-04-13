package com.civicworks.notifications.api;

import com.civicworks.notifications.application.NotificationService;
import com.civicworks.notifications.domain.*;
import com.civicworks.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Templates
    @PostMapping("/templates")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<NotificationTemplate> createTemplate(@Valid @RequestBody TemplateRequest req) {
        NotificationTemplate t = new NotificationTemplate();
        t.setName(req.name());
        t.setSubject(req.subject());
        t.setBody(req.body());
        t.setChannel(req.channel() != null ? req.channel() : "IN_APP");
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createTemplate(t));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<NotificationTemplate> updateTemplate(@PathVariable Long id,
                                                                @RequestBody TemplateRequest req) {
        NotificationTemplate t = new NotificationTemplate();
        t.setName(req.name());
        t.setSubject(req.subject());
        t.setBody(req.body());
        t.setChannel(req.channel() != null ? req.channel() : "IN_APP");
        t.setActive(req.active() != null ? req.active() : true);
        return ResponseEntity.ok(notificationService.updateTemplate(id, t));
    }

    @GetMapping("/templates")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<List<NotificationTemplate>> listTemplates() {
        return ResponseEntity.ok(notificationService.listTemplates());
    }

    // Messages
    @PostMapping("/messages")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<InAppMessage> createMessage(@Valid @RequestBody MessageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.createMessage(req.recipientId(), req.subject(), req.body(), req.templateId()));
    }

    @GetMapping("/messages")
    public ResponseEntity<List<InAppMessage>> listMessages() {
        return ResponseEntity.ok(notificationService.listMessages(SecurityUtils.currentUserId()));
    }

    @PostMapping("/messages/{id}/ack")
    public ResponseEntity<InAppMessage> ackMessage(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.acknowledgeMessage(id, SecurityUtils.currentUserId()));
    }

    // Reminders
    @PostMapping("/reminders")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<TaskReminder> createReminder(@Valid @RequestBody ReminderRequest req) {
        TaskReminder r = new TaskReminder();
        r.setRecipientId(req.recipientId());
        r.setEntityType(req.entityType());
        r.setEntityId(req.entityId());
        r.setMessage(req.message());
        r.setScheduledAt(req.scheduledAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createReminder(r));
    }

    @GetMapping("/reminders")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<List<TaskReminder>> listReminders() {
        return ResponseEntity.ok(notificationService.listReminders());
    }

    // Outbox
    @GetMapping("/outbox")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<List<NotificationOutbox>> listOutbox() {
        return ResponseEntity.ok(notificationService.listOutbox());
    }

    @PostMapping("/outbox/{id}/mark-exported")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<NotificationOutbox> markExported(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markExported(id));
    }

    public record TemplateRequest(@NotBlank String name, String subject, @NotBlank String body,
                                   String channel, Boolean active) {}
    public record MessageRequest(@NotNull Long recipientId, String subject, @NotBlank String body,
                                  Long templateId) {}
    public record ReminderRequest(@NotNull Long recipientId, String entityType, String entityId,
                                   @NotBlank String message, @NotNull Instant scheduledAt) {}
}
