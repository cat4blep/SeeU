package dev.keryeshka.voxyseeu.fabric.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AddonControlPayload(FabricAddonFragment fragment) implements CustomPacketPayload {
    private static final String CHANNEL = "seeu:addons_control";

    public static final Type<AddonControlPayload> TYPE = new Type<>(ResourceLocation.parse(CHANNEL));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddonControlPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AddonControlPayload decode(RegistryFriendlyByteBuf buf) {
            AddonControlPayload payload = new AddonControlPayload(FabricAddonFragment.fromOwned(
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readByteArray(FabricAddonWireLimits.FRAGMENT_BYTES)
            ));
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Addon control fragment contains trailing bytes");
            }
            return payload;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AddonControlPayload payload) {
            FabricAddonFragment fragment = payload.fragment();
            buf.writeVarLong(fragment.messageId());
            buf.writeVarInt(fragment.totalLength());
            buf.writeVarInt(fragment.fragmentIndex());
            buf.writeVarInt(fragment.fragmentCount());
            buf.writeByteArray(fragment.payloadForWire());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
