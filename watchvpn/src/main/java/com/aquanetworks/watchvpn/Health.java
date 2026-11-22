package com.aquanetworks.watchvpn;

import java.net.InetSocketAddress;
import java.net.Socket;

/** Honest proxy liveness: mixed-port TCP plus mihomo delay through the selected node. */
final class Health {
    static final int POLL_MS=10_000;
    static final String CLEAR_BANNER=
        "Прокси мёртв — часы останутся без интернета, пока не снимете http_proxy:\n"
        +SystemProxy.CLEAR_CMD;
    static boolean portOpen(String host,int port,int timeoutMs){
        if(host==null||host.isEmpty()||port<=0)return false;
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(host,port),Math.max(200,timeoutMs));
            return true;
        }catch(Exception e){return false;}
    }
    static boolean mixedPortOpen(){return portOpen("127.0.0.1",TunConfig.MIXED_PORT,500);}
    static boolean mixedPortOpen(int timeoutMs){return portOpen("127.0.0.1",TunConfig.MIXED_PORT,timeoutMs);}
    /** Connected only when the listener accepts TCP and the node delay API succeeds. */
    static boolean connected(boolean portOk,boolean delayOk){return portOk&&delayOk;}
    static String failMessage(boolean canWrite,String detail){
        String why=(detail==null||detail.isEmpty())?"Проверка узла не прошла.":detail;
        if(canWrite)return why+" http_proxy снят ("+SystemProxy.CLEAR_VALUE+").";
        return why+"\n"+CLEAR_BANNER;
    }
    static boolean needsClearBanner(boolean canWrite,boolean wantConnected,boolean actuallyUp){
        return wantConnected&&!actuallyUp&&!canWrite;
    }
}
