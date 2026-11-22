package com.aquanetworks.watchvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import androidx.core.app.NotificationCompat;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * System-wide TUN via {@link VpnService}. mihomo owns the stack; this process only
 * establishes the interface, passes the fd, and keeps a foreground/always-on lease.
 */
public final class WatchVpnService extends VpnService {
    static final String ACTION_CONNECT="com.aquanetworks.watchvpn.CONNECT";
    static final String ACTION_DISCONNECT="com.aquanetworks.watchvpn.DISCONNECT";
    static final String ACTION_RELOAD="com.aquanetworks.watchvpn.RELOAD";
    static final String ACTION_TOGGLE="com.aquanetworks.watchvpn.TOGGLE";
    private static final String CHANNEL="watchvpn";
    private static final int NOTIFY=7;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private final Runnable watchdog=this::watch;
    private ParcelFileDescriptor tun;
    private boolean ipv6Up;
    private int failures;
    private ConnectivityManager.NetworkCallback nets;

    static void startDirect(Context context){
        Intent i=new Intent(context,WatchVpnService.class).setAction(ACTION_CONNECT);
        context.startForegroundService(i);
    }
    static void stopDirect(Context context){
        try{context.startService(new Intent(context,WatchVpnService.class).setAction(ACTION_DISCONNECT));}
        catch(Exception ignored){}
    }
    static void reload(Context context){
        context.startService(new Intent(context,WatchVpnService.class).setAction(ACTION_RELOAD));
    }
    static Intent toggleIntent(Context context){
        return new Intent(context,WatchVpnService.class).setAction(ACTION_TOGGLE);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        startForeground(NOTIFY,notification("Watch VPN","Starting…"));
        String action=intent==null?ACTION_CONNECT:intent.getAction();
        if(action==null)action=ACTION_CONNECT;
        switch(action){
            case ACTION_DISCONNECT:
                teardown("idle","");
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return START_NOT_STICKY;
            case ACTION_TOGGLE:
                try{
                    if(Store.get(this).wantConnected()&&MihomoEngine.alive()){
                        Store.get(this).setWantConnected(false);
                        teardown("idle","");
                        stopForeground(STOP_FOREGROUND_REMOVE);
                        stopSelf();
                        return START_NOT_STICKY;
                    }
                    Store.get(this).setWantConnected(true);
                }catch(Exception e){VpnRuntime.onServicePhase("error",VpnRuntime.safe(e));}
                return bringUp();
            case ACTION_RELOAD:
                return bringUp();
            default:
                return bringUp();
        }
    }
    private int bringUp(){
        VpnRuntime.onServicePhase("connecting","");
        Intent prepare=VpnConsent.prepareIntent(this);
        AppLog.line("service bringUp prepare="+(prepare==null?"null":"intent")+" appops="+VpnConsent.appopsAllowed(this));
        new Thread(this::establishAndSpawn,"watchvpn-up").start();
        return START_STICKY;
    }
    private void establishAndSpawn(){
        synchronized(this){
            try{
                Store store=Store.get(this);
                if(store.nodes().isEmpty())throw WatchFail.of("Import a subscription first.");
                closeTun();
                MihomoEngine.stop();
                Builder builder=new Builder();
                builder.setSession("Watch VPN");
                builder.setMtu(TunConfig.MTU);
                builder.addAddress(TunConfig.INET4_ADDR,TunConfig.INET4_PREFIX);
                builder.addRoute("0.0.0.0",0);
                builder.addDnsServer(TunConfig.DNS4);
                builder.addDnsServer(TunConfig.DNS4B);
                ipv6Up=false;
                if(store.ipv6()){
                    try{
                        builder.addAddress(TunConfig.INET6_ADDR,TunConfig.INET6_PREFIX);
                        builder.addRoute("::",0);
                        builder.addDnsServer(TunConfig.DNS6);
                        ipv6Up=true;
                    }catch(IllegalArgumentException ignored){ipv6Up=false;}
                }
                try{builder.addDisallowedApplication(getPackageName());}
                catch(PackageManager.NameNotFoundException e){throw WatchFail.of("Could not exclude Watch VPN from its own tunnel.");}
                if(Build.VERSION.SDK_INT>=29)builder.setMetered(false);
                builder.setBlocking(true);
                AppLog.line("establish() calling");
                try{
                    tun=builder.establish();
                }catch(SecurityException se){
                    AppLog.line("establish SecurityException "+se.getMessage());
                    throw WatchFail.of(VpnConsent.establishFailed(this,"SecurityException: "+se.getMessage()));
                }catch(NullPointerException npe){
                    AppLog.line("establish NPE "+npe.getMessage());
                    if(VpnCapability.looksLikeMissingVpn(npe)){
                        try{Store.get(this).setTunMode(false);}catch(Exception ignored){}
                        throw WatchFail.of("VpnService отсутствует (IVpnManager=null). Переход на системный прокси "+SystemProxy.ENDPOINT+". "+npe.getMessage());
                    }
                    throw WatchFail.of(VpnConsent.establishFailed(this,"NullPointerException: "+npe.getMessage()));
                }catch(Exception e){
                    AppLog.line("establish threw "+e.getClass().getSimpleName()+" "+e.getMessage());
                    if(VpnCapability.looksLikeMissingVpn(e)){
                        try{Store.get(this).setTunMode(false);}catch(Exception ignored){}
                        throw WatchFail.of("VpnService отсутствует. Переход на системный прокси. "+e.getMessage());
                    }
                    throw WatchFail.of(VpnConsent.establishFailed(this,e.getClass().getSimpleName()+": "+e.getMessage()));
                }
                if(tun==null){
                    AppLog.line("establish returned null");
                    throw WatchFail.of(VpnConsent.establishFailed(this,"establish() returned null."));
                }
                AppLog.line("establish ok");
                String yaml=store.yaml(true,ipv6Up);
                int fd=tun.detachFd();
                tun=null;
                try{
                    MihomoEngine.start(this,yaml,fd);
                }finally{
                    MihomoEngine.closeFd(fd);
                }
                waitReady(store.secret());
                String selected=TunConfig.selectedName(store.nodes(),store.selected());
                new MihomoApi(TunConfig.API_PORT,store.secret()).select(TunConfig.GROUP,selected);
                failures=0;
                listenNetworks();
                MAIN.removeCallbacks(watchdog);
                MAIN.postDelayed(watchdog,4000);
                notify("Connected",selected);
                VpnRuntime.onServicePhase("connected","");
            }catch(Exception e){
                MihomoEngine.stop();
                closeTun();
                String msg=VpnRuntime.safe(e);
                AppLog.line("bringUp failed "+msg);
                if(VpnCapability.looksLikeMissingVpn(e)||(msg!=null&&msg.contains("IVpnManager"))){
                    try{Store.get(this).setTunMode(false);}catch(Exception ignored){}
                    AppLog.line("TUN missing, falling back to proxy");
                    WatchProxyService.start(this);
                    return;
                }
                VpnRuntime.onServicePhase("error",msg);
                notify("Error",msg);
                if(isConsentFailure(e)){
                    try{Store.get(this).setWantConnected(false);}catch(Exception ignored){}
                }else scheduleReconnect();
            }
        }
    }
    private static boolean isConsentFailure(Exception e){
        if(e instanceof SecurityException)return true;
        String m=e.getMessage();
        return m!=null&&(m.contains("establish()")||m.contains("SecurityException")||m.contains("ACTIVATE_VPN"));
    }
    private void waitReady(String secret)throws Exception{
        MihomoApi api=new MihomoApi(TunConfig.API_PORT,secret);
        for(int i=0;i<50;i++){
            if(!MihomoEngine.alive())throw WatchFail.of("VPN engine exited. "+shortLog());
            if(api.ready())return;
            Thread.sleep(150);
        }
        throw WatchFail.of("VPN engine did not open its control port. "+shortLog());
    }
    private String shortLog(){
        String d=MihomoEngine.diagnose(this);
        return d.length()>180?d.substring(d.length()-180):d;
    }
    private void watch(){
        try{
            if(!Store.get(this).wantConnected())return;
            if(!MihomoEngine.alive()){
                VpnRuntime.onServicePhase("connecting","VPN engine restarted.");
                scheduleReconnect();
                return;
            }
            MAIN.postDelayed(watchdog,5000);
        }catch(Exception e){scheduleReconnect();}
    }
    private void scheduleReconnect(){
        failures++;
        long delay=Math.min(30_000L,(1L<<Math.min(failures,5))*1000L);
        MAIN.removeCallbacks(watchdog);
        MAIN.postDelayed(()->{
            try{
                if(Store.get(this).wantConnected())new Thread(this::establishAndSpawn,"watchvpn-re").start();
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
    private void teardown(String phase,String error){
        MAIN.removeCallbacks(watchdog);
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        if(cm!=null&&nets!=null)try{cm.unregisterNetworkCallback(nets);}catch(Exception ignored){}
        nets=null;
        MihomoEngine.stop();
        closeTun();
        VpnRuntime.onServicePhase(phase,error);
    }
    private synchronized void closeTun(){
        if(tun!=null){try{tun.close();}catch(Exception ignored){}tun=null;}
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
    @Override public void onRevoke(){
        try{Store.get(this).setWantConnected(false);}catch(Exception ignored){}
        teardown("error","The system revoked the VPN.");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onDestroy(){
        MAIN.removeCallbacks(watchdog);
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        if(cm!=null&&nets!=null)try{cm.unregisterNetworkCallback(nets);}catch(Exception ignored){}
        nets=null;
        MihomoEngine.stop();
        closeTun();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return super.onBind(intent);}
    @SuppressWarnings("unused")
    private static boolean isV4(String host){
        try{return InetAddress.getByName(host) instanceof Inet4Address;}catch(Exception e){return false;}
    }
    @SuppressWarnings("unused")
    private static boolean isV6(String host){
        try{return InetAddress.getByName(host) instanceof Inet6Address;}catch(Exception e){return false;}
    }
}
