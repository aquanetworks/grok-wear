package com.aquanetworks.watchvpn;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * Android global {@code http_proxy}. The app can write it only with
 * {@code WRITE_SECURE_SETTINGS} (aShell You / ADB {@code pm grant}).
 * Never leave a dead listener in this setting — the watch would have no internet.
 */
final class SystemProxy {
    static final String PERMISSION=Manifest.permission.WRITE_SECURE_SETTINGS;
    static final String KEY="http_proxy";
    static final String ENDPOINT="127.0.0.1:"+TunConfig.MIXED_PORT;
    static final String CLEAR_VALUE=":0";
    static final String GRANT_CMD="pm grant com.aquanetworks.watchvpn android.permission.WRITE_SECURE_SETTINGS";
    static final String SET_CMD="settings put global http_proxy "+ENDPOINT;
    static final String CLEAR_CMD="settings put global http_proxy "+CLEAR_VALUE;
    static final String MESSENGER_WARN=
        "WhatsApp, Telegram и многие мессенджеры игнорируют системный HTTP-прокси. "
        +"Через него идут браузер и приложения, которые его уважают. TUN на этих часах недоступен (нет IVpnManager).";

    static boolean canWrite(Context context){
        if(context==null)return false;
        try{return context.checkSelfPermission(PERMISSION)==PackageManager.PERMISSION_GRANTED;}
        catch(Exception e){return false;}
    }
    static String current(Context context){
        if(context==null)return "";
        try{
            String v=Settings.Global.getString(context.getContentResolver(),KEY);
            return v==null?"":v.trim();
        }catch(Exception e){return "";}
    }
    static boolean isDisabled(String value){
        if(value==null)return true;
        String v=value.trim();
        if(v.isEmpty())return true;
        return ":0".equals(v)||"0".equals(v)||":none".equalsIgnoreCase(v)||"none".equalsIgnoreCase(v);
    }
    static boolean isOurs(String value){
        if(value==null)return false;
        String v=value.trim();
        if(v.startsWith("http://"))v=v.substring(7);
        return ENDPOINT.equals(v)||("127.0.0.1:"+TunConfig.MIXED_PORT).equals(v);
    }
    /** Set only after mihomo is listening. Returns false if permission missing or write failed. */
    static boolean apply(Context context){
        if(!canWrite(context)){
            AppLog.line("http_proxy skip set (no WRITE_SECURE_SETTINGS)");
            return false;
        }
        try{
            boolean ok=Settings.Global.putString(context.getContentResolver(),KEY,ENDPOINT);
            AppLog.line("http_proxy set "+ENDPOINT+" ok="+ok+" now="+current(context));
            return ok||isOurs(current(context));
        }catch(SecurityException se){
            AppLog.line("http_proxy set SecurityException "+se.getMessage());
            return false;
        }catch(Exception e){
            AppLog.line("http_proxy set "+e.getClass().getSimpleName()+" "+e.getMessage());
            return false;
        }
    }
    /** Best-effort clear. Safe to call when permission is missing. */
    static boolean clear(Context context){
        if(context==null)return false;
        if(!canWrite(context)){
            AppLog.line("http_proxy skip clear (no WRITE_SECURE_SETTINGS) current="+current(context));
            return false;
        }
        try{
            boolean ok=Settings.Global.putString(context.getContentResolver(),KEY,CLEAR_VALUE);
            AppLog.line("http_proxy clear ok="+ok+" now="+current(context));
            return ok||isDisabled(current(context));
        }catch(SecurityException se){
            AppLog.line("http_proxy clear SecurityException "+se.getMessage());
            return false;
        }catch(Exception e){
            AppLog.line("http_proxy clear "+e.getClass().getSimpleName()+" "+e.getMessage());
            return false;
        }
    }
    static String describe(Context context){
        boolean write=canWrite(context);
        String now=current(context);
        String state=isDisabled(now)?"снят":now;
        if(write)return "WRITE_SECURE_SETTINGS есть. Connect ставит "+ENDPOINT+", Disconnect / падение движка снимает. Сейчас http_proxy="+state+".";
        return "Нет WRITE_SECURE_SETTINGS. Один раз в aShell You:\n"+GRANT_CMD
            +"\nИли вручную после Connect:\n"+SET_CMD+"\nСнять:\n"+CLEAR_CMD
            +"\nСейчас http_proxy="+state+".";
    }
}
