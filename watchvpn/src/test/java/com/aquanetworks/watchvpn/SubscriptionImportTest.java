package com.aquanetworks.watchvpn;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public final class SubscriptionImportTest {
    static final String STUB=""
        +"vless://11111111-1111-4111-a111-111111111111@0.0.0.0:443?security=reality"
        +"&pbk=0123456789abcdef0123456789abcdef0123456789a&sid=abcd#%E2%9D%8C%20%D0%9F%D1%80%D0%B8%D0%BB%D0%BE%D0%B6%D0%B5%D0%BD%D0%B8%D0%B5%20%D0%BD%D0%B5%20%D0%BF%D0%BE%D0%B4%D0%B4%D0%B5%D1%80%D0%B6%D0%B8%D0%B2%D0%B0%D0%B5%D1%82%D1%81%D1%8F\n"
        +"vless://11111111-1111-4111-a111-111111111111@127.0.0.1:443?security=none#%E2%9C%85%20%D0%94%D0%BE%D1%81%D1%82%D1%83%D0%BF%20%D0%B2%20Telegram\n";
    static final String REAL=VlessUriTest.SYNTHETIC+"\n";

    @Test public void fallbackSkipsPlaceholderUa()throws Exception {
        Map<String,String> bodies=new HashMap<>();
        bodies.put("clashmeta",Base64.getEncoder().encodeToString(STUB.getBytes(StandardCharsets.UTF_8)));
        bodies.put("v2raytun",REAL);
        SubscriptionImport.Outcome out=SubscriptionImport.pull(
            "https://sub.example.test/s","clashmeta",(url,preset)->reply(bodies.getOrDefault(preset.id,STUB))
        );
        assertFalse(out.placeholdersOnly);
        assertEquals("v2raytun",out.preset.id);
        assertTrue(out.usedFallback);
        assertEquals(1,Placeholders.realCount(out.parsed.nodes));
        assertEquals("synthetic-nl",out.parsed.nodes.get(0).get("name"));
        assertTrue(out.tried.toString().contains("clashmeta"));
    }
    @Test public void preferredV2raytunUsedFirst()throws Exception {
        SubscriptionImport.Outcome out=SubscriptionImport.pull(
            "https://sub.example.test/s",UserAgents.DEFAULT_ID,(url,preset)->{
                if(!"v2raytun".equals(preset.id))throw new AssertionError("should stop at v2raytun");
                return reply(REAL);
            }
        );
        assertEquals("v2raytun",out.preset.id);
        assertFalse(out.placeholdersOnly);
        assertFalse(out.usedFallback);
    }
    @Test public void allPlaceholdersKeptWithWarning()throws Exception {
        SubscriptionImport.Outcome out=SubscriptionImport.pull(
            "https://sub.example.test/s","v2raytun",(url,preset)->reply(STUB)
        );
        assertTrue(out.placeholdersOnly);
        assertEquals(2,out.parsed.nodes.size());
        assertTrue(out.warning.toLowerCase().contains("placeholder"));
        assertTrue(out.tried.size()>=2);
    }
    @Test public void defaultUaStartsWithV2raytun() {
        assertTrue(UserAgents.byId(UserAgents.DEFAULT_ID).userAgent.toLowerCase().startsWith("v2raytun"));
        assertTrue(UserAgents.byId("v2raytun").userAgent.contains("/"));
    }
    @Test public void profileTitleBase64() {
        assertEquals("Netz",SubClient.decodeTitle("base64:TmV0eg=="));
        assertEquals("plain",SubClient.decodeTitle("plain"));
    }
    private static SubscriptionImport.Reply reply(String body){
        return new SubscriptionImport.Reply(body,"Netz","upload=0; download=0; total=1; expire=0");
    }
}
