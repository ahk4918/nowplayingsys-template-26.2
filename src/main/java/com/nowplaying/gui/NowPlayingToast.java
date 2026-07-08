package com.nowplaying.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

public class NowPlayingToast {
    private static final SystemToastId TOAST_ID = new SystemToastId(9001L);

    public static void show(Minecraft client, String message) {
        if (client == null) {
            return;
        }

        ToastManager toastManager = getToastManager(client);
        if (toastManager == null) {
            return;
        }

        SystemToast.addOrUpdate(
            toastManager,
            TOAST_ID,
            Component.literal("Now Playing"),
            Component.literal(message)
        );
    }

    private static ToastManager getToastManager(Minecraft client) {
        Object gui = client.gui;
        if (gui != null) {
            try {
                Method toastManagerMethod = gui.getClass().getMethod("toastManager");
                Object manager = toastManagerMethod.invoke(gui);
                if (manager instanceof ToastManager tm) {
                    return tm;
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall back to older Minecraft accessor.
            }
        }

        try {
            Method getToastManagerMethod = client.getClass().getMethod("getToastManager");
            Object manager = getToastManagerMethod.invoke(client);
            if (manager instanceof ToastManager tm) {
                return tm;
            }
        } catch (ReflectiveOperationException ignored) {
            // No compatible toast manager accessor exists on this runtime.
        }

        return null;
    }
}
