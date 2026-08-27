package dev.keryeshka.voxyseeu.api.addon.protocol;

import dev.keryeshka.voxyseeu.api.addon.AddonCloseReason;
import dev.keryeshka.voxyseeu.api.addon.AddonDecision;
import dev.keryeshka.voxyseeu.api.addon.AddonDescriptor;
import dev.keryeshka.voxyseeu.api.addon.AddonDirection;
import dev.keryeshka.voxyseeu.api.addon.AddonLimits;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bounded wire codec for SeeU's addon-only control and data channels. */
public final class AddonBusCodec {
    private static final int OFFER_LIST = 0;
    private static final int ACCEPTANCE_LIST = 1;
    private static final int CLOSE = 2;
    private static final int RENEGOTIATE = 3;

    private AddonBusCodec() {
    }

    public static void encodeControl(ByteBuf buf, AddonControlMessage message) {
        writeVarInt(buf, AddonLimits.BUS_PROTOCOL_VERSION);
        if (message instanceof AddonOfferList offers) {
            buf.writeByte(OFFER_LIST);
            writeVarLong(buf, offers.generation());
            writeVarInt(buf, offers.offers().size());
            for (AddonOffer offer : offers.offers()) {
                writeDescriptor(buf, offer.descriptor());
                writeBytes(buf, offer.helloDataForCodec(), AddonLimits.MAX_HANDSHAKE_BYTES);
            }
            return;
        }
        if (message instanceof AddonAcceptanceList acceptances) {
            buf.writeByte(ACCEPTANCE_LIST);
            writeVarLong(buf, acceptances.generation());
            writeVarInt(buf, acceptances.acceptances().size());
            for (AddonAcceptance acceptance : acceptances.acceptances()) {
                writeDescriptor(buf, acceptance.descriptor());
                AddonDecision decision = acceptance.decision();
                buf.writeBoolean(decision.accepted());
                writeBytes(buf, decision.acknowledgementData(), AddonLimits.MAX_HANDSHAKE_BYTES);
            }
            return;
        }
        if (message instanceof AddonCloseMessage close) {
            buf.writeByte(CLOSE);
            writeVarLong(buf, close.generation());
            writeAddonId(buf, close.addonId());
            writeVarInt(buf, close.reason().ordinal());
            return;
        }
        if (message instanceof AddonRenegotiateMessage renegotiate) {
            buf.writeByte(RENEGOTIATE);
            writeVarLong(buf, renegotiate.currentGeneration());
            return;
        }
        throw new IllegalArgumentException("Unknown addon control message: " + message);
    }

    public static AddonControlMessage decodeControl(ByteBuf buf) {
        requireBusVersion(buf);
        int type = buf.readUnsignedByte();
        AddonControlMessage message = switch (type) {
            case OFFER_LIST -> decodeOfferList(buf);
            case ACCEPTANCE_LIST -> decodeAcceptanceList(buf);
            case CLOSE -> decodeClose(buf);
            case RENEGOTIATE -> new AddonRenegotiateMessage(readVarLong(buf));
            default -> throw new IllegalArgumentException("Unknown addon control message type: " + type);
        };
        requireFullyRead(buf);
        return message;
    }

    public static void encodeData(ByteBuf buf, AddonEnvelope envelope) {
        writeVarInt(buf, AddonLimits.BUS_PROTOCOL_VERSION);
        writeVarLong(buf, envelope.generation());
        writeAddonId(buf, envelope.addonId());
        writeBytes(buf, envelope.payloadForCodec(), AddonLimits.MAX_DATA_BYTES);
    }

    public static AddonEnvelope decodeData(ByteBuf buf) {
        requireBusVersion(buf);
        AddonEnvelope envelope = AddonEnvelope.fromCodec(
                readPositiveVarLong(buf, "generation"),
                readAddonId(buf),
                readBytes(buf, AddonLimits.MAX_DATA_BYTES)
        );
        requireFullyRead(buf);
        return envelope;
    }

