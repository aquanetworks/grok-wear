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
 */
public final class WatchProxyService extends Service {
    static final String ACTION_CONNECT="com.aquanetworks.watchvpn.PROXY_CONNECT";
    static final String ACTION_DISCONNECT="com.aquanetworks.watchvpn.PROXY_DISCONNECT";
    static final String ACTION_RELOAD="com.aquanetworks.watchvpn.PROXY_RELOAD";
    private static final String CHANNEL="watchvpn";
    private static final int NOTIFY=7;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private final Runnable watchdog=this::watch;
    private PowerManager.WakeLock wake;
    private ConnectivityManager.NetworkCallback nets;
    private boolean healthy;

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
        new Thread(this::spawnProxy,"watchvpn-proxy").start();
        return START_STICKY;
    }
    private void spawnProxy(){
        synchronized(this){
            try{
                Store store=Store.get(this);
                if(store.nodes().isEmpty())throw WatchFail.of("Import a subscription first.");
                SystemProxy.clear(this);
                MihomoEngine.stop();
                String yaml=store.yaml(false,store.ipv6());
                AppLog.line("proxy in-process mixed="+TunConfig.MIXED_PORT+" jni="+MihomoJni.loaded());
                MihomoEngine.start(this,yaml,-1);
                waitReady(store.secret());
                String selected=TunConfig.selectedName(store.nodes(),store.selected());
                new MihomoApi(TunConfig.API_PORT,store.secret()).select(TunConfig.GROUP,selected);
                waitMixedPort();
                int delay=healthDelay(store,selected);
                AppLog.line("health delay="+delay+"ms port=open");
                acquireWake();
                listenNetworks();
                MAIN.removeCallbacks(watchdog);
                MAIN.postDelayed(watchdog,Health.POLL_MS);
                healthy=true;
                if(SystemProxy.canWrite(this)){
                    if(!SystemProxy.apply(this))AppLog.line("http_proxy apply failed after health");
                }else{
                    AppLog.line("proxy healthy, set manually after grant: "+SystemProxy.GRANT_CMD);
                }
                notify("Прокси "+SystemProxy.ENDPOINT,selected+" · "+delay+" мс");
                String warn=SystemProxy.canWrite(this)?""
                    :("Сначала: "+SystemProxy.GRANT_CMD+"\nПотом Connect сам ставит http_proxy. Сейчас вручную: "+SystemProxy.SET_CMD);
                VpnRuntime.onServicePhase("connected",warn);
            }catch(Exception e){
                healthy=false;
                MihomoEngine.stop();
                releaseWake();
                SystemProxy.clear(this);
                String msg=Health.failMessage(SystemProxy.canWrite(this),VpnRuntime.safe(e));
                AppLog.line("proxy bringUp failed "+msg);
                VpnRuntime.onServicePhase("error",msg);
                notify("Ошибка",msg);
            }
        }
    }
    private void waitReady(String secret)throws Exception{
        MihomoApi api=new MihomoApi(TunConfig.API_PORT,secret);
        for(int i=0;i<50;i++){
            if(!MihomoEngine.alive())throw WatchFail.of("Proxy engine exited. "+shortLog());
            if(api.ready())return;
            Thread.sleep(150);
        }
        throw WatchFail.of("Proxy engine did not open its control port. "+shortLog());
    }
    private void waitMixedPort()throws Exception{
        for(int i=0;i<40;i++){
            if(Health.mixedPortOpen(400))return;
            if(!MihomoEngine.alive())throw WatchFail.of("Engine died before mixed-port "+TunConfig.MIXED_PORT+" listened.");
            Thread.sleep(150);
        }
        throw WatchFail.of("127.0.0.1:"+TunConfig.MIXED_PORT+" не принимает TCP. "+shortLog());
    }
    private int healthDelay(Store store,String selected)throws Exception{
        try{
            int delay=new MihomoApi(TunConfig.API_PORT,store.secret()).delay(selected,8000);
            if(delay<=0)throw WatchFail.of("Delay API returned no result.");
            return delay;
        }catch(Exception e){
            throw WatchFail.of("Проверка узла не прошла (generate_204). "+e.getMessage());
        }
    }
    private String shortLog(){
        String d=MihomoEngine.diagnose(this);
        return d.length()>180?d.substring(d.length()-180):d;
    }
    private void watch(){
        try{
            if(!Store.get(this).wantConnected())return;
            boolean port=Health.mixedPortOpen(500);
            boolean engine=MihomoEngine.alive();
            if(!engine||!port){
                AppLog.line("watchdog dead engine="+engine+" port="+port);
                failSafe("Порт "+SystemProxy.ENDPOINT+" закрыт или движок не в процессе.");
                return;
            }
            Store store=Store.get(this);
            String name=TunConfig.selectedName(store.nodes(),store.selected());
            try{
                int delay=new MihomoApi(TunConfig.API_PORT,store.secret()).delay(name,8000);
                AppLog.line("watchdog ok delay="+delay);
                healthy=true;
            }catch(Exception e){
                AppLog.line("watchdog delay fail "+e.getMessage());
                failSafe("Проверка узла не прошла.");
                return;
            }
            MAIN.postDelayed(watchdog,Health.POLL_MS);
        }catch(Exception e){
            AppLog.line("watchdog "+e.getClass().getSimpleName());
            failSafe("Watchdog: "+e.getClass().getSimpleName());
        }
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
                MAIN.removeCallbacks(watchdog);
                MAIN.postDelayed(watchdog,1500);
            }
            @Override public void onLost(Network network){}
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
