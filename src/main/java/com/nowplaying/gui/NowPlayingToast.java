package com.nowplaying.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class NowPlayingToast {
    private static long showUntil = 0L;
    private static String lastMessage = "";

    public static void show(Minecraft client, String message) {
        if (client == null) {
            return;
        }
        lastMessage = message;
        showUntil = System.currentTimeMillis() + 2500L;
        client.gui.toastManager().showNowPlayingToast();
    }

    public static void render(Minecraft client, GuiGraphicsExtractor extractor) {
        if (client == null || System.currentTimeMillis() >= showUntil) {
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int x = width - 260;
        int y = 10;
        extractor.fill(x, y, x + 250, y + 36, 0xCC000000);
        extractor.outline(x, y, x + 250, y + 36, 0xFFFFFFFF);
        extractor.text(client.font, Component.literal(lastMessage), x + 8, y + 10, 0xFFFFFF);
    }
}
