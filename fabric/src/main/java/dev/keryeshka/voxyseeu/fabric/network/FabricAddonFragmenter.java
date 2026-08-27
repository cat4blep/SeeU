package dev.keryeshka.voxyseeu.fabric.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FabricAddonFragmenter {
    private long messageId;

    public List<FabricAddonFragment> fragment(byte[] message, int maximumBytes) {
        if (maximumBytes <= 0 || maximumBytes > FabricAddonWireLimits.CONTROL_BYTES) {
            throw new IllegalArgumentException("Fragmented channel limit is invalid");
        }
        if (message == null || message.length == 0 || message.length > maximumBytes) {
            throw new IllegalArgumentException("Fragmented message length is outside the channel limit");
        }

        long nextMessageId = nextMessageId();
        int count = FabricAddonFragment.fragmentCount(message.length);
        List<FabricAddonFragment> fragments = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int start = index * FabricAddonWireLimits.FRAGMENT_BYTES;
            int end = Math.min(message.length, start + FabricAddonWireLimits.FRAGMENT_BYTES);
            fragments.add(FabricAddonFragment.fromOwned(
                    nextMessageId,
                    message.length,
                    index,
                    count,
                    Arrays.copyOfRange(message, start, end)
            ));
        }
        return List.copyOf(fragments);
    }

    private long nextMessageId() {
        messageId = messageId == Long.MAX_VALUE ? 1L : messageId + 1L;
        return messageId;
    }
}
