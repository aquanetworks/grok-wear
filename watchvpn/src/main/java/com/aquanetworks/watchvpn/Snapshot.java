package com.aquanetworks.watchvpn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Snapshot {
    final String phase,error,selected,urlMasked,kind,consent,userAgentId,profileTitle,userinfo,logText;
    final boolean wantConnected,consentReady,appopsAllowed,engineAlive,ipv6;
    final int lastDelayMs;
    final List<ServerRow> servers;
    Snapshot(String phase,String error,String selected,String urlMasked,String kind,String consent,
             boolean wantConnected,boolean consentReady,boolean appopsAllowed,boolean engineAlive,boolean ipv6,
             int lastDelayMs,List<ServerRow> servers,String userAgentId,String profileTitle,String userinfo,String logText){
        this.phase=phase==null?"idle":phase;
        this.error=error==null?"":error;
        this.selected=selected==null?"":selected;
        this.urlMasked=urlMasked==null?"":urlMasked;
        this.kind=kind==null?"":kind;
        this.consent=consent==null?"":consent;
        this.wantConnected=wantConnected;
        this.consentReady=consentReady;
        this.appopsAllowed=appopsAllowed;
        this.engineAlive=engineAlive;
        this.ipv6=ipv6;
        this.lastDelayMs=lastDelayMs;
        this.servers=servers==null?Collections.emptyList():Collections.unmodifiableList(new ArrayList<>(servers));
        this.userAgentId=userAgentId==null?UserAgents.DEFAULT_ID:userAgentId;
        this.profileTitle=profileTitle==null?"":profileTitle;
        this.userinfo=userinfo==null?"":userinfo;
        this.logText=logText==null?"":logText;
    }
    static Snapshot empty(){
        return new Snapshot("idle","","","","","",false,false,false,false,true,-1,Collections.emptyList(),UserAgents.DEFAULT_ID,"","","");
    }
    boolean connected(){return "connected".equals(phase);}
    boolean busy(){return "connecting".equals(phase)||"stopping".equals(phase)||"updating".equals(phase)||"testing".equals(phase);}
    static final class ServerRow {
        final String name,type;final int delayMs;final boolean selected;
        ServerRow(String name,String type,int delayMs,boolean selected){
            this.name=name;this.type=type;this.delayMs=delayMs;this.selected=selected;
        }
    }
}
