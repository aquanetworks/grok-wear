package com.aquanetworks.watchvpn;

import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

/** Local mihomo REST on 127.0.0.1. Never routed through the TUN (UID excluded). */
final class MihomoApi {
    private static final OkHttpClient HTTP=new OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .retryOnConnectionFailure(false)
        .connectTimeout(2,TimeUnit.SECONDS).readTimeout(16,TimeUnit.SECONDS)
        .writeTimeout(4,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).build();
    private final String secret;private final int port;
    MihomoApi(int port,String secret){this.port=port;this.secret=secret==null?"":secret;}
    JSONObject version()throws Exception{return get("/version");}
    void select(String group,String name)throws Exception{
        put("/proxies/"+encode(group),new JSONObject().put("name",name));
    }
    int delay(String name,int timeoutMs)throws Exception{
        DelayResult result=delayResult(name,timeoutMs);
        if(result.ok())return result.delayMs;
        throw WatchFail.of(result.error);
    }
    DelayResult delayResult(String name,int timeoutMs)throws Exception{
        String path="/proxies/"+encode(name)+"/delay?url=https://www.gstatic.com/generate_204&timeout="+Math.max(500,timeoutMs);
        Raw raw=executeRaw(new Request.Builder().url(url(path)).get());
        return DelayResult.parse(name,raw.code,raw.body);
    }
    boolean ready(){
        try{JSONObject v=version();return v!=null&&(v.has("version")||v.has("meta"));}
        catch(Exception ignored){return false;}
    }
    private JSONObject get(String path)throws Exception{
        Raw raw=executeRaw(new Request.Builder().url(url(path)).get());
        if(raw.code<200||raw.code>=300)throw WatchFail.of(DelayResult.messageOf(raw.code,raw.body));
        if(raw.body==null||raw.body.trim().isEmpty())return new JSONObject();
        return new JSONObject(raw.body);
    }
    private JSONObject put(String path,JSONObject body)throws Exception{
        RequestBody payload=RequestBody.create(body.toString(),MediaType.get("application/json; charset=utf-8"));
        Raw raw=executeRaw(new Request.Builder().url(url(path)).put(payload));
        if(raw.code==204)return new JSONObject();
        if(raw.code<200||raw.code>=300)throw WatchFail.of(DelayResult.messageOf(raw.code,raw.body));
        if(raw.body==null||raw.body.trim().isEmpty())return new JSONObject();
        return new JSONObject(raw.body);
    }
    private Raw executeRaw(Request.Builder request)throws Exception{
        if(!secret.isEmpty())request.header("Authorization","Bearer "+secret);
        try(Response response=HTTP.newCall(request.build()).execute()){
            String text="";
            if(response.body()!=null)text=response.body().string();
            if(response.code()==204)return new Raw(204,"");
            return new Raw(response.code(),text==null?"":text);
        }
    }
    private String url(String path){return "http://127.0.0.1:"+port+(path.startsWith("/")?path:"/"+path);}
    private static String encode(String name){
        return java.net.URLEncoder.encode(name,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20");
    }
    static final class Raw {
        final int code;final String body;
        Raw(int code,String body){this.code=code;this.body=body==null?"":body;}
    }
    static final class DelayResult {
        final String name;final int delayMs;final String error;
        DelayResult(String name,int delayMs,String error){
            this.name=name==null?"":name;
            this.delayMs=delayMs;
            this.error=error;
        }
        boolean ok(){return error==null&&delayMs>0;}
        String summary(){return ok()?name+" "+delayMs+"ms":name+" FAIL "+error;}
        static DelayResult parse(String name,int code,String body){
            String text=body==null?"":body.trim();
            if(code>=200&&code<300){
                try{
                    JSONObject json=text.isEmpty()?new JSONObject():new JSONObject(text);
                    if(json.has("delay")){
                        int delay=json.getInt("delay");
                        if(delay>0)return new DelayResult(name,delay,null);
                    }
                    String msg=messageOf(code,text);
                    return new DelayResult(name,0,msg.isEmpty()?"Latency test returned no delay.":msg);
                }catch(Exception e){
                    return new DelayResult(name,0,text.isEmpty()?"Latency test returned no delay.":text);
                }
            }
            return new DelayResult(name,0,messageOf(code,text));
        }
        static String messageOf(int code,String body){
            String text=body==null?"":body.trim();
            String fromJson=jsonMessage(text);
            if(!fromJson.isEmpty())return fromJson;
            if(code==504||code==408)return text.isEmpty()?"Timeout":text;
            if(text.isEmpty())return "HTTP "+code;
            return text;
        }
        static String jsonMessage(String text){
            if(text==null||text.isEmpty()||text.charAt(0)!='{')return "";
            try{
                JSONObject json=new JSONObject(text);
                String message=json.optString("message","").trim();
                if(!message.isEmpty())return message;
                String error=json.optString("error","").trim();
                if(!error.isEmpty())return error;
                String msg=json.optString("msg","").trim();
                if(!msg.isEmpty())return msg;
            }catch(Exception ignored){}
            return "";
        }
    }
}
