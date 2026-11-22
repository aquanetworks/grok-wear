package com.aquanetworks.watchvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the tunnel after reboot when the user left it connected. Consent is checked at establish(). */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||intent.getAction()==null)return;
        try{
            if(!Store.get(context).wantConnected())return;
            AppLog.line("boot start wantConnected appops="+VpnConsent.appopsAllowed(context));
            WatchVpnService.start(context);
        }catch(Exception ignored){}
    }
}
