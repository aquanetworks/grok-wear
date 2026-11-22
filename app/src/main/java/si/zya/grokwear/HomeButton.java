package si.zya.grokwear;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Watch-local choice of which Bot the Home / assistant button calls.
 * Empty id follows the account's primary Bot (the previous default).
 */
final class HomeButton {
    static final String TAG="grokwear-home";
    static final String PREFS="grokwear_ui";
    static final String KEY_ID="home_bot_id";
    static final String KEY_NAME="home_bot_name";
    static final String PRIMARY="Основной бот";
    static final String PRIMARY_OPTION="Основной бот (по умолчанию)";
    static final String ROW="Кнопка Home";
    static final String ASSIGN="Назначить на кнопку Home";
    static final String ASSIGNED="На кнопке Home";

    static final class Choice {
        final String id,name;
        Choice(String id,String name){
            this.id=id==null?"":id;
            this.name=name==null?"":name;
        }
        boolean followsPrimary(){return id.isEmpty();}
    }
    static final class Resolved {
        final JSONObject bot;final boolean stale,followsPrimary;
        Resolved(JSONObject bot,boolean stale,boolean followsPrimary){
            this.bot=bot;this.stale=stale;this.followsPrimary=followsPrimary;
        }
        boolean picker(){return bot==null;}
    }

    static Choice load(Context context){
        if(context==null)return new Choice("","");
        try{
            SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            return new Choice(prefs.getString(KEY_ID,""),prefs.getString(KEY_NAME,""));
        }catch(RuntimeException unavailable){return new Choice("","");}
    }
    static void save(Context context,String id,String name){
        if(context==null)return;
        try{
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString(KEY_ID,id==null?"":id).putString(KEY_NAME,name==null?"":name).apply();
        }catch(RuntimeException ignored){}
        log("set "+(id==null||id.isEmpty()?"primary":id));
    }
    static void clear(Context context){
        if(context==null)return;
        try{context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY_ID).remove(KEY_NAME).apply();}
        catch(RuntimeException ignored){}
    }
    static boolean isChosen(Choice choice,String botId){
        return choice!=null&&!choice.id.isEmpty()&&choice.id.equals(botId);
    }
    static String label(Choice choice,JSONArray roster){
        if(choice==null||choice.followsPrimary())return PRIMARY;
        JSONObject found=find(roster,choice.id);
        if(found!=null){
            String name=found.optString("name","");
            return name.isEmpty()?PRIMARY:name;
        }
        return choice.name.isEmpty()?PRIMARY:choice.name;
    }

    /**
     * Chosen id in the roster wins; a missing id is stale (caller clears prefs) and falls back to
     * {@code primary}; no choice uses primary; neither → picker.
     */
    static Resolved resolve(Choice choice,JSONArray roster,JSONObject primary){
        if(choice!=null&&!choice.followsPrimary()){
            JSONObject found=find(roster,choice.id);
            if(found!=null)return new Resolved(found,false,false);
            log("stale "+choice.id+" → primary");
            return new Resolved(primary,true,true);
        }
        return new Resolved(primary,false,true);
    }
    static JSONObject find(JSONArray roster,String id){
        if(roster==null||id==null||id.isEmpty())return null;
        for(int i=0;i<roster.length();i++){
            JSONObject bot=roster.optJSONObject(i);
            if(bot!=null&&id.equals(bot.optString("id")))return bot;
        }
        return null;
    }
    /** Pinned first (pin order), then the rest of the roster. No duplicates. */
    static List<JSONObject> picker(JSONArray roster,JSONArray pinIds){
        ArrayList<JSONObject> result=new ArrayList<>();
        HashSet<String> added=new HashSet<>();
        if(pinIds!=null)for(int p=0;p<pinIds.length();p++){
            JSONObject bot=find(roster,pinIds.optString(p));
            if(bot!=null&&added.add(bot.optString("id")))result.add(bot);
        }
        if(roster!=null)for(int i=0;i<roster.length();i++){
            JSONObject bot=roster.optJSONObject(i);
            if(bot!=null&&added.add(bot.optString("id")))result.add(bot);
        }
        return result;
    }
    static void log(String line){
        if(line==null||line.isEmpty())return;
        try{android.util.Log.i(TAG,line.length()>200?line.substring(0,200):line);}catch(Throwable ignored){}
    }
}
