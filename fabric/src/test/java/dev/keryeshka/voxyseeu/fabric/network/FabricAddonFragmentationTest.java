package dev.keryeshka.voxyseeu.fabric.network;

import dev.keryeshka.voxyseeu.api.addon.AddonDescriptor;
import dev.keryeshka.voxyseeu.api.addon.AddonDirection;
import dev.keryeshka.voxyseeu.api.addon.AddonLimits;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonOffer;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonOfferList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricAddonFragmentationTest {
    @Test
    void reassemblesMessagesAtBothPublicChannelLimits() {
        assertRoundTrip(FabricAddonWireLimits.CONTROL_BYTES);
        assertRoundTrip(FabricAddonWireLimits.DATA_BYTES);
    }

    @Test
    void fullPublicHandshakeAndDataLimitsSurviveTheFabricAdapter() {
        byte[] maximumHello = bytes(AddonLimits.MAX_HANDSHAKE_BYTES);
        List<AddonOffer> offers = new ArrayList<>(AddonLimits.MAX_OFFERS);
        for (int index = 0; index < AddonLimits.MAX_OFFERS; index++) {
            offers.add(new AddonOffer(
                    new AddonDescriptor(
                            "addon_" + index,
                            1,
                            AddonDirection.CLIENTBOUND,
                            AddonLimits.MAX_DATA_BYTES
                    ),
                    maximumHello
            ));
        }
        byte[] encodedControl = FabricAddonWireCodec.encodeControl(new AddonOfferList(1, offers));
        assertTrue(encodedControl.length > 1024 * 1024);
        byte[] assembledControl = reassemble(encodedControl, FabricAddonWireLimits.CONTROL_BYTES);
        AddonOfferList decodedControl = (AddonOfferList) FabricAddonWireCodec.decodeControl(assembledControl);
        assertEquals(AddonLimits.MAX_OFFERS, decodedControl.offers().size());
        assertArrayEquals(maximumHello, decodedControl.offers().getLast().helloData());

        AddonEnvelope envelope = new AddonEnvelope(
                1,
                "test_addon",
                bytes(AddonLimits.MAX_DATA_BYTES)
        );
        byte[] encodedData = FabricAddonWireCodec.encodeData(envelope);
        assertTrue(encodedData.length > AddonLimits.MAX_DATA_BYTES);
        byte[] assembledData = reassemble(encodedData, FabricAddonWireLimits.DATA_BYTES);
        assertEquals(envelope, FabricAddonWireCodec.decodeData(assembledData));
    }

    @Test
    void rejectsOversizedMessagesBeforeFragmentingOrAllocatingAnAssembly() {
        FabricAddonFragmenter fragmenter = new FabricAddonFragmenter();
        assertThrows(
                IllegalArgumentException.class,
                () -> fragmenter.fragment(
                        new byte[FabricAddonWireLimits.DATA_BYTES + 1],
                        FabricAddonWireLimits.DATA_BYTES
                )
        );

        FabricAddonFragment oversizedForData = new FabricAddonFragment(
                1,
                FabricAddonWireLimits.DATA_BYTES + 1,
                0,
                FabricAddonFragment.fragmentCount(FabricAddonWireLimits.DATA_BYTES + 1),
                new byte[FabricAddonWireLimits.FRAGMENT_BYTES]
        );
        FabricAddonFragmentAssembler assembler = new FabricAddonFragmentAssembler(
                FabricAddonWireLimits.DATA_BYTES
        );

        assertThrows(IllegalArgumentException.class, () -> assembler.accept(oversizedForData));
        assertRoundTrip(assembler, new byte[]{1, 2, 3});
    }

    @Test
    void rejectsOutOfOrderFragmentsAndAcceptsTheNextCompleteMessage() {
        byte[] first = bytes(FabricAddonWireLimits.FRAGMENT_BYTES * 2 + 3);
        List<FabricAddonFragment> fragments = new FabricAddonFragmenter().fragment(
                first,
                FabricAddonWireLimits.DATA_BYTES
        );
        FabricAddonFragmentAssembler assembler = new FabricAddonFragmentAssembler(
                FabricAddonWireLimits.DATA_BYTES
        );

        assertFalse(assembler.accept(fragments.get(0)).isPresent());
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(fragments.get(2)));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(fragments.get(1)));
        assertRoundTrip(assembler, new byte[]{9, 8, 7});
    }

    @Test
    void rejectsAnInterleavedFirstFragmentAndAcceptsTheNextCompleteMessage() {
        FabricAddonFragmenter fragmenter = new FabricAddonFragmenter();
        List<FabricAddonFragment> abandoned = fragmenter.fragment(
                bytes(FabricAddonWireLimits.FRAGMENT_BYTES + 1),
                FabricAddonWireLimits.DATA_BYTES
        );
        byte[] replacement = bytes(FabricAddonWireLimits.FRAGMENT_BYTES + 7);
        List<FabricAddonFragment> replacementFragments = fragmenter.fragment(
                replacement,
                FabricAddonWireLimits.DATA_BYTES
        );
        FabricAddonFragmentAssembler assembler = new FabricAddonFragmentAssembler(
                FabricAddonWireLimits.DATA_BYTES
        );

        assertFalse(assembler.accept(abandoned.get(0)).isPresent());
        assertThrows(
                IllegalArgumentException.class,
                () -> assembler.accept(replacementFragments.get(0))
        );
        Optional<byte[]> result = Optional.empty();
        for (FabricAddonFragment fragment : replacementFragments) {
            result = assembler.accept(fragment);
        }

        assertTrue(result.isPresent());
        assertArrayEquals(replacement, result.orElseThrow());
    }

    @Test
    void incompleteAssembliesExpireAndReleaseTheirFragments() {
        byte[] message = bytes(FabricAddonWireLimits.FRAGMENT_BYTES + 1);
        List<FabricAddonFragment> fragments = new FabricAddonFragmenter().fragment(
                message,
                FabricAddonWireLimits.DATA_BYTES
        );
        FabricAddonFragmentAssembler assembler = new FabricAddonFragmentAssembler(
                FabricAddonWireLimits.DATA_BYTES
        );
        long startedNanos = 10L;

        assertFalse(assembler.accept(fragments.getFirst(), startedNanos).isPresent());
        long expiredNanos = startedNanos + FabricAddonFragmentAssembler.ASSEMBLY_TIMEOUT_NANOS;
        assertTrue(assembler.isExpired(expiredNanos));
        assertThrows(
                IllegalArgumentException.class,
                () -> assembler.accept(fragments.getLast(), expiredNanos)
        );
        assertFalse(assembler.isExpired(expiredNanos));
        assertRoundTrip(assembler, new byte[]{7, 8, 9});
    }

    @Test
    void fragmentMetadataAndPayloadShapeAreStrict() {
        int totalLength = FabricAddonWireLimits.FRAGMENT_BYTES + 1;
        int count = FabricAddonFragment.fragmentCount(totalLength);
        assertThrows(
                IllegalArgumentException.class,
                () -> new FabricAddonFragment(0, totalLength, 0, count, new byte[FabricAddonWireLimits.FRAGMENT_BYTES])
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FabricAddonFragment(1, totalLength, 1, count + 1, new byte[1])
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FabricAddonFragment(1, totalLength, 0, count, new byte[1])
        );
    }

    private static void assertRoundTrip(int size) {
        byte[] message = bytes(size);
        List<FabricAddonFragment> fragments = new FabricAddonFragmenter().fragment(message, size);
        for (FabricAddonFragment fragment : fragments) {
            assertTrue(fragment.payload().length <= FabricAddonWireLimits.FRAGMENT_BYTES);
        }
        assertEquals(FabricAddonFragment.fragmentCount(size), fragments.size());
        assertArrayEquals(message, reassemble(message, size));
    }

    private static void assertRoundTrip(FabricAddonFragmentAssembler assembler, byte[] message) {
        Optional<byte[]> result = Optional.empty();
        for (FabricAddonFragment fragment : new FabricAddonFragmenter().fragment(
                message,
                FabricAddonWireLimits.DATA_BYTES
        )) {
            result = assembler.accept(fragment);
        }
        assertArrayEquals(message, result.orElseThrow());
    }

    private static byte[] reassemble(byte[] message, int maximumBytes) {
        FabricAddonFragmentAssembler assembler = new FabricAddonFragmentAssembler(maximumBytes);
        Optional<byte[]> result = Optional.empty();
        for (FabricAddonFragment fragment : new FabricAddonFragmenter().fragment(message, maximumBytes)) {
            result = assembler.accept(fragment);
        }
        return result.orElseThrow();
    }

    private static byte[] bytes(int size) {
        byte[] result = new byte[size];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) (index * 31);
        }
        return result;
    }
}
