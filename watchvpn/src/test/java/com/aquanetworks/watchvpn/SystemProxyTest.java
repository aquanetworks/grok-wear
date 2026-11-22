package com.aquanetworks.watchvpn;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SystemProxyTest {
    @Test public void commandsHaveNoAdbPrefix() {
        assertEquals("pm grant com.aquanetworks.watchvpn android.permission.WRITE_SECURE_SETTINGS",SystemProxy.GRANT_CMD);
        assertEquals("settings put global http_proxy 127.0.0.1:7890",SystemProxy.SET_CMD);
        assertEquals("settings put global http_proxy :0",SystemProxy.CLEAR_CMD);
        assertFalse(SystemProxy.GRANT_CMD.contains("adb"));
        assertFalse(SystemProxy.SET_CMD.contains("adb"));
        assertFalse(SystemProxy.WHITELIST_CMD.contains("adb"));
        assertTrue(SystemProxy.WHITELIST_CMD.startsWith("dumpsys deviceidle whitelist +"));
        assertTrue(SystemProxy.describe(null).contains(SystemProxy.GRANT_CMD));
        assertEquals("127.0.0.1:7890",SystemProxy.ENDPOINT);
        assertEquals(7890,TunConfig.MIXED_PORT);
    }
    @Test public void disabledValues() {
        assertTrue(SystemProxy.isDisabled(null));
        assertTrue(SystemProxy.isDisabled(""));
        assertTrue(SystemProxy.isDisabled(":0"));
        assertTrue(SystemProxy.isDisabled("none"));
        assertFalse(SystemProxy.isDisabled("127.0.0.1:7890"));
    }
    @Test public void oursDetectsMixedPort() {
        assertTrue(SystemProxy.isOurs("127.0.0.1:7890"));
        assertTrue(SystemProxy.isOurs("http://127.0.0.1:7890"));
        assertFalse(SystemProxy.isOurs("127.0.0.1:8080"));
        assertFalse(SystemProxy.isOurs(":0"));
    }
    @Test public void messengerWarningIsExplicit() {
        String w=SystemProxy.MESSENGER_WARN.toLowerCase();
        assertTrue(w.contains("whatsapp"));
        assertTrue(w.contains("telegram"));
        assertTrue(w.contains("прокси")||w.contains("proxy"));
    }
}
