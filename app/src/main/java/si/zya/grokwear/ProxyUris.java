package si.zya.grokwear;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Converts common v2ray share URIs into Clash Meta proxy maps. */
final class ProxyUris {
    static List<Map<String,Object>> parseList(String text)throws Exception{
        ArrayList<Map<String,Object>> out=new ArrayList<>();
        for(String line:text.replace("\r","\n").split("\n")){
            String item=line.trim();if(item.isEmpty()||item.startsWith("#"))continue;
            out.add(parseOne(item));
        }
        if(out.isEmpty())throw new GrokCore.Failure("No proxy links found.",true);
        return out;
    }
    static Map<String,Object> parseOne(String raw)throws Exception{
        String text=raw.trim();int scheme=text.indexOf("://");
        if(scheme<=0)throw new GrokCore.Failure("Not a proxy link.",true);
        String kind=text.substring(0,scheme).toLowerCase(java.util.Locale.ROOT);
        switch(kind){
            case "vless":return VlessUri.parse(text).toClash();
            case "trojan":return trojan(text);
            case "ss":return shadowsocks(text);
            case "vmess":return vmess(text);
            case "hysteria2":
            case "hy2":return hysteria2(text);
            default:throw new GrokCore.Failure("Unsupported proxy link ("+kind+").",true);
        }
    }
    static String decodeBase64(String raw)throws Exception{
        String compact=raw.replaceAll("\\s+","");
        int pad=(4-compact.length()%4)%4;if(pad>0)compact=compact+"====".substring(0,pad);
        try{return new String(Base64.getUrlDecoder().decode(compact),StandardCharsets.UTF_8);}
        catch(IllegalArgumentException urlSafe){
            return new String(Base64.getDecoder().decode(compact),StandardCharsets.UTF_8);
        }
    }
    static boolean looksBase64(String raw){
        String compact=raw.replaceAll("\\s+","");
        return compact.length()>=16&&compact.matches("[A-Za-z0-9+/=_\\-]+")&&!compact.contains("://");
    }
    private static Map<String,Object> trojan(String raw)throws Exception{
        URI uri=URI.create(raw.trim());
        if(uri.getRawUserInfo()==null||uri.getHost()==null)throw new GrokCore.Failure("Trojan link is missing a server.",true);
        int port=uri.getPort();if(port<=0)port=443;
        Map<String,String> q=VlessUri.query(uri.getRawQuery());
        LinkedHashMap<String,Object> node=new LinkedHashMap<>();
        node.put("name",name(uri,uri.getHost()+":"+port));
        node.put("type","trojan");node.put("server",uri.getHost());node.put("port",port);
        node.put("password",uri.getRawUserInfo());node.put("udp",Boolean.TRUE);
        String sni=first(q,"sni","peer");if(!sni.isEmpty())node.put("sni",sni);
        if("1".equals(q.get("allowInsecure"))||"true".equalsIgnoreCase(q.get("allowInsecure")))node.put("skip-cert-verify",Boolean.TRUE);
        String fp=q.get("fp");if(fp!=null&&!fp.isEmpty())node.put("client-fingerprint",fp);
        String net=first(q,"type","network");if(!net.isEmpty()&&!"tcp".equals(net))node.put("network",net);
        return node;
    }
    private static Map<String,Object> shadowsocks(String raw)throws Exception{
        String text=raw.trim().substring(5);
        String name="";int hash=text.lastIndexOf('#');
        if(hash>=0){name=URLDecoder.decode(text.substring(hash+1),StandardCharsets.UTF_8.name()).trim();text=text.substring(0,hash);}
        String method,password,host;int port;
        if(text.contains("@")){
            int at=text.lastIndexOf('@');String user=text.substring(0,at);String server=text.substring(at+1);
            if(!user.contains(":"))user=decodeBase64(user);
            int colon=user.indexOf(':');if(colon<0)throw new GrokCore.Failure("Shadowsocks link is invalid.",true);
            method=user.substring(0,colon);password=user.substring(colon+1);
            URI uri=URI.create("ss://x@"+server);host=uri.getHost();port=uri.getPort();
        }else{
            String decoded=decodeBase64(text);int at=decoded.lastIndexOf('@');if(at<0)throw new GrokCore.Failure("Shadowsocks link is invalid.",true);
            String user=decoded.substring(0,at);String server=decoded.substring(at+1);
            int colon=user.indexOf(':');if(colon<0)throw new GrokCore.Failure("Shadowsocks link is invalid.",true);
            method=user.substring(0,colon);password=user.substring(colon+1);
            URI uri=URI.create("ss://x@"+server);host=uri.getHost();port=uri.getPort();
        }
        if(host==null||port<=0)throw new GrokCore.Failure("Shadowsocks link is missing a server.",true);
        LinkedHashMap<String,Object> node=new LinkedHashMap<>();
        node.put("name",name.isEmpty()?host+":"+port:name);
        node.put("type","ss");node.put("server",host);node.put("port",port);
        node.put("cipher",method);node.put("password",password);node.put("udp",Boolean.TRUE);
        return node;
    }
    private static Map<String,Object> vmess(String raw)throws Exception{
        String payload=raw.trim().substring(8);
        JSONObject json=new JSONObject(decodeBase64(payload));
        String host=json.optString("add");int port=json.optInt("port",0);if(port<=0)try{port=Integer.parseInt(json.optString("port","0"));}catch(NumberFormatException ignored){}
        if(host.isEmpty()||port<=0)throw new GrokCore.Failure("VMess link is missing a server.",true);
        LinkedHashMap<String,Object> node=new LinkedHashMap<>();
        String name=json.optString("ps");if(name.isEmpty())name=host+":"+port;
        node.put("name",name);node.put("type","vmess");node.put("server",host);node.put("port",port);
        node.put("uuid",json.optString("id"));node.put("alterId",json.optInt("aid",0));
        node.put("cipher",json.optString("scy","auto"));node.put("udp",Boolean.TRUE);
        String net=json.optString("net","tcp");if(!net.isEmpty())node.put("network",net);
        if("tls".equalsIgnoreCase(json.optString("tls")))node.put("tls",Boolean.TRUE);
        String sni=json.optString("sni");if(sni.isEmpty())sni=json.optString("host");if(!sni.isEmpty())node.put("servername",sni);
        return node;
    }
    private static Map<String,Object> hysteria2(String raw)throws Exception{
        URI uri=URI.create(raw.trim());
        if(uri.getHost()==null)throw new GrokCore.Failure("Hysteria2 link is missing a server.",true);
        int port=uri.getPort();if(port<=0)port=443;
        Map<String,String> q=VlessUri.query(uri.getRawQuery());
        LinkedHashMap<String,Object> node=new LinkedHashMap<>();
        node.put("name",name(uri,uri.getHost()+":"+port));
        node.put("type","hysteria2");node.put("server",uri.getHost());node.put("port",port);
        String password=uri.getRawUserInfo();if(password==null||password.isEmpty())password=first(q,"auth","password");
        node.put("password",password==null?"":password);
        String sni=first(q,"sni","peer");if(!sni.isEmpty())node.put("sni",sni);
        if("1".equals(q.get("insecure"))||"true".equalsIgnoreCase(q.get("insecure")))node.put("skip-cert-verify",Boolean.TRUE);
        return node;
    }
    private static String name(URI uri,String fallback)throws Exception{
        if(uri.getRawFragment()==null||uri.getRawFragment().isEmpty())return fallback;
        return URLDecoder.decode(uri.getRawFragment(),StandardCharsets.UTF_8.name()).trim();
    }
    private static String first(Map<String,String> q,String... keys){
        for(String key:keys){String v=q.get(key);if(v!=null&&!v.trim().isEmpty())return v.trim();}
        return "";
    }
}
