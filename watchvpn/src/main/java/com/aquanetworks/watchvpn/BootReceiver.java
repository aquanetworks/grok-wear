package com.aquanetworks.watchvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After reboot: restore proxy only if the user left Connect on; otherwise clear http_proxy. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||intent.getAction()==null)return;
        try{
            if(!Store.get(context).wantConnected()){
                SystemProxy.clear(context);
                AppLog.line("boot auto-connect off — cleared http_proxy");
                return;
            }
            AppLog.line("boot start wantConnected writeSecure="+SystemProxy.canWrite(context));
            EngineServices.start(context);
        }catch(Exception ignored){
            try{SystemProxy.clear(context);}catch(Exception ignored2){}
        }
    }
}
