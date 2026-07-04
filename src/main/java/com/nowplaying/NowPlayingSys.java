package com.nowplaying;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NowPlayingSys implements ModInitializer {
    public static final String MOD_ID = "nowplayingsys";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        String currentTrack = MediaDetector.getCurrentlyPlaying();
        LOGGER.info("NowPlayingSys initialized. Current track: {}", currentTrack);
        LOGGER.info("HUD overlay is enabled. Open a world and look at the top-left corner to see the current track.");
    }
}
