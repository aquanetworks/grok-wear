package si.zya.grokwear;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/** Shared OkHttp builders. Grok traffic is fail-closed through Watch VPN HTTP CONNECT. */
final class HttpClients {
    static final Interceptor FAIL_CLOSED=chain->{
        if(!AppProxy.ready())throw new IOException(AppProxy.OFF);
        return chain.proceed(chain.request());
    };
    static final EventListener PROXY_LOG=new EventListener(){
        @Override public void connectStart(Call call,InetSocketAddress address,Proxy proxy){
            AppProxy.log("CONNECT "+host(call)+" via "+describe(proxy));
            if(proxy==null||proxy.type()!=Proxy.Type.HTTP){
                AppProxy.log("ERROR direct-connect blocked for "+host(call));
                throw new AppProxy.Closed(AppProxy.OFF);
            }
        }
        @Override public void connectEnd(Call call,InetSocketAddress address,Proxy proxy,okhttp3.Protocol protocol){
            AppProxy.log("OK "+host(call));
        }
        @Override public void connectFailed(Call call,InetSocketAddress address,Proxy proxy,okhttp3.Protocol protocol,IOException ioe){
            AppProxy.log("FAIL "+host(call)+" "+(ioe==null?"io":ioe.getClass().getSimpleName()));
        }
        private static String host(Call call){
            try{return call.request().url().host();}catch(RuntimeException e){return "?";}
        }
        private static String describe(Proxy proxy){
            if(proxy==null)return "none";
            if(proxy.type()!=Proxy.Type.HTTP)return proxy.type().name();
            java.net.SocketAddress a=proxy.address();
            if(!(a instanceof InetSocketAddress))return "HTTP";
            InetSocketAddress inet=(InetSocketAddress)a;
            return "HTTP "+inet.getHostString()+":"+inet.getPort();
        }
    };

    static OkHttpClient.Builder tunneled(){
        return new OkHttpClient.Builder()
            .proxySelector(AppProxy.TUNNEL)
            .proxy(null)
            .socketFactory(AppProxy.LOCAL_ONLY)
            .dns(AppProxy.BLOCK_DIRECT)
            .eventListener(PROXY_LOG)
            .addInterceptor(FAIL_CLOSED)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10,TimeUnit.SECONDS);
    }
}

/** Evicts pooled Grok connections when the proxy endpoint changes or the port drops. */
final class HttpClientsEvict {
    static void evict(){
        try{BotAccount.networkChanged();}catch(RuntimeException ignored){}
    }
}
