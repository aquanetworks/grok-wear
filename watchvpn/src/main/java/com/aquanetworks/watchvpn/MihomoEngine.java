package com.aquanetworks.watchvpn;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs packaged mihomo. Default is in-process {@code libmihomo-jni.so}
 * (Android phantom-process killer cannot reap a child that does not exist).
 * Exec of {@code libmihomo.so} is a hidden fallback only.
 */
final class MihomoEngine {
    private static final AtomicInteger PID=new AtomicInteger(-1);
    private static final AtomicBoolean IN_PROCESS=new AtomicBoolean(false);
    static File jniLib(Context context){
        return new File(context.getApplicationInfo().nativeLibraryDir,"libmihomo-jni.so");
    }
    static File binary(Context context){
        return new File(context.getApplicationInfo().nativeLibraryDir,"libmihomo.so");
    }
    static File home(Context context){return new File(context.getNoBackupFilesDir(),"mihomo");}
    static synchronized void start(Context context,String yaml,int tunFd)throws Exception{
        stop();
        File dir=home(context);
        if(!dir.isDirectory()&&!dir.mkdirs())throw WatchFail.of("Could not open VPN work storage.");
        File config=new File(dir,"config.yaml");
        byte[] bytes=yaml.getBytes(StandardCharsets.UTF_8);
        try(FileOutputStream out=new FileOutputStream(config)){out.write(bytes);out.getFD().sync();}
        ConfigDump.log(yaml);
        boolean exec=false;
        try{exec=Store.get(context).execFallback();}catch(Exception ignored){}
        if(exec&&binary(context).isFile()){
            startExec(context,dir,config,tunFd);
            return;
        }
        AppLog.line("mihomo in-process "+MihomoJni.version()+" lib="+(jniLib(context).isFile()?"yes":"no")
            +" loaded="+MihomoJni.loaded());
        if(!MihomoJni.loaded()){
            AppLog.line("UnsatisfiedLinkError "+MihomoJni.loadError());
            throw WatchFail.of("libmihomo-jni.so failed to load. UnsatisfiedLinkError: "+MihomoJni.loadError());
        }
        String err=MihomoJni.start(dir.getAbsolutePath(),config.getAbsolutePath());
        if(err!=null&&!err.trim().isEmpty()){
            AppLog.line("mihomo start error "+err);
            throw WatchFail.of(err.trim());
        }
        IN_PROCESS.set(true);
        if(!MihomoJni.running()){
            String log=tail(new File(dir,"mihomo.log"),4000);
            AppLog.line("mihomo in-process not running "+log);
            throw WatchFail.of(log.isEmpty()?"In-process mihomo did not start.":"VPN engine failed to start.");
        }
    }
    private static void startExec(Context context,File dir,File config,int tunFd)throws Exception{
        File bin=binary(context);
        int pid=NativeSpawn.start(bin.getAbsolutePath(),dir.getAbsolutePath(),config.getAbsolutePath(),tunFd);
        AppLog.line("mihomo EXEC fallback pid="+pid+" (phantom killer risk)");
        if(pid<=0)throw WatchFail.of("Could not start the VPN engine (fork "+pid+").");
        PID.set(pid);
        IN_PROCESS.set(false);
        if(!alive()){
            String log=tail(new File(dir,"mihomo.log"),4000);
            throw WatchFail.of(log.isEmpty()?"VPN engine exited immediately.":"VPN engine failed to start.");
        }
    }
    static synchronized void stop(){
        if(IN_PROCESS.getAndSet(false)){
            MihomoJni.stop();
            return;
        }
        int pid=PID.getAndSet(-1);
        if(pid<=0){
            if(MihomoJni.loaded())MihomoJni.stop();
            return;
        }
        NativeSpawn.terminate(pid);
        for(int i=0;i<20;i++){
            int st=NativeSpawn.waitFor(pid,true);
            if(st!=-1)return;
            try{Thread.sleep(100);}catch(InterruptedException ie){Thread.currentThread().interrupt();break;}
        }
        NativeSpawn.killNow(pid);
        NativeSpawn.waitFor(pid,false);
    }
    static boolean alive(){
        if(IN_PROCESS.get()||MihomoJni.running())return MihomoJni.running();
        int pid=PID.get();
        if(pid<=0)return false;
        int st=NativeSpawn.waitFor(pid,true);
        if(st==-1)return true;
        PID.compareAndSet(pid,-1);
        return false;
    }
    static boolean inProcess(){return IN_PROCESS.get()||MihomoJni.running();}
    static void closeFd(int fd){
        if(fd<0)return;
        try{android.os.ParcelFileDescriptor.adoptFd(fd).close();}catch(Exception ignored){}
    }
    static String diagnose(Context context){
        StringBuilder out=new StringBuilder();
        File jni=jniLib(context);
        out.append("mode ").append(inProcess()?"in-process":"exec");
        out.append("\njni ").append(jni.getAbsolutePath()).append(jni.isFile()?" ok":" missing");
        if(jni.isFile())out.append(" ").append(jni.length()).append("b");
        out.append("\nloaded ").append(MihomoJni.loaded());
        if(!MihomoJni.loaded())out.append(" ").append(MihomoJni.loadError());
        out.append("\nversion ").append(MihomoJni.version());
        out.append("\nalive ").append(alive());
        out.append("\nmixed ").append(Health.mixedPortOpen());
        out.append("\npid ").append(PID.get());
        File log=new File(home(context),"mihomo.log");
        if(log.isFile())out.append("\nlog ").append(tail(log,800));
        return out.toString();
    }
    static String tailLog(Context context,int max){
        if(context==null)return "";
        return tail(new File(home(context),"mihomo.log"),max);
    }
    private static String tail(File file,int max){
        try{
            byte[] all=java.nio.file.Files.readAllBytes(file.toPath());
            String text=new String(all,StandardCharsets.UTF_8);
            return text.length()<=max?text:text.substring(text.length()-max);
        }catch(Exception ignored){return "";}
    }
}
