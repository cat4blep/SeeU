package dev.keryeshka.voxyseeu.common.protocol;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PacketCodec {
    private PacketCodec() {
    }

    public static void encodeClientHello(ByteBuf buf, ClientHelloPacket packet) {
        writeVarInt(buf, packet.protocolVersion());
        buf.writeBoolean(packet.enabled());
        writeVarInt(buf, packet.maximumRenderDistanceBlocks());
        writeVarInt(buf, packet.minimumProxyDistanceBlocks());
        buf.writeBoolean(packet.renderNameTags());
        buf.writeBoolean(packet.shareSelf());
        writeVarInt(buf, packet.shareMaximumDistanceBlocks());
    }

    public static ClientHelloPacket decodeClientHello(ByteBuf buf) {
        return new ClientHelloPacket(
                readVarInt(buf),
                buf.readBoolean(),
                readVarInt(buf),
                readVarInt(buf),
                buf.readBoolean(),
                buf.readBoolean(),
                readVarInt(buf)
        );
    }

    public static void encodeFarPlayers(ByteBuf buf, FarPlayersPacket packet) {
        writeUtf(buf, packet.dimensionKey());
        writeVarInt(buf, packet.players().size());
        for (FarPlayerSnapshot player : packet.players()) {
            writeUuid(buf, player.uuid());
            writeUtf(buf, player.name());
            buf.writeDouble(player.x());
            buf.writeDouble(player.y());
            buf.writeDouble(player.z());
            buf.writeFloat(player.bodyYaw());
            buf.writeFloat(player.headYaw());
            buf.writeFloat(player.pitch());
            buf.writeBoolean(player.sneaking());
            buf.writeBoolean(player.gliding());
            buf.writeBoolean(player.swimming());
            encodeItem(buf, player.mainHand());
            encodeItem(buf, player.offHand());
            encodeItem(buf, player.feet());
            encodeItem(buf, player.legs());
            encodeItem(buf, player.chest());
            encodeItem(buf, player.head());
            if (player.vehicle() == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                encodeVehicle(buf, player.vehicle());
            }
        }
    }

    public static FarPlayersPacket decodeFarPlayers(ByteBuf buf) {
        String dimensionKey = readUtf(buf);
        int size = readVarInt(buf);
        List<FarPlayerSnapshot> players = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            players.add(new FarPlayerSnapshot(
                    readUuid(buf),
                    readUtf(buf),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    decodeItem(buf),
                    decodeItem(buf),
                    decodeItem(buf),
                    decodeItem(buf),
                    decodeItem(buf),
                    decodeItem(buf),
                    buf.readBoolean() ? decodeVehicle(buf) : null
            ));
        }
        return new FarPlayersPacket(dimensionKey, List.copyOf(players));
    }

    private static void encodeItem(ByteBuf buf, FarItemSnapshot item) {
        FarItemSnapshot snapshot = item == null ? FarItemSnapshot.EMPTY : item;
        writeUtf(buf, snapshot.itemId());
        writeVarInt(buf, snapshot.count());
    }

    private static FarItemSnapshot decodeItem(ByteBuf buf) {
        return new FarItemSnapshot(readUtf(buf), readVarInt(buf));
    }

    private static void encodeVehicle(ByteBuf buf, FarVehicleSnapshot vehicle) {
        writeUuid(buf, vehicle.uuid());
        writeUtf(buf, vehicle.entityTypeId());
        buf.writeDouble(vehicle.x());
        buf.writeDouble(vehicle.y());
        buf.writeDouble(vehicle.z());
        buf.writeFloat(vehicle.yaw());
        buf.writeFloat(vehicle.pitch());
    }

    private static FarVehicleSnapshot decodeVehicle(ByteBuf buf) {
        return new FarVehicleSnapshot(
                readUuid(buf),
                readUtf(buf),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    private static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    private static void writeUtf(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buf) {
        int length = readVarInt(buf);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    private static int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;

        while (true) {
            if (position >= 35) {
                throw new IllegalArgumentException("VarInt is too big");
            }

            byte current = buf.readByte();
            value |= (current & 127) << position;
            if ((current & 128) == 0) {
                return value;
            }
            position += 7;
        }
    }
}
