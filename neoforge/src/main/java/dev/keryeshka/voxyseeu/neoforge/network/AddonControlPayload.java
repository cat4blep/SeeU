package dev.keryeshka.voxyseeu.neoforge.network;

import dev.keryeshka.voxyseeu.api.addon.protocol.AddonBusCodec;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AddonControlPayload(AddonControlMessage message) implements CustomPacketPayload {
    private static final String CHANNEL = "seeu:addons_control";

    public static final Type<AddonControlPayload> TYPE = new Type<>(Identifier.parse(CHANNEL));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddonControlPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AddonControlPayload decode(RegistryFriendlyByteBuf buf) {
            return new AddonControlPayload(AddonBusCodec.decodeControl(buf));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AddonControlPayload payload) {
            AddonBusCodec.encodeControl(buf, payload.message());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
