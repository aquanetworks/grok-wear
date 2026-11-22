package si.zya.grokwear;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

/** The watch's assistant gesture (holding a button) opens a call with the Home-button Bot (or the account primary). */
public final class GrokAssistantSessionService extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession(Bundle args){
        AssistLaunch.log("voice-session new");
        return new VoiceInteractionSession(this){
            @Override public void onShow(Bundle shown,int flags){super.onShow(shown,flags);
                AssistLaunch.log("voice-session show flags="+AssistLaunch.showFlags(flags)+" source=voice-session");
                AssistLaunch.start(getContext(),"voice-session");
                hide(); // Grok Wear's own screen shows the call; this session has no window of its own.
            }
        };
    }
}
