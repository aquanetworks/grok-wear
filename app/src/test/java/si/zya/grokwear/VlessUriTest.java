package si.zya.grokwear;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public final class VlessUriTest {
    static final String SYNTHETIC="vless://11111111-1111-4111-a111-111111111111@nl.example.test:443"
        +"?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.cloudflare.com"
        +"&fp=chrome&pbk=0123456789abcdef0123456789abcdef0123456789a&sid=abcd1234&type=tcp#synthetic-nl";

    @Test public void parsesRealityParams()throws Exception {
        VlessUri node=VlessUri.parse(SYNTHETIC);
        assertEquals("synthetic-nl",node.name);
        assertEquals("11111111-1111-4111-a111-111111111111",node.uuid);
        assertEquals("nl.example.test",node.host);
        assertEquals(443,node.port);
        assertEquals("xtls-rprx-vision",node.flow);
        assertEquals("reality",node.security);
        assertEquals("www.cloudflare.com",node.sni);
        assertEquals("chrome",node.fp);
        assertEquals("0123456789abcdef0123456789abcdef0123456789a",node.pbk);
        assertEquals("abcd1234",node.sid);
        assertEquals("tcp",node.network);
        assertTrue(node.reality());
    }
    @Test public void clashMapKeepsRealityOpts()throws Exception {
        Map<String,Object> clash=VlessUri.parse(SYNTHETIC).toClash();
        assertEquals("vless",clash.get("type"));
        assertEquals(Boolean.TRUE,clash.get("tls"));
        assertEquals("www.cloudflare.com",clash.get("servername"));
        assertEquals("chrome",clash.get("client-fingerprint"));
        @SuppressWarnings("unchecked") Map<String,Object> reality=(Map<String,Object>)clash.get("reality-opts");
        assertEquals("0123456789abcdef0123456789abcdef0123456789a",reality.get("public-key"));
        assertEquals("abcd1234",reality.get("short-id"));
    }
    @Test public void rejectsMissingServer() {
        try{VlessUri.parse("vless://11111111-1111-4111-a111-111111111111@");fail();}
        catch(Exception expected){assertTrue(expected.getMessage(),expected.getMessage().toLowerCase().contains("vless")||expected instanceof IllegalArgumentException);}
    }
    @Test public void rejectsNonUuid() {
        try{VlessUri.parse("vless://not-a-uuid@example.test:443?security=reality&pbk=x&sid=y");fail();}
        catch(Exception expected){assertTrue(expected.getMessage().toLowerCase().contains("uuid"));}
    }
    @Test public void defaultPortIs443()throws Exception {
        VlessUri node=VlessUri.parse("vless://11111111-1111-4111-a111-111111111111@example.test?security=tls&sni=example.test&type=ws&path=/vws&host=example.test#ws");
        assertEquals(443,node.port);
        assertEquals("ws",node.network);
        assertEquals("/vws",node.path);
        assertFalse(node.reality());
    }
}
