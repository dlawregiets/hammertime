package dev.hammertime;

import dev.hammertime.command.HammerCommand;
import dev.hammertime.config.HammerConfig;
import dev.hammertime.config.PlayerSettingsStore;
import dev.hammertime.mining.CubeMiner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class HammerTime implements ModInitializer {
    public static final String MOD_ID = "hammertime";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static HammerConfig config;
    private static PlayerSettingsStore store;
    private static CubeMiner miner;

    @Override
    public void onInitialize() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        config = HammerConfig.load(configDir.resolve("hammertime.json"));
        store = new PlayerSettingsStore(configDir.resolve("hammertime-players.json"), config);
        miner = new CubeMiner(config, store);

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                miner.onBlockBroken(serverLevel, serverPlayer, pos, state);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> store.save());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                HammerCommand.register(dispatcher, config, store, HammerTime::reloadConfig));

        LOGGER.info("HammerTime initialized");
    }

    public static HammerConfig config() {
        return config;
    }

    public static PlayerSettingsStore store() {
        return store;
    }

    public static void reloadConfig() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("hammertime.json");
        config.copyFrom(HammerConfig.load(path));
        LOGGER.info("HammerTime config reloaded: {}", config);
    }
}
