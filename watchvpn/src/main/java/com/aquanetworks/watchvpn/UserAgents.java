package com.aquanetworks.watchvpn;

import java.util.ArrayList;
import java.util.List;

/**
 * Subscription User-Agents. Remnawave matches {@code ^v2raytun} (see backend PR #52);
 * v2RayTun Android is documented as {@code v2raytun/android}.
 */
final class UserAgents {
    static final String DEFAULT_ID="v2raytun";
    static final class Preset {
        final String id,label,userAgent,accept;
        Preset(String id,String label,String userAgent,String accept){
            this.id=id;this.label=label;this.userAgent=userAgent;this.accept=accept;
        }
    }
    static final List<Preset> ALL=List.of(
        new Preset("v2raytun","v2RayTun","v2raytun/android","*/*"),
        new Preset("v2rayng","v2rayNG","v2rayNG/1.10.23","*/*"),
        new Preset("happ","Happ","Happ/3.6.1","*/*"),
        new Preset("clash-verge","clash-verge","clash-verge/v2.4.3","*/*"),
        new Preset("clashmeta","ClashMeta","ClashMeta/1.19.34","text/plain, text/yaml, application/json, */*"),
        new Preset("mihomo","mihomo","mihomo/1.19.34","text/plain, text/yaml, */*"),
        new Preset("shadowrocket","Shadowrocket","Shadowrocket/2592 CFNetwork/1568.100.1 Darwin/24.0.0","*/*")
    );
    static Preset byId(String id){
        if(id==null||id.isEmpty())return ALL.get(0);
        for(Preset p:ALL)if(p.id.equals(id))return p;
        return ALL.get(0);
    }
    /** Preferred first, then the rest — used for auto-fallback. */
    static List<Preset> order(String preferredId){
        Preset first=byId(preferredId);
        ArrayList<Preset> out=new ArrayList<>();
        out.add(first);
        for(Preset p:ALL)if(!p.id.equals(first.id))out.add(p);
        return out;
    }
}
