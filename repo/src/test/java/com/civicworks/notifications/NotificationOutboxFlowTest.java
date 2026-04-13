package com.civicworks.notifications;

import com.civicworks.notifications.application.NotificationService;
import com.civicworks.notifications.domain.*;
import com.civicworks.notifications.infra.*;
import com.civicworks.platform.clock.MunicipalClock;
import com.civicworks.platform.config.SystemConfigService;
import com.civicworks.platform.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Offline-only notification behavior:
 *  - External channels (EMAIL/SMS/IM) are disabled by default.
 *  - When disabled, no outbox row is written and no network send is attempted.
 *  - When enabled, the payload is written to notification_outbox for manual
 *    offline export.
 *  - IN_APP messages always persist in_app_messages.
 */
class NotificationOutboxFlowTest {

    private NotificationTemplateRepository templateRepo;
    private InAppMessageRepository messageRepo;
    private TaskReminderRepository reminderRepo;
    private DeliveryReceiptRepository receiptRepo;
    private NotificationOutboxRepository outboxRepo;
    private SystemConfigService systemConfig;
    private MunicipalClock clock;
    private NotificationService svc;

    @BeforeEach
    void setUp() {
        templateRepo = mock(NotificationTemplateRepository.class);
        messageRepo = mock(InAppMessageRepository.class);
        reminderRepo = mock(TaskReminderRepository.class);
        receiptRepo = mock(DeliveryReceiptRepository.class);
        outboxRepo = mock(NotificationOutboxRepository.class);
        systemConfig = mock(SystemConfigService.class);
        clock = mock(MunicipalClock.class);
        svc = new NotificationService(templateRepo, messageRepo, reminderRepo,
                receiptRepo, outboxRepo, systemConfig, clock);
        when(messageRepo.save(any(InAppMessage.class))).thenAnswer(i -> {
            InAppMessage m = i.getArgument(0);
            m.setId(1L);
            return m;
        });
    }

    private NotificationTemplate template(String channel) {
        NotificationTemplate t = new NotificationTemplate();
        t.setId(9L);
        t.setName("t");
        t.setBody("b");
        t.setChannel(channel);
        return t;
    }

    @Test
    void externalChannelDisabled_noOutboxRowWritten() {
        when(templateRepo.findById(9L)).thenReturn(Optional.of(template("EMAIL")));
        when(systemConfig.isChannelEnabled("EMAIL")).thenReturn(false);

        svc.createMessage(42L, "subj", "body", 9L);

        verify(outboxRepo, never()).save(any(NotificationOutbox.class));
        verify(messageRepo, times(1)).save(any(InAppMessage.class));
    }

    @Test
    void externalChannelEnabled_writesOutboxRow_andInAppMessage() {
        when(templateRepo.findById(9L)).thenReturn(Optional.of(template("SMS")));
        when(systemConfig.isChannelEnabled("SMS")).thenReturn(true);

        svc.createMessage(42L, "subj", "body", 9L);

        ArgumentCaptor<NotificationOutbox> cap = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outboxRepo).save(cap.capture());
        NotificationOutbox row = cap.getValue();
        assertEquals("SMS", row.getChannel());
        assertEquals("42", row.getRecipientRef());
        assertEquals("subj", row.getSubject());
        assertEquals("body", row.getBody());
        assertFalse(row.isExported(), "new outbox entries must start un-exported");
    }

    @Test
    void inAppTemplate_neverWritesOutbox() {
        when(templateRepo.findById(9L)).thenReturn(Optional.of(template("IN_APP")));
        // IN_APP is always "enabled" in our semantics.
        when(systemConfig.isChannelEnabled("IN_APP")).thenReturn(true);

        svc.createMessage(42L, "subj", "body", 9L);

        verify(outboxRepo, never()).save(any(NotificationOutbox.class));
    }

    @Test
    void enqueueExternal_disabledChannel_noOp_returnsNull() {
        when(systemConfig.isChannelEnabled("EMAIL")).thenReturn(false);

        NotificationOutbox row = svc.enqueueExternal("EMAIL", "alice@example.org",
                "s", "b", null);

        assertNull(row);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void enqueueExternal_enabledChannel_persistsOutbox() {
        when(systemConfig.isChannelEnabled("IM")).thenReturn(true);
        when(outboxRepo.save(any(NotificationOutbox.class))).thenAnswer(i -> i.getArgument(0));

        NotificationOutbox row = svc.enqueueExternal("im", "@alice",
                "s", "b", "{\"x\":1}");

        assertNotNull(row);
        assertEquals("IM", row.getChannel(), "channel normalized to upper case");
        assertEquals("@alice", row.getRecipientRef());
        assertEquals("{\"x\":1}", row.getPayloadJson());
    }

    @Test
    void enqueueExternal_rejectsInApp() {
        assertThrows(BusinessException.class,
                () -> svc.enqueueExternal("IN_APP", "x", "s", "b", null));
    }

    @Test
    void enqueueExternal_rejectsBlankChannel() {
        assertThrows(BusinessException.class,
                () -> svc.enqueueExternal("  ", "x", "s", "b", null));
        assertThrows(BusinessException.class,
                () -> svc.enqueueExternal(null, "x", "s", "b", null));
    }
}
