package dev.keryeshka.seeu.extra.neoforge;

import dev.keryeshka.seeu.extra.SeeUExtra;
import dev.keryeshka.seeu.extra.server.SeeUExtraServerConfig;
import dev.keryeshka.seeu.extra.server.SeeUExtraServerEndpoint;
import dev.keryeshka.voxyseeu.api.addon.SeeUServerAddons;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(SeeUExtra.MOD_ID)
public final class SeeUExtraNeoForge {
    private final SeeUExtraServerEndpoint endpoint;

    public SeeUExtraNeoForge() {
        endpoint = new SeeUExtraServerEndpoint(
                SeeUExtraServerConfig.load(FMLPaths.CONFIGDIR.get())
        );
        SeeUServerAddons.getInstance().register(SeeUExtra.DESCRIPTOR, endpoint);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        endpoint.tick(event.getServer());
    }
}
