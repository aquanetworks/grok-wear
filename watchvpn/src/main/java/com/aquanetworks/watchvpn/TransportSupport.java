package com.aquanetworks.watchvpn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Detects transports that need extra mapping or an on-watch warning. */
final class TransportSupport {
    static boolean isXhttp(String network){
        if(network==null)return false;
        String n=network.toLowerCase(Locale.ROOT);
        return "xhttp".equals(n)||"splithttp".equals(n);
    }
    static List<String> notes(List<Map<String,Object>> nodes){
        ArrayList<String> out=new ArrayList<>();
        if(nodes==null)return out;
        for(Map<String,Object> node:nodes){
            String name=String.valueOf(node.getOrDefault("name",""));
            String net=String.valueOf(node.getOrDefault("network",""));
            if(isXhttp(net)){
                out.add("xhttp on "+name+" — mapped as network=xhttp + xhttp-opts (mihomo 1.19.34). If hub.Parse rejects it, Журнал/logcat tag mihomo will show the exact error.");
            }
        }
        return out;
    }
    static void log(List<Map<String,Object>> nodes){
        for(String note:notes(nodes))AppLog.line(note);
    }
}
