package si.zya.grokwear;

import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

/** Local mihomo REST API on 127.0.0.1. Always {@link HttpClients#direct()} so it cannot recurse into the tunnel. */
final class ProxyApi {
    private static final OkHttpClient HTTP=HttpClients.direct()
        .connectTimeout(2,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS)
        .writeTimeout(4,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build();
    private final String secret;private final int port;
    ProxyApi(int port,String secret){this.port=port;this.secret=secret==null?"":secret;}
    JSONObject version()throws Exception{return get("/version");}
    JSONObject proxies()throws Exception{return get("/proxies");}
    void select(String group,String name)throws Exception{
        put("/proxies/"+encode(group),new JSONObject().put("name",name));
    }
    int delay(String name,int timeoutMs)throws Exception{
        JSONObject body=get("/proxies/"+encode(name)+"/delay?url=https://www.gstatic.com/generate_204&timeout="+Math.max(500,timeoutMs));
        if(body.has("delay"))return body.getInt("delay");
        throw new GrokCore.Failure("Latency test returned no delay.",true);
    }
    boolean ready(){
        try{JSONObject v=version();return v!=null&&(v.has("version")||v.has("meta"));}
        catch(Exception ignored){return false;}
    }
    private JSONObject get(String path)throws Exception{return execute(new Request.Builder().url(url(path)).get());}
    private JSONObject put(String path,JSONObject body)throws Exception{
        RequestBody payload=RequestBody.create(body.toString(),MediaType.get("application/json; charset=utf-8"));
        return execute(new Request.Builder().url(url(path)).put(payload));
    }
    private JSONObject execute(Request.Builder request)throws Exception{
        if(!secret.isEmpty())request.header("Authorization","Bearer "+secret);
        try(Response response=HTTP.newCall(request.build()).execute()){
            if(response.code()==204||response.body()==null)return new JSONObject();
            String text=response.body().string();
            if(response.code()<200||response.code()>=300)throw new GrokCore.Failure("VPN control port failed (HTTP "+response.code()+").",true);
            if(text==null||text.trim().isEmpty())return new JSONObject();
            return new JSONObject(text);
        }
    }
    private String url(String path){return "http://127.0.0.1:"+port+(path.startsWith("/")?path:"/"+path);}
    private static String encode(String name){
        return java.net.URLEncoder.encode(name,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20");
    }
}
