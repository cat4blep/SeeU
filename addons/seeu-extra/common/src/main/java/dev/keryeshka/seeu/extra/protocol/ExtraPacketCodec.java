package dev.keryeshka.seeu.extra.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ExtraPacketCodec {
    private ExtraPacketCodec() {
    }

    public static byte[] encodeClientOffer(ClientOffer offer) {
        ByteBuf buffer = Unpooled.buffer(16);
        try {
            writeVarInt(buffer, offer.protocolVersion());
            writeBoolean(buffer, offer.enabled());
            writeVarInt(buffer, offer.maximumDistanceBlocks());
            writeVarInt(buffer, offer.minimumDistanceBlocks());
            return copyPayload(buffer);
        } finally {
            buffer.release();
        }
    }

    public static ClientOffer decodeClientOffer(byte[] payload) {
        ByteBuf buffer = wrapPayload(payload);
        try {
            ClientOffer offer = new ClientOffer(
                    readVarInt(buffer),
                    readBoolean(buffer),
                    readVarInt(buffer),
                    readVarInt(buffer)
            );
            requireFullyRead(buffer);
            return offer;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw malformed(exception);
        } finally {
            buffer.release();
        }
    }

    public static byte[] encodeSnapshotPacket(EntitySnapshotPacket packet) {
        ByteBuf header = Unpooled.buffer();
        ByteBuf snapshots = Unpooled.buffer();
        try {
            writeUtf(header, packet.dimensionKey(), ExtraProtocol.MAX_RESOURCE_IDENTIFIER_BYTES);
            header.writeLong(packet.sequence());
            writeVarInt(header, packet.updateIntervalTicks());
            writeBoolean(header, packet.full());

            int encodedCount = 0;
            for (EntitySnapshot snapshot : packet.snapshots()) {
                int snapshotStart = snapshots.writerIndex();
                encodeSnapshot(snapshots, snapshot);
                int nextCount = encodedCount + 1;
                int totalBytes = header.readableBytes()
                        + varIntSize(nextCount)
                        + snapshots.readableBytes();
                if (totalBytes > ExtraProtocol.MAX_PACKET_BYTES) {
                    snapshots.writerIndex(snapshotStart);
                    break;
                }
                encodedCount = nextCount;
            }

            writeVarInt(header, encodedCount);
            header.writeBytes(snapshots, snapshots.readerIndex(), snapshots.readableBytes());
            return copyPayload(header);
        } finally {
            snapshots.release();
            header.release();
        }
    }

    public static EntitySnapshotPacket decodeSnapshotPacket(byte[] payload) {
        ByteBuf buffer = wrapPayload(payload);
        try {
            String dimensionKey = readUtf(buffer, ExtraProtocol.MAX_RESOURCE_IDENTIFIER_BYTES);
            long sequence = buffer.readLong();
            int updateIntervalTicks = readVarInt(buffer);
            boolean full = readBoolean(buffer);
            int count = readVarInt(buffer);
            if (count < 0 || count > ExtraProtocol.MAX_SNAPSHOTS) {
                throw new IllegalArgumentException("Snapshot count is outside the supported range");
            }

            List<EntitySnapshot> snapshots = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                snapshots.add(decodeSnapshot(buffer));
            }
            requireFullyRead(buffer);
            return new EntitySnapshotPacket(
                    dimensionKey,
                    sequence,
                    updateIntervalTicks,
                    full,
                    snapshots
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw malformed(exception);
        } finally {
            buffer.release();
        }
    }

    private static void encodeSnapshot(ByteBuf buffer, EntitySnapshot snapshot) {
        writeUuid(buffer, snapshot.uuid());
        writeUtf(buffer, snapshot.typeId(), ExtraProtocol.MAX_RESOURCE_IDENTIFIER_BYTES);
        buffer.writeDouble(snapshot.x());
        buffer.writeDouble(snapshot.y());
        buffer.writeDouble(snapshot.z());
        buffer.writeFloat(snapshot.bodyYaw());
        buffer.writeFloat(snapshot.headYaw());
        buffer.writeFloat(snapshot.pitch());
        buffer.writeDouble(snapshot.velocityX());
        buffer.writeDouble(snapshot.velocityY());
        buffer.writeDouble(snapshot.velocityZ());
        buffer.writeInt(snapshot.age());
        writeUtf(buffer, snapshot.pose(), ExtraProtocol.MAX_POSE_BYTES);
        buffer.writeByte(snapshot.flags());
        encodeEquipment(buffer, snapshot.mainHand());
        encodeEquipment(buffer, snapshot.offHand());
        encodeEquipment(buffer, snapshot.feet());
        encodeEquipment(buffer, snapshot.legs());
        encodeEquipment(buffer, snapshot.chest());
        encodeEquipment(buffer, snapshot.head());
    }

    private static EntitySnapshot decodeSnapshot(ByteBuf buffer) {
        return new EntitySnapshot(
                readUuid(buffer),
                readUtf(buffer, ExtraProtocol.MAX_RESOURCE_IDENTIFIER_BYTES),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readInt(),
                readUtf(buffer, ExtraProtocol.MAX_POSE_BYTES),
                buffer.readUnsignedByte(),
                decodeEquipment(buffer),
                decodeEquipment(buffer),
                decodeEquipment(buffer),
                decodeEquipment(buffer),
                decodeEquipment(buffer),
                decodeEquipment(buffer)
        );
    }

    private static void encodeEquipment(ByteBuf buffer, EquipmentSnapshot equipment) {
        writeUtf(buffer, equipment.itemId(), ExtraProtocol.MAX_RESOURCE_IDENTIFIER_BYTES);
        writeVarInt(buffer, equipment.count());
    }

    private static EquipmentSnapshot decodeEquipment(ByteBuf buffer) {
        return new EquipmentSnapshot(
                readUtf(buffer, ExtraProtocol.MAX_RESOURCE_IDENTIFIER_BYTES),
                readVarInt(buffer)
        );
    }

    private static ByteBuf wrapPayload(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload is missing");
        }
        if (payload.length > ExtraProtocol.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Payload exceeds the byte limit");
        }
        return Unpooled.wrappedBuffer(payload);
    }

    private static byte[] copyPayload(ByteBuf buffer) {
        byte[] payload = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), payload);
        return payload;
    }

    private static void writeUuid(ByteBuf buffer, UUID uuid) {
        buffer.writeLong(uuid.getMostSignificantBits());
        buffer.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    private static void writeBoolean(ByteBuf buffer, boolean value) {
        buffer.writeByte(value ? 1 : 0);
    }

    private static boolean readBoolean(ByteBuf buffer) {
        int value = buffer.readUnsignedByte();
        if (value > 1) {
            throw new IllegalArgumentException("Boolean field is not encoded as zero or one");
        }
        return value == 1;
    }

    private static void writeUtf(ByteBuf buffer, String value, int maximumBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes) {
            throw new IllegalArgumentException("String exceeds the byte limit");
        }
        writeVarInt(buffer, bytes.length);
        buffer.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buffer, int maximumBytes) {
        int length = readVarInt(buffer);
        if (length < 0 || length > maximumBytes || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("String length is outside the supported range");
        }

        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("String is not valid UTF-8", exception);
        }
    }

    private static void writeVarInt(ByteBuf buffer, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("VarInt value must not be negative");
        }
        while ((value & -128) != 0) {
            buffer.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buffer.writeByte(value);
    }

    private static int readVarInt(ByteBuf buffer) {
        int value = 0;
        int bytesRead = 0;
        int current;
        do {
            if (bytesRead == 5) {
                throw new IllegalArgumentException("VarInt exceeds the five-byte limit");
            }
            current = buffer.readUnsignedByte();
            value |= (current & 127) << (bytesRead * 7);
            bytesRead++;
        } while ((current & 128) != 0);

        if (value < 0 || bytesRead != varIntSize(value)) {
            throw new IllegalArgumentException("VarInt is negative or non-canonical");
        }
        return value;
    }

    private static int varIntSize(int value) {
        int size = 1;
        while ((value & -128) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    private static void requireFullyRead(ByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Payload contains trailing bytes");
        }
    }

    private static IllegalArgumentException malformed(RuntimeException exception) {
        return new IllegalArgumentException("Malformed SeeU Extra payload", exception);
    }
}
