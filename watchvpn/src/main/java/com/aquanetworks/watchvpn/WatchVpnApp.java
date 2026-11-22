package com.aquanetworks.watchvpn;

import android.app.Application;

public final class WatchVpnApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        VpnRuntime.attach(this);
    }
}
