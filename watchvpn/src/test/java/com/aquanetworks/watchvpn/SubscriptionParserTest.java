package com.aquanetworks.watchvpn;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public final class SubscriptionParserTest {
    static final String VLESS=VlessUriTest.SYNTHETIC;
    static final String CLASH=""
        +"mixed-port: 7890\n"
        +"proxies:\n"
        +"  - name: \"synthetic-nl\"\n"
        +"    type: vless\n"
        +"    server: nl.example.test\n"
        +"    port: 443\n"
        +"    uuid: 11111111-1111-4111-a111-111111111111\n"
        +"    network: tcp\n"
        +"    tls: true\n"
        +"    udp: true\n"
        +"    flow: xtls-rprx-vision\n"
        +"    servername: www.cloudflare.com\n"
        +"    client-fingerprint: chrome\n"
        +"    reality-opts:\n"
        +"      public-key: 0123456789abcdef0123456789abcdef0123456789a\n"
        +"      short-id: abcd1234\n"
        +"  - name: synthetic-de\n"
        +"    type: trojan\n"
        +"    server: de.example.test\n"
        +"    port: 443\n"
        +"    password: synthetic-pass\n"
        +"    sni: de.example.test\n";

    @Test public void clashYamlNestedReality()throws Exception {
        SubscriptionParser.Result r=SubscriptionParser.parse(CLASH);
        assertEquals("clash",r.kind);
        assertEquals(2,r.nodes.size());
        Map<String,Object> nl=r.nodes.get(0);
        assertEquals("vless",nl.get("type"));
        assertEquals("nl.example.test",nl.get("server"));
        assertEquals(443L,((Number)nl.get("port")).longValue());
        @SuppressWarnings("unchecked") Map<String,Object> reality=(Map<String,Object>)nl.get("reality-opts");
        assertEquals("0123456789abcdef0123456789abcdef0123456789a",reality.get("public-key"));
        assertEquals("abcd1234",reality.get("short-id"));
        assertEquals("synthetic-de",r.nodes.get(1).get("name"));
    }
    @Test public void v2rayBase64UriList()throws Exception {
        String body=VLESS+"\ntrojan://synthetic-pass@de.example.test:443?sni=de.example.test#synthetic-de\n";
        String encoded=Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
        SubscriptionParser.Result r=SubscriptionParser.parse(encoded);
        assertEquals("v2ray-uri",r.kind);
        assertEquals(2,r.nodes.size());
        assertEquals("vless",r.nodes.get(0).get("type"));
        assertEquals("trojan",r.nodes.get(1).get("type"));
    }
    @Test public void plainUriList()throws Exception {
        SubscriptionParser.Result r=SubscriptionParser.parse(VLESS+"\n");
        assertEquals(1,r.nodes.size());
        assertEquals("synthetic-nl",r.nodes.get(0).get("name"));
    }
    @Test public void v2RayTunUrlIsRejectedAsBody() {
        try{SubscriptionParser.parse("https://sub.example.test/api/v1/client/subscribe?token=synthetic");fail();}
        catch(Exception expected){assertTrue(expected.getMessage().toLowerCase().contains("url"));}
    }
    @Test public void httpsUrlNormalized()throws Exception {
        assertEquals("https://sub.example.test/s",SubscriptionParser.normalizeUrl(" https://sub.example.test/s "));
    }
    @Test public void httpUrlRejected() {
        try{SubscriptionParser.normalizeUrl("http://sub.example.test/s");fail();}
        catch(Exception expected){assertTrue(expected.getMessage().contains("HTTPS"));}
    }
    @Test public void uniqueNames()throws Exception {
        List<Map<String,Object>> nodes=SubscriptionParser.parse(VLESS+"\n"+VLESS).nodes;
        assertEquals(2,nodes.size());
        assertEquals("synthetic-nl",nodes.get(0).get("name"));
        assertEquals("synthetic-nl (2)",nodes.get(1).get("name"));
    }
    @Test public void roundTripJson()throws Exception {
        SubscriptionParser.Result r=SubscriptionParser.parse(CLASH);
        List<Map<String,Object>> again=SubscriptionParser.fromJson(SubscriptionParser.toJson(r.nodes));
        assertEquals(r.nodes.size(),again.size());
        assertEquals(r.nodes.get(0).get("uuid"),again.get(0).get("uuid"));
    }
}
