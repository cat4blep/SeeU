package dev.keryeshka.voxyseeu.common;

public final class SharedDefaults {
    public static final int PROTOCOL_VERSION = 3;
    public static final int DEFAULT_UPDATE_INTERVAL_TICKS = 10;
    public static final int DEFAULT_MAX_RENDER_DISTANCE_BLOCKS = 8192;
    public static final int DEFAULT_MIN_PROXY_DISTANCE_BLOCKS = 0;
    public static final boolean DEFAULT_RENDER_NAME_TAGS = true;
    public static final boolean DEFAULT_SEND_SPECTATORS = false;
    public static final boolean DEFAULT_SHARE_SELF = true;
    public static final int DEFAULT_SHARE_MAX_DISTANCE_BLOCKS = DEFAULT_MAX_RENDER_DISTANCE_BLOCKS;

    private SharedDefaults() {
    }
}
