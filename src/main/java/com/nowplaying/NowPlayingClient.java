package com.nowplaying;

import com.mojang.blaze3d.platform.InputConstants;
import com.nowplaying.gui.NowPlayingScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NowPlayingClient implements ClientModInitializer {
    private static boolean f8WasDown;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean f8Down = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_F8);
            if (f8Down && !f8WasDown) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null) {
                    Screen currentScreen = getCurrentScreen(minecraft);
                    if (currentScreen instanceof NowPlayingScreen) {
                        setCurrentScreen(minecraft, null);
                    } else {
                        setCurrentScreen(minecraft, new NowPlayingScreen());
                    }
                }
            }
            f8WasDown = f8Down;
        });
    }

    private static Screen getCurrentScreen(Minecraft minecraft) {
        Object gui = minecraft.gui;
        if (gui != null) {
            try {
                Method screenMethod = gui.getClass().getMethod("screen");
                Object screen = screenMethod.invoke(gui);
                if (screen instanceof Screen s) {
                    return s;
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall back to older client field access.
            }
        }

        try {
            Field screenField = minecraft.getClass().getField("screen");
            Object screen = screenField.get(minecraft);
            if (screen instanceof Screen s) {
                return s;
            }
        } catch (ReflectiveOperationException ignored) {
            // No compatible screen accessor exists on this runtime.
        }

        return null;
    }

    private static void setCurrentScreen(Minecraft minecraft, Screen screen) {
        Object gui = minecraft.gui;
        if (gui != null) {
            try {
                Method setScreenMethod = gui.getClass().getMethod("setScreen", Screen.class);
                setScreenMethod.invoke(gui, screen);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Fall back to older client method access.
            }
        }

        try {
            Method setScreenMethod = minecraft.getClass().getMethod("setScreen", Screen.class);
            setScreenMethod.invoke(minecraft, screen);
        } catch (ReflectiveOperationException ignored) {
            // No compatible screen mutator exists on this runtime.
        }
    }
}
