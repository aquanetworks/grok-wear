package si.zya.grokwear;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Process;
import android.view.KeyEvent;

/**
 * Opt-in Wear media-button catcher for HFP / headset keys. Off by default so a music app keeps
 * play/pause while it is actually playing. A short press of PLAY / PLAY_PAUSE / HEADSETHOOK /
 * PAUSE / VOICE_ASSIST starts a Grok call when nothing else is playing.
 */
final class HeadsetButtons {
    static final String ROW="Кнопка наушников";
    static final String ON="Вкл";
    static final String OFF="Выкл";
    static final String HINT="Короткое нажатие (play / pause / headset) вызывает Grok, если музыка не играет. По умолчанию выкл.";
    static final String CALL_ROW="Во время звонка";
    static final String CALL_END="завершить звонок";
    static final String CALL_MUTE="микрофон";
    static final String KEY="headset_buttons";
    static final String KEY_CALL="headset_call_action";
    static final String CALL_END_VALUE="end";
    static final String CALL_MUTE_VALUE="mute";
    static final String RECLAIM="si.zya.grokwear.HEADSET_RECLAIM";
    static final long DEBOUNCE_MS=1500;
    enum Outcome { IGNORE, LAUNCH, PASSTHROUGH, DEBOUNCED, END_CALL, MUTE }

    static boolean enabled(Context context){
        if(context==null)return false;
        try{return context.getSharedPreferences(HomeButton.PREFS,Context.MODE_PRIVATE).getBoolean(KEY,false);}
        catch(RuntimeException unavailable){return false;}
    }
    static void setEnabled(Context context,boolean on){
        if(context==null)return;
        try{context.getSharedPreferences(HomeButton.PREFS,Context.MODE_PRIVATE).edit().putBoolean(KEY,on).apply();}
        catch(RuntimeException ignored){}
        AssistLaunch.log("headset-toggle "+(on?"on":"off"));
        sync(context);
    }
    /** Default {@link #CALL_END}: hang up. Otherwise toggle mute. */
    static boolean endCallOnPress(Context context){
        if(context==null)return true;
        try{
            String value=context.getSharedPreferences(HomeButton.PREFS,Context.MODE_PRIVATE).getString(KEY_CALL,CALL_END_VALUE);
            return value==null||value.isEmpty()||CALL_END_VALUE.equals(value);
        }catch(RuntimeException unavailable){return true;}
    }
    static void setEndCallOnPress(Context context,boolean hangUp){
        if(context==null)return;
        try{context.getSharedPreferences(HomeButton.PREFS,Context.MODE_PRIVATE).edit().putString(KEY_CALL,hangUp?CALL_END_VALUE:CALL_MUTE_VALUE).apply();}
        catch(RuntimeException ignored){}
        AssistLaunch.log("headset-call-action "+(hangUp?"end":"mute"));
    }
    static String callActionLabel(boolean hangUp){return hangUp?CALL_END:CALL_MUTE;}
    static void sync(Context context){
        if(context==null)return;
        Intent intent=new Intent(context,HeadsetButtonService.class);
        try{
            if(enabled(context))context.startForegroundService(intent);
            else context.stopService(intent);
        }catch(RuntimeException failed){AssistLaunch.log("headset-service fail "+failed.getClass().getSimpleName());}
    }
    static void reclaim(Context context){
        if(context==null||!enabled(context))return;
        try{context.startForegroundService(new Intent(context,HeadsetButtonService.class).setAction(RECLAIM));}
        catch(RuntimeException failed){AssistLaunch.log("headset-reclaim fail "+failed.getClass().getSimpleName());}
    }

    static boolean isHeadsetKey(int keyCode){
        return keyCode==KeyEvent.KEYCODE_MEDIA_PLAY||keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            ||keyCode==KeyEvent.KEYCODE_HEADSETHOOK||keyCode==KeyEvent.KEYCODE_MEDIA_PAUSE
            ||keyCode==KeyEvent.KEYCODE_VOICE_ASSIST;
    }

    /**
     * First DOWN or UP of a headset key launches (or hangs up). The paired event within
     * {@link #DEBOUNCE_MS} is ignored. Music playing elsewhere is passed through.
     */
    static Outcome decide(int keyCode,int action,boolean musicActive,boolean grokCallActive,boolean hangUp,long lastAt,long now){
        if(!isHeadsetKey(keyCode))return Outcome.IGNORE;
        if(action!=KeyEvent.ACTION_DOWN&&action!=KeyEvent.ACTION_UP)return Outcome.IGNORE;
        if(lastAt>0&&now-lastAt<DEBOUNCE_MS)return Outcome.DEBOUNCED;
        if(grokCallActive)return hangUp?Outcome.END_CALL:Outcome.MUTE;
        if(musicActive)return Outcome.PASSTHROUGH;
        return Outcome.LAUNCH;
    }

    /** Another app is playing media. Our own MediaSession / uid is not "other". */
    static boolean otherMusicPlaying(Context context){
        if(context==null)return false;
        String pkg=context.getPackageName();
        int uid=Process.myUid();
        boolean listed=false,foreign=false;
        try{
            MediaSessionManager manager=context.getSystemService(MediaSessionManager.class);
            if(manager!=null){
                listed=true;
                for(MediaController controller:manager.getActiveSessions(null)){
                    if(controller==null||pkg.equals(controller.getPackageName()))continue;
                    PlaybackState state=controller.getPlaybackState();
                    if(state!=null&&playingState(state.getState()))foreign=true;
                }
            }
        }catch(RuntimeException ignored){listed=false;}
        if(foreign||foreignPlayback(context,uid))return true;
        try{
            AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            if(audio==null||!audio.isMusicActive())return false;
            // isMusicActive can be our PLAYING session. If we listed controllers and none were foreign, it's us.
            return !listed;
        }catch(RuntimeException ignored){return false;}
    }
    static boolean playingState(int state){
        return state==PlaybackState.STATE_PLAYING||state==PlaybackState.STATE_BUFFERING
            ||state==PlaybackState.STATE_FAST_FORWARDING||state==PlaybackState.STATE_REWINDING
            ||state==PlaybackState.STATE_SKIPPING_TO_NEXT||state==PlaybackState.STATE_SKIPPING_TO_PREVIOUS
            ||state==PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM;
    }
    private static boolean foreignPlayback(Context context,int uid){
        if(Build.VERSION.SDK_INT<26)return false;
        try{
            AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            if(audio==null)return false;
            for(android.media.AudioPlaybackConfiguration cfg:audio.getActivePlaybackConfigurations()){
                if(cfg==null)continue;
                int client=clientUid(cfg);
                if(client==uid||client<0)continue;
                AudioAttributes attr=cfg.getAudioAttributes();
                int usage=attr==null?AudioAttributes.USAGE_UNKNOWN:attr.getUsage();
                if(usage==AudioAttributes.USAGE_MEDIA||usage==AudioAttributes.USAGE_GAME
                    ||usage==AudioAttributes.USAGE_UNKNOWN)return true;
            }
        }catch(RuntimeException ignored){}
        return false;
    }
    private static int clientUid(android.media.AudioPlaybackConfiguration cfg){
        try{return (Integer)cfg.getClass().getMethod("getClientUid").invoke(cfg);}
        catch(ReflectiveOperationException|RuntimeException ignored){return -1;}
    }
}
