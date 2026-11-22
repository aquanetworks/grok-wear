package com.aquanetworks.watchvpn;

/** In-process mihomo via {@code libmihomo-jni.so} (c-shared). No child process. */
final class MihomoJni {
    private static final boolean LOADED;
    private static final String LOAD_ERROR;
    static {
        boolean ok=false;String err="";
        try{
            System.loadLibrary("mihomo-jni");
            ok=true;
        }catch(UnsatisfiedLinkError e){
            err=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        }
        LOADED=ok;LOAD_ERROR=err;
    }
    static boolean loaded(){return LOADED;}
    static String loadError(){return LOAD_ERROR;}
    static native String nativeStart(String homeDir,String configPath);
    static native void nativeStop();
    static native boolean nativeRunning();
    static native String nativeVersion();
    static String start(String home,String config)throws Exception{
        if(!LOADED)throw WatchFail.of("In-process mihomo is missing (libmihomo-jni.so). "+LOAD_ERROR);
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
