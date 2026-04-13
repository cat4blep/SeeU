package dev.keryeshka.voxyseeu.fabric.network;

import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.protocol.PacketCodec;
import dev.keryeshka.voxyseeu.common.protocol.ProtocolConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientHelloPayload(ClientHelloPacket packet) implements CustomPacketPayload {
    public static final Type<ClientHelloPayload> TYPE =
            new Type<>(Identifier.parse(ProtocolConstants.HELLO_CHANNEL));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientHelloPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClientHelloPayload decode(RegistryFriendlyByteBuf buf) {
                    return new ClientHelloPayload(PacketCodec.decodeClientHello(buf));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ClientHelloPayload payload) {
                    PacketCodec.encodeClientHello(buf, payload.packet());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
