package com.aquanetworks.watchvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

/** Restarts the tunnel after reboot when the user left it connected and consent is still granted. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||intent.getAction()==null)return;
        try{
            if(!Store.get(context).wantConnected())return;
            if(VpnService.prepare(context)!=null)return;
            WatchVpnService.start(context);
        }catch(Exception ignored){}
    }
}
