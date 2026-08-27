package dev.keryeshka.voxyseeu.common.protocol;

public final class PacketSequenceGate {
    private long lastAcceptedSequence = -1L;

    public boolean accept(long sequence) {
        if (sequence < 0L || sequence <= lastAcceptedSequence) {
            return false;
        }
        lastAcceptedSequence = sequence;
        return true;
    }

    public void reset() {
        lastAcceptedSequence = -1L;
    }

    public long lastAcceptedSequence() {
        return lastAcceptedSequence;
    }
}
