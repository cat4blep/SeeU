package dev.keryeshka.voxyseeu.fabric.network;

import dev.keryeshka.voxyseeu.api.addon.AddonLimits;
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
        PayloadTypeRegistry.playC2S().registerLarge(
                AddonControlPayload.TYPE,
                AddonControlPayload.STREAM_CODEC,
                AddonLimits.MAX_ENCODED_CONTROL_BYTES
        );
        PayloadTypeRegistry.playC2S().registerLarge(
                AddonDataPayload.TYPE,
                AddonDataPayload.STREAM_CODEC,
                AddonLimits.MAX_ENCODED_DATA_BYTES
        );
        PayloadTypeRegistry.playS2C().register(FarPlayersPayload.TYPE, FarPlayersPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().registerLarge(
                AddonControlPayload.TYPE,
                AddonControlPayload.STREAM_CODEC,
                AddonLimits.MAX_ENCODED_CONTROL_BYTES
        );
        PayloadTypeRegistry.playS2C().registerLarge(
                AddonDataPayload.TYPE,
                AddonDataPayload.STREAM_CODEC,
                AddonLimits.MAX_ENCODED_DATA_BYTES
        );
    }
}