    private static AddonOfferList decodeOfferList(ByteBuf buf) {
        long generation = readPositiveVarLong(buf, "generation");
        int count = readBoundedCount(buf);
        List<AddonOffer> offers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            offers.add(AddonOffer.fromCodec(
                    readDescriptor(buf),
                    readBytes(buf, AddonLimits.MAX_HANDSHAKE_BYTES)
            ));
        }
        return new AddonOfferList(generation, offers);
    }

    private static AddonAcceptanceList decodeAcceptanceList(ByteBuf buf) {
        long generation = readPositiveVarLong(buf, "generation");
        int count = readBoundedCount(buf);
        List<AddonAcceptance> acceptances = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            AddonDescriptor descriptor = readDescriptor(buf);
            boolean accepted = buf.readBoolean();
            byte[] acknowledgement = readBytes(buf, AddonLimits.MAX_HANDSHAKE_BYTES);
            acceptances.add(new AddonAcceptance(
                    descriptor,
                    new AddonDecision(accepted, acknowledgement)
            ));
        }
        return new AddonAcceptanceList(generation, acceptances);
    }

    private static AddonCloseMessage decodeClose(ByteBuf buf) {
        long generation = readPositiveVarLong(buf, "generation");
        String addonId = readAddonId(buf);
        int ordinal = readVarInt(buf);
        AddonCloseReason[] reasons = AddonCloseReason.values();
        if (ordinal < 0 || ordinal >= reasons.length) {
            throw new IllegalArgumentException("Invalid addon close reason: " + ordinal);
        }
        return new AddonCloseMessage(generation, addonId, reasons[ordinal]);
    }

    private static void writeDescriptor(ByteBuf buf, AddonDescriptor descriptor) {
        writeAddonId(buf, descriptor.id());
        writeVarInt(buf, descriptor.protocolVersion());
        writeVarInt(buf, descriptor.direction().ordinal());
        writeVarInt(buf, descriptor.maximumPayloadBytes());
    }

    private static AddonDescriptor readDescriptor(ByteBuf buf) {
        String id = readAddonId(buf);
        int protocolVersion = readVarInt(buf);
        int directionOrdinal = readVarInt(buf);
        AddonDirection[] directions = AddonDirection.values();
        if (directionOrdinal < 0 || directionOrdinal >= directions.length) {
            throw new IllegalArgumentException("Invalid addon direction: " + directionOrdinal);
        }
        int maximumPayloadBytes = readVarInt(buf);
        return new AddonDescriptor(id, protocolVersion, directions[directionOrdinal], maximumPayloadBytes);
    }

    private static int readBoundedCount(ByteBuf buf) {
        int count = readVarInt(buf);
        if (count < 0 || count > AddonLimits.MAX_OFFERS) {
            throw new IllegalArgumentException("Addon list count exceeds " + AddonLimits.MAX_OFFERS);
        }
        return count;
    }

    private static void writeAddonId(ByteBuf buf, String addonId) {
        if (!AddonDescriptor.isValidId(addonId)) {
            throw new IllegalArgumentException("Invalid addon id: " + addonId);
        }
        byte[] bytes = addonId.getBytes(StandardCharsets.US_ASCII);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readAddonId(ByteBuf buf) {
        int length = readLength(buf, AddonLimits.MAX_ADDON_ID_BYTES);
        if (length == 0) {
            throw new IllegalArgumentException("Addon id cannot be empty");
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        for (byte value : bytes) {
            if ((value & 0x80) != 0) {
                throw new IllegalArgumentException("Addon id must contain ASCII characters only");
            }
        }
        String id = new String(bytes, StandardCharsets.US_ASCII);
        if (!AddonDescriptor.isValidId(id)) {
            throw new IllegalArgumentException("Invalid addon id: " + id);
        }
        return id;
    }

    private static void writeBytes(ByteBuf buf, byte[] bytes, int maximum) {
        if (bytes.length > maximum) {
            throw new IllegalArgumentException("Byte array exceeds the negotiated wire limit");
        }
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    private static byte[] readBytes(ByteBuf buf, int maximum) {
        int length = readLength(buf, maximum);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return bytes;
    }

    private static int readLength(ByteBuf buf, int maximum) {
        int length = readVarInt(buf);
        if (length < 0 || length > maximum) {
            throw new IllegalArgumentException("Byte array length exceeds " + maximum);
        }
        if (buf.readableBytes() < length) {
            throw new IllegalArgumentException("Truncated addon payload");
        }
        return length;
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Cannot encode a negative VarInt");
        }
        while ((value & -128) != 0) {
            buf.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    private static int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;
        while (position < 35) {
            if (!buf.isReadable()) {
                throw new IllegalArgumentException("Truncated VarInt");
            }
            int current = buf.readUnsignedByte();
            value |= (current & 127) << position;
            if ((current & 128) == 0) {
                return value;
            }
            position += 7;
        }
        throw new IllegalArgumentException("VarInt exceeds the 5-byte limit");
    }

    private static void writeVarLong(ByteBuf buf, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Cannot encode a negative VarLong");
        }
        while ((value & -128L) != 0) {
            buf.writeByte((int) (value & 127L) | 128);
            value >>>= 7;
        }
        buf.writeByte((int) value);
    }

    private static long readVarLong(ByteBuf buf) {
        long value = 0;
        int position = 0;
        while (position < 70) {
            if (!buf.isReadable()) {
                throw new IllegalArgumentException("Truncated VarLong");
            }
            int current = buf.readUnsignedByte();
            value |= (long) (current & 127) << position;
            if ((current & 128) == 0) {
                if (value < 0) {
                    throw new IllegalArgumentException("VarLong exceeds the signed positive range");
                }
                return value;
            }
            position += 7;
        }
        throw new IllegalArgumentException("VarLong exceeds the 10-byte limit");
    }

    private static long readPositiveVarLong(ByteBuf buf, String field) {
        long value = readVarLong(buf);
        if (value <= 0) {
            throw new IllegalArgumentException("Addon " + field + " must be positive");
        }
        return value;
    }

    private static void requireFullyRead(ByteBuf buf) {
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Trailing bytes in addon packet");
        }
    }

    private static void requireBusVersion(ByteBuf buf) {
        int version = readVarInt(buf);
        if (version != AddonLimits.BUS_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported SeeU addon bus version: " + version);
        }
    }
}
