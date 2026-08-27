package dev.keryeshka.voxyseeu.common.protocol;

public record FarPlayerMetadata(
        String name,
        FarItemSnapshot mainHand,
        FarItemSnapshot offHand,
        FarItemSnapshot feet,
        FarItemSnapshot legs,
        FarItemSnapshot chest,
        FarItemSnapshot head
) {
}
