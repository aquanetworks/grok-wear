package com.aquanetworks.watchvpn;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public final class ConfigDumpTest {
    @Test public void redactsUuidAndRealityKeys()throws Exception {
        List<Map<String,Object>> nodes=SubscriptionParser.parse(VlessUriTest.SYNTHETIC).nodes;
        String yaml=TunConfig.render(nodes,"synthetic-nl","0123456789abcdef",false,false);
        String redacted=ConfigDump.redact(yaml);
        assertFalse(redacted.contains("11111111-1111-4111-a111-111111111111"));
        assertFalse(redacted.contains("0123456789abcdef0123456789abcdef0123456789a"));
        assertFalse(redacted.contains("abcd1234")&&redacted.contains("short-id: abcd"));
        assertTrue(redacted.contains("uuid: ***"));
        assertTrue(redacted.contains("public-key: ***"));
        assertTrue(redacted.contains("short-id: ***"));
        assertTrue(redacted.contains("secret: ***"));
        assertTrue(redacted.contains("server: nl.example.test"));
        assertTrue(redacted.contains("mixed-port: 7890"));
        ConfigDump.log(yaml);
        assertEquals(redacted,ConfigDump.last());
    }
}
