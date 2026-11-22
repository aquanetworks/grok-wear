package si.zya.grokwear;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public final class ClashConfigTest {
    @Test public void rendersLocalListenersAndMatch()throws Exception {
        List<Map<String,Object>> nodes=SubscriptionParser.parse(VlessUriTest.SYNTHETIC).nodes;
        String yaml=ClashConfig.render(nodes,"synthetic-nl",AppProxy.HTTP_PORT,AppProxy.SOCKS_PORT,AppProxy.API_PORT,"0123456789abcdef");
        assertTrue(yaml.contains("mixed-port: "+AppProxy.HTTP_PORT));
        assertTrue(yaml.contains("socks-port: "+AppProxy.SOCKS_PORT));
        assertTrue(yaml.contains("bind-address: 127.0.0.1"));
        assertTrue(yaml.contains("external-controller: 127.0.0.1:"+AppProxy.API_PORT));
        assertTrue(yaml.contains("geo-auto-update: false"));
        assertTrue(yaml.contains("MATCH,PROXY"));
        assertTrue(yaml.contains("type: vless"));
        assertTrue(yaml.contains("public-key:"));
        assertTrue(yaml.contains("short-id:"));
        assertTrue(yaml.contains("synthetic-nl"));
        Map<String,Object> reparsed=castMap(ClashYaml.load(yaml));
        assertEquals(Boolean.FALSE,reparsed.get("allow-lan"));
        assertEquals("rule",reparsed.get("mode"));
    }
    @Test public void refusesEmptyNodes() {
        try{ClashConfig.render(List.of(),"x",1,2,3,"0123456789abcdef");fail();}
        catch(Exception expected){assertTrue(expected.getMessage().toLowerCase().contains("server"));}
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> castMap(Object value){return (Map<String,Object>)value;}
}
