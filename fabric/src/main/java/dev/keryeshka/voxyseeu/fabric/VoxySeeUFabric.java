package dev.keryeshka.voxyseeu.fabric;

import dev.keryeshka.voxyseeu.fabric.config.VoxySeeUServerConfig;
import dev.keryeshka.voxyseeu.fabric.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.fabric.network.FabricPayloads;
import dev.keryeshka.voxyseeu.fabric.server.FabricFarPlayerService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class VoxySeeUFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricPayloads.register();

        FabricFarPlayerService service = new FabricFarPlayerService(VoxySeeUServerConfig.load());
        service.register();

        ServerPlayNetworking.registerGlobalReceiver(ClientHelloPayload.TYPE, (payload, context) ->
                service.handleHello(context.player(), payload.packet()));
    }
}
