package dev.keryeshka.voxyseeu.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateIntervalMigrationTest {
    @Test
    void migratesOnlyTheOldDefaultFromAnOlderConfig() {
        assertEquals(2, UpdateIntervalMigration.migrate(3, 4, 10));
        assertEquals(7, UpdateIntervalMigration.migrate(3, 4, 7));
        assertEquals(10, UpdateIntervalMigration.migrate(4, 4, 10));
    }
}
