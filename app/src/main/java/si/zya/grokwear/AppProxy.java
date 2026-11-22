package si.zya.grokwear;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.net.SocketFactory;
import okhttp3.Dns;

/**
 * Fail-closed HTTP CONNECT to Watch VPN ({@code 127.0.0.1:7890} by default).
 * Grok OkHttp clients must use {@link HttpClients#tunneled()} — never the system ProxySelector,
 * never a direct socket, never local DNS for Grok hosts.
 */
final class AppProxy {
    static final String TAG="grokwear-proxy";
    static final String OFF="Прокси не запущен. Откройте Watch VPN и нажмите Подключить.";
    static final String BROKEN="Прокси не запущен. Откройте Watch VPN и нажмите Подключить.";
    static final String DEFAULT_HOST="127.0.0.1";
    static final int DEFAULT_PORT=7890;
    static final String WATCH_VPN="com.aquanetworks.watchvpn";
    static final long CACHE_MS=2000;
    private static final String PREFS="grokwear_ext_proxy";
    interface Probe { boolean open(String host,int port,int timeoutMs); }
    static volatile Probe probe=AppProxy::tcp;
    private static volatile String host=DEFAULT_HOST;
    private static volatile int port=DEFAULT_PORT;
    private static volatile long probedAt;
    private static volatile boolean probedOk;
    private static volatile String lastFailure="";
    private static SharedPreferences prefs;

    static synchronized void attach(Context context){
        if(context==null)return;
        prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        host=normalizeHost(prefs.getString("host",DEFAULT_HOST));
        port=normalizePort(prefs.getInt("port",DEFAULT_PORT));
        invalidate();
        log("endpoint "+endpoint());
    }
    static synchronized void setEndpoint(String nextHost,int nextPort){
        host=normalizeHost(nextHost);port=normalizePort(nextPort);invalidate();
        if(prefs!=null)prefs.edit().putString("host",host).putInt("port",port).apply();
        log("endpoint "+endpoint());
        HttpClientsEvict.evict();
    }
    static String host(){return host;}
    static int port(){return port;}
    static String endpoint(){return host()+":"+port();}
    static void invalidate(){probedAt=0;probedOk=false;}
    static boolean ready(){
        long now=System.currentTimeMillis();
        if(probedAt>0&&now-probedAt>=0&&now-probedAt<CACHE_MS)return probedOk;
        boolean ok;
        try{ok=probe.open(host(),port(),250);}
        catch(RuntimeException failed){ok=false;}
        probedAt=now;probedOk=ok;
        if(!ok){
            lastFailure=OFF;
            log("closed "+endpoint());
        }
        return ok;
    }
    static String lastFailure(){return lastFailure==null?"":lastFailure;}
    static void noteFailure(String message){if(message!=null&&!message.isEmpty())lastFailure=message;}
    static Proxy httpProxy(){
        return new Proxy(Proxy.Type.HTTP,InetSocketAddress.createUnresolved(host(),port()));
    }
    static GrokCore.Failure closed(){return new GrokCore.Failure(OFF,true,"proxy");}
    static void require()throws GrokCore.Failure{if(!ready())throw closed();}
    static boolean isClosed(Throwable error){
        for(Throwable t=error;t!=null;t=t.getCause()){
            if(t instanceof Closed)return true;
            if(t instanceof GrokCore.Failure&&"proxy".equals(((GrokCore.Failure)t).code))return true;
            String m=t.getMessage();if(m!=null&&(m.equals(OFF)||m.equals(BROKEN)||m.contains("Прокси не запущен")))return true;
        }
        return false;
    }
    static final class Closed extends IllegalStateException {
        Closed(String message){super(message);}
    }

    /** Always HTTP CONNECT to the configured Watch VPN listener. Never system ProxySelector, never DIRECT. */
    static final ProxySelector TUNNEL=new ProxySelector(){
        public List<Proxy> select(URI uri){
            if(!ready())throw new Closed(OFF);
            Proxy p=httpProxy();
            log("select "+(uri==null?"?":uri.getHost())+" -> HTTP "+endpoint());
            return Collections.singletonList(p);
        }
        public void connectFailed(URI uri,SocketAddress sa,IOException ioe){
            invalidate();
            noteFailure(OFF);
            log("connectFailed "+(uri==null?"?":uri.getHost()));
        }
    };

