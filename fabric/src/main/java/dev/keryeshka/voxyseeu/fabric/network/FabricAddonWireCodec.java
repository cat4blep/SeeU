package dev.keryeshka.voxyseeu.fabric.network;

import dev.keryeshka.voxyseeu.api.addon.protocol.AddonBusCodec;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class FabricAddonWireCodec {
    private FabricAddonWireCodec() {
    }

    public static byte[] encodeControl(AddonControlMessage message) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            AddonBusCodec.encodeControl(buffer, message);
            return copy(buffer, FabricAddonWireLimits.CONTROL_BYTES);
        } finally {
            buffer.release();
        }
    }

    public static AddonControlMessage decodeControl(byte[] payload) {
        ByteBuf buffer = wrap(payload, FabricAddonWireLimits.CONTROL_BYTES);
        try {
            return AddonBusCodec.decodeControl(buffer);
        } finally {
            buffer.release();
        }
    }

    public static byte[] encodeData(AddonEnvelope envelope) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            AddonBusCodec.encodeData(buffer, envelope);
            return copy(buffer, FabricAddonWireLimits.DATA_BYTES);
        } finally {
            buffer.release();
        }
    }

    public static AddonEnvelope decodeData(byte[] payload) {
        ByteBuf buffer = wrap(payload, FabricAddonWireLimits.DATA_BYTES);
        try {
            return AddonBusCodec.decodeData(buffer);
        } finally {
            buffer.release();
        }
    }

    private static byte[] copy(ByteBuf buffer, int maximumBytes) {
        int length = buffer.readableBytes();
        if (length <= 0 || length > maximumBytes) {
            throw new IllegalArgumentException("Encoded addon message is outside the Fabric channel limit");
        }
        byte[] payload = new byte[length];
        buffer.getBytes(buffer.readerIndex(), payload);
        return payload;
    }

    private static ByteBuf wrap(byte[] payload, int maximumBytes) {
        if (payload == null || payload.length == 0 || payload.length > maximumBytes) {
            throw new IllegalArgumentException("Addon message is outside the Fabric channel limit");
        }
        return Unpooled.wrappedBuffer(payload);
    }
}
