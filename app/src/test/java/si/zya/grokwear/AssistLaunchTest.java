package si.zya.grokwear;

import android.content.Intent;
import android.media.session.PlaybackState;
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
    @Test public void shortMediaPlayLaunchesOnFirstEdge() {
        assertEquals(HeadsetButtons.Outcome.LAUNCH,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.ACTION_UP,false,false,true,0,10));
        assertEquals(HeadsetButtons.Outcome.LAUNCH,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.ACTION_DOWN,false,false,true,0,10));
        assertEquals(HeadsetButtons.Outcome.LAUNCH,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.ACTION_DOWN,false,false,true,0,1));
        assertEquals(HeadsetButtons.Outcome.LAUNCH,HeadsetButtons.decide(KeyEvent.KEYCODE_HEADSETHOOK,KeyEvent.ACTION_UP,false,false,true,0,1));
        assertEquals(HeadsetButtons.Outcome.LAUNCH,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PAUSE,KeyEvent.ACTION_DOWN,false,false,true,0,1));
        assertEquals(HeadsetButtons.Outcome.LAUNCH,HeadsetButtons.decide(KeyEvent.KEYCODE_VOICE_ASSIST,KeyEvent.ACTION_UP,false,false,true,0,1));
    }
    @Test public void pairedEventWithinDebounceIsIgnored() {
        long first=100;
        assertEquals(HeadsetButtons.Outcome.LAUNCH,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.ACTION_UP,false,false,true,0,first));
        assertEquals(HeadsetButtons.Outcome.DEBOUNCED,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.ACTION_DOWN,false,false,true,first,first+1));
        assertEquals(HeadsetButtons.Outcome.DEBOUNCED,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.ACTION_DOWN,false,false,true,first,first+HeadsetButtons.DEBOUNCE_MS-1));
        assertEquals(HeadsetButtons.Outcome.LAUNCH,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.ACTION_DOWN,false,false,true,first,first+HeadsetButtons.DEBOUNCE_MS));
    }
    @Test public void musicActivePassesThrough() {
        assertEquals(HeadsetButtons.Outcome.PASSTHROUGH,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.ACTION_UP,true,false,true,0,1));
        assertEquals(HeadsetButtons.Outcome.PASSTHROUGH,HeadsetButtons.decide(KeyEvent.KEYCODE_HEADSETHOOK,KeyEvent.ACTION_DOWN,true,false,true,0,1));
    }
    @Test public void grokCallHangUpByDefault() {
        assertTrue(HeadsetButtons.endCallOnPress(null));
        assertEquals(HeadsetButtons.Outcome.END_CALL,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.ACTION_UP,false,true,true,0,1));
        assertEquals(HeadsetButtons.Outcome.MUTE,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.ACTION_UP,false,true,false,0,1));
        assertEquals(HeadsetButtons.Outcome.DEBOUNCED,HeadsetButtons.decide(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.ACTION_DOWN,false,true,true,50,51));
    }
    @Test public void unknownKeysAreIgnored() {
        assertEquals(HeadsetButtons.Outcome.IGNORE,HeadsetButtons.decide(KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.ACTION_UP,false,false,true,0,1));
        assertFalse(HeadsetButtons.isHeadsetKey(KeyEvent.KEYCODE_SEARCH));
    }
    @Test public void playingStatesMatchActiveControllers() {
        assertTrue(HeadsetButtons.playingState(PlaybackState.STATE_PLAYING));
        assertTrue(HeadsetButtons.playingState(PlaybackState.STATE_BUFFERING));
        assertFalse(HeadsetButtons.playingState(PlaybackState.STATE_PAUSED));
        assertFalse(HeadsetButtons.playingState(PlaybackState.STATE_STOPPED));
        assertFalse(HeadsetButtons.playingState(PlaybackState.STATE_NONE));
    }
    @Test public void keyNamesForLogs() {
        assertEquals("VOICE_ASSIST",AssistLaunch.keyCodeName(KeyEvent.KEYCODE_VOICE_ASSIST));
        assertEquals("HEADSETHOOK",AssistLaunch.keyCodeName(KeyEvent.KEYCODE_HEADSETHOOK));
        assertEquals("MEDIA_PLAY_PAUSE",AssistLaunch.keyCodeName(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        assertEquals("MEDIA_PLAY",AssistLaunch.keyCodeName(KeyEvent.KEYCODE_MEDIA_PLAY));
        assertEquals("MEDIA_PAUSE",AssistLaunch.keyCodeName(KeyEvent.KEYCODE_MEDIA_PAUSE));
        assertEquals(126,KeyEvent.KEYCODE_MEDIA_PLAY);
    }
    @Test public void headsetToggleDefaultsOffAndUsesRussianLabels() {
        assertFalse(HeadsetButtons.enabled(null));
        assertEquals("Кнопка наушников",HeadsetButtons.ROW);
        assertEquals("Вкл",HeadsetButtons.ON);
        assertEquals("Выкл",HeadsetButtons.OFF);
        assertEquals("завершить звонок",HeadsetButtons.CALL_END);
        assertEquals("микрофон",HeadsetButtons.CALL_MUTE);
        assertEquals("завершить звонок",HeadsetButtons.callActionLabel(true));
        assertEquals("микрофон",HeadsetButtons.callActionLabel(false));
        assertEquals(Intent.ACTION_VOICE_COMMAND,AssistLaunch.VOICE_COMMAND);
        assertEquals("android.speech.action.VOICE_SEARCH_HANDS_FREE",AssistLaunch.HANDS_FREE);
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY,126);
    }
}
