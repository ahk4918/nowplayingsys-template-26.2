package com.nowplaying.gui;

import com.nowplaying.MediaDetector;
import com.nowplaying.NowPlayingSys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class NowPlayingScreen extends Screen {
    private static final int TOAST_WIDTH = 280;
    private static final int TOAST_HEIGHT = 120;
    private static final int ART_SIZE = 64;
    private static final int BUTTON_SIZE = 32;

    public NowPlayingScreen() {
        super(Component.literal("Now Playing"));
    }

    @Override
    protected void init() {
        int toastY = height / 2 - TOAST_HEIGHT / 2;
        
        int spacing = 12;
        int totalButtonWidth = (BUTTON_SIZE * 3) + (spacing * 2);
        int buttonsStartX = width / 2 - totalButtonWidth / 2;
        int buttonY = toastY + TOAST_HEIGHT - BUTTON_SIZE - 12;

        MediaDetector.refreshMetadata();
        NowPlayingSys.LOGGER.info("NowPlayingScreen opened; metadata={}", MediaDetector.getCurrentlyPlayingMetadata());

        addRenderableWidget(Button.builder(Component.literal("⏮"), button -> {
            boolean sent = MediaDetector.sendMprisCommand("Previous");
            showFeedback(sent ? "Previous track requested" : "Unable to request previous track");
        }).bounds(buttonsStartX, buttonY, BUTTON_SIZE, BUTTON_SIZE)
          .tooltip(Tooltip.create(Component.literal("Previous Track")))
          .build());

        addRenderableWidget(Button.builder(Component.literal("⏯"), button -> {
            boolean sent = MediaDetector.sendMprisCommand("PlayPause");
            showFeedback(sent ? "Play/Pause sent" : "Unable to send Play/Pause");
        }).bounds(buttonsStartX + BUTTON_SIZE + spacing, buttonY, BUTTON_SIZE, BUTTON_SIZE)
          .tooltip(Tooltip.create(Component.literal("Play / Pause")))
          .build());

        addRenderableWidget(Button.builder(Component.literal("⏭"), button -> {
            boolean sent = MediaDetector.sendMprisCommand("Next");
            showFeedback(sent ? "Next track requested" : "Unable to request next track");
        }).bounds(buttonsStartX + (BUTTON_SIZE + spacing) * 2, buttonY, BUTTON_SIZE, BUTTON_SIZE)
          .tooltip(Tooltip.create(Component.literal("Next Track")))
          .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);

        int toastX = width / 2 - TOAST_WIDTH / 2;
        int toastY = height / 2 - TOAST_HEIGHT / 2;
        int artX = toastX + 16;
        int artY = toastY + 16;
        int textX = artX + ART_SIZE + 16;
        int maxTextWidth = TOAST_WIDTH - (ART_SIZE + 48);

        MediaDetector.MediaMetadata metadata = MediaDetector.getCurrentlyPlayingMetadata();
        
        String titleLine, artistLine, sourceLine;
        if (metadata == null) {
            titleLine = "Nothing playing";
            artistLine = "Unknown artist";
            sourceLine = "No player detected";
        } else {
            titleLine = metadata.hasTrack() ? metadata.title() : "Nothing playing";
            artistLine = metadata.hasTrack() && !metadata.artist().isBlank() ? metadata.artist() : "Unknown artist";
            sourceLine = metadata.source() != null && !metadata.source().isBlank() ? metadata.source() : "No player detected";
        }

        String displayTitle = font.width(titleLine) > maxTextWidth 
            ? font.plainSubstrByWidth(titleLine, maxTextWidth - 12) + "..." 
            : titleLine;
        String displayArtist = font.width(artistLine) > maxTextWidth 
            ? font.plainSubstrByWidth(artistLine, maxTextWidth - 12) + "..." 
            : artistLine;

        // --- 1. Draw Background & Borders ---
        guiGraphics.fill(toastX, toastY, toastX + TOAST_WIDTH, toastY + TOAST_HEIGHT, 0xEE10141C);
        guiGraphics.fillGradient(toastX + 1, toastY + 1, toastX + TOAST_WIDTH - 1, toastY + TOAST_HEIGHT - 1, 0x443A4A63, 0x1110141C);

        int borderColor = 0xFF293B54;
        guiGraphics.fill(toastX, toastY - 1, toastX + TOAST_WIDTH, toastY, borderColor);
        guiGraphics.fill(toastX, toastY + TOAST_HEIGHT, toastX + TOAST_WIDTH, toastY + TOAST_HEIGHT + 1, borderColor);
        guiGraphics.fill(toastX - 1, toastY, toastX, toastY + TOAST_HEIGHT, borderColor);
        guiGraphics.fill(toastX + TOAST_WIDTH, toastY, toastX + TOAST_WIDTH + 1, toastY + TOAST_HEIGHT, borderColor);

        // --- 2. Draw Art Slot (disabled for now) ---
        guiGraphics.fill(artX - 1, artY - 1, artX + ART_SIZE + 1, artY + ART_SIZE + 1, 0xFF3E5C88);
        guiGraphics.fill(artX, artY, artX + ART_SIZE, artY + ART_SIZE, 0xFF13233A);
        guiGraphics.text(font, Component.literal("-"), artX + ART_SIZE / 2 - 1, artY + ART_SIZE / 2 - 4, 0xFF90B3D9);

        // --- 3. Draw Typography ---
        guiGraphics.text(font, Component.translatable("gui.nowplaying.nwplaign"), textX, artY + 4, 0xFF777777);

        int shadow = 0x66000000;
        int shadowOffset = 1;

        guiGraphics.text(font, Component.literal(displayTitle), textX + shadowOffset, artY + 18 + shadowOffset, shadow);
        guiGraphics.text(font, Component.literal(displayTitle), textX, artY + 18, 0xFFFFFFFF);

        guiGraphics.text(font, Component.literal(displayArtist), textX + shadowOffset, artY + 32 + shadowOffset, shadow);
        guiGraphics.text(font, Component.literal(displayArtist), textX, artY + 32, 0xFF90B3D9);

        guiGraphics.text(font, Component.literal(sourceLine), textX, artY + 46, 0xFF7788AA);

        String closeText = "[F8] Close";
        int closeWidth = font.width(closeText);
        guiGraphics.text(font, Component.literal(closeText), toastX + TOAST_WIDTH - closeWidth - 8, toastY + 8, 0xFF555555);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void showFeedback(String message) {
        NowPlayingToast.show(minecraft, message);
    }
}