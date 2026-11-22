package com.aquanetworks.watchvpn;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Parses {@code vless://} share links, including Reality (pbk/sid/sni/fp/flow). Synthetic data only in tests. */
final class VlessUri {
    private static final Pattern UUID=Pattern.compile("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    final String name,uuid,host,flow,security,sni,fp,pbk,sid,spx,network,path,hostHeader,serviceName,encryption,headerType,alpn,packetEncoding,mode;
    final int port;
    final Map<String,String> query;
    VlessUri(String name,String uuid,String host,int port,String flow,String security,String sni,String fp,String pbk,String sid,String spx,String network,String path,String hostHeader,String serviceName,String encryption,String headerType,String alpn,String packetEncoding,String mode,Map<String,String> query){
        this.name=name;this.uuid=uuid;this.host=host;this.port=port;this.flow=flow;this.security=security;this.sni=sni;this.fp=fp;this.pbk=pbk;this.sid=sid;this.spx=spx;this.network=network;this.path=path;this.hostHeader=hostHeader;this.serviceName=serviceName;this.encryption=encryption;this.headerType=headerType;this.alpn=alpn;this.packetEncoding=packetEncoding;this.mode=mode;this.query=query;
    }
    boolean reality(){return "reality".equalsIgnoreCase(security);}
    static VlessUri parse(String raw)throws Exception{
        if(raw==null)throw WatchFail.of("Empty VLESS link.");
        String text=raw.trim();
        if(!text.regionMatches(true,0,"vless://",0,8))throw WatchFail.of("Not a VLESS link.");
        URI uri=URI.create(text);
        if(uri.getRawUserInfo()==null||uri.getHost()==null)throw WatchFail.of("VLESS link is missing a server.");
        String uuid=uri.getRawUserInfo();
        if(!UUID.matcher(uuid).matches())throw WatchFail.of("VLESS UUID is invalid.");
        int port=uri.getPort();if(port<=0)port=443;if(port>65535)throw WatchFail.of("VLESS port is invalid.");
        Map<String,String> query=query(uri.getRawQuery());
        String security=first(query,"security","encryption");
        if(security.isEmpty())security="none";
        String network=first(query,"type","network");if(network.isEmpty())network="tcp";
        String name=fragment(uri.getRawFragment());if(name.isEmpty())name=uri.getHost()+":"+port;
        String encryption=value(query,"encryption");if(encryption.isEmpty())encryption="none";
        return new VlessUri(name,uuid,uri.getHost(),port,value(query,"flow"),security.toLowerCase(Locale.ROOT),
            first(query,"sni","serverName","servername"),value(query,"fp"),first(query,"pbk","publicKey","public-key"),
            first(query,"sid","shortId","short-id"),first(query,"spx","spiderX"),network.toLowerCase(Locale.ROOT),
            first(query,"path","serviceName".equals(network)?"":"path"),first(query,"host","authority"),
            first(query,"serviceName","servicename"),encryption,value(query,"headerType"),value(query,"alpn"),
            first(query,"packetEncoding","packet-encoding"),value(query,"mode"),query);
    }
    Map<String,Object> toClash(){
        LinkedHashMap<String,Object> node=new LinkedHashMap<>();
        node.put("name",name);node.put("type","vless");node.put("server",host);node.put("port",port);
        node.put("uuid",uuid);node.put("network",network);node.put("udp",Boolean.TRUE);
        node.put("encryption",encryption==null||encryption.isEmpty()?"none":encryption);
        if(!flow.isEmpty())node.put("flow",flow);
        boolean tls="tls".equals(security)||"reality".equals(security);
        node.put("tls",Boolean.valueOf(tls));
        if(!sni.isEmpty())node.put("servername",sni);
        if(!fp.isEmpty())node.put("client-fingerprint",fp);
        if(!alpn.isEmpty())node.put("alpn",alpn);
        if(!packetEncoding.isEmpty())node.put("packet-encoding",packetEncoding);
        if("reality".equals(security)){
            LinkedHashMap<String,Object> reality=new LinkedHashMap<>();
            if(!pbk.isEmpty())reality.put("public-key",pbk);
            if(!sid.isEmpty())reality.put("short-id",sid);
            if(!spx.isEmpty())reality.put("spider-x",spx);
            node.put("reality-opts",reality);
        }
        if("ws".equals(network)||"httpupgrade".equals(network)||"h2".equals(network)||"http".equals(network)){
            LinkedHashMap<String,Object> opts=new LinkedHashMap<>();
            if(!path.isEmpty())opts.put("path",path);
            if(!hostHeader.isEmpty()){
                LinkedHashMap<String,Object> headers=new LinkedHashMap<>();headers.put("Host",hostHeader);opts.put("headers",headers);
            }
            if(!opts.isEmpty())node.put(network+"-opts",opts);
        }
        if("grpc".equals(network)){
            LinkedHashMap<String,Object> opts=new LinkedHashMap<>();
            if(!serviceName.isEmpty())opts.put("grpc-service-name",serviceName);
            if(!mode.isEmpty())opts.put("grpc-mode",mode);
            if(!opts.isEmpty())node.put("grpc-opts",opts);
        }
        if(!headerType.isEmpty()&&"tcp".equals(network)){
            LinkedHashMap<String,Object> opts=new LinkedHashMap<>();opts.put("header",headerType);node.put("tcp-opts",opts);
        }
        return node;
    }
    static Map<String,String> query(String raw)throws Exception{
        if(raw==null||raw.isEmpty())return Collections.emptyMap();
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        for(String part:raw.split("&")){
            int eq=part.indexOf('=');String key=eq<0?part:part.substring(0,eq);String val=eq<0?"":part.substring(eq+1);
            if(key.isEmpty())continue;
            out.put(URLDecoder.decode(key,StandardCharsets.UTF_8.name()),URLDecoder.decode(val,StandardCharsets.UTF_8.name()));
        }
        return out;
    }
    private static String value(Map<String,String> query,String key){String v=query.get(key);return v==null?"":v.trim();}
    private static String first(Map<String,String> query,String... keys){for(String key:keys){String v=value(query,key);if(!v.isEmpty())return v;}return "";}
    private static String fragment(String raw)throws Exception{
        if(raw==null||raw.isEmpty())return "";
        return URLDecoder.decode(raw,StandardCharsets.UTF_8.name()).trim();
    }
}
