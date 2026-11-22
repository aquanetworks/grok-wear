package com.aquanetworks.watchvpn;

import org.junit.Test;
import static org.junit.Assert.*;

public final class HealthTest {
    @Test public void connectedRequiresPortAndDelay() {
        assertFalse(Health.connected(false,false));
        assertFalse(Health.connected(true,false));
        assertFalse(Health.connected(false,true));
        assertTrue(Health.connected(true,true));
    }
    @Test public void failMessageShowsClearWhenNoGrant() {
        String text=Health.failMessage(false,"node down");
        assertTrue(text.contains(SystemProxy.CLEAR_CMD));
        assertFalse(text.contains("adb"));
        assertTrue(Health.failMessage(true,"node down").contains(SystemProxy.CLEAR_VALUE));
        assertFalse(Health.failMessage(true,"x").contains(SystemProxy.CLEAR_CMD));
    }
    @Test public void closedPortIsNotOpen() {
        assertFalse(Health.portOpen("127.0.0.1",1,200));
        assertFalse(Health.portOpen("",7890,200));
    }
    @Test public void pollIntervalIsTenSeconds() {
        assertEquals(10_000,Health.POLL_MS);
    }
}
