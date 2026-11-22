package com.aquanetworks.watchvpn;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VpnConsentTextTest {
    @Test public void appopsCommandTargetsPackage() {
        assertTrue(VpnConsent.APPOPS_COMMAND.contains("com.aquanetworks.watchvpn"));
        assertTrue(VpnConsent.APPOPS_COMMAND.contains("ACTIVATE_VPN"));
        assertTrue(VpnConsent.APPOPS_COMMAND.startsWith("adb shell appops set "));
    }
    @Test public void missingUiMentionsAppops() {
        assertTrue(VpnConsent.MISSING_UI.contains(VpnConsent.APPOPS_COMMAND));
        assertTrue(VpnConsent.DENIED.contains(VpnConsent.APPOPS_COMMAND));
    }
    @Test public void establishFailedIncludesExceptionAndAppops() {
        String text=VpnConsent.establishFailed(null,"SecurityException: test");
        assertTrue(text.contains("SecurityException: test"));
        assertTrue(text.contains("ACTIVATE_VPN"));
        assertTrue(text.contains(VpnConsent.APPOPS_COMMAND));
    }
}
