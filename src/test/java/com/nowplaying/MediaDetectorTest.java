package com.nowplaying;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaDetectorTest {
    @Test
    void parsesSimpleMetadata() {
        String metadata = "title=\"Test Track\"\nartist=\"Example Artist\"";
        String result = MediaDetector.extractTitle(metadata);
        assertTrue(result.contains("Test Track"));
    }

    @Test
    void parsesDbusStyleMetadata() {
        String metadata = "method return time=...\n"
            + "  variant dict entry(\n"
            + "    string \"xesam:title\"\n"
            + "    variant string \"Night Drive\"\n"
            + "  )";
        String result = MediaDetector.extractTitle(metadata);
        assertTrue(result.contains("Night Drive"));
    }

    @Test
    void buildsMprisPlayPauseCommand() {
        String command = MediaDetector.buildMprisCommand("PlayPause");
        assertTrue(command.contains("PlayPause"));
        assertTrue(command.contains("org.mpris.MediaPlayer2.Player"));
    }
}
