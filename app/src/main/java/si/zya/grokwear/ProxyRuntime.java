package si.zya.grokwear;

import android.content.Context;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Process + fail-closed arming. UI observes {@link #addListener(Runnable)}. */
final class ProxyRuntime {
    static final class Snapshot {
        final String state,error,selected,kind,hostHint;final int servers,delayMs;final boolean connected,busy;final String[] names;
        Snapshot(String state,String error,String selected,String kind,int servers,int delayMs,boolean connected,boolean busy){
            this(state,error,selected,kind,servers,delayMs,connected,busy,new String[0],"");
        }
        Snapshot(String state,String error,String selected,String kind,int servers,int delayMs,boolean connected,boolean busy,String[] names,String hostHint){
            this.state=state;this.error=error==null?"":error;this.selected=selected==null?"":selected;this.kind=kind==null?"":kind;
            this.servers=servers;this.delayMs=delayMs;this.connected=connected;this.busy=busy;
            this.names=names==null?new String[0]:names;this.hostHint=hostHint==null?"":hostHint;
        }
        String line(){
            if(busy)return "VPN connecting…";
            if(connected)return delayMs>0?"VPN on · "+delayMs+" ms":"VPN on · "+selected;
            if(!error.isEmpty())return error;
            if(servers==0)return "VPN off · import a subscription";
            return "VPN off";
        }
    }
    private static final CopyOnWriteArraySet<Runnable> LISTENERS=new CopyOnWriteArraySet<>();
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"grok-vpn");t.setDaemon(true);return t;});
    private static final AtomicInteger EPOCH=new AtomicInteger();
    private static volatile Snapshot current=new Snapshot("off","","","",0,0,false,false);
    private static volatile Context app;
    static Snapshot snapshot(){return current;}
    static void addListener(Runnable listener){LISTENERS.add(listener);}
    static void removeListener(Runnable listener){LISTENERS.remove(listener);}
    static void attach(Context context){
        app=context.getApplicationContext();
        WORKER.execute(()->{
            try{
                ProxyStore store=new ProxyStore(app);refresh(store,current.error,false,current.delayMs);
                if(store.wantConnected()&&store.nodes().size()>0)connectLocked(store);
            }catch(Exception failed){publish(new Snapshot("error",safe(failed),"","",0,0,false,false,new String[0],""));}
        });
    }
    static void importSubscription(String raw){
        Context context=app;if(context==null)return;int mine=EPOCH.incrementAndGet();
        WORKER.execute(()->{
            try{
                publish(busy("Importing…"));
                ProxyStore store=new ProxyStore(context);
                String text=raw==null?"":raw.trim();
                if(SubscriptionParser.looksHttpsUrl(text)||text.toLowerCase(java.util.Locale.ROOT).startsWith("sub://")){
                    String url=SubscriptionParser.normalizeUrl(text);
                    store.importUrl(url,SubscriptionParser.parse(fetch(url)));
                }else store.importBody(text);
                if(mine!=EPOCH.get())return;
                refresh(store,"",false,0);
            }catch(Exception failed){if(mine==EPOCH.get())publish(errorOf(failed));}
        });
    }
    static void select(String name){
        Context context=app;if(context==null)return;int mine=EPOCH.incrementAndGet();
        WORKER.execute(()->{
            try{
                ProxyStore store=new ProxyStore(context);store.select(name);
                if(AppProxy.ready()&&ProxyEngine.alive()){
                    new ProxyApi(AppProxy.API_PORT,store.secret()).select(ClashConfig.GROUP,name);
                }else if(store.wantConnected())connectLocked(store);
                if(mine!=EPOCH.get())return;
                refresh(store,"",AppProxy.ready(),current.delayMs);
            }catch(Exception failed){if(mine==EPOCH.get())publish(errorOf(failed));}
        });
    }
    static void connect(){
        Context context=app;if(context==null)return;int mine=EPOCH.incrementAndGet();
        WORKER.execute(()->{
            try{if(mine!=EPOCH.get())return;connectLocked(new ProxyStore(context));}
            catch(Exception failed){if(mine==EPOCH.get()){AppProxy.disarm();publish(errorOf(failed));}}
        });
    }
    static void disconnect(){
        Context context=app;if(context==null)return;int mine=EPOCH.incrementAndGet();
        WORKER.execute(()->{
            AppProxy.disarm();ProxyEngine.stop();ProxyService.stop(context);
            try{new ProxyStore(context).setWantConnected(false);}catch(Exception ignored){}
            HttpClientsEvict.evict();
            if(mine==EPOCH.get())try{refresh(new ProxyStore(context),"",false,0);}catch(Exception failed){publish(new Snapshot("off","","","",0,0,false,false,new String[0],""));}
        });
    }
    static void test(){
        Context context=app;if(context==null)return;int mine=EPOCH.incrementAndGet();
        WORKER.execute(()->{
            try{publish(busy("Testing…"));int delay=pingSelected();if(mine!=EPOCH.get())return;refresh(new ProxyStore(context),"",true,delay);}
            catch(Exception failed){if(mine==EPOCH.get())publish(errorOf(failed));}
        });
    }
    static int pingSelected()throws Exception{
        if(!AppProxy.ready())throw AppProxy.closed();
        Context context=app;if(context==null)throw new GrokCore.Failure("VPN is not ready.",true);
        ProxyStore store=new ProxyStore(context);
        int delay=new ProxyApi(AppProxy.API_PORT,store.secret()).delay(ClashConfig.selectedName(store.nodes(),store.selected()),5000);
        refresh(store,"",true,delay);return delay;
    }
    static int pingTcp(Map<String,Object> node)throws Exception{
        String host=String.valueOf(node.get("server"));int port=intPort(node.get("port"));
        if(host.isEmpty()||port<=0)throw new GrokCore.Failure("Server address is missing.",true);
        long started=System.nanoTime();
        try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(host,port),4000);}
        return (int)Math.max(1,(System.nanoTime()-started)/1_000_000);
    }
    private static void connectLocked(ProxyStore store)throws Exception{
        List<Map<String,Object>> nodes=store.nodes();
        if(nodes.isEmpty())throw new GrokCore.Failure("Import a subscription first.",true);
        publish(busy("Starting VPN…"));
        store.setWantConnected(true);
        String yaml=store.yaml();
        ProxyEngine.start(app,yaml);
        ProxyApi api=new ProxyApi(AppProxy.API_PORT,store.secret());
        long deadline=System.currentTimeMillis()+12000;
        while(System.currentTimeMillis()<deadline){
            if(!ProxyEngine.alive())throw new GrokCore.Failure("VPN engine stopped. Check the server settings.",true);
            if(api.ready())break;
            Thread.sleep(250);
        }
        if(!api.ready())throw new GrokCore.Failure("VPN engine started but its local port never opened.",true);
        String selected=ClashConfig.selectedName(nodes,store.selected());
        try{api.select(ClashConfig.GROUP,selected);}catch(Exception ignored){}
        AppProxy.arm(AppProxy.HTTP_PORT,AppProxy.SOCKS_PORT);
        HttpClientsEvict.evict();
        int delay=0;
        try{delay=api.delay(selected,4000);}catch(Exception ignored){}
        refresh(store,"",true,delay);
        ProxyService.ensure(app);
    }
    private static void refresh(ProxyStore store,String error,boolean connected,int delayMs)throws Exception{
        JSONObjectView v=JSONObjectView.of(store);
        publish(new Snapshot(connected?"on":(error.isEmpty()?"off":"error"),error,v.selected,v.kind,v.servers,delayMs,connected,false,v.names,v.hostHint));
    }
    private static Snapshot busy(String state){Snapshot s=current;return new Snapshot(state,s.error,s.selected,s.kind,s.servers,s.delayMs,false,true,s.names,s.hostHint);}
    private static Snapshot errorOf(Exception failed){
        Snapshot s=current;String message=safe(failed);AppProxy.noteFailure(message);
        return new Snapshot("error",message,s.selected,s.kind,s.servers,0,false,false,s.names,s.hostHint);
    }
    private static String safe(Exception failed){
        if(failed instanceof GrokCore.Failure&&failed.getMessage()!=null&&!failed.getMessage().isEmpty())return failed.getMessage();
        if(AppProxy.isClosed(failed))return AppProxy.OFF;
        return "VPN failed. Check the subscription and try again.";
    }
    private static String fetch(String url)throws Exception{
        OkHttpClient client=HttpClients.subscription().build();
        Request request=new Request.Builder().url(url)
            .header("User-Agent","ClashMeta/1.19.0")
            .header("Accept","*/*").get().build();
        try(Response response=client.newCall(request).execute()){
            if(response.code()<200||response.code()>=300||response.body()==null)
                throw new GrokCore.Failure("Subscription download failed (HTTP "+response.code()+").",true);
            byte[] body=GrokVault.readBounded(response.body().byteStream(),SubscriptionParser.MAX_BYTES);
            return new String(body,java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private static int intPort(Object value){
        if(value instanceof Number)return ((Number)value).intValue();
        try{return Integer.parseInt(String.valueOf(value));}catch(Exception ignored){return 0;}
    }
    private static void publish(Snapshot next){
        current=next;
        android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(()->{for(Runnable listener:LISTENERS)listener.run();});
    }
    private static final class JSONObjectView {
        final String selected,kind,hostHint;final int servers;final String[] names;
        JSONObjectView(String selected,String kind,int servers,String[] names,String hostHint){
            this.selected=selected;this.kind=kind;this.servers=servers;this.names=names;this.hostHint=hostHint;
        }
        static JSONObjectView of(ProxyStore store)throws Exception{
            org.json.JSONObject root=store.snapshot();
            org.json.JSONArray nodes=root.optJSONArray("nodes");
            String[] names=new String[nodes==null?0:nodes.length()];
            for(int i=0;i<names.length;i++)names[i]=nodes.getJSONObject(i).optString("name","Server "+(i+1));
            return new JSONObjectView(root.optString("selected",""),root.optString("kind",""),names.length,names,hostHint(root.optString("subscriptionUrl","")));
        }
        static String hostHint(String url){
            if(url==null||url.isEmpty())return "";
            try{String host=java.net.URI.create(url).getHost();return host==null?"":host;}catch(Exception ignored){return "";}
        }
    }
}

/** Evicts pooled Grok connections when the tunnel arms or drops. */
final class HttpClientsEvict {
    static void evict(){
        try{BotAccount.networkChanged();}catch(RuntimeException ignored){}
    }
}