    /**
     * OkHttp opens this socket to the HTTP proxy, then wraps TLS through the tunnel.
     * Connecting to any other host (including Grok) is refused so there is no direct fallback.
     */
    static final SocketFactory LOCAL_ONLY=new SocketFactory(){
        public Socket createSocket(){return new GuardedSocket();}
        public Socket createSocket(String h,int p)throws IOException{check(h,p);Socket s=new GuardedSocket();s.connect(new InetSocketAddress(h,p));return s;}
        public Socket createSocket(String h,int p,InetAddress local,int lp)throws IOException{check(h,p);Socket s=new GuardedSocket();s.bind(new InetSocketAddress(local,lp));s.connect(new InetSocketAddress(h,p));return s;}
        public Socket createSocket(InetAddress a,int p)throws IOException{check(a==null?null:a.getHostAddress(),p);Socket s=new GuardedSocket();s.connect(new InetSocketAddress(a,p));return s;}
        public Socket createSocket(InetAddress a,int p,InetAddress local,int lp)throws IOException{check(a==null?null:a.getHostAddress(),p);Socket s=new GuardedSocket();s.bind(new InetSocketAddress(local,lp));s.connect(new InetSocketAddress(a,p));return s;}
    };
    static final class GuardedSocket extends Socket {
        @Override public void connect(SocketAddress endpoint)throws IOException{connect(endpoint,0);}
        @Override public void connect(SocketAddress endpoint,int timeout)throws IOException{
            if(!(endpoint instanceof InetSocketAddress))throw new ConnectException("Direct socket blocked");
            InetSocketAddress inet=(InetSocketAddress)endpoint;
            check(inet.getHostString(),inet.getPort());
            super.connect(endpoint,timeout);
        }
    }
    static void check(String h,int p)throws ConnectException{
        if(allowedEndpoint(h,p))return;
        log("socket-block "+h+":"+p);
        throw new ConnectException("Direct socket blocked for "+h);
    }
    static boolean allowedEndpoint(String h,int p){
        if(h==null||h.isEmpty()||p!=port())return false;
        String n=h.toLowerCase(Locale.ROOT);
        if(n.equals(host().toLowerCase(Locale.ROOT)))return true;
        return loopbackHost(n)&&loopbackHost(host().toLowerCase(Locale.ROOT));
    }

    /**
     * Local DNS is forbidden for Grok (and any other remote) hosts. HTTP CONNECT leaves hostnames
     * unresolved for the proxy. Only the proxy endpoint itself may be looked up.
     */
    static final Dns BLOCK_DIRECT=hostname->{
        if(hostname==null||hostname.isEmpty())throw new UnknownHostException("empty host");
        String h=hostname.toLowerCase(Locale.ROOT);
        if(loopbackHost(h)||h.equalsIgnoreCase(host()))return Dns.SYSTEM.lookup(hostname);
        log("dns-block "+h);
        throw new UnknownHostException("Direct DNS blocked for "+h+"; HTTP CONNECT proxy must resolve it");
    };

    static boolean loopbackHost(String h){
        return "127.0.0.1".equals(h)||"localhost".equals(h)||"::1".equals(h)||"0.0.0.0".equals(h);
    }
    static boolean grokHost(String hostname){
        if(hostname==null)return false;
        String h=hostname.toLowerCase(Locale.ROOT);
        return h.equals("api.x.ai")||h.endsWith(".x.ai")||h.equals("x.ai")
            ||h.equals("x.com")||h.endsWith(".x.com")
            ||h.contains("grok")
            ||h.equals("cursor.com")||h.endsWith(".cursor.com")||h.endsWith(".cursor.sh")||h.endsWith(".cursorvm.com")
            ||h.equals("api.github.com")||h.equals("github.com");
    }
    static String normalizeHost(String raw){
        if(raw==null)return DEFAULT_HOST;
        String h=raw.trim();
        if(h.isEmpty())return DEFAULT_HOST;
        if(h.startsWith("[")&&h.endsWith("]")&&h.length()>2)h=h.substring(1,h.length()-1);
        int colon=h.indexOf(':');
        if(colon>0&&h.indexOf(':',colon+1)<0)h=h.substring(0,colon);
        if(h.length()>253)return DEFAULT_HOST;
        return h;
    }
    static int normalizePort(int value){return value<=0||value>65535?DEFAULT_PORT:value;}
    static int parsePort(String raw){
        if(raw==null)return DEFAULT_PORT;
        try{return normalizePort(Integer.parseInt(raw.trim()));}catch(NumberFormatException e){return DEFAULT_PORT;}
    }
    static void log(String line){
        if(line==null||line.isEmpty())return;
        String text=line.length()>200?line.substring(0,200):line;
        try{android.util.Log.i(TAG,text);}catch(Throwable ignored){}
    }
    static boolean tcp(String h,int p,int timeoutMs){
        if(h==null||h.isEmpty()||p<=0)return false;
        try(Socket socket=new Socket()){
            InetAddress addr=InetAddress.getByName(h);
            if(addr.isAnyLocalAddress())return false;
            socket.connect(new InetSocketAddress(addr,p),Math.max(100,timeoutMs));
            return true;
        }catch(Exception failed){return false;}
    }
}
