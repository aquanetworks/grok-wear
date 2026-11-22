package si.zya.grokwear;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * App-scoped fail-closed routing. Grok HTTP and WebSocket clients must use {@link #TUNNEL}.
 * Direct access is only for the subscription fetch, the local mihomo API, and TCP latency to the
 * VLESS server itself. Never sets a system / VpnService / global HTTP proxy.
 */
final class AppProxy {
    static final String OFF="VPN is off. Connect it in VPN settings before Grok can be used.";
    static final String BROKEN="VPN is not ready. Connect it in VPN settings, then try again.";
    static final int HTTP_PORT=27890,SOCKS_PORT=27891,API_PORT=29090;
    private static volatile InetSocketAddress http,socks;
    private static volatile String lastFailure="";

    static synchronized void arm(int httpPort,int socksPort){
        if(httpPort<=0||httpPort>65535||socksPort<=0||socksPort>65535)throw new IllegalArgumentException("Invalid proxy port");
        http=loopback(httpPort);socks=loopback(socksPort);lastFailure="";
    }
    static synchronized void disarm(){http=null;socks=null;}
    static boolean ready(){return http!=null&&socks!=null;}
    static String lastFailure(){return lastFailure==null?"":lastFailure;}
    static void noteFailure(String message){if(message!=null&&!message.isEmpty())lastFailure=message;}
    static Proxy httpProxy(){InetSocketAddress a=http;return a==null?null:new Proxy(Proxy.Type.HTTP,a);}
    static Proxy socksProxy(){InetSocketAddress a=socks;return a==null?null:new Proxy(Proxy.Type.SOCKS,a);}
    static GrokCore.Failure closed(){return new GrokCore.Failure(ready()?BROKEN:OFF,true,"proxy");}
    static void require()throws GrokCore.Failure{if(!ready())throw closed();}
    static boolean isClosed(Throwable error){
        for(Throwable t=error;t!=null;t=t.getCause()){
            if(t instanceof Closed)return true;
            if(t instanceof GrokCore.Failure&&"proxy".equals(((GrokCore.Failure)t).code))return true;
            String m=t.getMessage();if(m!=null&&(m.equals(OFF)||m.equals(BROKEN)))return true;
        }
        return false;
    }

    static final class Closed extends IllegalStateException {
        Closed(String message){super(message);}
    }

    static final ProxySelector TUNNEL=new ProxySelector(){
        public List<Proxy> select(URI uri){
            if(loopback(uri))return Collections.singletonList(Proxy.NO_PROXY);
            Proxy p=httpProxy();
            if(p==null)throw new Closed(OFF);
            return Collections.singletonList(p);
        }
        public void connectFailed(URI uri,SocketAddress sa,IOException ioe){
            if(ioe!=null)noteFailure("Proxy connect failed");
        }
    };
    static final ProxySelector DIRECT=new ProxySelector(){
        public List<Proxy> select(URI uri){return Collections.singletonList(Proxy.NO_PROXY);}
        public void connectFailed(URI uri,SocketAddress sa,IOException ioe){}
    };

    static boolean loopback(URI uri){
        if(uri==null)return false;
        String host=uri.getHost();
        return host!=null&&(host.equals("127.0.0.1")||host.equals("localhost")||host.equals("::1"));
    }
    private static InetSocketAddress loopback(int port){return InetSocketAddress.createUnresolved("127.0.0.1",port);}
}
