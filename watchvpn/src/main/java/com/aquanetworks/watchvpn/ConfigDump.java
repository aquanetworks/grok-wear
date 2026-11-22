package com.aquanetworks.watchvpn;

/** Redacts secrets from generated Clash YAML for logcat {@code watchvpn-config}. */
final class ConfigDump {
    static final String TAG="watchvpn-config";
    private static volatile String LAST="";
    static String redact(String yaml){
        if(yaml==null)return "";
        String text=yaml;
        text=text.replaceAll("(?im)^(\\s*(uuid|password|secret|private-key|token)\\s*:\\s*).+$","$1***");
        text=text.replaceAll("(?im)^(\\s*(public-key|pbk|short-id|sid)\\s*:\\s*).+$","$1***");
        return text;
    }
    static String last(){return LAST==null?"":LAST;}
    static void log(String yaml){
        String redacted=redact(yaml);
        LAST=redacted;
        try{
            for(String line:redacted.split("\n",-1)){
                android.util.Log.i(TAG,line);
            }
        }catch(Throwable ignored){}
        AppLog.line("config dumped to logcat "+TAG+" ("+redacted.length()+"b)");
    }
}
