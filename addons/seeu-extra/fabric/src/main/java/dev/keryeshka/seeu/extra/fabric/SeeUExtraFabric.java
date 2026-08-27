package dev.keryeshka.seeu.extra.fabric;

import dev.keryeshka.seeu.extra.SeeUExtra;
import dev.keryeshka.seeu.extra.server.SeeUExtraServerConfig;
import dev.keryeshka.seeu.extra.server.SeeUExtraServerEndpoint;
import dev.keryeshka.voxyseeu.api.addon.SeeUServerAddons;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SeeUExtraFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SeeUExtraServerEndpoint endpoint = new SeeUExtraServerEndpoint(
                SeeUExtraServerConfig.load(FabricLoader.getInstance().getConfigDir())
        );
        SeeUServerAddons.getInstance().register(SeeUExtra.DESCRIPTOR, endpoint);
        ServerTickEvents.END_SERVER_TICK.register(endpoint::tick);
    }
}
