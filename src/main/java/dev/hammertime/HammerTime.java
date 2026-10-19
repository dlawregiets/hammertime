package dev.hammertime;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HammerTime implements ModInitializer {
    public static final String MOD_ID = "hammertime";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("HammerTime initializing");
    }
}
