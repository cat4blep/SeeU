package dev.keryeshka.voxyseeu.common.protocol;

public record FarItemSnapshot(
        String itemId,
        int count
) {
    public static final FarItemSnapshot EMPTY = new FarItemSnapshot("", 0);

    public boolean isEmpty() {
        return itemId.isEmpty() || count <= 0;
    }
}
