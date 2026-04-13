package com.civicworks.platform.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Runtime config reads go through the DB so admin edits apply without
 * restart. These tests pin the runtime-read contract so the admin PUT
 * can't silently become a dead write.
 */
class SystemConfigServiceTest {

    private SystemConfigRepository repo;
    private SystemConfigService svc;

    @BeforeEach
    void setUp() {
        repo = mock(SystemConfigRepository.class);
        svc = new SystemConfigService(repo);
    }

    private void seed(String key, String value) {
        SystemConfig c = new SystemConfig();
        c.setConfigKey(key); c.setConfigValue(value);
        when(repo.findByConfigKey(key)).thenReturn(Optional.of(c));
    }

    @Test
    void channelEnabled_respectsRuntimeValue() {
        seed("emailChannelEnabled", "true");
        // SMS left unset → defaults to false
        assertTrue(svc.isChannelEnabled("EMAIL"));
        assertFalse(svc.isChannelEnabled("SMS"));
        assertFalse(svc.isChannelEnabled("IM"));
    }

    @Test
    void inAppAlwaysEnabled() {
        // Even with no rows present, IN_APP is the local channel.
        assertTrue(svc.isChannelEnabled("IN_APP"));
        assertTrue(svc.isChannelEnabled("in_app"));
    }

    @Test
    void channelLookupCaseInsensitive() {
        seed("smsChannelEnabled", "true");
        assertTrue(svc.isChannelEnabled("sms"));
        assertTrue(svc.isChannelEnabled("Sms"));
        assertTrue(svc.isChannelEnabled("SMS"));
    }

    @Test
    void unknownChannelIsDisabled() {
        assertFalse(svc.isChannelEnabled("WHATSAPP"));
        assertFalse(svc.isChannelEnabled(null));
    }

    @Test
    void getInt_fallsBackOnInvalidOrMissing() {
        assertEquals(42, svc.getInt("missing", 42));
        seed("sessionTtlMinutes", "notAnInt");
        assertEquals(480, svc.getInt("sessionTtlMinutes", 480));
        seed("sessionTtlMinutes", "600");
        assertEquals(600, svc.getInt("sessionTtlMinutes", 480));
    }

    @Test
    void getBoolean_respectsWhitespaceAndCase() {
        seed("emailChannelEnabled", " TRUE ");
        assertTrue(svc.getBoolean("emailChannelEnabled", false));
        seed("emailChannelEnabled", "false");
        assertFalse(svc.getBoolean("emailChannelEnabled", true));
    }
}
