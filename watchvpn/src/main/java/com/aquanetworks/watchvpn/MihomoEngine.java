package com.aquanetworks.watchvpn;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Starts packaged {@code libmihomo.so} from {@code nativeLibraryDir} via JNI fork/exec.
 * Android 10+ forbids exec from writable app dirs; extracted JNI libs remain executable.
 * TUN fd is inherited as fd 3 ({@link TunConfig#TUN_FD}); Java ProcessBuilder cannot do that (CLOEXEC).
 */
final class MihomoEngine {
    private static final AtomicInteger PID=new AtomicInteger(-1);
    static File binary(Context context){
        return new File(context.getApplicationInfo().nativeLibraryDir,"libmihomo.so");
    }
    static File home(Context context){return new File(context.getNoBackupFilesDir(),"mihomo");}
    static synchronized void start(Context context,String yaml,int tunFd)throws Exception{
        stop();
        File bin=binary(context);
        if(!bin.isFile())throw WatchFail.of("VPN engine is missing from this APK (libmihomo.so).");
        File dir=home(context);
        if(!dir.isDirectory()&&!dir.mkdirs())throw WatchFail.of("Could not open VPN work storage.");
        File config=new File(dir,"config.yaml");
        byte[] bytes=yaml.getBytes(StandardCharsets.UTF_8);
        try(FileOutputStream out=new FileOutputStream(config)){out.write(bytes);out.getFD().sync();}
        int pid=NativeSpawn.start(bin.getAbsolutePath(),dir.getAbsolutePath(),config.getAbsolutePath(),tunFd);
        AppLog.line("mihomo spawn pid="+pid+" tunFd="+(tunFd>=0?"yes":"api"));
        if(pid<=0)throw WatchFail.of("Could not start the VPN engine (fork "+pid+").");
        PID.set(pid);
        if(!alive()){
            String log=tail(new File(dir,"mihomo.log"),4000);
            AppLog.line("mihomo exited immediately "+log);
            throw WatchFail.of(log.isEmpty()?"VPN engine exited immediately.":"VPN engine failed to start.");
        }
    }
    static synchronized void stop(){
        int pid=PID.getAndSet(-1);
        if(pid<=0)return;
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
        int pid=PID.get();
        if(pid<=0)return false;
        int st=NativeSpawn.waitFor(pid,true);
        if(st==-1)return true;
        PID.compareAndSet(pid,-1);
        return false;
    }
    static void closeFd(int fd){
        if(fd<0)return;
        try{android.os.ParcelFileDescriptor.adoptFd(fd).close();}catch(Exception ignored){}
    }
    static String diagnose(Context context){
        File bin=binary(context);
        StringBuilder out=new StringBuilder();
        out.append("binary ").append(bin.getAbsolutePath()).append(bin.isFile()?" ok":" missing");
        if(bin.isFile())out.append(" ").append(bin.length()).append("b");
        out.append("\nextractDir ").append(context.getApplicationInfo().nativeLibraryDir);
        out.append("\nalive ").append(alive());
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
