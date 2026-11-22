package com.aquanetworks.watchvpn;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * clash://install-config?url=… and watchvpn://import?url=… (including {@code adb shell am start}).
 * String-only so unit tests do not need Android.
 */
final class ImportLinks {
    static String extract(String raw)throws Exception{
        if(raw==null)return "";
        String text=raw.trim();
        if(text.isEmpty())return "";
        if(SubscriptionParser.looksHttpsUrl(text))return SubscriptionParser.normalizeUrl(text);
        if(isImportText(text)){
            String url=queryValue(text,"url");
            if(url.isEmpty())url=queryValue(text,"config");
            if(url.isEmpty())throw WatchFail.of("Import link has no url parameter.");
            return SubscriptionParser.normalizeUrl(url);
        }
        return "";
    }
    static boolean isImportText(String raw){
        if(raw==null)return false;
        String text=raw.trim().toLowerCase(Locale.ROOT);
        return text.startsWith("clash://")||text.startsWith("clashmeta://")
            ||text.startsWith("watchvpn://")||text.startsWith("stash://");
    }
    static boolean isImportScheme(String scheme,String host){
        if(scheme==null)return false;
        scheme=scheme.toLowerCase(Locale.ROOT);
        if(!scheme.equals("clash")&&!scheme.equals("clashmeta")&&!scheme.equals("watchvpn")&&!scheme.equals("stash"))return false;
        if(host==null)host="";
        host=host.toLowerCase(Locale.ROOT);
        return host.equals("install-config")||host.equals("install")||host.equals("import")||host.isEmpty();
    }
    static String queryValue(String raw,String key)throws Exception{
        int q=raw.indexOf('?');
        if(q<0)return "";
        String query=raw.substring(q+1);
        String prefix=key+"=";
        for(String part:query.split("&")){
            if(part.regionMatches(true,0,prefix,0,prefix.length())){
                String value=part.substring(prefix.length());
                int hash=value.indexOf('#');
                if(hash>=0)value=value.substring(0,hash);
                return URLDecoder.decode(value.replace("+","%2B"),StandardCharsets.UTF_8.name()).trim();
            }
        }
        return "";
    }
    static String maskUrl(String url){
        if(url==null||url.isEmpty())return "";
        try{
            java.net.URI uri=java.net.URI.create(url.trim());
            String host=uri.getHost();
            String path=uri.getPath();
            if(host==null)return "https://…";
            if(path==null||path.isEmpty()||path.equals("/"))return uri.getScheme()+"://"+host;
            return uri.getScheme()+"://"+host+"/…";
        }catch(Exception ignored){return "https://…";}
    }
}
