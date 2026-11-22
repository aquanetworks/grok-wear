package si.zya.grokwear;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/** Shared OkHttp builders. Grok traffic is fail-closed through the in-app HTTP CONNECT proxy. */
final class HttpClients {
    static final Interceptor FAIL_CLOSED=chain->{
        if(!AppProxy.ready())throw new IOException(AppProxy.OFF);
        return chain.proceed(chain.request());
    };

    static OkHttpClient.Builder tunneled(){
        return new OkHttpClient.Builder()
            .proxySelector(AppProxy.TUNNEL)
            .addInterceptor(FAIL_CLOSED)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false);
    }

    /** Subscription fetch, mihomo API, and TCP-adjacent HTTP that must not enter the tunnel. */
    static OkHttpClient.Builder direct(){
        return new OkHttpClient.Builder()
            .proxy(java.net.Proxy.NO_PROXY)
            .proxySelector(AppProxy.DIRECT)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false);
    }

    static OkHttpClient.Builder subscription(){
        return direct().followRedirects(true).followSslRedirects(true)
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
            .writeTimeout(15,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS);
    }
}
