package dev.keryeshka.voxyseeu.fabric;

import dev.keryeshka.voxyseeu.common.server.SeeUServerConfig;
import dev.keryeshka.voxyseeu.fabric.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.fabric.network.FabricPayloads;
import dev.keryeshka.voxyseeu.fabric.server.FabricFarPlayerService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

public final class VoxySeeUFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricPayloads.register();

        FabricFarPlayerService service = new FabricFarPlayerService(
                SeeUServerConfig.load(FabricLoader.getInstance().getConfigDir())
        );
        service.register();

        ServerPlayNetworking.registerGlobalReceiver(ClientHelloPayload.TYPE, (payload, context) ->
                service.acceptHello(context.player(), payload.packet()));
    }
}
