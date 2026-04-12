package dev.keryeshka.voxyseeu.common.protocol;

public record FarItemSnapshot(
        String itemId,
        int count
) {
    public static final FarItemSnapshot EMPTY = new FarItemSnapshot("", 0);

    public FarItemSnapshot {
        itemId = itemId == null ? "" : itemId;
        count = Math.max(0, count);
    }

    public boolean isEmpty() {
        return itemId.isEmpty() || count <= 0;
    }
}
