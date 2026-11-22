package com.aquanetworks.watchvpn;

import android.content.Context;
import android.net.VpnService;
import java.lang.reflect.Method;

/**
 * Samsung Wear (Galaxy Watch Ultra / Android 16) may have no {@code IVpnManager};
 * {@code VpnService.Builder.establish()} then NPEs. TUN is optional and off by default.
 */
final class VpnCapability {
    private static volatile Boolean CACHED;
    static boolean available(Context context){
        Boolean cached=CACHED;
        if(cached!=null)return cached.booleanValue();
        boolean on=probe(context);
        CACHED=Boolean.valueOf(on);
        return on;
    }
    private static boolean probe(Context context){
        if(!binderPresent()){
            AppLog.line("vpn binder missing (vpn_management/vpn)");
            return false;
        }
        if(context==null)return false;
        try{
            VpnService.prepare(context);
            return true;
        }catch(Throwable t){
            if(looksLikeMissingVpn(t)){
                AppLog.line("vpn prepare NPE: "+t.getMessage());
                return false;
            }
            AppLog.line("vpn prepare "+t.getClass().getSimpleName());
            return binderPresent();
        }
    }
    static boolean binderPresent(){
        try{
            Class<?> sm=Class.forName("android.os.ServiceManager");
            Method get=sm.getMethod("getService",String.class);
            for(String name:new String[]{"vpn_management","vpn"}){
                Object binder=get.invoke(null,name);
                if(binder!=null)return true;
            }
            return false;
        }catch(Throwable t){return false;}
    }
    static boolean looksLikeMissingVpn(Throwable t){
        while(t!=null){
            if(t instanceof NullPointerException){
                String m=t.getMessage()==null?"":t.getMessage();
                if(m.contains("IVpnManager")||m.contains("establishVpn")||m.contains("null object reference"))return true;
            }
            t=t.getCause();
        }
        return false;
    }
    static String describe(Context context){
        boolean on=available(context);
        if(on)return "VpnService есть. TUN по умолчанию выключен — системный прокси "+SystemProxy.ENDPOINT+".";
        return "VpnService нет (IVpnManager=null). Режим: системный HTTP-прокси "+SystemProxy.ENDPOINT+".";
    }
}
