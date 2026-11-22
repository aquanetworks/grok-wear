package com.aquanetworks.watchvpn;

import java.util.ArrayList;
import java.util.List;

/** Downloads a subscription, retrying User-Agents until real (non-placeholder) nodes appear. */
final class SubscriptionImport {
    interface Transport {
        Reply get(String url,UserAgents.Preset preset)throws Exception;
    }
    static final class Reply {
        final String body,profileTitle,userinfo;
        Reply(String body,String profileTitle,String userinfo){
            this.body=body==null?"":body;
            this.profileTitle=profileTitle==null?"":profileTitle;
            this.userinfo=userinfo==null?"":userinfo;
        }
    }
    static final class Outcome {
        final SubscriptionParser.Result parsed;
        final UserAgents.Preset preset;
        final String profileTitle,userinfo,warning;
        final boolean placeholdersOnly,usedFallback;
        final List<String> tried;
        Outcome(SubscriptionParser.Result parsed,UserAgents.Preset preset,String profileTitle,String userinfo,
                String warning,boolean placeholdersOnly,boolean usedFallback,List<String> tried){
            this.parsed=parsed;this.preset=preset;this.profileTitle=profileTitle;this.userinfo=userinfo;
            this.warning=warning==null?"":warning;
            this.placeholdersOnly=placeholdersOnly;this.usedFallback=usedFallback;
            this.tried=tried==null?List.of():tried;
        }
    }
    static Outcome pull(String url,String preferredId,Transport transport)throws Exception{
        if(transport==null)throw WatchFail.of("Subscription transport is missing.");
        String normalized=SubscriptionParser.normalizeUrl(url);
        List<UserAgents.Preset> order=UserAgents.order(preferredId);
        ArrayList<String> tried=new ArrayList<>();
        Outcome last=null;
        Exception lastError=null;
        for(UserAgents.Preset preset:order){
            try{
                Reply reply=transport.get(normalized,preset);
                SubscriptionParser.Result parsed=SubscriptionParser.parse(reply.body);
                int real=Placeholders.realCount(parsed.nodes);
                tried.add(preset.id+" "+real+"/"+parsed.nodes.size());
                boolean stubs=real==0;
                String warning=parsed.warning;
                if(!reply.profileTitle.isEmpty())warning=(warning.isEmpty()?"":warning+" ")+"profile-title: "+reply.profileTitle;
                if(stubs)warning=(warning.isEmpty()?"":warning+" ")+"placeholders for "+preset.label;
                else if(!preset.id.equals(UserAgents.byId(preferredId).id))
                    warning=(warning.isEmpty()?"":warning+" ")+"UA fallback: "+preset.label;
                last=new Outcome(parsed,preset,reply.profileTitle,reply.userinfo,warning.trim(),stubs,tried.size()>1&&!stubs,new ArrayList<>(tried));
                if(!stubs)return last;
            }catch(Exception e){
                lastError=e;
                tried.add(preset.id+" fail");
                AppLog.line("sub parse/fetch "+preset.id+" failed");
            }
        }
        if(last!=null){
            String warning="Panel returned placeholder servers. Tried "+String.join(", ",tried)+". Pick another client UA and update.";
            return new Outcome(last.parsed,last.preset,last.profileTitle,last.userinfo,warning,true,true,tried);
        }
        if(lastError!=null)throw lastError;
        throw WatchFail.of("Could not download the subscription.");
    }
}
