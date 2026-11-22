package si.zya.grokwear;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the packaged mihomo binary ({@code libmihomo.so}) from {@code nativeLibraryDir}.
 * Android 10+ forbids exec from the writable app data dir; extracted JNI libs remain executable.
 * API 36 still extracts {@code lib*.so} when {@code extractNativeLibs/useLegacyPackaging} is on.
 */
final class ProxyEngine {
    private static final AtomicReference<Process> LIVE=new AtomicReference<>();
    static File binary(Context context){
        File dir=new File(context.getApplicationInfo().nativeLibraryDir);
        File so=new File(dir,"libmihomo.so");
        return so;
    }
    static File home(Context context){return new File(context.getNoBackupFilesDir(),"mihomo");}
    static synchronized void start(Context context,String yaml)throws Exception{
        stop();
        File bin=binary(context);
        if(!bin.isFile())throw new GrokCore.Failure("VPN engine is missing from this APK (libmihomo.so).",true);
        File dir=home(context);if(!dir.isDirectory()&&!dir.mkdirs())throw new GrokCore.Failure("Could not open VPN work storage.",true);
        File config=new File(dir,"config.yaml");
        byte[] bytes=yaml.getBytes(StandardCharsets.UTF_8);
        try(FileOutputStream out=new FileOutputStream(config)){out.write(bytes);out.getFD().sync();}
        ProcessBuilder builder=new ProcessBuilder(bin.getAbsolutePath(),"-d",dir.getAbsolutePath(),"-f",config.getAbsolutePath());
        builder.directory(dir);builder.redirectErrorStream(true);
        builder.environment().put("HOME",dir.getAbsolutePath());
        builder.environment().put("LD_LIBRARY_PATH",new File(context.getApplicationInfo().nativeLibraryDir).getAbsolutePath());
        Process process=builder.start();
        LIVE.set(process);
        drain(process);
        if(!alive()){
            String log=tail(new File(dir,"mihomo.log"),4000);
            throw new GrokCore.Failure(log.isEmpty()?"VPN engine exited immediately.":"VPN engine failed to start.",true);
        }
    }
    static synchronized void stop(){
        Process process=LIVE.getAndSet(null);
        if(process==null)return;
        process.destroy();
        try{if(!process.waitFor(2,TimeUnit.SECONDS))process.destroyForcibly();}catch(InterruptedException ignored){Thread.currentThread().interrupt();process.destroyForcibly();}
    }
    static boolean alive(){
        Process process=LIVE.get();
        if(process==null)return false;
        try{process.exitValue();return false;}catch(IllegalThreadStateException running){return true;}
    }
    static String diagnose(Context context){
        File bin=binary(context);
        StringBuilder out=new StringBuilder();
        out.append("binary ").append(bin.getAbsolutePath()).append(bin.isFile()?" ok":" missing");
        if(bin.isFile())out.append(" ").append(bin.length()).append("b");
        out.append("\nextractDir ").append(context.getApplicationInfo().nativeLibraryDir);
        out.append("\nalive ").append(alive());
        out.append("\nready ").append(AppProxy.ready());
        File log=new File(home(context),"mihomo.log");
        if(log.isFile())out.append("\nlog ").append(tail(log,800));
        return out.toString();
    }
    private static void drain(Process process){
        Thread t=new Thread(()->{
            try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
                while(in.readLine()!=null){}
            }catch(Exception ignored){}
        },"mihomo-log");
        t.setDaemon(true);t.start();
    }
    private static String tail(File file,int max){
        try{
            byte[] all=java.nio.file.Files.readAllBytes(file.toPath());
            String text=new String(all,StandardCharsets.UTF_8);
            return text.length()<=max?text:text.substring(text.length()-max);
        }catch(Exception ignored){return "";}
    }
}
