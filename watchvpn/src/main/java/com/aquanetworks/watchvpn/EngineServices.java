package com.aquanetworks.watchvpn;

import android.content.Context;

/** Starts proxy (default) or TUN (only if the user enabled it and IVpnManager exists). */
final class EngineServices {
    static boolean useTun(Context context){
        try{return Store.get(context).tunMode()&&VpnCapability.available(context);}
        catch(Exception e){return false;}
    }
    static void start(Context context){
        if(useTun(context)){
            AppLog.line("engine start TUN");
            WatchVpnService.startDirect(context);
            return;
        }
        AppLog.line("engine start PROXY "+SystemProxy.ENDPOINT);
        WatchProxyService.start(context);
    }
    static void stop(Context context){
        SystemProxy.clear(context);
        WatchProxyService.stop(context);
        WatchVpnService.stopDirect(context);
    }
    static void reload(Context context){start(context);}
}
