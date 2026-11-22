package com.aquanetworks.watchvpn;

import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile (Wear OS 5 / Samsung). Toggles the system VPN. */
public final class QsTileService extends TileService {
    private final VpnRuntime.Listener listener=this::render;
    @Override public void onStartListening(){
        VpnRuntime.addListener(listener);
        render(VpnRuntime.snapshot());
    }
    @Override public void onStopListening(){VpnRuntime.removeListener(listener);}
    @Override public void onClick(){
        Snapshot snap=VpnRuntime.snapshot();
        if(snap.connected()||snap.wantConnected)VpnRuntime.disconnect();
        else VpnRuntime.connect();
    }
    private void render(Snapshot snap){
        Tile tile=getQsTile();
        if(tile==null)return;
        boolean on=snap.connected()||snap.wantConnected;
        tile.setLabel("Watch VPN");
        if(Build.VERSION.SDK_INT>=29)tile.setSubtitle(on?(snap.selected.isEmpty()?SystemProxy.ENDPOINT:snap.selected):"Off");
        tile.setState(on?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
