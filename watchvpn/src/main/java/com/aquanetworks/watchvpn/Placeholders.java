package com.aquanetworks.watchvpn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Detects panel "this app is not supported" stub nodes so we can retry with another User-Agent.
 * Synthetic tests use dummy hosts like {@code 0.0.0.0} — never a real subscription.
 */
final class Placeholders {
    static int realCount(List<Map<String,Object>> nodes){
        if(nodes==null)return 0;
        int n=0;
        for(Map<String,Object> node:nodes)if(!isPlaceholder(node))n++;
        return n;
    }
    static boolean onlyPlaceholders(List<Map<String,Object>> nodes){
        return realCount(nodes)==0;
    }
    static List<Map<String,Object>> realNodes(List<Map<String,Object>> nodes){
        ArrayList<Map<String,Object>> out=new ArrayList<>();
        if(nodes==null)return out;
        for(Map<String,Object> node:nodes)if(!isPlaceholder(node))out.add(node);
        return out;
    }
    static boolean isPlaceholder(Map<String,Object> node){
        if(node==null)return true;
        String name=String.valueOf(node.getOrDefault("name","")).toLowerCase(Locale.ROOT);
        String server=String.valueOf(node.getOrDefault("server","")).toLowerCase(Locale.ROOT).trim();
        int port=port(node.get("port"));
        if(nameLooksStub(name))return true;
        if(serverLooksStub(server))return true;
        if(port<=0)return true;
        return false;
    }
    static boolean nameLooksStub(String name){
        if(name==null||name.isEmpty())return true;
        String n=name.toLowerCase(Locale.ROOT);
        return n.contains("не поддерживается")||n.contains("not supported")||n.contains("unsupported")
            ||n.contains("update app")||n.contains("update the app")||n.contains("обновите")
            ||n.contains("приложение не поддерживается")||n.contains("доступ в telegram")
            ||n.contains("access to telegram")||n.contains("telegram only")
            ||n.contains("open in telegram")||n.contains("please update");
    }
    static boolean serverLooksStub(String server){
        if(server==null||server.isEmpty()||server.equals("null"))return true;
        if(server.equals("0.0.0.0")||server.equals("127.0.0.1")||server.equals("::")||server.equals("localhost"))return true;
        if(server.equals("example")||server.equals("example.com")||server.equals("example.org")||server.equals("example.net"))return true;
        return false;
    }
    private static int port(Object value){
        if(value instanceof Number)return ((Number)value).intValue();
        if(value==null)return 0;
        try{return Integer.parseInt(String.valueOf(value).trim());}catch(Exception ignored){return 0;}
    }
}
