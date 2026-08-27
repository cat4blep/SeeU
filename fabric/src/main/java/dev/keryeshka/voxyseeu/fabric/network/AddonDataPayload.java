package dev.keryeshka.voxyseeu.fabric.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AddonDataPayload(FabricAddonFragment fragment) implements CustomPacketPayload {
    private static final String CHANNEL = "seeu:addons_data";

    public static final Type<AddonDataPayload> TYPE = new Type<>(ResourceLocation.parse(CHANNEL));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddonDataPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AddonDataPayload decode(RegistryFriendlyByteBuf buf) {
            AddonDataPayload payload = new AddonDataPayload(FabricAddonFragment.fromOwned(
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readByteArray(FabricAddonWireLimits.FRAGMENT_BYTES)
            ));
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Addon data fragment contains trailing bytes");
            }
            return payload;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AddonDataPayload payload) {
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
