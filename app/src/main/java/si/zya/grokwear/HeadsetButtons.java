package si.zya.grokwear;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/**
 * Opt-in Wear media-button catcher for HFP / headset keys. Off by default so short play/pause
 * still reaches music apps. AT+BVRA itself is usually delivered as {@code VOICE_COMMAND} to the
 * default assistant; this session is a best-effort extra path.
 */
final class HeadsetButtons {
    static final String ROW="Кнопка наушников";
    static final String ON="Вкл";
    static final String OFF="Выкл";
    static final String HINT="Наушники к часам. Galaxy Buds: удержание = Voice command. Выкл. по умолчанию — иначе может отобрать play/pause у музыки.";
    static final String KEY="headset_buttons";
    static final long LONG_MS=500;
    enum Decision { IGNORE, ASSIST, CONSUME }

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
    static void sync(Context context){
        if(context==null)return;
        Intent intent=new Intent(context,HeadsetButtonService.class);
        try{
            if(enabled(context))context.startForegroundService(intent);
            else context.stopService(intent);
        }catch(RuntimeException failed){AssistLaunch.log("headset-service fail "+failed.getClass().getSimpleName());}
    }

    /**
     * {@code VOICE_ASSIST}/{@code SEARCH}: start a call on key up.
     * {@code HEADSETHOOK}/{@code MEDIA_PLAY_PAUSE}: only a long press starts a call; a short press
     * is ignored so music apps keep play/pause.
     */
    static Decision decide(int keyCode,int action,int repeat,long downAtElapsed,long nowElapsed,boolean longFired){
        if(keyCode==KeyEvent.KEYCODE_VOICE_ASSIST||keyCode==KeyEvent.KEYCODE_SEARCH){
            if(action==KeyEvent.ACTION_UP)return Decision.ASSIST;
            if(action==KeyEvent.ACTION_DOWN)return Decision.CONSUME;
            return Decision.IGNORE;
        }
        if(keyCode!=KeyEvent.KEYCODE_HEADSETHOOK&&keyCode!=KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)return Decision.IGNORE;
        boolean longPress=repeat>=1||(downAtElapsed>0&&nowElapsed-downAtElapsed>=LONG_MS);
        if(action==KeyEvent.ACTION_DOWN){
            if(longPress)return longFired?Decision.CONSUME:Decision.ASSIST;
            return Decision.IGNORE;
        }
        if(action==KeyEvent.ACTION_UP){
            if(longFired)return Decision.CONSUME;
            if(longPress)return Decision.ASSIST;
            return Decision.IGNORE;
        }
        return Decision.IGNORE;
    }
}
