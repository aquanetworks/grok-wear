package com.aquanetworks.watchvpn;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** AES-256-GCM via Android Keystore. Subscription URL never hits SharedPreferences plaintext. */
final class Vault {
    private static final String ALIAS="com.aquanetworks.watchvpn.v1";
    private static final byte[] AAD="watchvpn/v1".getBytes(StandardCharsets.UTF_8);
    private final AtomicFile file;
    Vault(Context context){
        file=new AtomicFile(new File(context.getNoBackupFilesDir(),"watchvpn-v1.enc"));
    }
    synchronized JSONObject read()throws Exception{
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile()+".bak").exists())return new JSONObject();
        try(InputStream in=file.openRead()){
            byte[] encoded=readBounded(in,512*1024);
            if(encoded.length<29)throw WatchFail.of("VPN storage is unreadable.");
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Arrays.copyOf(encoded,12)));
            c.updateAAD(AAD);
            byte[] clear=c.doFinal(encoded,12,encoded.length-12);
            try{return new JSONObject(new String(clear,StandardCharsets.UTF_8));}
            finally{Arrays.fill(clear,(byte)0);}
        }catch(WatchFail e){throw e;}
        catch(Exception e){throw WatchFail.of("VPN storage is unreadable.");}
    }
    synchronized void write(JSONObject value)throws Exception{
        File parent=file.getBaseFile().getParentFile();
        if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw WatchFail.of("Could not open VPN storage.");
        String serialized=value.toString();
        byte[] clear=serialized.getBytes(StandardCharsets.UTF_8);
        FileOutputStream out=null;
        try{
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,key(true));
            c.updateAAD(AAD);
            out=file.startWrite();
            out.write(c.getIV());
            out.write(c.doFinal(clear));
            out.getFD().sync();
            file.finishWrite(out);
            out=null;
        }catch(Exception e){
            if(out!=null)file.failWrite(out);
            throw WatchFail.of("Could not save VPN settings.");
        }finally{Arrays.fill(clear,(byte)0);}
    }
    private javax.crypto.SecretKey key(boolean create)throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        javax.crypto.SecretKey k=(javax.crypto.SecretKey)ks.getKey(ALIAS,null);
        if(k!=null)return k;
        if(!create)throw WatchFail.of("VPN storage is unreadable.");
        KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    private static byte[] readBounded(InputStream in,int max)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        byte[] block=new byte[4096];int n;
        while((n=in.read(block))!=-1){
            if(b.size()+n>max)throw WatchFail.of("VPN storage is unreadable.");
            b.write(block,0,n);
        }
        return b.toByteArray();
    }
}
