package com.aquanetworks.watchvpn;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MihomoApiTest {
    @Test public void delayParseKeepsExactMessage() {
        MihomoApi.DelayResult reality=MihomoApi.DelayResult.parse("GE",503,"{\"message\":\"reality verification failed\"}");
        assertFalse(reality.ok());
        assertEquals("reality verification failed",reality.error);
        assertTrue(reality.summary().contains("reality verification failed"));
        MihomoApi.DelayResult tls=MihomoApi.DelayResult.parse("GE",503,"{\"message\":\"tls: handshake failure\"}");
        assertEquals("tls: handshake failure",tls.error);
        MihomoApi.DelayResult timeout=MihomoApi.DelayResult.parse("GE",504,"{\"message\":\"Timeout\"}");
        assertEquals("Timeout",timeout.error);
        MihomoApi.DelayResult dns=MihomoApi.DelayResult.parse("GE",503,"{\"message\":\"dns lookup failed\"}");
        assertEquals("dns lookup failed",dns.error);
        MihomoApi.DelayResult io=MihomoApi.DelayResult.parse("GE",503,"{\"message\":\"i/o timeout\"}");
        assertEquals("i/o timeout",io.error);
    }
    @Test public void delayParseSuccess() {
        MihomoApi.DelayResult ok=MihomoApi.DelayResult.parse("GE",200,"{\"delay\":321}");
        assertTrue(ok.ok());
        assertEquals(321,ok.delayMs);
        assertEquals("GE 321ms",ok.summary());
    }
    @Test public void delayParseEmptyBodyOn503() {
        MihomoApi.DelayResult r=MihomoApi.DelayResult.parse("GE",503,"");
        assertEquals("HTTP 503",r.error);
    }
}
