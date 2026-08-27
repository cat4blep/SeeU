package dev.keryeshka.voxyseeu.neoforge.network;

import dev.keryeshka.voxyseeu.api.addon.protocol.AddonBusCodec;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AddonDataPayload(AddonEnvelope envelope) implements CustomPacketPayload {
    private static final String CHANNEL = "seeu:addons_data";

    public static final Type<AddonDataPayload> TYPE = new Type<>(Identifier.parse(CHANNEL));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddonDataPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AddonDataPayload decode(RegistryFriendlyByteBuf buf) {
            return new AddonDataPayload(AddonBusCodec.decodeData(buf));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AddonDataPayload payload) {
            AddonBusCodec.encodeData(buf, payload.envelope());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
