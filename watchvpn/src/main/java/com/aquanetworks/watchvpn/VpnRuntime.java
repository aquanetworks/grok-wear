package com.aquanetworks.watchvpn;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Process-wide coordinator. UI and the VPN service publish through here. */
final class VpnRuntime {
    interface Listener { void onChange(Snapshot snapshot); }
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final CopyOnWriteArrayList<Listener> LISTENERS=new CopyOnWriteArrayList<>();
    private static Application APP;
    private static volatile Snapshot LAST=Snapshot.empty();
    private static volatile String PHASE="idle",ERROR="";

    static synchronized void attach(Application app){
        APP=app;
        WORK.execute(()->{
            try{
                Store store=Store.get(app);
                if(store.wantConnected()){
                    AppLog.line("restore wantConnected, starting service");
                    EngineServices.start(app);
                }else{
                    SystemProxy.clear(app);
                    AppLog.line("restore idle — cleared http_proxy");
                }
                publishLocked();
            }catch(Exception e){error(safe(e));}
        });
    }
    static void addListener(Listener listener){LISTENERS.add(listener);listener.onChange(LAST);}
    static void refreshUi(){WORK.execute(()->{try{publishLocked();}catch(Exception e){error(safe(e));}});}
    static void removeListener(Listener listener){LISTENERS.remove(listener);}
    static Snapshot snapshot(){return LAST;}
    static void connect(){
        WORK.execute(()->{
            try{
                Store store=Store.get(APP);
                AppLog.line("connect tap tun="+store.tunMode()+" vpn="+VpnCapability.available(APP)
                    +" writeSecure="+SystemProxy.canWrite(APP)+" nodes="+store.nodes().size());
                if(store.nodes().isEmpty()){error("Import a subscription first.");return;}
                store.setWantConnected(true);
                phase("connecting");
                EngineServices.start(APP);
            }catch(Exception e){error(safe(e));}
        });
    }
    static void disconnect(){
        WORK.execute(()->{
            try{
                Store.get(APP).setWantConnected(false);
                phase("stopping");
                EngineServices.stop(APP);
            }catch(Exception e){error(safe(e));}
        });
    }
    static void toggle(){
        if(LAST.wantConnected||LAST.connected())disconnect();else connect();
    }
    static void importText(String raw){
        WORK.execute(()->{
            try{
                phase("updating");
                String text=raw==null?"":raw.trim();
                if(text.isEmpty()){error("Empty subscription.");return;}
                String url=ImportLinks.extract(text);
                if(!url.isEmpty()){
                    AppLog.line("import subscription UA="+Store.get(APP).userAgentId());
                    SubscriptionImport.Outcome outcome=SubscriptionImport.pull(url,Store.get(APP).userAgentId(),SubClient.HTTP_TRANSPORT);
                    Store.get(APP).importUrl(url,outcome.parsed,outcome.preset.id,outcome.profileTitle,outcome.userinfo);
                    PHASE="idle";
                    ERROR=outcome.placeholdersOnly?outcome.warning:"";
                    if(!outcome.warning.isEmpty())AppLog.line(outcome.warning);
                    TransportSupport.log(outcome.parsed.nodes);
                    AppLog.line("import nodes="+outcome.parsed.nodes.size()+" real="+Placeholders.realCount(outcome.parsed.nodes)+" ua="+outcome.preset.id);
                    publishLocked();
                    if(Store.get(APP).wantConnected())EngineServices.reload(APP);
                    return;
                }
                SubscriptionParser.Result parsed=SubscriptionParser.parse(text);
                Store.get(APP).importUrl("",parsed);
                PHASE="idle";ERROR=parsed.warning;
                publishLocked();
            }catch(Exception e){error(safe(e));}
        });
    }
    static void importIntent(Intent intent){
        if(intent==null)return;
        Uri uri=intent.getData();
        String raw="";
        if(uri!=null){
            String q=uri.getQueryParameter("url");
            if(q==null||q.isEmpty())q=uri.getQueryParameter("config");
            raw=q!=null&&!q.isEmpty()?q:uri.toString();
        }
        if(raw.isEmpty()&&intent.getStringExtra("url")!=null)raw=intent.getStringExtra("url");
        if(!raw.isEmpty())importText(raw);
    }
    static void refreshSubscription(){
        WORK.execute(()->{
            try{
                String url=Store.get(APP).subscriptionUrl();
                if(url.isEmpty()){error("No saved subscription URL. Enter one first.");return;}
                importText(url);
            }catch(Exception e){error(safe(e));}
        });
    }
    static void select(String name){
        WORK.execute(()->{
            try{
                Store.get(APP).select(name);
                AppLog.line("selected "+name);
                if(MihomoEngine.alive()){
                    new MihomoApi(TunConfig.API_PORT,Store.get(APP).secret()).select(TunConfig.GROUP,name);
                }
                if("error".equals(PHASE))PHASE="idle";
                ERROR="";
                publishLocked();
            }catch(Exception e){error(safe(e));}
        });
    }
    static void setUserAgent(String id){
        WORK.execute(()->{
            try{
                Store.get(APP).setUserAgentId(id);
                AppLog.line("UA preset "+id);
                publishLocked();
            }catch(Exception e){error(safe(e));}
        });
    }
    static void setExecFallback(boolean on){
        WORK.execute(()->{
            try{
                Store.get(APP).setExecFallback(on);
                AppLog.line("execFallback "+on);
                publishLocked();
            }catch(Exception e){error(safe(e));}
        });
    }
    static void setLogLevel(String level){
        WORK.execute(()->{
            try{
                Store.get(APP).setLogLevel(level);
                AppLog.line("log-level "+Store.get(APP).logLevel());
                publishLocked();
                if(Store.get(APP).wantConnected())EngineServices.reload(APP);
            }catch(Exception e){error(safe(e));}
        });
    }
    static String nextLogLevel(String current){
        String level=TunConfig.normalizeLogLevel(current);
        if("debug".equals(level))return "info";
        if("info".equals(level))return "warning";
        if("warning".equals(level))return "error";
        return "debug";
    }
    static void setTunMode(boolean on){
        WORK.execute(()->{
            try{
                boolean enable=on&&VpnCapability.available(APP);
                Store.get(APP).setTunMode(enable);
                AppLog.line("tunMode "+enable);
                publishLocked();
            }catch(Exception e){error(safe(e));}
        });
    }
    static void testLatency(String name){
        WORK.execute(()->{
            try{
                phase("testing");
                boolean started=false;
                if(!MihomoEngine.alive()){
                    Store store=Store.get(APP);
                    String yaml=store.yaml(false,false);
                    MihomoEngine.start(APP,yaml,-1);
                    started=true;
                    waitReady(store.secret(),Health.GRACE_MS/Health.BIND_POLL_MS);
                }
                String target=name.isEmpty()?Store.get(APP).selected():name;
                MihomoApi api=new MihomoApi(TunConfig.API_PORT,Store.get(APP).secret());
                if(!target.isEmpty())api.select(TunConfig.GROUP,target);
                MihomoApi.DelayResult result=api.delayResult(target,8000);
                AppLog.line("delay "+result.summary());
                if(!result.ok())throw WatchFail.of(result.error);
                LinkedHashMap<String,Integer> map=new LinkedHashMap<>();
                map.put(target,result.delayMs);
                Store.get(APP).putDelays(mergeDelays(map));
                if(started&&!Store.get(APP).wantConnected())MihomoEngine.stop();
                PHASE=MihomoEngine.alive()&&Store.get(APP).wantConnected()?"connected":"idle";
                ERROR="";
                publishLocked();
            }catch(Exception e){
                try{if(!Store.get(APP).wantConnected())MihomoEngine.stop();}catch(Exception ignored){}
                error(safe(e));
            }
        });
    }
    static void onServicePhase(String phase,String error){
        WORK.execute(()->{
            PHASE=phase==null?"idle":phase;
            ERROR=error==null?"":error;
            publishLocked();
        });
    }
    static void consentDenied(){
        AppLog.line("prepare activity canceled — trying establish()");
        connect();
    }
    static void consentMissing(){
        AppLog.line("prepare activity missing — trying establish()");
        connect();
    }
    private static void phase(String phase){PHASE=phase;ERROR="";publishLocked();}
    private static void error(String message){
        PHASE="error";ERROR=message==null?"":message;AppLog.line("error "+ERROR);publishLocked();
    }
    private static void publishLocked(){
        try{
            Store store=Store.get(APP);
            JSONObject root=store.snapshot();
            List<Map<String,Object>> nodes=SubscriptionParser.fromJson(root.optJSONArray("nodes"));
            String selected=TunConfig.selectedName(nodes,root.optString("selected",""));
            ArrayList<Snapshot.ServerRow> rows=new ArrayList<>();
            org.json.JSONObject delays=root.optJSONObject("delays");
            for(Map<String,Object> node:nodes){
                String name=String.valueOf(node.get("name"));
                int delay=delays==null?-1:delays.optInt(name,-1);
                rows.add(new Snapshot.ServerRow(name,String.valueOf(node.getOrDefault("type","")),delay,name.equals(selected)));
            }
            int last=selected.isEmpty()||delays==null?-1:delays.optInt(selected,-1);
            String help=SystemProxy.describe(APP)+"\n"+VpnCapability.describe(APP)+"\n"+SystemProxy.MESSENGER_WARN;
            Snapshot snap=new Snapshot(PHASE,ERROR,selected,ImportLinks.maskUrl(root.optString("subscriptionUrl","")),
                root.optString("kind",""),help,
                root.optBoolean("wantConnected",false),VpnConsent.prepareReady(APP),VpnConsent.appopsAllowed(APP),
                MihomoEngine.alive(),root.optBoolean("ipv6",true),last,rows,
                root.optString("userAgentId",UserAgents.DEFAULT_ID),root.optString("profileTitle",""),
                root.optString("userinfo",""),AppLog.combined(APP),
                SystemProxy.ENDPOINT,SystemProxy.canWrite(APP),SystemProxy.isOurs(SystemProxy.current(APP)),
                VpnCapability.available(APP),root.optBoolean("tunMode",false),store.logLevel());
            LAST=snap;
            MAIN.post(()->{for(Listener l:LISTENERS)l.onChange(snap);});
        }catch(Exception e){
            Snapshot snap=new Snapshot("error",safe(e),"","","","",false,false,false,false,true,-1,java.util.Collections.emptyList(),
                UserAgents.DEFAULT_ID,"","",AppLog.combined(APP),
                SystemProxy.ENDPOINT,false,false,false,false,TunConfig.DEFAULT_LOG_LEVEL);
            LAST=snap;
            MAIN.post(()->{for(Listener l:LISTENERS)l.onChange(snap);});
        }
    }
    private static Map<String,Integer> mergeDelays(Map<String,Integer> extra)throws Exception{
        LinkedHashMap<String,Integer> all=new LinkedHashMap<>();
        org.json.JSONObject existing=Store.get(APP).snapshot().optJSONObject("delays");
        if(existing!=null){
            java.util.Iterator<String> keys=existing.keys();
            while(keys.hasNext()){String k=keys.next();all.put(k,existing.optInt(k,-1));}
        }
        all.putAll(extra);
        return all;
    }
    private static void waitReady(String secret,int ticks)throws Exception{
        MihomoApi api=new MihomoApi(TunConfig.API_PORT,secret);
        for(int i=0;i<ticks;i++){
            if(api.ready())return;
            Thread.sleep(Health.BIND_POLL_MS);
        }
        throw WatchFail.of("VPN engine did not open its control port.");
    }
    static String safe(Exception e){
        if(e==null)return "Something went wrong.";
        String m=e instanceof WatchFail && e.getMessage()!=null?e.getMessage():(e.getClass().getSimpleName()+(e.getMessage()==null?"":": "+e.getMessage()));
        m=AppLog.redact(m);
        return m.length()>500?m.substring(0,500):m;
    }
    static Context app(){return APP;}
}
