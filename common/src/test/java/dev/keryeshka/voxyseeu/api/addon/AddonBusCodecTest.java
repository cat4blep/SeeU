package dev.keryeshka.voxyseeu.api.addon;

import dev.keryeshka.voxyseeu.api.addon.protocol.AddonAcceptance;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonAcceptanceList;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonBusCodec;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonCloseMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonOffer;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonOfferList;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonRenegotiateMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonBusCodecTest {
    private static final AddonDescriptor DESCRIPTOR =
            new AddonDescriptor("seeu_extra", 3, AddonDirection.BIDIRECTIONAL, 4096);

    @Test
    void roundTripsEveryControlMessageAndData() {
        AddonOfferList offers = new AddonOfferList(7, List.of(new AddonOffer(DESCRIPTOR, new byte[]{1, 2, 3})));
        AddonOfferList decodedOffers = assertInstanceOf(AddonOfferList.class, roundTripControl(offers));
        assertEquals(7, decodedOffers.generation());
        assertEquals(DESCRIPTOR, decodedOffers.offers().getFirst().descriptor());
        assertArrayEquals(new byte[]{1, 2, 3}, decodedOffers.offers().getFirst().helloData());

        AddonAcceptanceList acceptances = new AddonAcceptanceList(7, List.of(
                new AddonAcceptance(DESCRIPTOR, AddonDecision.accept(new byte[]{9, 8})),
                new AddonAcceptance(
                        new AddonDescriptor("absent", 1, AddonDirection.CLIENTBOUND, 64),
                        AddonDecision.reject()
                )
        ));
        AddonAcceptanceList decodedAcceptances =
                assertInstanceOf(AddonAcceptanceList.class, roundTripControl(acceptances));
        assertTrue(decodedAcceptances.acceptances().getFirst().decision().accepted());
        assertArrayEquals(
                new byte[]{9, 8},
                decodedAcceptances.acceptances().getFirst().decision().acknowledgementData()
        );

        assertEquals(
                new AddonCloseMessage(7, "seeu_extra", AddonCloseReason.ENDPOINT_FAILURE),
                roundTripControl(new AddonCloseMessage(7, "seeu_extra", AddonCloseReason.ENDPOINT_FAILURE))
        );
        assertEquals(new AddonRenegotiateMessage(7), roundTripControl(new AddonRenegotiateMessage(7)));

        AddonEnvelope decodedEnvelope = roundTripData(new AddonEnvelope(7, "seeu_extra", new byte[]{4, 5}));
        assertEquals(7, decodedEnvelope.generation());
        assertEquals("seeu_extra", decodedEnvelope.addonId());
        assertArrayEquals(new byte[]{4, 5}, decodedEnvelope.payload());
    }

    @Test
    void recordsDefensivelyCopyOpaqueBytes() {
        byte[] hello = {1};
        AddonOffer offer = new AddonOffer(DESCRIPTOR, hello);
        hello[0] = 2;
        assertArrayEquals(new byte[]{1}, offer.helloData());
        byte[] exposedHello = offer.helloData();
        exposedHello[0] = 3;
        assertArrayEquals(new byte[]{1}, offer.helloData());

        byte[] acknowledgement = {4};
        AddonDecision decision = AddonDecision.accept(acknowledgement);
        acknowledgement[0] = 5;
        assertArrayEquals(new byte[]{4}, decision.acknowledgementData());

        byte[] payload = {6};
        AddonEnvelope envelope = new AddonEnvelope(1, "seeu_extra", payload);
        payload[0] = 7;
        assertArrayEquals(new byte[]{6}, envelope.payload());
    }

    @Test
    void rejectsInvalidDescriptorsAndHandshakeOrDataCaps() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddonDescriptor("Uppercase", 1, AddonDirection.CLIENTBOUND, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AddonDescriptor("xx", 0, AddonDirection.CLIENTBOUND, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AddonDescriptor("xx", 1, AddonDirection.CLIENTBOUND, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new AddonDescriptor("xx", 1, AddonDirection.CLIENTBOUND, AddonLimits.MAX_DATA_BYTES + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AddonOffer(DESCRIPTOR, new byte[AddonLimits.MAX_HANDSHAKE_BYTES + 1]));
        assertThrows(IllegalArgumentException.class,
                () -> AddonDecision.accept(new byte[AddonLimits.MAX_HANDSHAKE_BYTES + 1]));
        assertThrows(IllegalArgumentException.class,
                () -> new AddonEnvelope(1, "seeu_extra", new byte[AddonLimits.MAX_DATA_BYTES + 1]));
    }

    @Test
    void rejectsTooManyOrDuplicateOffersBeforeEncoding() {
        List<AddonOffer> tooMany = new ArrayList<>();
        for (int i = 0; i <= AddonLimits.MAX_OFFERS; i++) {
            tooMany.add(new AddonOffer(
                    new AddonDescriptor("addon_" + i, 1, AddonDirection.CLIENTBOUND, 1),
                    new byte[0]
            ));
        }
        assertThrows(IllegalArgumentException.class, () -> new AddonOfferList(1, tooMany));
        assertThrows(IllegalArgumentException.class,
                () -> new AddonOfferList(1, List.of(
                        new AddonOffer(DESCRIPTOR, new byte[0]),
                        new AddonOffer(DESCRIPTOR, new byte[0])
                )));
    }

    @Test
    void rejectsOversizedLengthsBeforeAllocationAndTrailingBytes() {
        ByteBuf unsupportedVersion = Unpooled.buffer();
        unsupportedVersion.writeByte(AddonLimits.BUS_PROTOCOL_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> AddonBusCodec.decodeControl(unsupportedVersion));

        ByteBuf oversizedId = Unpooled.buffer();
        oversizedId.writeByte(AddonLimits.BUS_PROTOCOL_VERSION);
        oversizedId.writeByte(0); // offer list
        oversizedId.writeByte(1); // generation
        oversizedId.writeByte(1); // offer count
        oversizedId.writeByte(AddonLimits.MAX_ADDON_ID_BYTES + 1);
        assertThrows(IllegalArgumentException.class, () -> AddonBusCodec.decodeControl(oversizedId));

        ByteBuf trailing = Unpooled.buffer();
        AddonBusCodec.encodeData(trailing, new AddonEnvelope(1, "xx", new byte[0]));
        trailing.writeByte(0);
        assertThrows(IllegalArgumentException.class, () -> AddonBusCodec.decodeData(trailing));
    }

    private static AddonControlMessage roundTripControl(AddonControlMessage message) {
        ByteBuf buffer = Unpooled.buffer();
        AddonBusCodec.encodeControl(buffer, message);
        return AddonBusCodec.decodeControl(buffer);
    }

    private static AddonEnvelope roundTripData(AddonEnvelope envelope) {
        ByteBuf buffer = Unpooled.buffer();
        AddonBusCodec.encodeData(buffer, envelope);
        return AddonBusCodec.decodeData(buffer);
    }
}
