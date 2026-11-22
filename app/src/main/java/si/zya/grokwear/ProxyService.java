package si.zya.grokwear;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;

/** Keeps the in-app mihomo process alive while the VPN is on. Not a system VpnService. */
public final class ProxyService extends Service {
    private static final String CHANNEL="grok_vpn",START="si.zya.grokwear.VPN_START",STOP="si.zya.grokwear.VPN_STOP";
    private static final int NOTIFICATION=17;
    static void ensure(Context context){
        if(!AppProxy.ready())return;
        try{context.startForegroundService(new Intent(context,ProxyService.class).setAction(START));}
        catch(RuntimeException ignored){}
    }
    static void stop(Context context){
        try{context.stopService(new Intent(context,ProxyService.class).setAction(STOP));}catch(RuntimeException ignored){}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
        if(!AppProxy.ready()){stopSelf();return START_NOT_STICKY;}
        NotificationManager manager=getSystemService(NotificationManager.class);
        if(manager!=null)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"VPN",NotificationManager.IMPORTANCE_LOW));
        PendingIntent tap=PendingIntent.getActivity(this,17,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new NotificationCompat.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_reply_status)
            .setContentTitle("Grok Wear VPN")
            .setContentText("App-only tunnel is on")
            .setContentIntent(tap)
            .setOngoing(true)
            .setSilent(true)
            .build();
        try{startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);}
        catch(RuntimeException fallback){startForeground(NOTIFICATION,notification);}
        try{new OngoingActivity.Builder(this,NOTIFICATION,new NotificationCompat.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_reply_status).setContentTitle("VPN").setOngoing(true))
            .setStaticIcon(R.drawable.ic_reply_status)
            .setTouchIntent(tap)
            .setStatus(new Status.Builder().addTemplate("VPN on").build())
            .build().apply(this);}catch(RuntimeException ignored){}
        return START_STICKY;
    }
    @Override public void onDestroy(){
        if(!AppProxy.ready())ProxyEngine.stop();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
