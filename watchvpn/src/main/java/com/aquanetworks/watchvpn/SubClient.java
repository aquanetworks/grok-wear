package com.aquanetworks.watchvpn;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Downloads a HTTPS subscription. Direct, never logs the URL. */
final class SubClient {
    private static final OkHttpClient HTTP=new OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .followRedirects(true).followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
        .writeTimeout(15,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build();
    static final SubscriptionImport.Transport HTTP_TRANSPORT=SubClient::fetch;
    static SubscriptionImport.Reply fetch(String url,UserAgents.Preset preset)throws Exception{
        String normalized=SubscriptionParser.normalizeUrl(url);
        UserAgents.Preset p=preset==null?UserAgents.byId(UserAgents.DEFAULT_ID):preset;
        Request request=new Request.Builder().url(normalized)
            .header("User-Agent",p.userAgent)
            .header("Accept",p.accept)
            .header("Accept-Language","en-US,en;q=0.9")
            .get().build();
        try(Response response=HTTP.newCall(request).execute()){
            if(response.body()==null)throw WatchFail.of("Empty subscription response.");
            if(response.code()<200||response.code()>=300)throw WatchFail.of("Subscription download failed (HTTP "+response.code()+").");
            String text=response.body().string();
            if(text==null||text.trim().isEmpty())throw WatchFail.of("Empty subscription response.");
            if(text.length()>SubscriptionParser.MAX_BYTES)throw WatchFail.of("Subscription is too large.");
            String title=header(response,"profile-title","Profile-Title");
            String info=header(response,"subscription-userinfo","Subscription-Userinfo");
            return new SubscriptionImport.Reply(text,decodeTitle(title),info);
        }catch(WatchFail e){throw e;}
        catch(Exception e){throw WatchFail.of("Could not download the subscription. Check Wi‑Fi or LTE.");}
    }
    static String header(Response response,String... names){
        for(String name:names){
            String v=response.header(name);
            if(v!=null&&!v.trim().isEmpty())return v.trim();
        }
        return "";
    }
    static String decodeTitle(String raw){
        if(raw==null||raw.isEmpty())return "";
        String text=raw.trim();
        if(text.regionMatches(true,0,"base64:",0,7)){
            try{
                String payload=text.substring(7).trim().replaceAll("\\s+","");
                int pad=(4-payload.length()%4)%4;if(pad>0)payload=payload+"====".substring(0,pad);
                return new String(Base64.getDecoder().decode(payload),StandardCharsets.UTF_8).trim();
            }catch(Exception ignored){return text;}
        }
        return text;
    }
}
