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
        @SuppressWarnings("unchecked") Map<String,Object> root=(Map<String,Object>)ClashYaml.load(yaml);
        @SuppressWarnings("unchecked") Map<String,Object> tun=(Map<String,Object>)root.get("tun");
        assertEquals(Boolean.TRUE,tun.get("enable"));
        assertEquals(Boolean.FALSE,tun.get("auto-route"));
        assertEquals((long)TunConfig.TUN_FD,((Number)tun.get("file-descriptor")).longValue());
    }
    @Test public void apiOnlyOmitsTun()throws Exception {
        List<Map<String,Object>> nodes=SubscriptionParser.parse(VlessUriTest.SYNTHETIC).nodes;
        String yaml=TunConfig.render(nodes,"synthetic-nl","0123456789abcdef",false,false);
        assertFalse(yaml.contains("file-descriptor:"));
        Object root=ClashYaml.load(yaml);
        @SuppressWarnings("unchecked") Map<String,Object> map=(Map<String,Object>)root;
        assertFalse(map.containsKey("tun"));
        assertEquals(Boolean.FALSE,map.get("ipv6"));
    }
    @Test public void refusesEmptyNodes() {
        try{TunConfig.render(List.of(),"x","0123456789abcdef",true,true);fail();}
        catch(Exception expected){assertTrue(expected.getMessage().toLowerCase().contains("server"));}
    }
}
