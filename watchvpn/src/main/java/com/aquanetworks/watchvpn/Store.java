package com.aquanetworks.watchvpn;

import android.content.Context;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Persisted subscription, selected node, and always-on preference. */
final class Store {
    private static Store INSTANCE;
    private final Vault vault;
    static synchronized Store get(Context context){
        if(INSTANCE==null)INSTANCE=new Store(context.getApplicationContext());
        return INSTANCE;
    }
    Store(Context context){this.vault=new Vault(context);}
    synchronized JSONObject snapshot()throws Exception{return vault.read();}
    synchronized String subscriptionUrl()throws Exception{return vault.read().optString("subscriptionUrl","");}
    synchronized List<Map<String,Object>> nodes()throws Exception{return SubscriptionParser.fromJson(vault.read().optJSONArray("nodes"));}
    synchronized String selected()throws Exception{return vault.read().optString("selected","");}
    synchronized boolean wantConnected()throws Exception{return vault.read().optBoolean("wantConnected",false);}
    synchronized boolean ipv6()throws Exception{return vault.read().optBoolean("ipv6",true);}
    /** TUN is off by default — Samsung Wear often has no IVpnManager. */
    synchronized boolean tunMode()throws Exception{return vault.read().optBoolean("tunMode",false);}
    synchronized void setTunMode(boolean on)throws Exception{
        JSONObject root=vault.read();root.put("tunMode",on);vault.write(root);
    }
    synchronized String secret()throws Exception{
        JSONObject root=vault.read();
        String secret=root.optString("apiSecret","");
        if(secret.length()>=16)return secret;
        secret=newSecret();root.put("apiSecret",secret);vault.write(root);return secret;
    }
    synchronized String userAgentId()throws Exception{
        String id=vault.read().optString("userAgentId",UserAgents.DEFAULT_ID);
        return id==null||id.isEmpty()?UserAgents.DEFAULT_ID:id;
    }
    synchronized void setUserAgentId(String id)throws Exception{
        JSONObject root=vault.read();
        root.put("userAgentId",id==null||id.isEmpty()?UserAgents.DEFAULT_ID:id);
        vault.write(root);
    }
    synchronized String profileTitle()throws Exception{return vault.read().optString("profileTitle","");}
    synchronized String userinfo()throws Exception{return vault.read().optString("userinfo","");}
    synchronized void importUrl(String url,SubscriptionParser.Result parsed)throws Exception{
        importUrl(url,parsed,userAgentId(),"","");
    }
    synchronized void importUrl(String url,SubscriptionParser.Result parsed,String uaId,String title,String userinfo)throws Exception{
        JSONObject root=vault.read();
        root.put("subscriptionUrl",url==null?"":url);
        root.put("kind",parsed.kind);
        root.put("nodes",SubscriptionParser.toJson(parsed.nodes));
        if(uaId!=null&&!uaId.isEmpty())root.put("userAgentId",uaId);
        root.put("profileTitle",title==null?"":title);
        root.put("userinfo",userinfo==null?"":userinfo);
        String selected=root.optString("selected","");
        boolean keep=false;
        for(Map<String,Object> node:parsed.nodes)if(selected.equals(String.valueOf(node.get("name"))))keep=true;
        if(!keep){
            java.util.List<Map<String,Object>> real=Placeholders.realNodes(parsed.nodes);
            String pick=real.isEmpty()?(parsed.nodes.isEmpty()?"":String.valueOf(parsed.nodes.get(0).get("name"))):String.valueOf(real.get(0).get("name"));
            root.put("selected",pick);
        }
        root.put("importedAtMs",System.currentTimeMillis());
        vault.write(root);
    }
    synchronized void select(String name)throws Exception{
        JSONObject root=vault.read();
        JSONArray nodes=root.optJSONArray("nodes");
        if(nodes==null||nodes.length()==0)throw WatchFail.of("Import a subscription first.");
        boolean found=false;
        for(int i=0;i<nodes.length();i++)if(name.equals(nodes.getJSONObject(i).optString("name")))found=true;
        if(!found)throw WatchFail.of("That server is not in the subscription.");
        root.put("selected",name);vault.write(root);
    }
    synchronized void setWantConnected(boolean on)throws Exception{
        JSONObject root=vault.read();root.put("wantConnected",on);vault.write(root);
    }
    synchronized void setIpv6(boolean on)throws Exception{
        JSONObject root=vault.read();root.put("ipv6",on);vault.write(root);
    }
    synchronized void putDelays(Map<String,Integer> delays)throws Exception{
        JSONObject root=vault.read();
        JSONObject json=new JSONObject();
        for(Map.Entry<String,Integer> e:delays.entrySet())json.put(e.getKey(),e.getValue().intValue());
        root.put("delays",json);vault.write(root);
    }
    synchronized int delay(String name)throws Exception{
        JSONObject delays=vault.read().optJSONObject("delays");
        return delays==null?-1:delays.optInt(name,-1);
    }
    synchronized List<Integer> delayList(List<Map<String,Object>> nodes)throws Exception{
        JSONObject delays=vault.read().optJSONObject("delays");
        ArrayList<Integer> out=new ArrayList<>();
        for(Map<String,Object> node:nodes){
            String name=String.valueOf(node.get("name"));
            out.add(delays==null?-1:delays.optInt(name,-1));
        }
        return out;
    }
    synchronized String yaml(boolean tun,boolean ipv6)throws Exception{
        JSONObject root=vault.read();
        List<Map<String,Object>> nodes=SubscriptionParser.fromJson(root.optJSONArray("nodes"));
        String secret=root.optString("apiSecret","");
        if(secret.length()<16){secret=newSecret();root.put("apiSecret",secret);vault.write(root);}
        return TunConfig.render(nodes,root.optString("selected",""),secret,tun,ipv6);
    }
    private static String newSecret(){
        byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);
        return UUID.nameUUIDFromBytes(bytes).toString().replace("-","");
    }
}
