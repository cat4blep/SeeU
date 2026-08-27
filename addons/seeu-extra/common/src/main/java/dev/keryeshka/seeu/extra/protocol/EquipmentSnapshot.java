package dev.keryeshka.seeu.extra.protocol;

import dev.keryeshka.seeu.extra.ResourceIdentifier;

public record EquipmentSnapshot(String itemId, int count) {
    public static final EquipmentSnapshot EMPTY = new EquipmentSnapshot("", 0);

    public EquipmentSnapshot {
        if (itemId == null) {
            throw new IllegalArgumentException("Item identifier is missing");
        }
        if (itemId.isEmpty()) {
            if (count != 0) {
                throw new IllegalArgumentException("Empty equipment must have a zero count");
            }
        } else {
            if (!ResourceIdentifier.isValid(itemId)) {
                throw new IllegalArgumentException("Invalid item identifier: " + itemId);
            }
            if (count < 1 || count > ExtraProtocol.MAX_ITEM_COUNT) {
                throw new IllegalArgumentException("Equipment count is outside the supported range");
            }
        }
    }

    public boolean isEmpty() {
        return itemId.isEmpty();
    }
}
