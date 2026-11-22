package com.aquanetworks.watchvpn;

import android.content.Context;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Last ~50 app lines plus mihomo log, for the on-watch Log screen. Also mirrors to logcat {@code watchvpn}. */
final class AppLog {
    static final int MAX=50;
    private static final ArrayDeque<String> LINES=new ArrayDeque<>();
    static void line(String raw){
        if(raw==null||raw.isEmpty())return;
        String text=redact(raw);
        if(text.length()>240)text=text.substring(0,240);
        String stamped=System.currentTimeMillis()%100000+" "+text;
        synchronized(LINES){
            LINES.addLast(stamped);
            while(LINES.size()>MAX)LINES.removeFirst();
        }
        try{android.util.Log.i("watchvpn",text);}catch(Throwable ignored){}
    }
    static List<String> lines(){
        synchronized(LINES){return new ArrayList<>(LINES);}
    }
    static String text(){
        StringBuilder out=new StringBuilder();
        for(String line:lines())out.append(line).append('\n');
        return out.toString();
    }
    static String combined(Context context){
        StringBuilder out=new StringBuilder();
        out.append("— app —\n").append(text());
        String mihomo=context==null?"":MihomoEngine.tailLog(context,8000);
        if(!mihomo.isEmpty())out.append("\n— mihomo —\n").append(redact(mihomo));
        String config=ConfigDump.last();
        if(!config.isEmpty())out.append("\n— config —\n").append(config);
        return out.toString().trim();
    }
    static String redact(String raw){
        if(raw==null)return "";
        String text=raw.replaceAll("(?i)https?://\\S+","https://…");
        text=text.replaceAll("(?i)token=[^\\s&]+","token=…");
        text=text.replaceAll("(?i)(uuid|password|pbk|sid)=[^\\s&]+","$1=…");
        return text;
    }
}
