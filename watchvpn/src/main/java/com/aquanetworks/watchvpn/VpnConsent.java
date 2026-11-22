package com.aquanetworks.watchvpn;

import android.app.AppOpsManager;
import android.content.Context;
import android.net.VpnService;
import android.os.Build;
import android.os.Process;

/**
 * VpnService.prepare() plus AppOps {@code ACTIVATE_VPN}.
 * Wear often has no consent UI; {@link #APPOPS_COMMAND} is the documented ADB workaround.
 */
final class VpnConsent {
    static final String OP="android:activate_vpn";
    static final String APPOPS_COMMAND="adb shell appops set com.aquanetworks.watchvpn ACTIVATE_VPN allow";
    static final String MISSING_UI=
        "This watch has no VPN consent screen. From a computer with ADB (WearLoad): "
        +APPOPS_COMMAND;
    static final String DENIED=
        "VPN permission was refused. If the watch closed the dialog immediately, grant it with: "
        +APPOPS_COMMAND;

    static boolean prepareReady(Context context){
        try{return VpnService.prepare(context)==null;}
        catch(Exception ignored){return false;}
    }
    static boolean appopsAllowed(Context context){
        try{
            AppOpsManager ops=context.getSystemService(AppOpsManager.class);
            if(ops==null)return false;
            int mode;
            if(Build.VERSION.SDK_INT>=29)mode=ops.unsafeCheckOpNoThrow(OP,Process.myUid(),context.getPackageName());
            else{
                @SuppressWarnings("deprecation")
                int old=ops.checkOpNoThrow(OP,Process.myUid(),context.getPackageName());
                mode=old;
            }
            return mode==AppOpsManager.MODE_ALLOWED;
        }catch(Exception ignored){return false;}
    }
    static String describe(Context context){
        boolean prepare=prepareReady(context);
        boolean appops=appopsAllowed(context);
        if(prepare)return appops?"VPN permission is granted (prepare + appops).":"VPN permission is granted (prepare).";
        if(appops)return "appops ACTIVATE_VPN is allowed, but VpnService.prepare() still wants a dialog.";
        return MISSING_UI;
    }
}
