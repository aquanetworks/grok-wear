package com.aquanetworks.watchvpn;

import android.app.Application;

public final class WatchVpnApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{SystemProxy.clear(WatchVpnApp.this);}catch(Throwable ignored){}
            AppLog.line("crash clear http_proxy "+(error==null?"":error.getClass().getSimpleName()));
            if(previous!=null)previous.uncaughtException(thread,error);
            else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
        });
        VpnRuntime.attach(this);
    }
}
