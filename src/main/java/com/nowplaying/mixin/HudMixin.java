package com.nowplaying.mixin;

import com.nowplaying.MediaDetector;
import com.nowplaying.NowPlayingSys;
import com.nowplaying.gui.NowPlayingToast;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class HudMixin {
    @Unique
    private static final int HUD_COLOR = 0xFFFFFF;
    @Unique
    private static String lastReportedTrack = null;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void nowPlayingSys$renderOverlay(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        String track = MediaDetector.getCurrentlyPlaying();
        String hudText = MediaDetector.formatHudText(track);
        if (!track.equals(lastReportedTrack)) {
            lastReportedTrack = track;
            NowPlayingSys.LOGGER.info("Now playing: {}", track);
            NowPlayingToast.show(client, hudText);
        }

        extractor.text(client.font, hudText, 8, 8, HUD_COLOR);
    }
}
