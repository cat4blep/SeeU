package dev.keryeshka.voxyseeu.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketCodecTest {
    private static final UUID PLAYER_ID = UUID.fromString("5c3d21f0-926e-4dc5-8531-e3bc45e4cb9f");
    private static final UUID VEHICLE_ID = UUID.fromString("41b9e4c1-ea0b-4fe8-8d36-0a76bcbcd30e");
    private static final FarPlayerMetadata METADATA = new FarPlayerMetadata(
            "Player",
            new FarItemSnapshot("minecraft:diamond_sword", 1),
            FarItemSnapshot.EMPTY,
            new FarItemSnapshot("minecraft:diamond_boots", 1),
            FarItemSnapshot.EMPTY,
            FarItemSnapshot.EMPTY,
            new FarItemSnapshot("minecraft:diamond_helmet", 1)
    );

    @Test
    void roundTripsFramesWithAndWithoutMetadata() {
        FarPlayerSnapshot full = snapshot(METADATA);
        FarPlayersPacket firstFrame = new FarPlayersPacket(
                "minecraft:overworld",
                17L,
                2,
                List.of(full)
        );
        assertEquals(firstFrame, roundTrip(firstFrame));

        FarPlayersPacket deltaFrame = new FarPlayersPacket(
                "minecraft:overworld",
                18L,
                2,
                List.of(full.withoutMetadata())
        );
        FarPlayersPacket decoded = roundTrip(deltaFrame);
        assertEquals(deltaFrame, decoded);
        assertNull(decoded.players().getFirst().metadata());
    }

    @Test
    void rejectsNegativeAndOversizedPlayerCountsBeforeAllocation() {
        ByteBuf negative = frameHeader();
        writeVarInt(negative, -1);
        assertThrows(IllegalArgumentException.class, () -> PacketCodec.decodeFarPlayers(negative));

        ByteBuf oversized = frameHeader();
        writeVarInt(oversized, PacketCodec.MAX_PLAYERS_PER_PACKET + 1);
        assertThrows(IllegalArgumentException.class, () -> PacketCodec.decodeFarPlayers(oversized));
    }

    @Test
    void rejectsOversizedUtfAndTrailingData() {
        ByteBuf oversizedDimension = Unpooled.buffer();
        writeVarInt(oversizedDimension, PacketCodec.MAX_DIMENSION_KEY_BYTES + 1);
        assertThrows(IllegalArgumentException.class, () -> PacketCodec.decodeFarPlayers(oversizedDimension));

        ByteBuf trailing = Unpooled.buffer();
        PacketCodec.encodeFarPlayers(trailing, new FarPlayersPacket("minecraft:overworld", 1L, 2, List.of()));
        trailing.writeByte(0);
        assertThrows(IllegalArgumentException.class, () -> PacketCodec.decodeFarPlayers(trailing));
    }

    @Test
    void rejectsNonFiniteCoordinatesAndAnglesOnBothBoundaries() {
        FarPlayerSnapshot nanPosition = new FarPlayerSnapshot(
                PLAYER_ID,
                Double.NaN,
                2.0D,
                3.0D,
                0.0F,
                0.0F,
                0.0F,
                false,
                false,
                false,
                null,
                METADATA
        );
        assertThrows(IllegalArgumentException.class, () -> encode(nanPosition));

        ByteBuf decodedNan = frameHeader();
        writeVarInt(decodedNan, 1);
        decodedNan.writeLong(PLAYER_ID.getMostSignificantBits());
        decodedNan.writeLong(PLAYER_ID.getLeastSignificantBits());
        decodedNan.writeDouble(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> PacketCodec.decodeFarPlayers(decodedNan));

        FarVehicleSnapshot infiniteVehicle = new FarVehicleSnapshot(
                VEHICLE_ID,
                "minecraft:boat",
                1.0D,
                2.0D,
                3.0D,
                Float.POSITIVE_INFINITY,
                0.0F
        );
        FarPlayerSnapshot invalidVehicle = new FarPlayerSnapshot(
                PLAYER_ID,
                1.0D,
                2.0D,
                3.0D,
                0.0F,
                0.0F,
                0.0F,
                false,
                false,
                false,
                infiniteVehicle,
                METADATA
        );
        assertThrows(IllegalArgumentException.class, () -> encode(invalidVehicle));
    }

    @Test
    void rejectsNegativeAndOversizedItemCounts() {
        FarPlayerMetadata negative = new FarPlayerMetadata(
                "Player",
                new FarItemSnapshot("minecraft:stone", -1),
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY
        );
        assertThrows(IllegalArgumentException.class, () -> encode(snapshot(negative)));

        FarPlayerMetadata oversized = new FarPlayerMetadata(
                "Player",
                new FarItemSnapshot("minecraft:stone", PacketCodec.MAX_ITEM_COUNT + 1),
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY
        );
        assertThrows(IllegalArgumentException.class, () -> encode(snapshot(oversized)));
    }

    private static FarPlayersPacket roundTrip(FarPlayersPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        PacketCodec.encodeFarPlayers(buffer, packet);
        return PacketCodec.decodeFarPlayers(buffer);
    }

    private static void encode(FarPlayerSnapshot snapshot) {
        ByteBuf buffer = Unpooled.buffer();
        PacketCodec.encodeFarPlayers(
                buffer,
                new FarPlayersPacket("minecraft:overworld", 1L, 2, List.of(snapshot))
        );
    }

    private static FarPlayerSnapshot snapshot(FarPlayerMetadata metadata) {
        return new FarPlayerSnapshot(
                PLAYER_ID,
                1.25D,
                64.0D,
                -3.5D,
                10.0F,
                15.0F,
                -5.0F,
                true,
                false,
                true,
                new FarVehicleSnapshot(
                        VEHICLE_ID,
                        "minecraft:oak_boat",
                        1.25D,
                        63.5D,
                        -3.5D,
                        10.0F,
                        0.0F
                ),
                metadata
        );
    }

    private static ByteBuf frameHeader() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(0); // empty dimension key
        buffer.writeByte(1); // sequence
        buffer.writeByte(2); // update interval
        return buffer;
    }

    private static void writeVarInt(ByteBuf buffer, int value) {
        while ((value & -128) != 0) {
            buffer.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buffer.writeByte(value);
    }
}
