package si.zya.grokwear;

import android.service.voice.VoiceInteractionService;

/**
 * Grok Wear as the watch's digital assistant. Android binds this while Grok Wear holds that role; holding
 * the assistant button opens GrokAssistantSessionService, which starts a call with the Home-button Bot. It never
 * opens the microphone itself: the call does, once it starts.
 */
public final class GrokAssistantService extends VoiceInteractionService {
    @Override public void onReady(){
        super.onReady();
        AssistLaunch.log("voice-interaction ready");
    }
    /** The assistant gesture from the lock screen: the same call as the button, once the watch is unlocked. */
    @Override public void onLaunchVoiceAssistFromKeyguard(){
        AssistLaunch.log("keyguard source=voice-interaction");
        AssistLaunch.start(this,"keyguard");
    }
}
