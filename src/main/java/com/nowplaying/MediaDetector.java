package com.nowplaying;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaDetector {
    private static final int COMMAND_TIMEOUT = 3;
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?:^|\\n)(?:xesam:)?title\\s*=\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern DBUS_TITLE_PATTERN = Pattern.compile("(?:^|\\n)(?:xesam:)?title\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern TITLE_KEY_PATTERN = Pattern.compile("(?i)(?:^|\\W)(?:xesam:|mpris:)?title\\b");
    private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("\\\"([^\\\"]*)\\\"");

    public static String buildMprisCommand(String method) {
        return String.format(
            "dbus-send --print-reply --dest=org.mpris.MediaPlayer2.spotify /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.%s 2>/dev/null",
            method
        );
    }

    public static boolean sendMprisCommand(String method) {
        try {
            String command = buildMprisCommand(method);
            Process process = new ProcessBuilder("bash", "-c", command)
                .redirectErrorStream(true)
                .start();
            boolean finished = process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            NowPlayingSys.LOGGER.warn("Failed to send MPRIS command {}", method, e);
            return false;
        }
    }

    public static String getCurrentlyPlaying() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                return getWindowsMedia();
            } else if (os.contains("linux")) {
                return getLinuxMedia();
            } else if (os.contains("mac")) {
                return getMacMedia();
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
        return "No media detected";
    }

    public static String formatHudText(String track) {
        if (track == null || track.isBlank()) {
            return "Now Playing: Nothing";
        }
        return "Now Playing: " + track;
    }

    static String extractTitle(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return "";
        }

        String normalized = metadata.replace("\r", "\n");
        Matcher matcher = DBUS_TITLE_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        Matcher fallbackMatcher = TITLE_PATTERN.matcher(normalized);
        if (fallbackMatcher.find()) {
            return fallbackMatcher.group(1).trim();
        }

        Matcher titleKeyMatcher = TITLE_KEY_PATTERN.matcher(normalized);
        while (titleKeyMatcher.find()) {
            Matcher quotedMatcher = QUOTED_STRING_PATTERN.matcher(normalized);
            while (quotedMatcher.find()) {
                int quoteStart = quotedMatcher.start();
                if (quoteStart <= titleKeyMatcher.start()) {
                    continue;
                }

                String candidate = quotedMatcher.group(1).trim();
                if (!candidate.isEmpty() && !candidate.equalsIgnoreCase("title") && !candidate.equalsIgnoreCase("xesam:title")) {
                    return candidate;
                }
            }
        }

        return "";
    }

    private static String getWindowsMedia() throws IOException, InterruptedException {
        String spotifyTrack = runCommand(new String[]{"powershell", "-command",
            "(New-Object -ComObject Spotify.Application).CurrentTrack.Name 2>$null"});
        if (!spotifyTrack.isEmpty() && !spotifyTrack.equals("Error")) {
            return "Spotify: " + spotifyTrack;
        }

        String wmpTrack = runCommand(new String[]{"powershell", "-command",
            "(New-Object -ComObject WMPlayer.OCX.7).currentMedia.name 2>$null"});
        if (!wmpTrack.isEmpty() && !wmpTrack.equals("Error")) {
            return "WMP: " + wmpTrack;
        }

        return "No media detected";
    }

    private static String getLinuxMedia() throws IOException, InterruptedException {
        String spotifyMetadata = runCommand(new String[]{
            "bash", "-c",
            "dbus-send --print-reply --dest=org.mpris.MediaPlayer2.spotify /org/mpris/MediaPlayer2 " +
            "org.freedesktop.DBus.Properties.Get string:org.mpris.MediaPlayer2.Player string:Metadata 2>/dev/null"
        });
        String spotifyTrack = extractTitle(spotifyMetadata);
        if (!spotifyTrack.isEmpty() && !spotifyTrack.equals("Error")) {
            return "Spotify: " + spotifyTrack;
        }

        String genericMetadata = runCommand(new String[]{
            "bash", "-c",
            "dbus-send --print-reply --dest=org.mpris.MediaPlayer2 /org/mpris/MediaPlayer2 " +
            "org.freedesktop.DBus.Properties.Get string:org.mpris.MediaPlayer2.Player string:Metadata 2>/dev/null"
        });
        String genericTrack = extractTitle(genericMetadata);
        if (!genericTrack.isEmpty() && !genericTrack.equals("Error")) {
            return "Player: " + genericTrack;
        }

        return "No media detected";
    }

    private static String getMacMedia() throws IOException, InterruptedException {
        String spotifyTrack = runCommand(new String[]{"osascript", "-e",
            "tell application \"Spotify\" to if running then get name of current track"});
        if (!spotifyTrack.isEmpty() && !spotifyTrack.equals("Error") && !spotifyTrack.equals("missing value")) {
            return "Spotify: " + spotifyTrack;
        }

        String musicTrack = runCommand(new String[]{"osascript", "-e",
            "tell application \"Music\" to if running then get name of current track"});
        if (!musicTrack.isEmpty() && !musicTrack.equals("Error") && !musicTrack.equals("missing value")) {
            return "Music: " + musicTrack;
        }

        return "No media detected";
    }

    private static String runCommand(String[] command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();

        if (!process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return "Error";
        }

        try (InputStream inputStream = process.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            String result = output.toString().trim();
            return result.isEmpty() ? "Error" : result;
        }
    }
}