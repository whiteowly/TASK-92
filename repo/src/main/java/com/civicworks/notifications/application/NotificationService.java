package com.civicworks.notifications.application;

import com.civicworks.notifications.domain.*;
import com.civicworks.notifications.infra.*;
import com.civicworks.platform.clock.MunicipalClock;
import com.civicworks.platform.config.SystemConfigService;
import com.civicworks.platform.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationTemplateRepository templateRepo;
    private final InAppMessageRepository messageRepo;
    private final TaskReminderRepository reminderRepo;
    private final DeliveryReceiptRepository receiptRepo;
    private final NotificationOutboxRepository outboxRepo;
    private final SystemConfigService systemConfig;
    private final MunicipalClock clock;

    public NotificationService(NotificationTemplateRepository templateRepo,
                               InAppMessageRepository messageRepo,
                               TaskReminderRepository reminderRepo,
                               DeliveryReceiptRepository receiptRepo,
                               NotificationOutboxRepository outboxRepo,
                               SystemConfigService systemConfig,
                               MunicipalClock clock) {
        this.templateRepo = templateRepo;
        this.messageRepo = messageRepo;
        this.reminderRepo = reminderRepo;
        this.receiptRepo = receiptRepo;
        this.outboxRepo = outboxRepo;
        this.systemConfig = systemConfig;
        this.clock = clock;
    }

    // Templates
    public NotificationTemplate createTemplate(NotificationTemplate template) {
        return templateRepo.save(template);
    }

    public NotificationTemplate updateTemplate(Long id, NotificationTemplate updates) {
        NotificationTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Template not found"));
        t.setName(updates.getName());
        t.setSubject(updates.getSubject());
        t.setBody(updates.getBody());
        t.setChannel(updates.getChannel());
        t.setActive(updates.isActive());
        return templateRepo.save(t);
    }

    public List<NotificationTemplate> listTemplates() {
        return templateRepo.findAll();
    }

    // Messages
    @Transactional
    public InAppMessage createMessage(Long recipientId, String subject, String body, Long templateId) {
        InAppMessage msg = new InAppMessage();
        msg.setRecipientId(recipientId);
        msg.setSubject(subject);
        msg.setBody(body);
        msg.setTemplateId(templateId);
        InAppMessage saved = messageRepo.save(msg);

        DeliveryReceipt receipt = new DeliveryReceipt();
        receipt.setMessageId(saved.getId());
        receipt.setStatus("DELIVERED");
        receiptRepo.save(receipt);

        // If the selected template targets an external channel (EMAIL/SMS/IM),
        // route an export-ready row to notification_outbox iff that channel is
        // enabled in system_config. This system is offline-only: no network
        // send path exists — the outbox is the ONLY way external-channel
        // payloads leave the system, and that is via manual export.
        if (templateId != null) {
            templateRepo.findById(templateId).ifPresent(t -> {
                String channel = t.getChannel();
                if (channel != null && !"IN_APP".equalsIgnoreCase(channel.trim())
                        && systemConfig.isChannelEnabled(channel)) {
                    NotificationOutbox entry = new NotificationOutbox();
                    entry.setChannel(channel.toUpperCase());
                    entry.setRecipientRef(String.valueOf(recipientId));
                    entry.setSubject(subject);
                    entry.setBody(body);
                    outboxRepo.save(entry);
                }
            });
        }

        return saved;
    }

    /**
     * External-channel enqueue helper: writes directly to
     * {@code notification_outbox} if and only if the named channel is enabled
     * in {@code system_config}. Never performs a network send — the outbox
     * is export-only.
     *
     * @return the persisted outbox row, or {@code null} if the channel is
     *         disabled (contract: disabled channels silently no-op).
     */
    @Transactional
    public NotificationOutbox enqueueExternal(String channel, String recipientRef,
                                              String subject, String body,
                                              String payloadJson) {
        if (channel == null || channel.isBlank()) {
            throw BusinessException.badRequest(
                    com.civicworks.platform.error.ErrorCode.VALIDATION_ERROR,
                    "channel is required");
        }
        String ch = channel.trim().toUpperCase();
        if ("IN_APP".equals(ch)) {
            throw BusinessException.badRequest(
                    com.civicworks.platform.error.ErrorCode.VALIDATION_ERROR,
                    "IN_APP is not an external channel; use createMessage");
        }
        if (!systemConfig.isChannelEnabled(ch)) {
            // Offline-only contract: disabled channels are a no-op. Callers
            // should not treat this as a failure — there is simply no queue
            // destination until an admin enables the channel.
            return null;
        }
        NotificationOutbox entry = new NotificationOutbox();
        entry.setChannel(ch);
        entry.setRecipientRef(recipientRef);
        entry.setSubject(subject);
        entry.setBody(body);
        entry.setPayloadJson(payloadJson);
        return outboxRepo.save(entry);
    }

    public List<InAppMessage> listMessages(Long recipientId) {
        return messageRepo.findByRecipientIdOrderByCreatedAtDesc(recipientId);
    }

    @Transactional
    public InAppMessage acknowledgeMessage(Long messageId, Long userId) {
        InAppMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> BusinessException.notFound("Message not found"));
        if (!msg.getRecipientId().equals(userId)) {
            throw BusinessException.forbidden("Not the recipient");
        }
        msg.setReadAt(clock.instant());
        messageRepo.save(msg);

        DeliveryReceipt receipt = new DeliveryReceipt();
        receipt.setMessageId(messageId);
        receipt.setStatus("ACKNOWLEDGED");
        receiptRepo.save(receipt);

        return msg;
    }

    // Reminders
    public TaskReminder createReminder(TaskReminder reminder) {
        return reminderRepo.save(reminder);
    }

    public List<TaskReminder> listReminders() {
        return reminderRepo.findAll();
    }

    @Transactional
    public void processReminders() {
        List<TaskReminder> due = reminderRepo.findDueReminders(clock.instant());
        for (TaskReminder r : due) {
            createMessage(r.getRecipientId(), "Reminder", r.getMessage(), null);
            r.setSent(true);
            r.setRetryCount(r.getRetryCount() + 1);
            reminderRepo.save(r);
        }
    }

    // Outbox
    public List<NotificationOutbox> listOutbox() {
        return outboxRepo.findByExportedFalse();
    }

    @Transactional
    public NotificationOutbox markExported(Long id) {
        NotificationOutbox entry = outboxRepo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Outbox entry not found"));
        entry.setExported(true);
        entry.setExportedAt(clock.instant());
        return outboxRepo.save(entry);
    }
}
