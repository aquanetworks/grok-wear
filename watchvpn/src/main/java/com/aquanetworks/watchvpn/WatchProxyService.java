package com.aquanetworks.watchvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;

/**
 * Foreground in-process mihomo mixed HTTP/SOCKS on {@link SystemProxy#ENDPOINT}.
 * Connected is shown only after 7890 accepts TCP and a node delay check succeeds.
 * Startup grace (30 s) never failSafes; later health needs {@link Health#FAIL_STREAK} misses.
 */
public final class WatchProxyService extends Service {
    static final String ACTION_CONNECT="com.aquanetworks.watchvpn.PROXY_CONNECT";
    static final String ACTION_DISCONNECT="com.aquanetworks.watchvpn.PROXY_DISCONNECT";
    static final String ACTION_RELOAD="com.aquanetworks.watchvpn.PROXY_RELOAD";
    private static final String CHANNEL="watchvpn";
    private static final int NOTIFY=7;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService WATCH_EX=java.util.concurrent.Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"watchvpn-watch");t.setDaemon(true);return t;
    });
    private final Runnable watchdog=()->WATCH_EX.execute(this::watch);
    private PowerManager.WakeLock wake;
    private ConnectivityManager.NetworkCallback nets;
    private boolean healthy;
    private long startedAt;
    private int failStreak;
    private String lastHealthError="";

    static void start(Context context){
        Intent i=new Intent(context,WatchProxyService.class).setAction(ACTION_CONNECT);
        context.startForegroundService(i);
    }
    static void stop(Context context){
        try{context.startService(new Intent(context,WatchProxyService.class).setAction(ACTION_DISCONNECT));}
        catch(Exception e){SystemProxy.clear(context);}
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        startForeground(NOTIFY,notification("Watch VPN","Starting…"));
        String action=intent==null?ACTION_CONNECT:intent.getAction();
        if(action==null)action=ACTION_CONNECT;
        switch(action){
            case ACTION_DISCONNECT:
                teardown("idle","",true);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return START_NOT_STICKY;
            case ACTION_RELOAD:
                return bringUp();
            default:
                return bringUp();
        }
    }
    private int bringUp(){
        VpnRuntime.onServicePhase("connecting","");
        new Thread(this::startInProcess,"watchvpn-inprocess").start();
        return START_STICKY;
    }
    private void startInProcess(){
        synchronized(this){
            try{
                Store store=Store.get(this);
                if(store.nodes().isEmpty())throw WatchFail.of("Import a subscription first.");
                SystemProxy.clear(this);
                MihomoEngine.stop();
                AppLog.line("in-process start jni.loaded="+MihomoJni.loaded()
                    +(MihomoJni.loaded()?"":" UnsatisfiedLinkError="+MihomoJni.loadError())
                    +" mixed="+TunConfig.MIXED_PORT);
                if(!MihomoJni.loaded()){
                    throw WatchFail.of("System.loadLibrary(mihomo-jni) failed: UnsatisfiedLinkError "
                        +(MihomoJni.loadError()==null?"":MihomoJni.loadError()));
                }
                String yaml=store.yaml(false,store.ipv6());
                TransportSupport.log(store.nodes());
                MihomoEngine.start(this,yaml,-1);
                startedAt=System.currentTimeMillis();
                failStreak=0;
                lastHealthError="";
                healthy=false;
                acquireWake();
                listenNetworks();
                MAIN.removeCallbacks(watchdog);
                MAIN.post(watchdog);
                notify("Запуск mihomo","ждём 127.0.0.1:"+TunConfig.MIXED_PORT+" до "+(Health.GRACE_MS/1000)+" с");
                VpnRuntime.onServicePhase("connecting","Ожидание порта "+SystemProxy.ENDPOINT+" (до "+(Health.GRACE_MS/1000)+" с).");
            }catch(Exception e){
                healthy=false;
                MihomoEngine.stop();
                releaseWake();
                SystemProxy.clear(this);
                String msg=Health.failMessage(SystemProxy.canWrite(this),VpnRuntime.safe(e));
                AppLog.line("in-process bringUp failed "+msg);
                VpnRuntime.onServicePhase("error",msg);
                notify("Ошибка",msg);
            }
        }
    }
    private void watch(){
        try{
            if(!Store.get(this).wantConnected())return;
            long now=System.currentTimeMillis();
            boolean grace=Health.inGrace(startedAt,now);
            boolean port=Health.mixedPortOpen(500);
            boolean engine=MihomoEngine.alive();
            int next=grace?Health.BIND_POLL_MS:Health.POLL_MS;
            if(grace){
                AppLog.line("grace ms="+(now-startedAt)+"/" +Health.GRACE_MS+" engine="+engine+" port="+port+" jni="+MihomoJni.running());
                if(port&&engine)tryPromote();
                MAIN.postDelayed(watchdog,next);
                return;
            }
            if(!engine||!port){
                failStreak++;
                AppLog.line("watchdog streak="+failStreak+"/"+Health.FAIL_STREAK+" engine="+engine+" port="+port);
                if(Health.shouldFailSafe(false,failStreak)){
                    failSafe("Порт "+SystemProxy.ENDPOINT+" закрыт или движок не в процессе.");
                    return;
                }
                MAIN.postDelayed(watchdog,next);
                return;
            }
            if(!tryPromote()){
                failStreak++;
                AppLog.line("watchdog delay streak="+failStreak+"/"+Health.FAIL_STREAK+" "+lastHealthError);
                if(Health.shouldFailSafe(false,failStreak)){
                    failSafe(lastHealthError.isEmpty()?"Проверка узла не прошла.":lastHealthError);
                    return;
                }
            }
            MAIN.postDelayed(watchdog,next);
        }catch(Exception e){
            AppLog.line("watchdog "+e.getClass().getSimpleName()+" "+e.getMessage());
            boolean grace=Health.inGrace(startedAt,System.currentTimeMillis());
            if(grace){
                MAIN.postDelayed(watchdog,Health.BIND_POLL_MS);
                return;
            }
            failStreak++;
            if(Health.shouldFailSafe(false,failStreak))failSafe("Watchdog: "+e.getClass().getSimpleName());
            else MAIN.postDelayed(watchdog,Health.POLL_MS);
        }
    }
    private boolean tryPromote(){
        try{
            Store store=Store.get(this);
            String name=TunConfig.selectedName(store.nodes(),store.selected());
            MihomoApi.DelayResult result=new MihomoApi(TunConfig.API_PORT,store.secret()).delayResult(name,8000);
            AppLog.line("delay "+result.summary());
            if(!result.ok()){
                lastHealthError=result.error==null?"Проверка узла не прошла.":result.error;
                return false;
            }
            failStreak=0;
            if(!healthy){
                healthy=true;
                if(SystemProxy.canWrite(this)){
                    if(!SystemProxy.apply(this))AppLog.line("http_proxy apply failed after health");
                }else{
                    AppLog.line("proxy healthy, set manually after grant: "+SystemProxy.GRANT_CMD);
                }
                notify("Прокси "+SystemProxy.ENDPOINT,name+" · "+result.delayMs+" мс");
                String warn=SystemProxy.canWrite(this)?""
                    :("Сначала: "+SystemProxy.GRANT_CMD+"\nПотом Connect сам ставит http_proxy. Сейчас вручную: "+SystemProxy.SET_CMD);
                VpnRuntime.onServicePhase("connected",warn);
                pingAllAsync(store,name);
            }
            return true;
        }catch(Exception e){
            lastHealthError=VpnRuntime.safe(e);
            AppLog.line("delay FAIL "+lastHealthError);
            return false;
        }
    }
    private void pingAllAsync(Store store,String skip){
        new Thread(()->{
            try{
                MihomoApi api=new MihomoApi(TunConfig.API_PORT,store.secret());
                for(java.util.Map<String,Object> node:store.nodes()){
                    String name=String.valueOf(node.get("name"));
                    if(name.equals(skip))continue;
                    try{
                        AppLog.line("delay "+api.delayResult(name,8000).summary());
                    }catch(Exception e){
                        AppLog.line("delay "+name+" FAIL "+VpnRuntime.safe(e));
                    }
                }
            }catch(Exception e){AppLog.line("delay-all "+e.getClass().getSimpleName());}
        },"watchvpn-delay").start();
    }
    private void failSafe(String reason){
        healthy=false;
        AppLog.line("failSafe "+reason);
        boolean canWrite=SystemProxy.canWrite(this);
        SystemProxy.clear(this);
        try{Store.get(this).setWantConnected(false);}catch(Exception ignored){}
        String msg=Health.failMessage(canWrite,reason);
        teardown("error",msg,false);
        notify("Прокси снят",msg);
        MAIN.removeCallbacks(watchdog);
    }
    private void listenNetworks(){
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        if(cm==null)return;
        if(nets!=null)try{cm.unregisterNetworkCallback(nets);}catch(Exception ignored){}
        nets=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network network){
                AppLog.line("network available (no failSafe from callback)");
            }
            @Override public void onLost(Network network){AppLog.line("network lost");}
            @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps){}
        };
        try{
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),nets);
        }catch(Exception ignored){nets=null;}
    }
    private void acquireWake(){
        try{
            if(wake!=null&&wake.isHeld())return;
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
            if(pm==null)return;
            wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"watchvpn:proxy");
            wake.setReferenceCounted(false);
            wake.acquire();
            AppLog.line("wakelock acquired");
        }catch(Exception e){AppLog.line("wakelock "+e.getClass().getSimpleName());}
    }
    private void releaseWake(){
        try{
            if(wake!=null&&wake.isHeld())wake.release();
        }catch(Exception ignored){}
        wake=null;
    }
    private void teardown(String phase,String error,boolean clearProxy){
        MAIN.removeCallbacks(watchdog);
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        if(cm!=null&&nets!=null)try{cm.unregisterNetworkCallback(nets);}catch(Exception ignored){}
        nets=null;
        if(clearProxy)SystemProxy.clear(this);
        MihomoEngine.stop();
        releaseWake();
        healthy=false;
        failStreak=0;
        VpnRuntime.onServicePhase(phase,error);
    }
    private void notify(String title,String text){
        startForeground(NOTIFY,notification(title,text));
    }
    private Notification notification(String title,String text){
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null&&nm.getNotificationChannel(CHANNEL)==null){
            NotificationChannel ch=new NotificationChannel(CHANNEL,"Watch VPN",NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open=PendingIntent.getActivity(this,1,
            new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder=new NotificationCompat.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        try{
            new OngoingActivity.Builder(this,NOTIFY,builder)
                .setStaticIcon(R.drawable.ic_vpn)
                .setTouchIntent(open)
                .setStatus(new Status.Builder().addTemplate(title).build())
                .build()
                .apply(this);
        }catch(Exception ignored){}
        return builder.build();
    }
    @Override public void onDestroy(){
        MAIN.removeCallbacks(watchdog);
        SystemProxy.clear(this);
        MihomoEngine.stop();
        releaseWake();
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        if(cm!=null&&nets!=null)try{cm.unregisterNetworkCallback(nets);}catch(Exception ignored){}
        nets=null;
        super.onDestroy();
    }
    @Override public void onTaskRemoved(Intent rootIntent){
        try{
            if(!Store.get(this).wantConnected()||!healthy){
                SystemProxy.clear(this);
                MihomoEngine.stop();
                releaseWake();
            }
        }catch(Exception ignored){}
        super.onTaskRemoved(rootIntent);
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
