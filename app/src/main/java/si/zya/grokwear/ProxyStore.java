package si.zya.grokwear;

import android.content.Context;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Encrypted subscription URL + server list (Android Keystore via GrokVault). */
final class ProxyStore {
    private final GrokVault vault;
    ProxyStore(Context context){vault=new GrokVault(context,".proxy-v1");}
    synchronized JSONObject snapshot()throws Exception{return GrokCore.copy(vault.read());}
    synchronized String subscriptionUrl()throws Exception{return vault.read().optString("subscriptionUrl","");}
    synchronized List<Map<String,Object>> nodes()throws Exception{return SubscriptionParser.fromJson(vault.read().optJSONArray("nodes"));}
    synchronized String selected()throws Exception{return vault.read().optString("selected","");}
    synchronized boolean wantConnected()throws Exception{return vault.read().optBoolean("wantConnected",false);}
    synchronized String secret()throws Exception{
        JSONObject root=vault.read();String secret=root.optString("apiSecret","");
        if(secret.length()>=16)return secret;
        secret=newSecret();root.put("apiSecret",secret);vault.write(root);return secret;
    }
    synchronized void importUrl(String url,SubscriptionParser.Result parsed)throws Exception{
        JSONObject root=vault.read();
        root.put("subscriptionUrl",url==null?"":url);
        root.put("kind",parsed.kind);
        root.put("nodes",SubscriptionParser.toJson(parsed.nodes));
        String selected=root.optString("selected","");
        boolean keep=false;for(Map<String,Object> node:parsed.nodes)if(selected.equals(String.valueOf(node.get("name"))))keep=true;
        if(!keep)root.put("selected",parsed.nodes.isEmpty()?"":String.valueOf(parsed.nodes.get(0).get("name")));
        root.put("importedAtMs",System.currentTimeMillis());
        vault.write(root);
    }
    synchronized void importBody(String body)throws Exception{importUrl("",SubscriptionParser.parse(body));}
    synchronized void select(String name)throws Exception{
        JSONObject root=vault.read();
        JSONArray nodes=root.optJSONArray("nodes");if(nodes==null||nodes.length()==0)throw new GrokCore.Failure("Import a subscription first.",true);
        boolean found=false;for(int i=0;i<nodes.length();i++)if(name.equals(nodes.getJSONObject(i).optString("name")))found=true;
        if(!found)throw new GrokCore.Failure("That server is not in the subscription.",true);
        root.put("selected",name);vault.write(root);
    }
    synchronized void setWantConnected(boolean on)throws Exception{
        JSONObject root=vault.read();root.put("wantConnected",on);vault.write(root);
    }
    synchronized void clear()throws Exception{vault.write(new JSONObject());}
    synchronized String yaml()throws Exception{
        JSONObject root=vault.read();
        List<Map<String,Object>> nodes=SubscriptionParser.fromJson(root.optJSONArray("nodes"));
        String secret=root.optString("apiSecret","");if(secret.length()<16){secret=newSecret();root.put("apiSecret",secret);vault.write(root);}
        return ClashConfig.render(nodes,root.optString("selected",""),AppProxy.HTTP_PORT,AppProxy.SOCKS_PORT,AppProxy.API_PORT,secret);
    }
    private static String newSecret(){
        byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);
        return UUID.nameUUIDFromBytes(bytes).toString().replace("-","");
    }
}
