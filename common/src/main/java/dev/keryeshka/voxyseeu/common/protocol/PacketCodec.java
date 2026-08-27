package dev.keryeshka.voxyseeu.common.protocol;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PacketCodec {
    public static final int MAX_PLAYERS_PER_PACKET = 4_096;
    public static final int MAX_UPDATE_INTERVAL_TICKS = 1_200;
    public static final int MAX_ITEM_COUNT = 1_000_000;
    public static final int MAX_DIMENSION_KEY_BYTES = 256;
    public static final int MAX_PLAYER_NAME_BYTES = 256;
    public static final int MAX_REGISTRY_ID_BYTES = 256;

    private PacketCodec() {
    }

    public static void encodeClientHello(ByteBuf buf, ClientHelloPacket packet) {
        writeVarInt(buf, packet.protocolVersion());
        buf.writeBoolean(packet.enabled());
        writeVarInt(buf, packet.maximumRenderDistanceBlocks());
        writeVarInt(buf, packet.minimumProxyDistanceBlocks());
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
                readVarInt(buf)
        );
    }

    public static void encodeFarPlayers(ByteBuf buf, FarPlayersPacket packet) {
        writeUtf(buf, packet.dimensionKey(), MAX_DIMENSION_KEY_BYTES, "dimension key");
        if (packet.sequence() < 0) {
            throw new IllegalArgumentException("Packet sequence cannot be negative: " + packet.sequence());
        }
        writeVarLong(buf, packet.sequence());
        requireRange(packet.updateIntervalTicks(), 1, MAX_UPDATE_INTERVAL_TICKS, "update interval");
        writeVarInt(buf, packet.updateIntervalTicks());
        requireRange(packet.players().size(), 0, MAX_PLAYERS_PER_PACKET, "player count");
        writeVarInt(buf, packet.players().size());
        for (FarPlayerSnapshot player : packet.players()) {
            writeUuid(buf, player.uuid());
            buf.writeDouble(requireFinite(player.x(), "player x"));
            buf.writeDouble(requireFinite(player.y(), "player y"));
            buf.writeDouble(requireFinite(player.z(), "player z"));
            buf.writeFloat(requireFinite(player.bodyYaw(), "player body yaw"));
            buf.writeFloat(requireFinite(player.headYaw(), "player head yaw"));
            buf.writeFloat(requireFinite(player.pitch(), "player pitch"));
            buf.writeBoolean(player.sneaking());
            buf.writeBoolean(player.gliding());
            buf.writeBoolean(player.swimming());
            if (player.vehicle() == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                encodeVehicle(buf, player.vehicle());
            }
            if (player.metadata() == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                encodeMetadata(buf, player.metadata());
            }
        }
    }

    public static FarPlayersPacket decodeFarPlayers(ByteBuf buf) {
        String dimensionKey = readUtf(buf, MAX_DIMENSION_KEY_BYTES, "dimension key");
        long sequence = readVarLong(buf);
        if (sequence < 0) {
            throw new IllegalArgumentException("Packet sequence cannot be negative: " + sequence);
        }
        int updateIntervalTicks = readVarInt(buf);
        requireRange(updateIntervalTicks, 1, MAX_UPDATE_INTERVAL_TICKS, "update interval");
        int size = readVarInt(buf);
        requireRange(size, 0, MAX_PLAYERS_PER_PACKET, "player count");
        List<FarPlayerSnapshot> players = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            players.add(new FarPlayerSnapshot(
                    readUuid(buf),
                    readFiniteDouble(buf, "player x"),
                    readFiniteDouble(buf, "player y"),
                    readFiniteDouble(buf, "player z"),
                    readFiniteFloat(buf, "player body yaw"),
                    readFiniteFloat(buf, "player head yaw"),
                    readFiniteFloat(buf, "player pitch"),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean() ? decodeVehicle(buf) : null,
                    buf.readBoolean() ? decodeMetadata(buf) : null
            ));
        }
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Far-player payload contains trailing data");
        }
        return new FarPlayersPacket(dimensionKey, sequence, updateIntervalTicks, List.copyOf(players));
    }

    private static void encodeMetadata(ByteBuf buf, FarPlayerMetadata metadata) {
        writeUtf(buf, metadata.name(), MAX_PLAYER_NAME_BYTES, "player name");
        encodeItem(buf, metadata.mainHand());
        encodeItem(buf, metadata.offHand());
        encodeItem(buf, metadata.feet());
        encodeItem(buf, metadata.legs());
        encodeItem(buf, metadata.chest());
        encodeItem(buf, metadata.head());
    }

    private static FarPlayerMetadata decodeMetadata(ByteBuf buf) {
        return new FarPlayerMetadata(
                readUtf(buf, MAX_PLAYER_NAME_BYTES, "player name"),
                decodeItem(buf),
                decodeItem(buf),
                decodeItem(buf),
                decodeItem(buf),
                decodeItem(buf),
                decodeItem(buf)
        );
    }

    private static void encodeItem(ByteBuf buf, FarItemSnapshot itemSnapshot) {
        requireRange(itemSnapshot.count(), 0, MAX_ITEM_COUNT, "item count");
        writeUtf(buf, itemSnapshot.itemId(), MAX_REGISTRY_ID_BYTES, "item id");
        writeVarInt(buf, itemSnapshot.count());
    }

    private static FarItemSnapshot decodeItem(ByteBuf buf) {
        String itemId = readUtf(buf, MAX_REGISTRY_ID_BYTES, "item id");
        int count = readVarInt(buf);
        requireRange(count, 0, MAX_ITEM_COUNT, "item count");
        return new FarItemSnapshot(itemId, count);
    }

    private static void encodeVehicle(ByteBuf buf, FarVehicleSnapshot vehicle) {
        writeUuid(buf, vehicle.uuid());
        writeUtf(buf, vehicle.entityTypeId(), MAX_REGISTRY_ID_BYTES, "vehicle entity type id");
        buf.writeDouble(requireFinite(vehicle.x(), "vehicle x"));
        buf.writeDouble(requireFinite(vehicle.y(), "vehicle y"));
        buf.writeDouble(requireFinite(vehicle.z(), "vehicle z"));
        buf.writeFloat(requireFinite(vehicle.yaw(), "vehicle yaw"));
        buf.writeFloat(requireFinite(vehicle.pitch(), "vehicle pitch"));
    }

    private static FarVehicleSnapshot decodeVehicle(ByteBuf buf) {
        return new FarVehicleSnapshot(
                readUuid(buf),
                readUtf(buf, MAX_REGISTRY_ID_BYTES, "vehicle entity type id"),
                readFiniteDouble(buf, "vehicle x"),
                readFiniteDouble(buf, "vehicle y"),
                readFiniteDouble(buf, "vehicle z"),
                readFiniteFloat(buf, "vehicle yaw"),
                readFiniteFloat(buf, "vehicle pitch")
        );
    }

    private static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    private static void writeUtf(ByteBuf buf, String value, int maximumBytes, String fieldName) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maximumBytes + " UTF-8 bytes");
        }
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buf, int maximumBytes, String fieldName) {
        int length = readVarInt(buf);
        if (length < 0 || length > maximumBytes) {
            throw new IllegalArgumentException(fieldName + " length is outside 0.." + maximumBytes + ": " + length);
        }
        if (length > buf.readableBytes()) {
            throw new IllegalArgumentException(fieldName + " length " + length + " exceeds the remaining payload");
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static double readFiniteDouble(ByteBuf buf, String fieldName) {
        return requireFinite(buf.readDouble(), fieldName);
    }

    private static float readFiniteFloat(ByteBuf buf, String fieldName) {
        return requireFinite(buf.readFloat(), fieldName);
    }

    private static double requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        return value;
    }

    private static float requireFinite(float value, String fieldName) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        return value;
    }

    private static void requireRange(int value, int minimum, int maximum, String fieldName) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    fieldName + " is outside " + minimum + ".." + maximum + ": " + value
            );
        }
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
                throw new IllegalArgumentException("VarInt exceeds the 5-byte limit");
            }

            byte current = buf.readByte();
            if (position == 28 && (current & 240) != 0) {
                throw new IllegalArgumentException("VarInt exceeds the 32-bit limit");
            }
            value |= (current & 127) << position;
            if ((current & 128) == 0) {
                return value;
            }
            position += 7;
        }
    }

    private static void writeVarLong(ByteBuf buf, long value) {
        while ((value & -128L) != 0L) {
            buf.writeByte((int) (value & 127L) | 128);
            value >>>= 7;
        }
        buf.writeByte((int) value);
    }

    private static long readVarLong(ByteBuf buf) {
        long value = 0L;
        int position = 0;

        while (true) {
            if (position >= 70) {
                throw new IllegalArgumentException("VarLong exceeds the 10-byte limit");
            }

            byte current = buf.readByte();
            if (position == 63 && (current & 126) != 0) {
                throw new IllegalArgumentException("VarLong exceeds the 64-bit limit");
            }
            value |= (long) (current & 127) << position;
            if ((current & 128) == 0) {
                return value;
            }
            position += 7;
        }
    }
}
