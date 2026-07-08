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
    private static final String WINDOWS_METADATA_SEPARATOR = "|||";

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

    private static boolean isRunningUnderWine() {
        return System.getenv("WINEPREFIX") != null || System.getenv("WINE") != null
                || System.getenv("WINELOADER") != null;
    }

    private static void pollMetadata() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("linux")) {
                currentMetadata = getLinuxMediaMetadata();
            } else if (os.contains("win") && isRunningUnderWine()) {
                // Running under Wine on Linux — use MPRIS/D-Bus instead of PowerShell
                currentMetadata = getLinuxMediaMetadata();
            } else if (os.contains("win")) {
                currentMetadata = isWindows10OrNewer() ? getWindowsMediaMetadata() : new MediaMetadata("Windows", "", "", "");
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

        String psScript = buildWindowsPowerShellScript(tmpPath);

        String output = runCommand(new String[]{"powershell", "-NoProfile", "-Command", psScript});

        return parseWindowsMetadataOutput(output);
    }

    static String buildWindowsPowerShellScript(String tmpPath) {
        String safeTmpPath = tmpPath.replace("'", "''");
        return "$ErrorActionPreference='Stop'; " +
               "try { " +
               "  Add-Type -AssemblyName System.Runtime.WindowsRuntime; " +
               "  [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]; " +
               "  [void][Windows.Storage.Streams.DataReader, Windows, ContentType=WindowsRuntime]; " +
               "  $managerTask = [System.WindowsRuntimeSystemExtensions]::AsTask([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()); " +
               "  $manager = $managerTask.GetAwaiter().GetResult(); " +
               "  if (-not $manager) { return }; " +
               "  $sessions = $manager.GetSessions(); " +
               "  $session = $null; " +
               "  foreach ($candidate in $sessions) { " +
               "    $playback = $candidate.GetPlaybackInfo(); " +
               "    if ($playback -and $playback.PlaybackStatus -eq [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing) { " +
               "      $session = $candidate; break; " +
               "    } " +
               "  } " +
               "  if (-not $session) { $session = $manager.GetCurrentSession(); } " +
               "  if (-not $session -and $sessions.Count -gt 0) { $session = $sessions[0]; } " +
               "  if (-not $session) { return }; " +
               "  $propsTask = [System.WindowsRuntimeSystemExtensions]::AsTask($session.TryGetMediaPropertiesAsync()); " +
               "  $props = $propsTask.GetAwaiter().GetResult(); " +
               "  if (-not $props) { return }; " +
               "  $title = if ($props.Title) { [string]$props.Title } else { '' }; " +
               "  $artist = if ($props.Artist) { [string]$props.Artist } else { '' }; " +
               "  $artUrl = ''; " +
               "  if ($props.Thumbnail) { " +
               "    $streamTask = [System.WindowsRuntimeSystemExtensions]::AsTask($props.Thumbnail.OpenReadAsync()); " +
               "    $stream = $streamTask.GetAwaiter().GetResult(); " +
               "    if ($stream) { " +
               "      $size = [int]$stream.Size; " +
               "      if ($size -gt 0) { " +
               "        $reader = New-Object Windows.Storage.Streams.DataReader($stream); " +
               "        try { " +
               "          $loadTask = [System.WindowsRuntimeSystemExtensions]::AsTask($reader.LoadAsync($size)); " +
               "          $null = $loadTask.GetAwaiter().GetResult(); " +
               "          $buffer = New-Object byte[] $size; " +
               "          $reader.ReadBytes($buffer); " +
               "          [System.IO.File]::WriteAllBytes('" + safeTmpPath + "', $buffer); " +
               "          $artUrl = ('file:///' + '" + safeTmpPath + "'.Replace('\\', '/')); " +
               "        } finally { $reader.Dispose(); $stream.Dispose(); } " +
               "      } " +
               "    } " +
               "  } " +
               "  Write-Output ($title + '" + WINDOWS_METADATA_SEPARATOR + "' + $artist + '" + WINDOWS_METADATA_SEPARATOR + "' + $artUrl); " +
               "} catch { }";
    }

    static MediaMetadata parseWindowsMetadataOutput(String output) {
        if (output == null || output.equals("Error") || output.isBlank()) {
            return new MediaMetadata("None", "", "", "");
        }

        String[] lines = output.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.contains(WINDOWS_METADATA_SEPARATOR)) {
                continue;
            }

            String[] parts = line.split("\\Q" + WINDOWS_METADATA_SEPARATOR + "\\E", 3);
            String title = parts.length > 0 ? parts[0].trim() : "";
            String artist = parts.length > 1 ? parts[1].trim() : "";
            String artUrl = parts.length > 2 ? parts[2].trim() : "";
            if (!title.isEmpty()) {
                return new MediaMetadata("Windows", title, artist, artUrl);
            }
        }

        return new MediaMetadata("None", "", "", "");
    }

    static boolean isWindows10OrNewer() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return false;
        }

        String version = System.getProperty("os.version", "0");
        String[] tokens = version.split("\\.");
        int major = parseIntOrDefault(tokens.length > 0 ? tokens[0] : "0", 0);
        int minor = parseIntOrDefault(tokens.length > 1 ? tokens[1] : "0", 0);

        // Win10 reports 10.0, Win11 also reports 10.0 with modern builds.
        return major > 10 || (major == 10 && minor >= 0);
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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