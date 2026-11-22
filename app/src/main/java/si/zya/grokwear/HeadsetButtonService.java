package si.zya.grokwear;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.KeyEvent;
import androidx.core.app.NotificationCompat;

/** Foreground MediaSession so an opt-in headset button can reach Grok Wear while the app is in the background. */
public final class HeadsetButtonService extends Service {
    private static final String CHANNEL="grok_headset";
    private static final int NOTIFICATION=42;
    private MediaSession session;
    private long lastHandledAt;

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!HeadsetButtons.enabled(this)){stopSelf();return START_NOT_STICKY;}
        try{
            startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        }catch(RuntimeException refused){
            AssistLaunch.log("headset-foreground fail");
            stopSelf();
            return START_NOT_STICKY;
        }
        reclaimSession();
        KeyEvent event=keyEvent(intent);
        if(event!=null)handle(event);
        return START_STICKY;
    }
    @Override public void onDestroy(){
        releaseSession();
        super.onDestroy();
    }
    private void reclaimSession(){
        try{
            boolean created=session==null;
            if(created){
                MediaSession next=new MediaSession(this,"grokwear-headset");
                next.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
                next.setPlaybackToLocal(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                next.setMediaButtonReceiver(buttonReceiver());
                next.setCallback(new MediaSession.Callback(){
                    @Override public boolean onMediaButtonEvent(Intent mediaButton){
                        KeyEvent event=keyEvent(mediaButton);
                        if(event==null)return false;
                        return handle(event);
                    }
                    @Override public void onPlay(){handleSynthetic(KeyEvent.KEYCODE_MEDIA_PLAY);}
                    @Override public void onPause(){handleSynthetic(KeyEvent.KEYCODE_MEDIA_PAUSE);}
                    @Override public void onStop(){handleSynthetic(KeyEvent.KEYCODE_MEDIA_PAUSE);}
                });
                session=next;
            }
            session.setActive(true);
            applyPlayingState();
            AssistLaunch.log(created?"headset-session on":"headset-session reclaim");
        }catch(RuntimeException failed){AssistLaunch.log("headset-session fail");}
    }
    private void applyPlayingState(){
        if(session==null)return;
        try{
            session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP)
                .setState(PlaybackState.STATE_PLAYING,0,1f,SystemClock.elapsedRealtime())
                .build());
        }catch(RuntimeException ignored){}
    }
    private PendingIntent buttonReceiver(){
        Intent intent=new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(new ComponentName(this,HeadsetMediaReceiver.class));
        return PendingIntent.getBroadcast(this,3,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
    }
    private static KeyEvent keyEvent(Intent mediaButton){
        if(mediaButton==null)return null;
        try{
            if(android.os.Build.VERSION.SDK_INT>=33)return mediaButton.getParcelableExtra(Intent.EXTRA_KEY_EVENT,KeyEvent.class);
            return mediaButton.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        }catch(RuntimeException ignored){return null;}
    }
    private void handleSynthetic(int keyCode){
        handle(new KeyEvent(KeyEvent.ACTION_DOWN,keyCode));
    }
    private boolean handle(KeyEvent event){
        int key=event.getKeyCode(),action=event.getAction();
        long now=SystemClock.elapsedRealtime();
        AssistLaunch.log("media key="+AssistLaunch.keyCodeName(key)+" action="+(action==KeyEvent.ACTION_DOWN?"DOWN":action==KeyEvent.ACTION_UP?"UP":String.valueOf(action))+" repeat="+event.getRepeatCount()+" source=mediasession");
        boolean music=HeadsetButtons.otherMusicPlaying(this);
        boolean call=VoiceService.snapshot().active;
        HeadsetButtons.Outcome outcome=HeadsetButtons.decide(key,action,music,call,HeadsetButtons.endCallOnPress(this),lastHandledAt,now);
        if(outcome==HeadsetButtons.Outcome.IGNORE)return false;
        if(outcome==HeadsetButtons.Outcome.PASSTHROUGH){AssistLaunch.log("passthrough music-active");return false;}
        if(outcome==HeadsetButtons.Outcome.DEBOUNCED){AssistLaunch.log("debounced");return true;}
        lastHandledAt=now;
        if(outcome==HeadsetButtons.Outcome.END_CALL){
            AssistLaunch.log("end-call");
            VoiceService.stop(this);
            reclaimSession();
            return true;
        }
        if(outcome==HeadsetButtons.Outcome.MUTE){
            AssistLaunch.log("mute");
            VoiceService.toggleMute(this);
            return true;
        }
        AssistLaunch.log("launch");
        AssistLaunch.start(this,"mediasession",key);
        return true;
    }
    private void releaseSession(){
        MediaSession held=session;session=null;
        if(held==null)return;
        try{held.setCallback(null);held.setActive(false);held.release();}catch(RuntimeException ignored){}
        AssistLaunch.log("headset-session off");
    }
    private Notification notification(){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(manager!=null)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Headset button",NotificationManager.IMPORTANCE_MIN));
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class).putExtra("debug_screen",BuildConfig.DEBUG?"settings":null),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_voice_status)
            .setContentTitle(HeadsetButtons.ROW)
            .setContentText("Grok Wear · headset")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }
}
