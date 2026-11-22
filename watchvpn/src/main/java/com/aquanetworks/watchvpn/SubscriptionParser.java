package com.aquanetworks.watchvpn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Imports v2RayTun HTTPS bodies, Clash YAML, and V2Ray base64/URI lists into Clash Meta proxy maps.
 * Never logs the subscription URL or node credentials.
 */
final class SubscriptionParser {
    static final int MAX_NODES=200,MAX_BYTES=2*1024*1024;
    static final class Result {
        final String kind;final List<Map<String,Object>> nodes;final String warning;
        Result(String kind,List<Map<String,Object>> nodes,String warning){this.kind=kind;this.nodes=nodes;this.warning=warning==null?"":warning;}
    }
    static Result parse(String raw)throws Exception{
        if(raw==null)throw WatchFail.of("Empty subscription.");
        if(raw.length()>MAX_BYTES)throw WatchFail.of("Subscription is too large.");
        String text=raw.trim();
        if(text.isEmpty())throw WatchFail.of("Empty subscription.");
        if(looksUrlOnly(text))throw WatchFail.of("That is a URL. Import it from VPN settings.");
        String warning="";
        if(ProxyUris.looksBase64(text)&&!looksYaml(text)&&!looksUriList(text)&&!looksJson(text)){
            try{text=ProxyUris.decodeBase64(text).trim();warning="Decoded a V2Ray base64 subscription.";}
            catch(Exception ignored){}
        }
        if(looksJson(text))return json(text,warning);
        if(looksYaml(text))return yaml(text,warning);
        if(looksUriList(text))return new Result("v2ray-uri",unique(ProxyUris.parseList(text)),warning);
        try{return new Result("v2ray-uri",unique(ProxyUris.parseList(text)),warning);}
        catch(Exception uri){
            try{return yaml(text,warning);}
            catch(Exception yaml){throw WatchFail.of("Couldn’t read this subscription. Use Clash YAML or vless:// links.");}
        }
    }
    static boolean looksUrlOnly(String text){
        String lower=text.toLowerCase(Locale.ROOT);
        return (lower.startsWith("https://")||lower.startsWith("http://")||lower.startsWith("sub://"))&&!text.contains("\n")&&!text.contains("proxies:");
    }
    static boolean looksYaml(String text){
        String lower=text.toLowerCase(Locale.ROOT);
        return lower.contains("\nproxies:")||lower.startsWith("proxies:")||lower.contains("proxy-groups:")||lower.contains("mixed-port:");
    }
    static boolean looksUriList(String text){
        String lower=text.toLowerCase(Locale.ROOT);
        return lower.contains("vless://")||lower.contains("vmess://")||lower.contains("trojan://")||lower.contains("ss://")||lower.contains("hysteria2://")||lower.contains("hy2://");
    }
    static boolean looksJson(String text){return text.startsWith("{")||text.startsWith("[");}
    static boolean looksHttpsUrl(String text){
        if(text==null)return false;String t=text.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("https://")&&t.length()<4096&&!t.contains("\n");
    }
    static String normalizeUrl(String raw)throws Exception{
        if(raw==null)throw WatchFail.of("Empty subscription URL.");
        String text=raw.trim();
        if(text.regionMatches(true,0,"sub://",0,6)){
            String decoded=ProxyUris.decodeBase64(text.substring(6)).trim();
            if(!decoded.regionMatches(true,0,"https://",0,8)&&!decoded.regionMatches(true,0,"http://",0,7))
                throw WatchFail.of("Subscription URL is invalid.");
            text=decoded;
        }
        if(text.length()>4096||text.contains("\n")||text.contains("\r"))throw WatchFail.of("Subscription URL is too long.");
        if(text.regionMatches(true,0,"https://",0,8))return text;
        if(text.regionMatches(true,0,"http://",0,7))throw WatchFail.of("Subscription URL must be HTTPS.");
        throw WatchFail.of("Subscription URL must be HTTPS.");
    }
    private static Result yaml(String text,String warning)throws Exception{
        return new Result("clash",unique(ClashYaml.proxies(text)),warning);
    }
    private static Result json(String text,String warning)throws Exception{
        if(text.startsWith("[")){
            JSONArray array=new JSONArray(text);
            ArrayList<Map<String,Object>> nodes=new ArrayList<>();
            for(int i=0;i<array.length();i++){
                Object item=array.get(i);
                if(item instanceof String)nodes.add(ProxyUris.parseOne((String)item));
                else if(item instanceof JSONObject)nodes.add(objectMap((JSONObject)item));
            }
            return new Result("json",unique(nodes),warning);
        }
        JSONObject json=new JSONObject(text);
        if(json.has("proxies")){
            Object proxies=json.get("proxies");
            if(!(proxies instanceof JSONArray))throw WatchFail.of("JSON subscription has no proxies list.");
            ArrayList<Map<String,Object>> nodes=new ArrayList<>();
            JSONArray array=(JSONArray)proxies;
            for(int i=0;i<array.length();i++)nodes.add(objectMap(array.getJSONObject(i)));
            return new Result("clash-json",unique(nodes),warning);
        }
        if(json.has("outbounds"))throw WatchFail.of("sing-box JSON outbounds are not imported. Use Clash YAML or vless:// links.");
        if(json.has("v")&&json.has("add")&&json.has("id"))
            return new Result("vmess",unique(List.of(ProxyUris.parseOne("vmess://"+java.util.Base64.getEncoder().encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))))),warning);
        throw WatchFail.of("JSON subscription is not a Clash or URI list.");
    }
    static List<Map<String,Object>> unique(List<Map<String,Object>> nodes)throws Exception{
        if(nodes.size()>MAX_NODES)throw WatchFail.of("Subscription has too many servers (max "+MAX_NODES+").");
        LinkedHashMap<String,Map<String,Object>> named=new LinkedHashMap<>();
        int i=0;
        for(Map<String,Object> node:nodes){
            i++;
            String name=String.valueOf(node.getOrDefault("name","Server "+i)).trim();
            if(name.isEmpty())name="Server "+i;
            name=name.replace('\n',' ').replace('\r',' ');
            String unique=name;int n=2;while(named.containsKey(unique))unique=name+" ("+n++ +")";
            LinkedHashMap<String,Object> copy=new LinkedHashMap<>(node);copy.put("name",unique);named.put(unique,copy);
        }
        return new ArrayList<>(named.values());
    }
    static JSONArray toJson(List<Map<String,Object>> nodes)throws Exception{
        JSONArray array=new JSONArray();
        for(Map<String,Object> node:nodes)array.put(mapObject(node));
        return array;
    }
    static List<Map<String,Object>> fromJson(JSONArray array)throws Exception{
        ArrayList<Map<String,Object>> nodes=new ArrayList<>();
        if(array==null)return nodes;
        for(int i=0;i<array.length();i++)nodes.add(objectMap(array.getJSONObject(i)));
        return nodes;
    }
    static JSONObject mapObject(Map<String,Object> map)throws Exception{
        JSONObject json=new JSONObject();
        for(Map.Entry<String,Object> e:map.entrySet())json.put(e.getKey(),jsonValue(e.getValue()));
        return json;
    }
    static Map<String,Object> objectMap(JSONObject json)throws Exception{
        LinkedHashMap<String,Object> map=new LinkedHashMap<>();
        java.util.Iterator<String> keys=json.keys();
        while(keys.hasNext()){String key=keys.next();map.put(key,javaValue(json.get(key)));}
        return map;
    }
    private static Object jsonValue(Object value)throws Exception{
        if(value==null)return JSONObject.NULL;
        if(value instanceof Map)return mapObject((Map<String,Object>)value);
        if(value instanceof List){
            JSONArray array=new JSONArray();
            for(Object item:(List<?>)value)array.put(jsonValue(item));
            return array;
        }
        return value;
    }
    private static Object javaValue(Object value)throws Exception{
        if(value==null||value==JSONObject.NULL)return null;
        if(value instanceof JSONObject)return objectMap((JSONObject)value);
        if(value instanceof JSONArray){
            JSONArray array=(JSONArray)value;ArrayList<Object> list=new ArrayList<>();
            for(int i=0;i<array.length();i++)list.add(javaValue(array.get(i)));
            return list;
        }
        return value;
    }
}
