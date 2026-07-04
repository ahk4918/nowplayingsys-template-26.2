package com.nowplaying.gui;

import com.nowplaying.MediaDetector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class NowPlayingScreen extends Screen {
    private static final int BUTTON_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 20;

    public NowPlayingScreen() {
        super(Component.literal("Now Playing Controls"));
    }

    @Override
    protected void init() {
        int centerX = width / 2 - BUTTON_WIDTH / 2;
        int y = 80;

        addRenderableWidget(Button.builder(Component.literal("Play/Pause"), button -> {
            boolean sent = MediaDetector.sendMprisCommand("PlayPause");
            showFeedback(sent ? "Play/Pause sent" : "Unable to send Play/Pause");
        }).bounds(centerX, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
            boolean sent = MediaDetector.sendMprisCommand("Next");
            showFeedback(sent ? "Next track requested" : "Unable to request next track");
        }).bounds(centerX, y + 28, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.literal("Previous"), button -> {
            boolean sent = MediaDetector.sendMprisCommand("Previous");
            showFeedback(sent ? "Previous track requested" : "Unable to request previous track");
        }).bounds(centerX, y + 56, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.literal("Stop"), button -> {
            boolean sent = MediaDetector.sendMprisCommand("Stop");
            showFeedback(sent ? "Stop requested" : "Unable to request stop");
        }).bounds(centerX, y + 84, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> {
            String track = MediaDetector.getCurrentlyPlaying();
            showFeedback(MediaDetector.formatHudText(track));
        }).bounds(centerX, y + 112, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);
        String currentTrack = MediaDetector.getCurrentlyPlaying();
        guiGraphics.centeredText(font, title, width / 2, 20, 0xFFFFFF);
        guiGraphics.text(font, Component.literal("Current: " + MediaDetector.formatHudText(currentTrack)), 24, 48, 0xAAAAAA);
        guiGraphics.text(font, Component.literal("Press F8 again to close this screen."), 24, 64, 0xCCCCCC);
    }

    private void showFeedback(String message) {
        if (minecraft != null) {
            minecraft.gui.chatListener().handleSystemMessage(Component.literal(message), false);
        }
    }
}
