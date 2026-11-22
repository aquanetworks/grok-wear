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
        assertEquals(30_000,Health.GRACE_MS);
        assertEquals(500,Health.BIND_POLL_MS);
        assertEquals(3,Health.FAIL_STREAK);
    }
    @Test public void neverFailSafeDuringGrace() {
        long start=1_000L;
        assertTrue(Health.inGrace(start,start+1));
        assertTrue(Health.inGrace(start,start+29_999));
        assertFalse(Health.inGrace(start,start+30_000));
        assertFalse(Health.shouldFailSafe(true,1));
        assertFalse(Health.shouldFailSafe(true,99));
        assertFalse(Health.shouldFailSafe(false,2));
        assertTrue(Health.shouldFailSafe(false,3));
        assertTrue(Health.shouldFailSafe(false,4));
    }
}
