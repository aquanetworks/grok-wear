package si.zya.grokwear;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Watch VPN reachability + optional GET check for the settings screen. */
final class ProxyRuntime {
    static final class Snapshot {
        final String state,error,endpoint,check;final boolean connected,busy,watchVpnInstalled;
        Snapshot(String state,String error,String endpoint,String check,boolean connected,boolean busy,boolean watchVpnInstalled){
            this.state=state==null?"":state;
            this.error=error==null?"":error;
            this.endpoint=endpoint==null?AppProxy.endpoint():endpoint;
            this.check=check==null?"":check;
            this.connected=connected;this.busy=busy;this.watchVpnInstalled=watchVpnInstalled;
        }
        String line(){
            if(busy)return state.isEmpty()?"Проверка…":state;
            if(connected)return "Прокси: OK ("+endpoint+")";
            return "нет соединения";
        }
    }
    private static final CopyOnWriteArraySet<Runnable> LISTENERS=new CopyOnWriteArraySet<>();
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"grok-ext-proxy");t.setDaemon(true);return t;});
    private static final AtomicInteger EPOCH=new AtomicInteger();
    private static volatile Snapshot current=new Snapshot("off","","127.0.0.1:7890","",false,false,false);
    private static volatile Context app;
    static Snapshot snapshot(){return current;}
    static void addListener(Runnable listener){LISTENERS.add(listener);}
    static void removeListener(Runnable listener){LISTENERS.remove(listener);}
    static void attach(Context context){
        app=context.getApplicationContext();
        AppProxy.attach(app);
        WORKER.execute(ProxyRuntime::refreshNow);
    }
    static void refresh(){WORKER.execute(ProxyRuntime::refreshNow);}
    static void setEndpoint(String host,int port){
        AppProxy.setEndpoint(host,port);
        WORKER.execute(ProxyRuntime::refreshNow);
    }
    static void check(){
        Context context=app;if(context==null)return;int mine=EPOCH.incrementAndGet();
        WORKER.execute(()->{
            publish(new Snapshot("Проверка…",current.error,AppProxy.endpoint(),current.check,AppProxy.ready(),true,installed(context)));
            try{
                String result=pingGrok();
                if(mine!=EPOCH.get())return;
                refreshNow();
                Snapshot s=current;
                publish(new Snapshot(s.state,s.error,s.endpoint,result,s.connected,false,s.watchVpnInstalled));
            }catch(Exception failed){
                if(mine!=EPOCH.get())return;
                String message=AppProxy.isClosed(failed)?AppProxy.OFF:("Проверка: "+safe(failed));
                AppProxy.noteFailure(message);
                AppProxy.log("check FAIL");
                publish(new Snapshot("error",message,AppProxy.endpoint(),message,AppProxy.ready(),false,installed(context)));
            }
        });
    }
    static String pingGrok()throws Exception{
        AppProxy.require();
        OkHttpClient client=HttpClients.tunneled().connectTimeout(5,TimeUnit.SECONDS)
            .readTimeout(8,TimeUnit.SECONDS).writeTimeout(5,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build();
        long started=System.nanoTime();
        Request request=new Request.Builder().url("https://api.x.ai/").get().build();
        try(Response response=client.newCall(request).execute()){
            int ms=(int)Math.max(1,(System.nanoTime()-started)/1_000_000);
            String line="HTTP "+response.code()+" · "+ms+" мс";
            AppProxy.log("check "+line);
            return line;
        }
    }
    static boolean installed(Context context){
        if(context==null)return false;
        try{context.getPackageManager().getPackageInfo(AppProxy.WATCH_VPN,0);return true;}
        catch(PackageManager.NameNotFoundException missing){return false;}
        catch(RuntimeException ignored){return false;}
    }
    static boolean launchWatchVpn(Context context){
        if(context==null)return false;
        try{
            Intent intent=context.getPackageManager().getLaunchIntentForPackage(AppProxy.WATCH_VPN);
            if(intent==null){AppProxy.log("watchvpn launch missing");return false;}
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            AppProxy.log("watchvpn launched");
            return true;
        }catch(RuntimeException failed){
            AppProxy.log("watchvpn launch fail");
            return false;
        }
    }
    private static void refreshNow(){
        Context context=app;
        boolean ok=AppProxy.ready();
        publish(new Snapshot(ok?"on":"off",ok?"":AppProxy.OFF,AppProxy.endpoint(),current.check,ok,false,installed(context)));
    }
    private static String safe(Exception failed){
        if(failed instanceof GrokCore.Failure&&failed.getMessage()!=null&&!failed.getMessage().isEmpty())return failed.getMessage();
        return failed==null?"ошибка":failed.getClass().getSimpleName();
    }
    private static void publish(Snapshot next){
        current=next;
        android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(()->{for(Runnable listener:LISTENERS)listener.run();});
    }
}
