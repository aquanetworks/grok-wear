package com.aquanetworks.watchvpn;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ImportLinksTest {
    @Test public void clashInstallConfig()throws Exception {
        String url=ImportLinks.extract("clash://install-config?url=https%3A%2F%2Fsub.example.test%2Fs%3Ftoken%3Dsynthetic");
        assertEquals("https://sub.example.test/s?token=synthetic",url);
    }
    @Test public void clashUnencodedHttps()throws Exception {
        String url=ImportLinks.extract("clash://install-config?url=https://sub.example.test/api/v1/client/subscribe?token=synthetic");
        assertTrue(url.startsWith("https://sub.example.test/"));
        assertTrue(url.contains("token=synthetic"));
    }
    @Test public void watchvpnScheme()throws Exception {
        assertEquals("https://sub.example.test/s",ImportLinks.extract("watchvpn://import?url=https://sub.example.test/s"));
    }
    @Test public void rawHttps()throws Exception {
        assertEquals("https://sub.example.test/s",ImportLinks.extract(" https://sub.example.test/s "));
    }
    @Test public void httpRejected() {
        try{ImportLinks.extract("clash://install-config?url=http://sub.example.test/s");fail();}
        catch(Exception expected){assertTrue(expected.getMessage().contains("HTTPS"));}
    }
    @Test public void masksQuery() {
        assertEquals("https://sub.example.test/…",ImportLinks.maskUrl("https://sub.example.test/api?token=secret-value"));
    }
    @Test public void importSchemeHosts() {
        assertTrue(ImportLinks.isImportScheme("clash","install-config"));
        assertTrue(ImportLinks.isImportScheme("watchvpn","import"));
        assertFalse(ImportLinks.isImportScheme("https","example.test"));
    }
}
