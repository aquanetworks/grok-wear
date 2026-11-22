package si.zya.grokwear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/** System media-button delivery when the MediaSession is not the current one, including screen-off. */
public final class HeadsetMediaReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(context==null||intent==null||!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction()))return;
        if(!HeadsetButtons.enabled(context))return;
        AssistLaunch.log("media-button-receiver source=broadcast");
        Intent service=new Intent(context,HeadsetButtonService.class).setAction(HeadsetButtons.RECLAIM);
        KeyEvent event=null;
        try{
            if(android.os.Build.VERSION.SDK_INT>=33)event=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT,KeyEvent.class);
            else event=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        }catch(RuntimeException ignored){}
        if(event!=null)service.putExtra(Intent.EXTRA_KEY_EVENT,event);
        try{context.startForegroundService(service);}
        catch(RuntimeException failed){AssistLaunch.log("media-button-receiver fail");}
    }
}
