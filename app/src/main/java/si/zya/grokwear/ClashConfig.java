package si.zya.grokwear;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds an app-scoped mihomo config: local mixed HTTP+SOCKS, no geo download, MATCH through the selected node. */
final class ClashConfig {
    static final String GROUP="PROXY";
    static String render(List<Map<String,Object>> nodes,String selected,int httpPort,int socksPort,int apiPort,String secret)throws Exception{
        if(nodes==null||nodes.isEmpty())throw new GrokCore.Failure("Pick a server from the subscription first.",true);
        if(httpPort<=0||socksPort<=0||apiPort<=0)throw new GrokCore.Failure("Proxy ports are invalid.",true);
        if(secret==null||secret.length()<16)throw new GrokCore.Failure("Proxy API secret is missing.",true);
        ArrayList<String> names=new ArrayList<>();
        for(Map<String,Object> node:nodes){
            String name=String.valueOf(node.get("name"));
            if(name==null||name.isEmpty())throw new GrokCore.Failure("A server is missing its name.",true);
            names.add(name);
        }
        if(selected==null||selected.isEmpty()||!names.contains(selected))selected=names.get(0);
        LinkedHashMap<String,Object> root=new LinkedHashMap<>();
        root.put("mixed-port",httpPort);
        root.put("socks-port",socksPort);
        root.put("allow-lan",Boolean.FALSE);
        root.put("bind-address","127.0.0.1");
        root.put("mode","rule");
        root.put("log-level","error");
        root.put("ipv6",Boolean.FALSE);
        root.put("external-controller","127.0.0.1:"+apiPort);
        root.put("secret",secret);
        root.put("geodata-mode",Boolean.FALSE);
        root.put("geo-auto-update",Boolean.FALSE);
        root.put("find-process-mode","off");
        root.put("tcp-concurrent",Boolean.TRUE);
        root.put("unified-delay",Boolean.TRUE);
        root.put("proxies",nodes);
        LinkedHashMap<String,Object> group=new LinkedHashMap<>();
        group.put("name",GROUP);group.put("type","select");group.put("proxies",names);
        ArrayList<Map<String,Object>> groups=new ArrayList<>();groups.add(group);
        root.put("proxy-groups",groups);
        ArrayList<String> rules=new ArrayList<>();
        rules.add("DST-PORT,"+httpPort+",REJECT");
        rules.add("DST-PORT,"+socksPort+",REJECT");
        rules.add("DST-PORT,"+apiPort+",REJECT");
        rules.add("MATCH,"+GROUP);
        root.put("rules",rules);
        String yaml=ClashYaml.dump(root);
        // Select the chosen node by putting it first; mihomo's select group uses the first unless API overrides.
        return yaml+"\n# selected: "+selected+"\n";
    }
    static String selectedName(List<Map<String,Object>> nodes,String selected){
        if(nodes==null||nodes.isEmpty())return "";
        for(Map<String,Object> node:nodes)if(selected!=null&&selected.equals(String.valueOf(node.get("name"))))return selected;
        return String.valueOf(nodes.get(0).get("name"));
    }
}
