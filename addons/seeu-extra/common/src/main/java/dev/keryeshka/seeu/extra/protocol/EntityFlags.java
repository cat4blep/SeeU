package dev.keryeshka.seeu.extra.protocol;

public final class EntityFlags {
    public static final int ON_FIRE = 1;
    public static final int INVISIBLE = 1 << 1;
    public static final int GLOWING = 1 << 2;
    public static final int CROUCHING = 1 << 3;
    public static final int SPRINTING = 1 << 4;
    public static final int SWIMMING = 1 << 5;
    public static final int FALL_FLYING = 1 << 6;
    public static final int BABY = 1 << 7;
    public static final int KNOWN_MASK = ON_FIRE
            | INVISIBLE
            | GLOWING
            | CROUCHING
            | SPRINTING
            | SWIMMING
            | FALL_FLYING
            | BABY;

    private EntityFlags() {
    }

    public static boolean has(int flags, int flag) {
        return (flags & flag) != 0;
    }

    public static void requireValid(int flags) {
        if (flags < 0 || (flags & ~KNOWN_MASK) != 0) {
            throw new IllegalArgumentException("Entity flags contain unsupported bits");
        }
    }
}
