package si.zya.grokwear;

import android.app.Application;

/** Restores the in-app VPN on process start if the user left it connected. */
public final class GrokWearApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        ProxyRuntime.attach(this);
    }
}
