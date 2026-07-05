package com.nowplaying;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaDetector {
    private static final int COMMAND_TIMEOUT = 3;

    // Robust multi-line block parsers for Linux MPRIS D-Bus output
    private static final Pattern TITLE_BLOCK_PATTERN = Pattern.compile(
        "string\\s+\\\"(?:xesam:)?title\\\"\\s*\\n\\s*variant\\s+string\\s+\\\"([^\\\"]+)\\\""
    );
    private static final Pattern ARTIST_BLOCK_PATTERN = Pattern.compile(
        "string\\s+\\\"(?:xesam:)?artist\\\"\\s*\\n\\s*variant\\s+array\\s+\\[\\s*\\n?\\s*string\\s+\\\"([^\\\"]+)\\\""
    );
    private static final Pattern ART_URL_BLOCK_PATTERN = Pattern.compile(
        "string\\s+\\\"mpris:artUrl\\\"\\s*\\n\\s*variant\\s+string\\s+\\\"([^\\\"]+)\\\""
    );
    private static final Pattern MPRIS_NAME_PATTERN = Pattern.compile("\\\"(org\\.mpris\\.MediaPlayer2\\.[^\\\"]+)\\\"");

    // Thread-safe Caching Variables
    private static volatile MediaMetadata currentMetadata = new MediaMetadata("None", "", "", "");
    private static volatile String activeMprisPlayer = "org.mpris.MediaPlayer2.spotify"; 

    // Background Polling Scheduler
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MediaDetector-Background");
        t.setDaemon(true);
        return t;
    });

    static {
        SCHEDULER.scheduleAtFixedRate(MediaDetector::pollMetadata, 0, 2, TimeUnit.SECONDS);
    }

    public static record MediaMetadata(String source, String title, String artist, String artUrl) {
        public boolean hasTrack() {
            return title != null && !title.isBlank();
        }
        public boolean hasArtUrl() {
            return artUrl != null && !artUrl.isBlank();
        }
    }

    public static MediaMetadata getCurrentlyPlayingMetadata() {
        return currentMetadata;
    }

    public static void refreshMetadata() {
        try {
            pollMetadata();
        } catch (Exception ignored) {}
    }

    private static void pollMetadata() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("linux")) {
                currentMetadata = getLinuxMediaMetadata();
            } else if (os.contains("win")) {
                currentMetadata = getWindowsMediaMetadata();
            } else if (os.contains("mac")) {
                currentMetadata = getMacMediaMetadata();
            } else {
                currentMetadata = new MediaMetadata("None", "", "", "");
            }
        } catch (Exception e) {
            currentMetadata = new MediaMetadata("Error", "Error fetching data", "", "");
        }
    }

    public static String buildMprisCommand(String method) {
        return String.format(
            "dbus-send --print-reply --dest=%s /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.%s 2>/dev/null",
            activeMprisPlayer, method
        );
    }

    public static boolean sendMprisCommand(String method) {
        try {
            String command = buildMprisCommand(method);
            Process process = new ProcessBuilder("bash", "-c", command)
                .redirectErrorStream(true)
                .start();
            boolean finished = process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static MediaMetadata getLinuxMediaMetadata() throws IOException, InterruptedException {
        for (String player : listMprisPlayers()) {
            String source = player.substring(player.lastIndexOf('.') + 1);
            MediaMetadata metadata = getLinuxMetadataFromPlayer(player, source);
            
            if (metadata.hasTrack()) {
                activeMprisPlayer = player;
                return metadata;
            }
        }
        return new MediaMetadata("None", "", "", "");
    }

    private static String[] listMprisPlayers() throws IOException, InterruptedException {
        String namesOutput = runCommand(new String[]{
            "bash", "-c",
            "dbus-send --print-reply --dest=org.freedesktop.DBus /org/freedesktop/DBus org.freedesktop.DBus.ListNames 2>/dev/null"
        });
        if (namesOutput.equals("Error")) return new String[0];

        return MPRIS_NAME_PATTERN.matcher(namesOutput).results()
            .map(matchResult -> matchResult.group(1))
            .toArray(String[]::new);
    }

    private static MediaMetadata getLinuxMetadataFromPlayer(String player, String source) throws IOException, InterruptedException {
        String output = runCommand(new String[]{
            "bash", "-c",
            "dbus-send --print-reply --dest=" + player + " /org/mpris/MediaPlayer2 " +
            "org.freedesktop.DBus.Properties.Get string:org.mpris.MediaPlayer2.Player string:Metadata 2>/dev/null"
        });
        return new MediaMetadata(source, extractTitle(output), extractArtist(output), extractArtUrl(output));
    }

    private static MediaMetadata getWindowsMediaMetadata() throws IOException, InterruptedException {
        String tmpPath = new File(System.getProperty("java.io.tmpdir"), "mcmusic_art.jpg").getAbsolutePath().replace("\\", "/");
        
        String psScript = "$s = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync().GetAwaiter().GetResult().GetCurrentSession(); " +
                          "if ($s) { " +
                          "  $p = $s.TryGetMediaPropertiesAsync().GetAwaiter().GetResult(); " +
                          "  $artUrl = ''; " +
                          "  if ($p.Thumbnail) { " +
                          "    $stream = $p.Thumbnail.OpenReadAsync().GetAwaiter().GetResult(); " +
                          "    $buffer = New-Object Byte[] $stream.Size; " +
                          "    $reader = New-Object Windows.Storage.Streams.DataReader $stream; " +
                          "    $reader.LoadAsync($stream.Size).GetAwaiter().GetResult(); " +
                          "    $reader.ReadBytes($buffer); " +
                          "    [System.IO.File]::WriteAllBytes('" + tmpPath + "', $buffer); " +
                          "    $artUrl = 'file://" + tmpPath + "'; " +
                          "  }; " +
                          "  Write-Output ($p.Title + '|||' + $p.Artist + '|||' + $artUrl) " +
                          "}";
        
        String output = runCommand(new String[]{"powershell", "-NoProfile", "-Command", psScript});
        
        if (!output.equals("Error") && !output.isBlank()) {
            String[] parts = output.split("\\|\\|\\|");
            String title = parts.length > 0 ? parts[0].trim() : "";
            String artist = parts.length > 1 ? parts[1].trim() : "";
            String artUrl = parts.length > 2 ? parts[2].trim() : "";
            if (!title.isEmpty()) return new MediaMetadata("Windows", title, artist, artUrl);
        }
        return new MediaMetadata("None", "", "", "");
    }

    private static MediaMetadata getMacMediaMetadata() throws IOException, InterruptedException {
        String track = runCommand(new String[]{"osascript", "-e", "tell application \"Spotify\" to if running then get name of current track"});
        if (!track.equals("Error") && !track.equals("missing value") && !track.isBlank()) {
            return new MediaMetadata("Spotify", track, "", "");
        }
        return new MediaMetadata("None", "", "", "");
    }

    private static String runCommand(String[] command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return "Error";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            return output.toString().trim().isEmpty() ? "Error" : output.toString().trim();
        }
    }

    static String extractTitle(String metadata) {
        if (metadata == null || metadata.equals("Error")) return "";
        Matcher m = TITLE_BLOCK_PATTERN.matcher(metadata);
        return m.find() ? m.group(1).trim() : "";
    }

    static String extractArtist(String metadata) {
        if (metadata == null || metadata.equals("Error")) return "";
        Matcher m = ARTIST_BLOCK_PATTERN.matcher(metadata);
        return m.find() ? m.group(1).trim() : "";
    }

    private static String extractArtUrl(String dbusOutput) {
        if (dbusOutput == null || dbusOutput.equals("Error")) return "";
        Matcher matcher = ART_URL_BLOCK_PATTERN.matcher(dbusOutput);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    public static String getCurrentlyPlaying() {
        MediaMetadata metadata = getCurrentlyPlayingMetadata();
        if (metadata == null || !metadata.hasTrack()) {
            return "No media detected";
        }
        return formatHudText(metadata);
    }

    public static String formatHudText(MediaMetadata metadata) {
        if (metadata == null || !metadata.hasTrack()) {
            return "Now Playing: Nothing";
        }
        if (metadata.artist() != null && !metadata.artist().isBlank()) {
            return "Now Playing: " + metadata.title() + " — " + metadata.artist();
        }
        return "Now Playing: " + metadata.title();
    }

    public static String formatHudText(String track) {
        if (track == null || track.isBlank()) {
            return "Now Playing: Nothing";
        }
        return "Now Playing: " + track;
    }
}