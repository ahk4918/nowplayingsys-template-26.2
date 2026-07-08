package com.nowplaying;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void parsesWindowsMetadataLineFromOutput() {
        String output = "some warning line\nSong Name|||Artist Name|||file:///tmp/mcmusic_art.jpg";
        MediaDetector.MediaMetadata metadata = MediaDetector.parseWindowsMetadataOutput(output);

        assertEquals("Windows", metadata.source());
        assertEquals("Song Name", metadata.title());
        assertEquals("Artist Name", metadata.artist());
        assertEquals("file:///tmp/mcmusic_art.jpg", metadata.artUrl());
    }

    @Test
    void returnsNoneForInvalidWindowsOutput() {
        MediaDetector.MediaMetadata metadata = MediaDetector.parseWindowsMetadataOutput("noise only");
        assertEquals("None", metadata.source());
        assertFalse(metadata.hasTrack());
    }

    @Test
    void buildsWindowsScriptWithWinRtBootstrap() {
        String script = MediaDetector.buildWindowsPowerShellScript("C:/Temp/mcmusic_art.jpg");
        assertTrue(script.contains("System.Runtime.WindowsRuntime"));
        assertTrue(script.contains("GlobalSystemMediaTransportControlsSessionManager"));
        assertTrue(script.contains("Write-Output"));
    }

    @Test
    void detectsWindows10ByOsVersion() {
        String oldName = System.getProperty("os.name");
        String oldVersion = System.getProperty("os.version");
        try {
            System.setProperty("os.name", "Windows 10");
            System.setProperty("os.version", "10.0");
            assertTrue(MediaDetector.isWindows10OrNewer());
        } finally {
            restoreProperty("os.name", oldName);
            restoreProperty("os.version", oldVersion);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
