package com.dlawregiets.hammertime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Global HammerTime settings, persisted as JSON. */
public final class HammerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("hammertime");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int defaultSize = 3;
    public int[] allowedSizes = {3, 5};
    public boolean requireSneak = true;
    public boolean durabilityPerBlock = true;
    public int maxBlocksPerBreak = 128;
    public boolean enabledByDefault = true;

    public boolean isAllowedSize(int size) {
        for (int allowed : allowedSizes) {
            if (allowed == size) {
                return true;
            }
        }
        return false;
    }

    /** Loads the config, writing defaults if the file is missing or unreadable. */
    public static HammerConfig load(Path path) {
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                HammerConfig config = GSON.fromJson(reader, HammerConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to read {}; using defaults", path, e);
            }
        }
        HammerConfig config = new HammerConfig();
        config.save(path);
        return config;
    }

    public void save(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to write {}", path, e);
        }
    }

    /** Copies every field from {@code other} into this instance (used by /hammer reload). */
    public void copyFrom(HammerConfig other) {
        this.defaultSize = other.defaultSize;
        this.allowedSizes = other.allowedSizes;
        this.requireSneak = other.requireSneak;
        this.durabilityPerBlock = other.durabilityPerBlock;
        this.maxBlocksPerBreak = other.maxBlocksPerBreak;
        this.enabledByDefault = other.enabledByDefault;
    }

    @Override
    public String toString() {
        return "HammerConfig{defaultSize=" + defaultSize
                + ", allowedSizes=" + Arrays.toString(allowedSizes)
                + ", requireSneak=" + requireSneak
                + ", durabilityPerBlock=" + durabilityPerBlock
                + ", maxBlocksPerBreak=" + maxBlocksPerBreak
                + ", enabledByDefault=" + enabledByDefault + "}";
    }
}
