package com.aquanetworks.watchvpn;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Downloads a v2RayTun / Clash HTTPS subscription. Direct, never logs the URL. */
final class SubClient {
    static final String USER_AGENT="ClashMeta/1.19.34 v2RayTun/2.4.8 WatchVPN/0.1";
    private static final OkHttpClient HTTP=new OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .followRedirects(true).followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
        .writeTimeout(15,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build();
    static String fetch(String url)throws Exception{
        String normalized=SubscriptionParser.normalizeUrl(url);
        Request request=new Request.Builder().url(normalized)
            .header("User-Agent",USER_AGENT)
            .header("Accept","text/plain,text/yaml,application/json,*/*")
            .get().build();
        try(Response response=HTTP.newCall(request).execute()){
            if(response.body()==null)throw WatchFail.of("Empty subscription response.");
            if(response.code()<200||response.code()>=300)throw WatchFail.of("Subscription download failed (HTTP "+response.code()+").");
            String text=response.body().string();
            if(text==null||text.trim().isEmpty())throw WatchFail.of("Empty subscription response.");
            if(text.length()>SubscriptionParser.MAX_BYTES)throw WatchFail.of("Subscription is too large.");
            return text;
        }catch(WatchFail e){throw e;}
        catch(Exception e){throw WatchFail.of("Could not download the subscription. Check Wi‑Fi or LTE.");}
    }
}
