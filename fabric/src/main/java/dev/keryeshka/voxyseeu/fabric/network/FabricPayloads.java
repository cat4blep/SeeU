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

        PayloadTypeRegistry.serverboundPlay().register(ClientHelloPayload.TYPE, ClientHelloPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().registerLarge(
                AddonControlPayload.TYPE,
                AddonControlPayload.STREAM_CODEC,
                AddonLimits.MAX_ENCODED_CONTROL_BYTES
        );
        PayloadTypeRegistry.serverboundPlay().registerLarge(
                AddonDataPayload.TYPE,
                AddonDataPayload.STREAM_CODEC,
                AddonLimits.MAX_ENCODED_DATA_BYTES
        );
        PayloadTypeRegistry.clientboundPlay().register(FarPlayersPayload.TYPE, FarPlayersPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().registerLarge(
                AddonControlPayload.TYPE,
                AddonControlPayload.STREAM_CODEC,
                AddonLimits.MAX_ENCODED_CONTROL_BYTES
        );
        PayloadTypeRegistry.clientboundPlay().registerLarge(
                AddonDataPayload.TYPE,
                AddonDataPayload.STREAM_CODEC,
                AddonLimits.MAX_ENCODED_DATA_BYTES
        );
    }
}
