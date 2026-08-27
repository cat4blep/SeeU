package dev.keryeshka.voxyseeu.common;

public final class UpdateIntervalMigration {
    public static final int LEGACY_DEFAULT_UPDATE_INTERVAL_TICKS = 10;

    private UpdateIntervalMigration() {
    }

    public static int migrate(int storedConfigVersion, int currentConfigVersion, int updateIntervalTicks) {
        if (storedConfigVersion < currentConfigVersion
                && updateIntervalTicks == LEGACY_DEFAULT_UPDATE_INTERVAL_TICKS) {
            return SharedDefaults.DEFAULT_UPDATE_INTERVAL_TICKS;
        }
        return updateIntervalTicks;
    }
}
