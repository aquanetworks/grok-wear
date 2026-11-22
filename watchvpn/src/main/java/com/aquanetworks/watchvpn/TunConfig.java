package com.aquanetworks.watchvpn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clash Meta config for a system VpnService TUN.
 * {@code auto-route} is off because Android already installs routes.
 * {@code file-descriptor} is the inherited TUN fd (always 3 in the child).
 */
final class TunConfig {
    static final String GROUP="PROXY";
    static final int MIXED_PORT=17890,SOCKS_PORT=17891,API_PORT=19090,TUN_FD=3,MTU=1400;
    static final String INET4="172.19.0.1/30",INET6="fdfe:dcba:9876::1/126";
    static final String INET4_ADDR="172.19.0.1",INET6_ADDR="fdfe:dcba:9876::1";
    static final int INET4_PREFIX=30,INET6_PREFIX=126;
    static final String DNS4="8.8.8.8",DNS4B="1.1.1.1",DNS6="2001:4860:4860::8888";
    static final String FAKE_IP="198.18.0.1/16";

    static String render(List<Map<String,Object>> nodes,String selected,String secret,boolean tun,boolean ipv6)throws Exception{
        if(nodes==null||nodes.isEmpty())throw WatchFail.of("Pick a server from the subscription first.");
        if(secret==null||secret.length()<16)throw WatchFail.of("VPN API secret is missing.");
        ArrayList<String> names=new ArrayList<>();
        for(Map<String,Object> node:nodes){
            String name=String.valueOf(node.get("name"));
            if(name==null||name.isEmpty())throw WatchFail.of("A server is missing its name.");
            names.add(name);
        }
        if(selected==null||selected.isEmpty()||!names.contains(selected))selected=names.get(0);
        LinkedHashMap<String,Object> root=new LinkedHashMap<>();
        root.put("mixed-port",MIXED_PORT);
        root.put("socks-port",SOCKS_PORT);
        root.put("allow-lan",Boolean.FALSE);
        root.put("bind-address","127.0.0.1");
        root.put("mode","rule");
        root.put("log-level","error");
        root.put("ipv6",Boolean.valueOf(ipv6));
        root.put("external-controller","127.0.0.1:"+API_PORT);
        root.put("secret",secret);
        root.put("geodata-mode",Boolean.FALSE);
        root.put("geo-auto-update",Boolean.FALSE);
        root.put("find-process-mode","off");
        root.put("tcp-concurrent",Boolean.TRUE);
        root.put("unified-delay",Boolean.TRUE);
        if(tun)root.put("tun",tunMap(ipv6));
        root.put("dns",dnsMap(ipv6));
        root.put("proxies",nodes);
        LinkedHashMap<String,Object> group=new LinkedHashMap<>();
        group.put("name",GROUP);group.put("type","select");group.put("proxies",names);
        ArrayList<Map<String,Object>> groups=new ArrayList<>();groups.add(group);
        root.put("proxy-groups",groups);
        ArrayList<String> rules=new ArrayList<>();
        rules.add("DST-PORT,"+MIXED_PORT+",REJECT");
        rules.add("DST-PORT,"+SOCKS_PORT+",REJECT");
        rules.add("DST-PORT,"+API_PORT+",REJECT");
        rules.add("MATCH,"+GROUP);
        root.put("rules",rules);
        return ClashYaml.dump(root)+"\n# selected: "+selected+"\n";
    }
    static Map<String,Object> tunMap(boolean ipv6){
        LinkedHashMap<String,Object> tun=new LinkedHashMap<>();
        tun.put("enable",Boolean.TRUE);
        tun.put("stack","mixed");
        tun.put("auto-route",Boolean.FALSE);
        tun.put("auto-detect-interface",Boolean.FALSE);
        tun.put("strict-route",Boolean.FALSE);
        tun.put("mtu",MTU);
        tun.put("file-descriptor",TUN_FD);
        ArrayList<String> v4=new ArrayList<>();v4.add(INET4);tun.put("inet4-address",v4);
        if(ipv6){ArrayList<String> v6=new ArrayList<>();v6.add(INET6);tun.put("inet6-address",v6);}
        ArrayList<String> hijack=new ArrayList<>();
        hijack.add("any:53");hijack.add("tcp://any:53");
        tun.put("dns-hijack",hijack);
        return tun;
    }
    static Map<String,Object> dnsMap(boolean ipv6){
        LinkedHashMap<String,Object> dns=new LinkedHashMap<>();
        dns.put("enable",Boolean.TRUE);
        dns.put("ipv6",Boolean.valueOf(ipv6));
        dns.put("enhanced-mode","fake-ip");
        dns.put("fake-ip-range",FAKE_IP);
        dns.put("use-hosts",Boolean.TRUE);
        ArrayList<String> filter=new ArrayList<>();
        filter.add("*.lan");filter.add("localhost");filter.add("*.local");
        dns.put("fake-ip-filter",filter);
        ArrayList<String> defaults=new ArrayList<>();
        defaults.add(DNS4);defaults.add(DNS4B);
        dns.put("default-nameserver",defaults);
        ArrayList<String> nameservers=new ArrayList<>();
        nameservers.add("https://dns.google/dns-query");
        nameservers.add("https://cloudflare-dns.com/dns-query");
        dns.put("nameserver",nameservers);
        return dns;
    }
    static String selectedName(List<Map<String,Object>> nodes,String selected){
        if(nodes==null||nodes.isEmpty())return "";
        for(Map<String,Object> node:nodes)if(selected!=null&&selected.equals(String.valueOf(node.get("name"))))return selected;
        return String.valueOf(nodes.get(0).get("name"));
    }
}
