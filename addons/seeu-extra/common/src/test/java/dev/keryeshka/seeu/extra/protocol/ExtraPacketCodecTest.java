package dev.keryeshka.seeu.extra.protocol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraPacketCodecTest {
    @Test
    void clientOfferRoundTrips() {
        ClientOffer offer = new ClientOffer(ExtraProtocol.VERSION, true, 4096, 128);

        assertEquals(offer, ExtraPacketCodec.decodeClientOffer(ExtraPacketCodec.encodeClientOffer(offer)));
    }

    @Test
    void fullSnapshotRoundTrips() {
        EntitySnapshot snapshot = sampleSnapshot();
        EntitySnapshotPacket packet = new EntitySnapshotPacket(
                "minecraft:overworld",
                42,
                4,
                true,
                List.of(snapshot)
        );

        byte[] encoded = ExtraPacketCodec.encodeSnapshotPacket(packet);

        assertEquals(packet, ExtraPacketCodec.decodeSnapshotPacket(encoded));
    }

    @Test
    void decoderRejectsTrailingBytes() {
        byte[] encoded = ExtraPacketCodec.encodeClientOffer(
                new ClientOffer(ExtraProtocol.VERSION, true, 4096, 128)
        );
        byte[] withTrailingByte = Arrays.copyOf(encoded, encoded.length + 1);

        assertThrows(IllegalArgumentException.class, () -> ExtraPacketCodec.decodeClientOffer(withTrailingByte));
    }

    @Test
    void decoderRejectsNonCanonicalVarIntAndBoolean() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExtraPacketCodec.decodeClientOffer(new byte[]{(byte) 0x81, 0, 1, 0, 0})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ExtraPacketCodec.decodeClientOffer(new byte[]{1, 2, 0, 0})
        );
    }

    @Test
    void boundsRejectInvalidRangesAndSnapshotCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClientOffer(ExtraProtocol.VERSION, true, 100, 101)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EntitySnapshotPacket(
                        "minecraft:overworld",
                        1,
                        4,
                        true,
                        java.util.Collections.nCopies(ExtraProtocol.MAX_SNAPSHOTS + 1, sampleSnapshot())
                )
        );
    }

    @Test
    void snapshotRejectsUnsafeCoordinatesAndEquipmentCounts() {
        EntitySnapshot sample = sampleSnapshot();
        assertThrows(
                IllegalArgumentException.class,
                () -> new EntitySnapshot(
                        sample.uuid(),
                        sample.typeId(),
                        ExtraProtocol.MAX_ABSOLUTE_COORDINATE + 1,
                        sample.y(),
                        sample.z(),
                        sample.bodyYaw(),
                        sample.headYaw(),
                        sample.pitch(),
                        sample.velocityX(),
                        sample.velocityY(),
                        sample.velocityZ(),
                        sample.age(),
                        sample.pose(),
                        sample.flags(),
                        sample.mainHand(),
                        sample.offHand(),
                        sample.feet(),
                        sample.legs(),
                        sample.chest(),
                        sample.head()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EquipmentSnapshot("minecraft:stone", ExtraProtocol.MAX_ITEM_COUNT + 1)
        );
    }

    @Test
    void oversizedSnapshotSetsAreTrimmedToTheNearestPrefix() {
        List<EntitySnapshot> snapshots = new ArrayList<>(ExtraProtocol.MAX_SNAPSHOTS);
        String longTypeId = "test:" + "a".repeat(240);
        String longItemId = "test:" + "b".repeat(240);
        EquipmentSnapshot equipment = new EquipmentSnapshot(longItemId, 1);
        for (int index = 0; index < ExtraProtocol.MAX_SNAPSHOTS; index++) {
            snapshots.add(new EntitySnapshot(
                    new UUID(0, index + 1L),
                    longTypeId,
                    index,
                    64.0,
                    index,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0,
                    0.0,
                    0.0,
                    index,
                    "STANDING",
                    0,
                    equipment,
                    equipment,
                    equipment,
                    equipment,
                    equipment,
                    equipment
            ));
        }
        EntitySnapshotPacket packet = new EntitySnapshotPacket(
                "minecraft:overworld",
                7,
                4,
                true,
                snapshots
        );

        byte[] encoded = ExtraPacketCodec.encodeSnapshotPacket(packet);
        EntitySnapshotPacket decoded = ExtraPacketCodec.decodeSnapshotPacket(encoded);

        assertTrue(encoded.length <= ExtraProtocol.MAX_PACKET_BYTES);
        assertFalse(decoded.snapshots().isEmpty());
        assertTrue(decoded.snapshots().size() < snapshots.size());
        assertEquals(snapshots.subList(0, decoded.snapshots().size()), decoded.snapshots());
    }

    private static EntitySnapshot sampleSnapshot() {
        return new EntitySnapshot(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "minecraft:zombie",
                10.25,
                64.0,
                -30.5,
                90.0F,
                95.0F,
                -10.0F,
                0.1,
                0.0,
                -0.2,
                120,
                "STANDING",
                EntityFlags.ON_FIRE | EntityFlags.SPRINTING,
                new EquipmentSnapshot("minecraft:iron_sword", 1),
                EquipmentSnapshot.EMPTY,
                EquipmentSnapshot.EMPTY,
                EquipmentSnapshot.EMPTY,
                EquipmentSnapshot.EMPTY,
                new EquipmentSnapshot("minecraft:carved_pumpkin", 1)
        );
    }
}
