package com.nowplaying;

import com.mojang.blaze3d.platform.InputConstants;
import com.nowplaying.gui.NowPlayingScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public class NowPlayingClient implements ClientModInitializer {
    private static boolean f8WasDown;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean f8Down = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_F8);
            if (f8Down && !f8WasDown) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null) {
                    Screen currentScreen = minecraft.gui.screen();
                    if (currentScreen instanceof NowPlayingScreen) {
                        minecraft.gui.setScreen(null);
                    } else {
                        minecraft.gui.setScreen(new NowPlayingScreen());
                    }
                }
            }
            f8WasDown = f8Down;
        });
    }
}
