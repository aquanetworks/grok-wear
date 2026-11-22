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
import androidx.core.app.NotificationCompat;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;

/**
 * Foreground mihomo mixed HTTP/SOCKS on {@link SystemProxy#ENDPOINT}.
 * Sets Android {@code http_proxy} only after the listener is up, and always
 * clears it if the engine dies so the watch is not left without internet.
 */
public final class WatchProxyService extends Service {
    static final String ACTION_CONNECT="com.aquanetworks.watchvpn.PROXY_CONNECT";
    static final String ACTION_DISCONNECT="com.aquanetworks.watchvpn.PROXY_DISCONNECT";
    static final String ACTION_RELOAD="com.aquanetworks.watchvpn.PROXY_RELOAD";
    private static final String CHANNEL="watchvpn";
    private static final int NOTIFY=7;
    private static final int HEALTH_FAILS=3;
    private static final int RECONNECT_MAX=5;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private final Runnable watchdog=this::watch;
    private int failures;
    private int healthFails;
    private ConnectivityManager.NetworkCallback nets;

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
                boolean ipv6=store.ipv6();
                String yaml=store.yaml(false,ipv6);
                AppLog.line("proxy spawn mixed="+TunConfig.MIXED_PORT);
                MihomoEngine.start(this,yaml,-1);
                waitReady(store.secret());
                String selected=TunConfig.selectedName(store.nodes(),store.selected());
                new MihomoApi(TunConfig.API_PORT,store.secret()).select(TunConfig.GROUP,selected);
                failures=0;
                healthFails=0;
                if(SystemProxy.canWrite(this)){
                    if(!SystemProxy.apply(this))throw WatchFail.of("Could not set http_proxy. "+SystemProxy.GRANT_CMD);
                }else{
                    AppLog.line("proxy up, set manually: "+SystemProxy.SET_CMD);
                }
                listenNetworks();
                MAIN.removeCallbacks(watchdog);
                MAIN.postDelayed(watchdog,4000);
                String extra=SystemProxy.canWrite(this)?SystemProxy.ENDPOINT:"set "+SystemProxy.SET_CMD;
                notify("Прокси "+SystemProxy.ENDPOINT,selected+" · "+extra);
                String warn=SystemProxy.canWrite(this)?"":("После Connect в aShell You: "+SystemProxy.SET_CMD);
                VpnRuntime.onServicePhase("connected",warn);
            }catch(Exception e){
                MihomoEngine.stop();
                SystemProxy.clear(this);
                String msg=VpnRuntime.safe(e);
                AppLog.line("proxy bringUp failed "+msg);
                VpnRuntime.onServicePhase("error",msg);
                notify("Ошибка",msg);
                scheduleReconnect();
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
    private String shortLog(){
        String d=MihomoEngine.diagnose(this);
        return d.length()>180?d.substring(d.length()-180):d;
    }
    private void watch(){
        try{
            if(!Store.get(this).wantConnected())return;
            if(!MihomoEngine.alive()){
                AppLog.line("watchdog mihomo dead — clearing http_proxy");
                SystemProxy.clear(this);
                VpnRuntime.onServicePhase("connecting","Движок упал, прокси снят. Перезапуск…");
                notify("Прокси снят","mihomo died");
                scheduleReconnect();
                return;
            }
            healthCheck();
            MAIN.postDelayed(watchdog,8000);
        }catch(Exception e){
            AppLog.line("watchdog "+e.getClass().getSimpleName());
            scheduleReconnect();
        }
    }
    private void healthCheck(){
        try{
            Store store=Store.get(this);
            String name=TunConfig.selectedName(store.nodes(),store.selected());
            if(name.isEmpty())return;
            new MihomoApi(TunConfig.API_PORT,store.secret()).delay(name,4000);
            healthFails=0;
        }catch(Exception e){
            healthFails++;
            AppLog.line("health fail "+healthFails+"/"+HEALTH_FAILS+" "+e.getMessage());
            if(healthFails>=HEALTH_FAILS)failSafe("Узел не отвечает. Прокси снят, чтобы часы не остались без интернета.");
        }
    }
    private void failSafe(String reason){
        AppLog.line("failSafe "+reason);
        try{Store.get(this).setWantConnected(false);}catch(Exception ignored){}
        teardown("error",reason,true);
        notify("Прокси снят",reason);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    private void scheduleReconnect(){
        failures++;
        if(failures>RECONNECT_MAX){
            failSafe("Не удалось поднять прокси. http_proxy снят. "+SystemProxy.CLEAR_CMD);
            return;
        }
        long delay=Math.min(30_000L,(1L<<Math.min(failures,5))*1000L);
        MAIN.removeCallbacks(watchdog);
        MAIN.postDelayed(()->{
            try{
                if(Store.get(this).wantConnected())new Thread(this::spawnProxy,"watchvpn-proxy-re").start();
            }catch(Exception ignored){}
        },delay);
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
    private void teardown(String phase,String error,boolean clearProxy){
        MAIN.removeCallbacks(watchdog);
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        if(cm!=null&&nets!=null)try{cm.unregisterNetworkCallback(nets);}catch(Exception ignored){}
        nets=null;
        if(clearProxy)SystemProxy.clear(this);
        MihomoEngine.stop();
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
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        if(cm!=null&&nets!=null)try{cm.unregisterNetworkCallback(nets);}catch(Exception ignored){}
        nets=null;
        super.onDestroy();
    }
    @Override public void onTaskRemoved(Intent rootIntent){
        try{
            if(!Store.get(this).wantConnected()){
                SystemProxy.clear(this);
                MihomoEngine.stop();
            }
        }catch(Exception ignored){}
        super.onTaskRemoved(rootIntent);
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
