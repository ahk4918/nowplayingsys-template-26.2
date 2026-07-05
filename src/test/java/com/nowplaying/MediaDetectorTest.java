package com.nowplaying;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaDetectorTest {
    // Real dbus-send output format (condensed)
    private static final String DBUS_METADATA =
        "method return time=...\n" +
        "   variant       dict entry(\n" +
        "         string \"xesam:title\"\n" +
        "         variant             string \"Test Track\"\n" +
        "      )\n" +
        "      dict entry(\n" +
        "         string \"xesam:artist\"\n" +
        "         variant             array [\n" +
        "               string \"Example Artist\"\n" +
        "            ]\n" +
        "      )\n" +
        "      dict entry(\n" +
        "         string \"mpris:artUrl\"\n" +
        "         variant             string \"https://i.scdn.co/image/abc123\"\n" +
        "      )\n" +
        "   )\n";

    @Test
    void parsesSimpleMetadata() {
        assertTrue(MediaDetector.extractTitle(DBUS_METADATA).contains("Test Track"));
    }

    @Test
    void parsesDbusStyleMetadata() {
        String metadata = "method return time=...\n"
            + "  variant dict entry(\n"
            + "    string \"xesam:title\"\n"
            + "    variant             string \"Night Drive\"\n"
            + "  )";
        assertTrue(MediaDetector.extractTitle(metadata).contains("Night Drive"));
    }

    @Test
    void buildsMprisPlayPauseCommand() {
        String command = MediaDetector.buildMprisCommand("PlayPause");
        assertTrue(command.contains("PlayPause"));
        assertTrue(command.contains("org.mpris.MediaPlayer2.Player"));
    }

    @Test
    void extractsArtistFromMetadata() {
        assertTrue(MediaDetector.extractArtist(DBUS_METADATA).contains("Example Artist"));
    }
}
