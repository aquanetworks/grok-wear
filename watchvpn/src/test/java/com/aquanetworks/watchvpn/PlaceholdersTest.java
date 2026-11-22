package com.aquanetworks.watchvpn;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public final class PlaceholdersTest {
    @Test public void stubNameAndLoopbackArePlaceholders()throws Exception {
        Map<String,Object> unsupported=VlessUri.parse(
            "vless://11111111-1111-4111-a111-111111111111@0.0.0.0:443?security=reality&pbk=0123456789abcdef0123456789abcdef0123456789a&sid=ab#%E2%9D%8C%20%D0%9F%D1%80%D0%B8%D0%BB%D0%BE%D0%B6%D0%B5%D0%BD%D0%B8%D0%B5%20%D0%BD%D0%B5%20%D0%BF%D0%BE%D0%B4%D0%B4%D0%B5%D1%80%D0%B6%D0%B8%D0%B2%D0%B0%D0%B5%D1%82%D1%81%D1%8F"
        ).toClash();
        Map<String,Object> telegram=VlessUri.parse(
            "vless://11111111-1111-4111-a111-111111111111@127.0.0.1:443?security=none#%E2%9C%85%20%D0%94%D0%BE%D1%81%D1%82%D1%83%D0%BF%20%D0%B2%20Telegram"
        ).toClash();
        assertTrue(Placeholders.isPlaceholder(unsupported));
        assertTrue(Placeholders.isPlaceholder(telegram));
        assertTrue(Placeholders.onlyPlaceholders(List.of(unsupported,telegram)));
        assertEquals(0,Placeholders.realCount(List.of(unsupported,telegram)));
    }
    @Test public void realHostIsNotPlaceholder()throws Exception {
        Map<String,Object> nl=VlessUri.parse(VlessUriTest.SYNTHETIC).toClash();
        assertFalse(Placeholders.isPlaceholder(nl));
        assertEquals(1,Placeholders.realCount(List.of(nl)));
        assertFalse(Placeholders.onlyPlaceholders(List.of(nl)));
    }
    @Test public void emptyListIsAllPlaceholders() {
        assertTrue(Placeholders.onlyPlaceholders(List.of()));
        assertTrue(Placeholders.onlyPlaceholders(null));
    }
}
