package si.zya.grokwear;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;

/**
 * Shared entry for Home, assistant, and headset buttons. Logs every trigger to {@code grokwear-assist}.
 * Actual Bot resolution stays in {@link MainActivity#assist}.
 */
final class AssistLaunch {
    static final String TAG="grokwear-assist";
    static final String EXTRA_SOURCE="assist_source";
    static final String VOICE_COMMAND=Intent.ACTION_VOICE_COMMAND;
    static final String HANDS_FREE=RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE;
    static final String VOICE_ASSIST="android.intent.action.VOICE_ASSIST";

    static boolean isAssistAction(String action){
        if(action==null||action.isEmpty())return false;
        return Intent.ACTION_ASSIST.equals(action)||VOICE_ASSIST.equals(action)
            ||VOICE_COMMAND.equals(action)||HANDS_FREE.equals(action);
    }
    static void log(String line){
        if(line==null||line.isEmpty())return;
        String text=line.length()>220?line.substring(0,220):line;
        try{android.util.Log.i(TAG,text);}catch(Throwable ignored){}
    }
    static void note(Intent intent,String where){
        String action=intent==null?null:intent.getAction();
        String source=intent==null?null:intent.getStringExtra(EXTRA_SOURCE);
        int key=intent==null?0:intent.getIntExtra("assist_keycode",0);
        String extra=key==0?"":(" key="+keyCodeName(key));
        log((where==null?"assist":where)+" action="+(action==null?"none":action)
            +" source="+(source==null||source.isEmpty()?"intent":source)+extra);
    }
    static void start(Context context,String source){
        start(context,source,0);
    }
    static void start(Context context,String source,int keyCode){
        if(context==null)return;
        if(VoiceService.snapshot().active){log("skip source="+source+" (call active)");return;}
        log("launch source="+(source==null?"?":source)+(keyCode==0?"":(" key="+keyCodeName(keyCode))));
        try{
            Intent intent=new Intent(context,MainActivity.class)
                .setAction(Intent.ACTION_ASSIST)
                .putExtra(EXTRA_SOURCE,source==null?"":source)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            if(keyCode!=0)intent.putExtra("assist_keycode",keyCode);
            context.startActivity(intent);
        }catch(RuntimeException refused){log("launch fail source="+source);}
    }
    static void wakeForAssist(android.app.Activity activity,Intent intent){
        if(activity==null||!isAssistAction(intent==null?null:intent.getAction()))return;
        if(Build.VERSION.SDK_INT<27)return;
        try{activity.setShowWhenLocked(true);activity.setTurnScreenOn(true);}catch(RuntimeException ignored){}
    }
    static String keyCodeName(int keyCode){
        switch(keyCode){
            case KeyEvent.KEYCODE_VOICE_ASSIST:return "VOICE_ASSIST";
            case KeyEvent.KEYCODE_SEARCH:return "SEARCH";
            case KeyEvent.KEYCODE_HEADSETHOOK:return "HEADSETHOOK";
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:return "MEDIA_PLAY_PAUSE";
            case KeyEvent.KEYCODE_MEDIA_PLAY:return "MEDIA_PLAY";
            case KeyEvent.KEYCODE_MEDIA_PAUSE:return "MEDIA_PAUSE";
            case KeyEvent.KEYCODE_CALL:return "CALL";
            default:return String.valueOf(keyCode);
        }
    }
    static String showFlags(int flags){
        StringBuilder out=new StringBuilder("0x").append(Integer.toHexString(flags));
        if((flags&android.service.voice.VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE)!=0)out.append(" assist_gesture");
        if((flags&android.service.voice.VoiceInteractionSession.SHOW_SOURCE_APPLICATION)!=0)out.append(" application");
        if((flags&android.service.voice.VoiceInteractionSession.SHOW_SOURCE_ACTIVITY)!=0)out.append(" activity");
        if(Build.VERSION.SDK_INT>=31&&(flags&0x40)!=0)out.append(" push_to_talk");
        return out.toString();
    }
}
