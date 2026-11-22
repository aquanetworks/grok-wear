package com.aquanetworks.watchvpn

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/** Wear tile: tap opens the app (toggle extra). */
class WearVpnTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = ResolvableFuture.create<TileBuilders.Tile>()
        val snap = VpnRuntime.snapshot()
        val on = snap.connected() || snap.wantConnected
        val title = if (on) "Прокси вкл" else "Прокси выкл"
        val sub = when {
            snap.error.isNotEmpty() -> "ошибка"
            snap.selected.isNotEmpty() -> snap.selected
            else -> "Watch VPN"
        }
        val click = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .addKeyToExtraMapping(
                                "toggle",
                                ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build(),
                            )
                            .build()
                    )
                    .build()
            )
            .build()
        val column = LayoutElementBuilders.Column.Builder()
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(click).build())
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(title)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(18f))
                            .setColor(ColorBuilders.argb(if (on) 0xFF30D158.toInt() else 0xFFFFFFFF.toInt()))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(sub)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(12f))
                            .setColor(ColorBuilders.argb(0xFF8E8E93.toInt()))
                            .build()
                    )
                    .build()
            )
            .build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(column)
            )
            .build()
        future.set(tile)
        return future
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        future.set(ResourceBuilders.Resources.Builder().setVersion("1").build())
        return future
    }
}
