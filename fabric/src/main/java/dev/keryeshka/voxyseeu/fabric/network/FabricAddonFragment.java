package dev.keryeshka.voxyseeu.fabric.network;

import java.util.Arrays;

public final class FabricAddonFragment {
    private final long messageId;
    private final int totalLength;
    private final int fragmentIndex;
    private final int fragmentCount;
    private final byte[] payload;

    public FabricAddonFragment(
            long messageId,
            int totalLength,
            int fragmentIndex,
            int fragmentCount,
            byte[] payload
    ) {
        this(messageId, totalLength, fragmentIndex, fragmentCount, payload, true);
    }

    static FabricAddonFragment fromOwned(
            long messageId,
            int totalLength,
            int fragmentIndex,
            int fragmentCount,
            byte[] payload
    ) {
        return new FabricAddonFragment(
                messageId,
                totalLength,
                fragmentIndex,
                fragmentCount,
                payload,
                false
        );
    }

    private FabricAddonFragment(
            long messageId,
            int totalLength,
            int fragmentIndex,
            int fragmentCount,
            byte[] payload,
            boolean copyPayload
    ) {
        if (messageId <= 0L) {
            throw new IllegalArgumentException("Fragment message ID must be positive");
        }
        if (totalLength <= 0 || totalLength > FabricAddonWireLimits.CONTROL_BYTES) {
            throw new IllegalArgumentException("Fragmented message length is outside the supported range");
        }
        int expectedCount = fragmentCount(totalLength);
        if (fragmentCount != expectedCount
                || fragmentIndex < 0
                || fragmentIndex >= fragmentCount) {
            throw new IllegalArgumentException("Fragment count or index is invalid");
        }
        int expectedLength = fragmentIndex == fragmentCount - 1
                ? totalLength - fragmentIndex * FabricAddonWireLimits.FRAGMENT_BYTES
                : FabricAddonWireLimits.FRAGMENT_BYTES;
        if (payload == null || payload.length != expectedLength) {
            throw new IllegalArgumentException("Fragment payload length is invalid");
        }
        this.messageId = messageId;
        this.totalLength = totalLength;
        this.fragmentIndex = fragmentIndex;
        this.fragmentCount = fragmentCount;
        this.payload = copyPayload ? Arrays.copyOf(payload, payload.length) : payload;
    }

    public long messageId() {
        return messageId;
    }

    public int totalLength() {
        return totalLength;
    }

    public int fragmentIndex() {
        return fragmentIndex;
    }

    public int fragmentCount() {
        return fragmentCount;
    }

    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    byte[] payloadForWire() {
        return payload;
    }

    static int fragmentCount(int totalLength) {
        return (totalLength + FabricAddonWireLimits.FRAGMENT_BYTES - 1)
                / FabricAddonWireLimits.FRAGMENT_BYTES;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof FabricAddonFragment fragment
                && messageId == fragment.messageId
                && totalLength == fragment.totalLength
                && fragmentIndex == fragment.fragmentIndex
                && fragmentCount == fragment.fragmentCount
                && Arrays.equals(payload, fragment.payload);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(messageId);
        result = 31 * result + totalLength;
        result = 31 * result + fragmentIndex;
        result = 31 * result + fragmentCount;
        return 31 * result + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return "FabricAddonFragment[messageId=" + messageId
                + ", totalLength=" + totalLength
                + ", fragmentIndex=" + fragmentIndex
                + ", fragmentCount=" + fragmentCount
                + ", payloadLength=" + payload.length + ']';
    }
}
