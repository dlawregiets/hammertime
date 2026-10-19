package dev.hammertime.config;

/** Per-player HammerTime settings. */
public record PlayerSettings(int size, boolean enabled) {
    public static PlayerSettings fromConfig(HammerConfig config) {
        return new PlayerSettings(config.defaultSize, config.enabledByDefault);
    }
}
