package com.aquanetworks.watchvpn;

import android.util.Log;

/** In-process mihomo via {@code libmihomo-jni.so} (c-shared). No child process. */
final class MihomoJni {
    static final String TAG="watchvpn";
    private static final boolean LOADED;
    private static final String LOAD_ERROR;
    static {
        boolean ok=false;String err="";
        try{
            Log.i(TAG,"System.loadLibrary(\"mihomo-jni\") …");
            System.loadLibrary("mihomo-jni");
            ok=true;
            Log.i(TAG,"System.loadLibrary(\"mihomo-jni\") OK");
        }catch(UnsatisfiedLinkError e){
            err=e.getMessage()==null?e.toString():e.getMessage();
            Log.e(TAG,"UnsatisfiedLinkError loadLibrary mihomo-jni: "+err,e);
        }catch(Throwable t){
            err=t.getClass().getSimpleName()+": "+t.getMessage();
            Log.e(TAG,"loadLibrary mihomo-jni failed: "+err,t);
        }
        LOADED=ok;LOAD_ERROR=err;
        try{AppLog.line(ok?"jni loaded in-process":"jni LOAD FAIL "+err);}catch(Throwable ignored){}
    }
    static boolean loaded(){return LOADED;}
    static String loadError(){return LOAD_ERROR;}
    static native String nativeStart(String homeDir,String configPath);
    static native void nativeStop();
    static native boolean nativeRunning();
    static native String nativeVersion();
    static String start(String home,String config)throws Exception{
        if(!LOADED){
            Log.e(TAG,"nativeStart skipped: UnsatisfiedLinkError "+LOAD_ERROR);
            throw WatchFail.of("In-process mihomo is missing (libmihomo-jni.so). UnsatisfiedLinkError: "+LOAD_ERROR);
        }
        Log.i(TAG,"nativeStart home="+home);
        String err=nativeStart(home,config);
        return err==null?"":err;
    }
    static void stop(){if(LOADED)try{nativeStop();}catch(Throwable ignored){}}
    static boolean running(){return LOADED&&nativeRunning();}
    static String version(){
        if(!LOADED)return "unloaded";
        try{String v=nativeVersion();return v==null||v.isEmpty()?"mihomo":v;}
        catch(Throwable t){return "mihomo";}
    }
}
