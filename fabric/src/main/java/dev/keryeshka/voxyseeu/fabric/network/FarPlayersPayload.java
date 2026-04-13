package dev.keryeshka.voxyseeu.fabric.network;

import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;
import dev.keryeshka.voxyseeu.common.protocol.PacketCodec;
import dev.keryeshka.voxyseeu.common.protocol.ProtocolConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FarPlayersPayload(FarPlayersPacket packet) implements CustomPacketPayload {
    public static final Type<FarPlayersPayload> TYPE =
            new Type<>(Identifier.parse(ProtocolConstants.PLAYERS_CHANNEL));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarPlayersPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FarPlayersPayload decode(RegistryFriendlyByteBuf buf) {
                    return new FarPlayersPayload(PacketCodec.decodeFarPlayers(buf));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FarPlayersPayload payload) {
                    PacketCodec.encodeFarPlayers(buf, payload.packet());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
