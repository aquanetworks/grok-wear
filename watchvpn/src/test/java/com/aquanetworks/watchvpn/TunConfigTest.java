package com.aquanetworks.watchvpn;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public final class TunConfigTest {
    @Test public void tunYamlHasFdAndFakeIp()throws Exception {
        List<Map<String,Object>> nodes=SubscriptionParser.parse(VlessUriTest.SYNTHETIC).nodes;
        String yaml=TunConfig.render(nodes,"synthetic-nl","0123456789abcdef",true,true);
        assertTrue(yaml,yaml.contains("file-descriptor: "+TunConfig.TUN_FD));
        assertTrue(yaml.contains("auto-route: false"));
        assertTrue(yaml.contains("auto-detect-interface: false"));
        assertTrue(yaml.contains("stack: mixed"));
        assertTrue(yaml.contains("enhanced-mode: fake-ip"));
        assertTrue(yaml.contains("fake-ip-range: "+TunConfig.FAKE_IP));
        assertTrue(yaml.contains("inet4-address:"));
        assertTrue(yaml.contains(TunConfig.INET4));
        assertTrue(yaml.contains("inet6-address:"));
        assertTrue(yaml.contains(TunConfig.INET6));
        assertTrue(yaml.contains("dns-hijack:"));
        assertTrue(yaml.contains("any:53"));
        assertTrue(yaml.contains("MATCH,PROXY"));
        assertTrue(yaml.contains("public-key:"));
        assertTrue(yaml.contains("short-id:"));
        assertTrue(yaml.contains("mixed-port: "+TunConfig.MIXED_PORT));
        assertTrue(yaml.contains("log-level: debug"));
        @SuppressWarnings("unchecked") Map<String,Object> root=(Map<String,Object>)ClashYaml.load(yaml);
        @SuppressWarnings("unchecked") Map<String,Object> tun=(Map<String,Object>)root.get("tun");
        assertEquals(Boolean.TRUE,tun.get("enable"));
        assertEquals(Boolean.FALSE,tun.get("auto-route"));
        assertEquals((long)TunConfig.TUN_FD,((Number)tun.get("file-descriptor")).longValue());
        @SuppressWarnings("unchecked") Map<String,Object> dns=(Map<String,Object>)root.get("dns");
        assertEquals("fake-ip",dns.get("enhanced-mode"));
        assertPlainDns(dns);
    }
    @Test public void apiOnlyOmitsTunUsesRedirHostAndPlainDns()throws Exception {
        List<Map<String,Object>> nodes=SubscriptionParser.parse(VlessUriTest.SYNTHETIC).nodes;
        String yaml=TunConfig.render(nodes,"synthetic-nl","0123456789abcdef",false,false);
        assertFalse(yaml.contains("file-descriptor:"));
        Object root=ClashYaml.load(yaml);
        @SuppressWarnings("unchecked") Map<String,Object> map=(Map<String,Object>)root;
        assertFalse(map.containsKey("tun"));
        assertEquals(Boolean.FALSE,map.get("ipv6"));
        assertEquals(Boolean.FALSE,map.get("allow-lan"));
        assertEquals("127.0.0.1",map.get("bind-address"));
        assertEquals("rule",map.get("mode"));
        assertEquals("debug",map.get("log-level"));
        assertEquals((long)TunConfig.MIXED_PORT,((Number)map.get("mixed-port")).longValue());
        assertEquals(7890L,((Number)map.get("mixed-port")).longValue());
        assertTrue(yaml.contains("MATCH,PROXY"));
        assertTrue(yaml.contains("proxy-server-nameserver:"));
        assertFalse(yaml.contains("dns.google"));
        assertFalse(yaml.contains("https://"));
        @SuppressWarnings("unchecked") Map<String,Object> dns=(Map<String,Object>)map.get("dns");
        assertEquals(Boolean.TRUE,dns.get("enable"));
        assertEquals("redir-host",dns.get("enhanced-mode"));
        assertPlainDns(dns);
    }
    @Test public void logLevelOverride()throws Exception {
        List<Map<String,Object>> nodes=SubscriptionParser.parse(VlessUriTest.SYNTHETIC).nodes;
        String yaml=TunConfig.render(nodes,"synthetic-nl","0123456789abcdef",false,false,TunConfig.TUN_FD,"info");
        assertTrue(yaml.contains("log-level: info"));
        assertEquals("debug",TunConfig.normalizeLogLevel("nope"));
    }
    @Test public void refusesEmptyNodes() {
        try{TunConfig.render(List.of(),"x","0123456789abcdef",true,true);fail();}
        catch(Exception expected){assertTrue(expected.getMessage().toLowerCase().contains("server"));}
    }
    @SuppressWarnings("unchecked")
    private static void assertPlainDns(Map<String,Object> dns){
        List<Object> nameserver=(List<Object>)dns.get("nameserver");
        List<Object> defaults=(List<Object>)dns.get("default-nameserver");
        List<Object> proxyNs=(List<Object>)dns.get("proxy-server-nameserver");
        assertEquals(List.of("1.1.1.1","8.8.8.8"),nameserver);
        assertEquals(List.of("1.1.1.1","8.8.8.8"),defaults);
        assertEquals(List.of("1.1.1.1","8.8.8.8"),proxyNs);
    }
}
