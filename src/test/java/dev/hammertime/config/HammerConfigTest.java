package dev.hammertime.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HammerConfigTest {
    @Test
    void writesDefaultsWhenMissingAndReadsBack() throws Exception {
        Path dir = Files.createTempDirectory("hammertime-test");
        Path file = dir.resolve("hammertime.json");

        HammerConfig config = HammerConfig.load(file);
        assertEquals(3, config.defaultSize);
        assertTrue(Files.exists(file));
        assertTrue(config.isAllowedSize(5));
        assertFalse(config.isAllowedSize(7));

        config.defaultSize = 5;
        config.save(file);

        HammerConfig reloaded = HammerConfig.load(file);
        assertEquals(5, reloaded.defaultSize);
    }

    @Test
    void malformedFileFallsBackToDefaults() throws Exception {
        Path dir = Files.createTempDirectory("hammertime-test");
        Path file = dir.resolve("hammertime.json");
        Files.writeString(file, "{ not valid json ");

        HammerConfig config = HammerConfig.load(file);
        assertEquals(3, config.defaultSize);
    }

    @Test
    void storeRoundTripsPerPlayerSettings() throws Exception {
        Path dir = Files.createTempDirectory("hammertime-test");
        Path file = dir.resolve("hammertime-players.json");
        HammerConfig config = new HammerConfig();

        PlayerSettingsStore store = new PlayerSettingsStore(file, config);
        UUID id = UUID.randomUUID();
        assertEquals(3, store.get(id).size());
        assertTrue(store.get(id).enabled());

        store.set(id, new PlayerSettings(5, false));

        PlayerSettingsStore reloaded = new PlayerSettingsStore(file, config);
        assertEquals(5, reloaded.get(id).size());
        assertFalse(reloaded.get(id).enabled());
    }
}
