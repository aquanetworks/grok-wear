package si.zya.grokwear;

import org.junit.After;
import org.junit.Test;
import java.net.Proxy;
import java.net.URI;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import static org.junit.Assert.*;

public final class AppProxyTest {
    @After public void reset(){AppProxy.disarm();}

    @Test public void selectThrowsWhenDisarmed() {
        AppProxy.disarm();
        try{AppProxy.TUNNEL.select(URI.create("https://api.x.ai/"));fail();}
        catch(AppProxy.Closed closed){assertEquals(AppProxy.OFF,closed.getMessage());}
    }
    @Test public void requireFailClosed() {
        AppProxy.disarm();
        try{AppProxy.require();fail();}
        catch(GrokCore.Failure failed){assertEquals("proxy",failed.code);assertEquals(AppProxy.OFF,failed.getMessage());}
    }
    @Test public void httpConnectWhenArmed() {
        AppProxy.arm(AppProxy.HTTP_PORT,AppProxy.SOCKS_PORT);
        List<Proxy> proxies=AppProxy.TUNNEL.select(URI.create("https://api.x.ai/voice"));
        assertEquals(1,proxies.size());
        assertEquals(Proxy.Type.HTTP,proxies.get(0).type());
        assertEquals("127.0.0.1",((java.net.InetSocketAddress)proxies.get(0).address()).getHostString());
        assertEquals(AppProxy.HTTP_PORT,((java.net.InetSocketAddress)proxies.get(0).address()).getPort());
        assertTrue(AppProxy.ready());
    }
    @Test public void loopbackBypassesTunnel() {
        AppProxy.arm(AppProxy.HTTP_PORT,AppProxy.SOCKS_PORT);
        List<Proxy> proxies=AppProxy.TUNNEL.select(URI.create("http://127.0.0.1:"+AppProxy.API_PORT+"/version"));
        assertEquals(Proxy.NO_PROXY,proxies.get(0));
    }
    @Test public void okHttpFailsClosedWithoutProxy()throws Exception {
        AppProxy.disarm();
        OkHttpClient client=HttpClients.tunneled().build();
        try{
            client.newCall(new Request.Builder().url("https://api.x.ai/").build()).execute();
            fail();
        }catch(Exception failed){assertTrue(AppProxy.isClosed(failed));}
    }
    @Test public void directSelectorNeverTunnels() {
        AppProxy.arm(AppProxy.HTTP_PORT,AppProxy.SOCKS_PORT);
        assertEquals(Proxy.NO_PROXY,AppProxy.DIRECT.select(URI.create("https://sub.example.test/")).get(0));
    }
    @Test public void isClosedWalksCause() {
        GrokCore.Failure inner=AppProxy.closed();
        Exception wrapped=new RuntimeException(new java.io.IOException(inner));
        assertTrue(AppProxy.isClosed(wrapped));
    }
}
