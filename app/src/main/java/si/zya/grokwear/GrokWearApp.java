package si.zya.grokwear;

import android.app.Application;

/** Loads the Watch VPN HTTP CONNECT endpoint and starts fail-closed probing. */
public final class GrokWearApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        ProxyRuntime.attach(this);
        HeadsetButtons.sync(this);
    }
}
