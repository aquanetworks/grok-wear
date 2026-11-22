package si.zya.grokwear;

import android.content.Intent;
import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AssistLaunchTest {
    @Test public void assistActionsIncludeHeadsetIntents() {
        assertTrue(AssistLaunch.isAssistAction(Intent.ACTION_ASSIST));
        assertTrue(AssistLaunch.isAssistAction("android.intent.action.VOICE_ASSIST"));
        assertTrue(AssistLaunch.isAssistAction(Intent.ACTION_VOICE_COMMAND));
        assertTrue(AssistLaunch.isAssistAction("android.speech.action.VOICE_SEARCH_HANDS_FREE"));
        assertFalse(AssistLaunch.isAssistAction(Intent.ACTION_MAIN));
        assertFalse(AssistLaunch.isAssistAction(null));
        assertFalse(AssistLaunch.isAssistAction(""));
    }
    @Test public void voiceAssistKeyStartsCallOnUp() {
        assertEquals(HeadsetButtons.Decision.CONSUME,HeadsetButtons.decide(KeyEvent.KEYCODE_VOICE_ASSIST,KeyEvent.ACTION_DOWN,0,1,1,false));
        assertEquals(HeadsetButtons.Decision.ASSIST,HeadsetButtons.decide(KeyEvent.KEYCODE_VOICE_ASSIST,KeyEvent.ACTION_UP,0,1,20,false));
        assertEquals(HeadsetButtons.Decision.ASSIST,HeadsetButtons.decide(KeyEvent.KEYCODE_SEARCH,KeyEvent.ACTION_UP,0,1,20,false));
    }
    @Test public void shortHeadsetHookDoesNotStealPlayPause() {
        assertEquals(HeadsetButtons.Decision.IGNORE,HeadsetButtons.decide(KeyEvent.KEYCODE_HEADSETHOOK,KeyEvent.ACTION_DOWN,0,100,100,false));
        assertEquals(HeadsetButtons.Decision.IGNORE,HeadsetButtons.decide(KeyEvent.KEYCODE_HEADSETHOOK,KeyEvent.ACTION_UP,0,100,200,false));
        assertEquals(HeadsetButtons.Decision.IGNORE,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.ACTION_UP,0,100,250,false));
    }
    @Test public void longHeadsetHookStartsCall() {
        assertEquals(HeadsetButtons.Decision.ASSIST,HeadsetButtons.decide(KeyEvent.KEYCODE_HEADSETHOOK,KeyEvent.ACTION_DOWN,1,100,700,false));
        assertEquals(HeadsetButtons.Decision.CONSUME,HeadsetButtons.decide(KeyEvent.KEYCODE_HEADSETHOOK,KeyEvent.ACTION_DOWN,2,100,900,true));
        assertEquals(HeadsetButtons.Decision.ASSIST,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.ACTION_UP,0,100,100+HeadsetButtons.LONG_MS,false));
        assertEquals(HeadsetButtons.Decision.CONSUME,HeadsetButtons.decide(KeyEvent.KEYCODE_HEADSETHOOK,KeyEvent.ACTION_UP,0,100,800,true));
    }
    @Test public void unknownKeysAreIgnored() {
        assertEquals(HeadsetButtons.Decision.IGNORE,HeadsetButtons.decide(KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.ACTION_UP,0,1,2,false));
    }
    @Test public void keyNamesForLogs() {
        assertEquals("VOICE_ASSIST",AssistLaunch.keyCodeName(KeyEvent.KEYCODE_VOICE_ASSIST));
        assertEquals("HEADSETHOOK",AssistLaunch.keyCodeName(KeyEvent.KEYCODE_HEADSETHOOK));
        assertEquals("MEDIA_PLAY_PAUSE",AssistLaunch.keyCodeName(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
    }
    @Test public void headsetToggleDefaultsOffAndUsesRussianLabels() {
        assertFalse(HeadsetButtons.enabled(null));
        assertEquals("Кнопка наушников",HeadsetButtons.ROW);
        assertEquals("Вкл",HeadsetButtons.ON);
        assertEquals("Выкл",HeadsetButtons.OFF);
        assertEquals(Intent.ACTION_VOICE_COMMAND,AssistLaunch.VOICE_COMMAND);
        assertEquals("android.speech.action.VOICE_SEARCH_HANDS_FREE",AssistLaunch.HANDS_FREE);
    }
}
