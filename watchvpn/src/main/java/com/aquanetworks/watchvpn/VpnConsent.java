package com.aquanetworks.watchvpn;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Process;

/**
 * VpnService.prepare() plus AppOps {@code ACTIVATE_VPN}.
 * Wear often has no consent UI; {@link #APPOPS_COMMAND} is the documented ADB workaround.
 * On some Wear builds prepare() still returns an intent after appops allow — establish() is the real check.
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
    static Intent prepareIntent(Context context){
        try{return VpnService.prepare(context);}
        catch(Exception e){AppLog.line("prepare() threw "+e.getClass().getSimpleName());return null;}
    }
    static boolean canLaunchPrepare(Context context,Intent prepare){
        if(context==null||prepare==null)return false;
        try{
            return context.getPackageManager().resolveActivity(prepare,PackageManager.MATCH_DEFAULT_ONLY)!=null;
        }catch(Exception e){return false;}
    }
    static boolean appopsAllowed(Context context){
        if(context==null)return false;
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
        if(context==null)return APPOPS_COMMAND;
        boolean prepare=prepareReady(context);
        boolean appops=appopsAllowed(context);
        if(prepare)return appops?"VPN permission is granted (prepare + appops).":"VPN permission is granted (prepare() is null).";
        if(appops)return "appops ACTIVATE_VPN is allowed. Connect will call establish() even if prepare() returns an intent.";
        return "prepare() wants a dialog. If Connect fails, run: "+APPOPS_COMMAND;
    }
    static String establishFailed(Context context,String detail){
        String ops=appopsAllowed(context)?"allow":"not-allow";
        String extra=(detail==null||detail.isEmpty())?"":detail+" ";
        return extra+"establish() failed (appops ACTIVATE_VPN="+ops+"). "+APPOPS_COMMAND;
    }
}
