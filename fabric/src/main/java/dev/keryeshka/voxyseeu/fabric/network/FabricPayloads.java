package dev.keryeshka.voxyseeu.fabric.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class FabricPayloads {
    private static boolean registered;

    private FabricPayloads() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        PayloadTypeRegistry.playC2S().register(ClientHelloPayload.TYPE, ClientHelloPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FarPlayersPayload.TYPE, FarPlayersPayload.STREAM_CODEC);
    }
}
