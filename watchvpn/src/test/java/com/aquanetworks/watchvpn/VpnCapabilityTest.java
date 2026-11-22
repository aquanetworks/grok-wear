package com.aquanetworks.watchvpn;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VpnCapabilityTest {
    @Test public void npeFromIVpnManagerIsMissingVpn() {
        NullPointerException npe=new NullPointerException(
            "Attempt to invoke interface method 'android.os.ParcelFileDescriptor android.net.IVpnManager.establishVpn(android.net.VpnConfig)' on a null object reference"
        );
        assertTrue(VpnCapability.looksLikeMissingVpn(npe));
        assertTrue(VpnCapability.looksLikeMissingVpn(new RuntimeException("wrap",npe)));
    }
    @Test public void unrelatedNpeIsNotMissingVpn() {
        assertFalse(VpnCapability.looksLikeMissingVpn(new NullPointerException("foo is null")));
        assertFalse(VpnCapability.looksLikeMissingVpn(null));
        assertFalse(VpnCapability.looksLikeMissingVpn(new SecurityException("not allowed")));
    }
}
