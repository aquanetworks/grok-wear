package si.zya.grokwear;

import org.junit.After;
import org.junit.Test;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import static org.junit.Assert.*;

public final class AppProxyTest {
    @After public void reset(){
        AppProxy.probe=AppProxy::tcp;
        AppProxy.setEndpoint(AppProxy.DEFAULT_HOST,AppProxy.DEFAULT_PORT);
        AppProxy.invalidate();
    }

    @Test public void selectThrowsWhenPortClosed() {
        AppProxy.probe=(h,p,t)->false;
        AppProxy.invalidate();
        try{AppProxy.TUNNEL.select(URI.create("https://api.x.ai/"));fail();}
        catch(AppProxy.Closed closed){assertEquals(AppProxy.OFF,closed.getMessage());}
    }
    @Test public void requireFailClosedRussian() {
        AppProxy.probe=(h,p,t)->false;
        AppProxy.invalidate();
        try{AppProxy.require();fail();}
        catch(GrokCore.Failure failed){
            assertEquals("proxy",failed.code);
            assertEquals(AppProxy.OFF,failed.getMessage());
            assertTrue(failed.getMessage().contains("Watch VPN"));
        }
    }
    @Test public void httpConnectUnresolvedWhenOpen() {
        AppProxy.probe=(h,p,t)->true;
        AppProxy.invalidate();
        List<Proxy> proxies=AppProxy.TUNNEL.select(URI.create("https://api.x.ai/voice"));
        assertEquals(1,proxies.size());
        assertEquals(Proxy.Type.HTTP,proxies.get(0).type());
        InetSocketAddress addr=(InetSocketAddress)proxies.get(0).address();
        assertEquals("127.0.0.1",addr.getHostString());
        assertEquals(7890,addr.getPort());
        assertTrue(addr.isUnresolved());
        assertTrue(AppProxy.ready());
    }
    @Test public void neverReturnsDirectOrSystemProxy() {
        AppProxy.probe=(h,p,t)->true;
        AppProxy.invalidate();
        for(String url:new String[]{"https://api.x.ai/","https://api2.cursor.sh/","wss://api.x.ai/v1","https://api.github.com/"}){
            List<Proxy> proxies=AppProxy.TUNNEL.select(URI.create(url));
            assertEquals(Proxy.Type.HTTP,proxies.get(0).type());
            assertNotEquals(Proxy.NO_PROXY,proxies.get(0));
        }
    }
    @Test public void okHttpFailsClosedWithoutProxy()throws Exception {
        AppProxy.probe=(h,p,t)->false;
        AppProxy.invalidate();
        OkHttpClient client=HttpClients.tunneled().build();
        try{
            client.newCall(new Request.Builder().url("https://api.x.ai/").build()).execute();
            fail();
        }catch(Exception failed){assertTrue(AppProxy.isClosed(failed));}
    }
    @Test public void dnsThrowsForGrokHosts()throws Exception {
        try{AppProxy.BLOCK_DIRECT.lookup("api.x.ai");fail();}
        catch(UnknownHostException blocked){assertTrue(blocked.getMessage().contains("Direct DNS"));}
        try{AppProxy.BLOCK_DIRECT.lookup("api2.cursor.sh");fail();}
        catch(UnknownHostException blocked){assertTrue(blocked.getMessage().toLowerCase().contains("dns"));}
        try{AppProxy.BLOCK_DIRECT.lookup("cursor.com");fail();}
        catch(UnknownHostException ignored){}
        List<java.net.InetAddress> loop=AppProxy.BLOCK_DIRECT.lookup("127.0.0.1");
        assertFalse(loop.isEmpty());
    }
    @Test public void grokHostDetection() {
        assertTrue(AppProxy.grokHost("api.x.ai"));
        assertTrue(AppProxy.grokHost("voice.x.ai"));
        assertTrue(AppProxy.grokHost("api2.cursor.sh"));
        assertTrue(AppProxy.grokHost("gateway.cursorvm.com"));
        assertTrue(AppProxy.grokHost("x.com"));
        assertFalse(AppProxy.grokHost("127.0.0.1"));
        assertFalse(AppProxy.grokHost("localhost"));
    }
    @Test public void readyIsCachedAboutTwoSeconds() {
        AtomicInteger hits=new AtomicInteger();
        AppProxy.probe=(h,p,t)->{hits.incrementAndGet();return true;};
        AppProxy.invalidate();
        assertTrue(AppProxy.ready());
        assertTrue(AppProxy.ready());
        assertEquals(1,hits.get());
    }
    @Test public void isClosedWalksCause() {
        GrokCore.Failure inner=AppProxy.closed();
        Exception wrapped=new RuntimeException(new java.io.IOException(inner));
        assertTrue(AppProxy.isClosed(wrapped));
    }
    @Test public void endpointParse() {
        assertEquals("127.0.0.1",AppProxy.normalizeHost(""));
        assertEquals("10.0.0.2",AppProxy.normalizeHost("10.0.0.2:80"));
        assertEquals(7890,AppProxy.parsePort("nope"));
        assertEquals(1080,AppProxy.parsePort("1080"));
        AppProxy.setEndpoint("10.1.2.3",1080);
        assertEquals("10.1.2.3:1080",AppProxy.endpoint());
    }
    @Test public void tunneledClientUsesOurSelectorAndDns() {
        OkHttpClient client=HttpClients.tunneled().build();
        assertSame(AppProxy.TUNNEL,client.proxySelector());
        assertSame(AppProxy.BLOCK_DIRECT,client.dns());
        assertSame(AppProxy.LOCAL_ONLY,client.socketFactory());
        assertNull(client.proxy());
    }
    @Test public void guardedSocketBlocksGrokHosts()throws Exception {
        java.net.Socket socket=AppProxy.LOCAL_ONLY.createSocket();
        try{
            socket.connect(java.net.InetSocketAddress.createUnresolved("api.x.ai",443),50);
            fail();
        }catch(java.net.ConnectException blocked){
            assertTrue(blocked.getMessage().contains("Direct socket"));
        }
        try{
            AppProxy.check("cursor.com",443);
            fail();
        }catch(java.net.ConnectException blocked){
            assertTrue(blocked.getMessage().contains("cursor.com"));
        }
        assertTrue(AppProxy.allowedEndpoint("127.0.0.1",7890));
        assertFalse(AppProxy.allowedEndpoint("127.0.0.1",443));
        assertFalse(AppProxy.allowedEndpoint("api.x.ai",7890));
    }
}
