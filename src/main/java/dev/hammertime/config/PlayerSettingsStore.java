package dev.hammertime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Loads and saves per-player settings, keyed by UUID. */
public final class PlayerSettingsStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("hammertime");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, PlayerSettings>>() {
    }.getType();

    private final Path path;
    private final HammerConfig config;
    private final Map<UUID, PlayerSettings> settings = new HashMap<>();

    public PlayerSettingsStore(Path path, HammerConfig config) {
        this.path = path;
        this.config = config;
        load();
    }

    public PlayerSettings get(UUID uuid) {
        return settings.getOrDefault(uuid, PlayerSettings.fromConfig(config));
    }

    public void set(UUID uuid, PlayerSettings value) {
        settings.put(uuid, value);
        save();
    }

    private void load() {
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, PlayerSettings> raw = GSON.fromJson(reader, TYPE);
            if (raw != null) {
                raw.forEach((key, value) -> {
                    try {
                        settings.put(UUID.fromString(key), value);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Ignoring malformed UUID '{}' in {}", key, path);
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read {}; starting empty", path, e);
        }
    }

    public void save() {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Map<String, PlayerSettings> raw = new HashMap<>();
            settings.forEach((uuid, value) -> raw.put(uuid.toString(), value));
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(raw, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to write {}", path, e);
        }
    }
}
