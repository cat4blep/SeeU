package dev.keryeshka.voxyseeu.fabric.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class FabricAddonFragmentAssembler {
    static final long ASSEMBLY_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5L);

    private final int maximumBytes;

    private long messageId;
    private int totalLength;
    private int fragmentCount;
    private int nextFragmentIndex;
    private int writeOffset;
    private long startedNanos;
    private List<byte[]> fragments;

    public FabricAddonFragmentAssembler(int maximumBytes) {
        if (maximumBytes <= 0 || maximumBytes > FabricAddonWireLimits.CONTROL_BYTES) {
            throw new IllegalArgumentException("Fragmented channel limit is invalid");
        }
        this.maximumBytes = maximumBytes;
    }

    public Optional<byte[]> accept(FabricAddonFragment fragment) {
        return accept(fragment, System.nanoTime());
    }

    Optional<byte[]> accept(FabricAddonFragment fragment, long nowNanos) {
        if (isExpired(nowNanos)) {
            throw reject("Fragmented addon message assembly expired");
        }
        if (fragment.totalLength() > maximumBytes) {
            throw reject("Fragmented message exceeds the channel limit");
        }
        if (fragment.fragmentIndex() == 0) {
            if (fragments != null) {
                throw reject("A fragmented addon message is already in progress");
            }
            begin(fragment, nowNanos);
        } else if (!matchesCurrentAssembly(fragment)) {
            throw reject("Addon fragments are missing, reordered, or interleaved");
        }

        byte[] payload = fragment.payloadForWire();
        fragments.add(payload);
        writeOffset += payload.length;
        nextFragmentIndex++;
        if (nextFragmentIndex != fragmentCount) {
            return Optional.empty();
        }
        if (writeOffset != totalLength) {
            throw reject("Reassembled addon message length does not match its header");
        }

        byte[] completed = new byte[totalLength];
        int offset = 0;
        for (byte[] part : fragments) {
            System.arraycopy(part, 0, completed, offset, part.length);
            offset += part.length;
        }
        resetState();
        return Optional.of(completed);
    }

    public boolean isExpired(long nowNanos) {
        return fragments != null && nowNanos - startedNanos >= ASSEMBLY_TIMEOUT_NANOS;
    }

    public void clear() {
        resetState();
    }

    private void begin(FabricAddonFragment fragment, long nowNanos) {
        messageId = fragment.messageId();
        totalLength = fragment.totalLength();
        fragmentCount = fragment.fragmentCount();
        nextFragmentIndex = 0;
        writeOffset = 0;
        startedNanos = nowNanos;
        fragments = new ArrayList<>(fragmentCount);
    }

    private boolean matchesCurrentAssembly(FabricAddonFragment fragment) {
        return fragments != null
                && fragment.messageId() == messageId
                && fragment.totalLength() == totalLength
                && fragment.fragmentCount() == fragmentCount
                && fragment.fragmentIndex() == nextFragmentIndex;
    }

    private void resetState() {
        messageId = 0L;
        totalLength = 0;
        fragmentCount = 0;
        nextFragmentIndex = 0;
        writeOffset = 0;
        startedNanos = 0L;
        fragments = null;
    }

    private IllegalArgumentException reject(String message) {
        resetState();
        return new IllegalArgumentException(message);
    }
}
