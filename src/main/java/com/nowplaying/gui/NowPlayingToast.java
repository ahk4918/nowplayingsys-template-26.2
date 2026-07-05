package com.nowplaying.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.network.chat.Component;

public class NowPlayingToast {
    private static final SystemToastId TOAST_ID = new SystemToastId(9001L);

    public static void show(Minecraft client, String message) {
        if (client == null) {
            return;
        }
        SystemToast.addOrUpdate(
            client.gui.toastManager(),
            TOAST_ID,
            Component.literal("Now Playing"),
            Component.literal(message)
        );
    }
}
