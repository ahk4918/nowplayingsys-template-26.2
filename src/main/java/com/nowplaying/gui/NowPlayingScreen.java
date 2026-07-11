package com.nowplaying.gui;

import com.daqem.uilib.gui.AbstractScreen;
import com.nowplaying.MediaDetector;
import com.nowplaying.NowPlayingSys;
import com.daqem.uilib.gui.background.DarkenedBackground;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.color.ColorComponent;
import com.daqem.uilib.gui.component.text.TextAlign;
import com.daqem.uilib.gui.component.text.TruncatedTextComponent;
import com.daqem.uilib.gui.widget.ButtonWidget;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;

public class NowPlayingScreen extends AbstractScreen {
    private static final int PANEL_WIDTH = 392;
    private static final int PANEL_HEIGHT = 228;
    private static final int PANEL_RADIUS_PAD = 8;
    private static final int ALBUM_SIZE = 144;
    private static final int PROGRESS_WIDTH = 182;
    private static final int PROGRESS_HEIGHT = 6;
    private static final Identifier FALLBACK_ALBUM_ART = Identifier.fromNamespaceAndPath(NowPlayingSys.MOD_ID, "textures/gui/album_art.png");
    private static final RenderPipeline ART_PIPELINE = RenderPipelines.GUI_TEXTURED;
    private static final ExecutorService ART_LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NowPlaying-AlbumArtLoader");
        thread.setDaemon(true);
        return thread;
    });

    private TruncatedTextComponent titleText;
    private TruncatedTextComponent artistText;
    private TruncatedTextComponent sourceText;
    private TruncatedTextComponent statusText;
    private AlbumArtComponent albumArtComponent;
    private ColorComponent progressFill;
    private ColorComponent progressTrack;
    private ButtonWidget prevButton;
    private ButtonWidget playPauseButton;
    private ButtonWidget nextButton;
    private boolean isPlaying = false;
    private float currentProgress = 0f;
    private int metadataTickCounter = 0;
    private String pendingArtUrl = "";
    private String appliedArtUrl = "";
    private CompletableFuture<AlbumArtPayload> albumArtFuture;
    private Identifier dynamicArtTextureId;
    private DynamicTexture dynamicArtTexture;

    private record AlbumArtPayload(String sourceUrl, NativeImage image, int width, int height) {
    }

    public NowPlayingScreen() {
        super(Component.literal("Now Playing"));
        this.setBackground(new DarkenedBackground());
    }

    @Override
    public void init() {
        this.clear();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int rightPaneX = panelX + 184;

        ColorComponent panel = new ColorComponent(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF131A27);
        ColorComponent panelBorder = new ColorComponent(panelX + PANEL_RADIUS_PAD, panelY + PANEL_RADIUS_PAD, PANEL_WIDTH - (PANEL_RADIUS_PAD * 2), PANEL_HEIGHT - (PANEL_RADIUS_PAD * 2), 0xFF1C2C43);

        ColorComponent leftColumn = new ColorComponent(panelX + 16, panelY + 16, 160, PANEL_HEIGHT - 32, 0xCC101B2A);
        ColorComponent rightColumn = new ColorComponent(panelX + 184, panelY + 16, 192, PANEL_HEIGHT - 32, 0xCC0F1826);
        ColorComponent divider = new ColorComponent(panelX + 180, panelY + 20, 1, PANEL_HEIGHT - 40, 0xFF2F4361);

        ColorComponent artFrame = new ColorComponent(panelX + 24, panelY + 24, ALBUM_SIZE, ALBUM_SIZE, 0xFF2A3F5E);
        albumArtComponent = new AlbumArtComponent(panelX + 24, panelY + 24, ALBUM_SIZE, ALBUM_SIZE, FALLBACK_ALBUM_ART, ALBUM_SIZE, ALBUM_SIZE);

        titleText = new TruncatedTextComponent(rightPaneX + 8, panelY + 28, 176, Component.literal("Unknown Title"), 0xFFF1F7FF);
        titleText.setDrawShadow(true);
        titleText.setTextAlign(TextAlign.LEFT);

        artistText = new TruncatedTextComponent(rightPaneX + 8, panelY + 48, 176, Component.literal("Unknown Artist"), 0xFFB6C3D6);
        artistText.setTextAlign(TextAlign.LEFT);

        sourceText = new TruncatedTextComponent(rightPaneX + 8, panelY + 72, 176, Component.literal("Source: Unknown"), 0xFF8FA8C8);
        sourceText.setTextAlign(TextAlign.LEFT);

        statusText = new TruncatedTextComponent(rightPaneX + 8, panelY + 92, 176, Component.literal("Status: Idle"), 0xFF93A1B5);
        statusText.setTextAlign(TextAlign.LEFT);

        progressTrack = new ColorComponent(rightPaneX + 8, panelY + 122, PROGRESS_WIDTH, PROGRESS_HEIGHT, 0xFF1A3557);
        progressFill = new ColorComponent(rightPaneX + 8, panelY + 122, 0, PROGRESS_HEIGHT, 0xFF57A6FF);

        TruncatedTextComponent nowPlayingLabel = new TruncatedTextComponent(panelX + 30, panelY + 176, 132, Component.literal("Now Playing"), 0xFFD9E6F7);
        nowPlayingLabel.setTextAlign(TextAlign.CENTER);

        prevButton = new ButtonWidget(rightPaneX + 8, panelY + 156, 56, 20, Component.literal("Prev"), button -> {
            MediaDetector.sendMprisCommand("Previous");
        });
        prevButton.setTooltip(Tooltip.create(Component.literal("Previous Track")));

        playPauseButton = new ButtonWidget(rightPaneX + 70, panelY + 156, 56, 20, Component.literal(isPlaying ? "Pause" : "Play"), button -> {
            if (MediaDetector.sendMprisCommand("PlayPause")) {
                isPlaying = !isPlaying;
                playPauseButton.setMessage(Component.literal(isPlaying ? "Pause" : "Play"));
                statusText.setText(Component.literal(isPlaying ? "Status: Playing" : "Status: Paused"));
            }
        });
        playPauseButton.setTooltip(Tooltip.create(Component.literal("Play / Pause")));

        nextButton = new ButtonWidget(rightPaneX + 132, panelY + 156, 56, 20, Component.literal("Next"), button -> {
            MediaDetector.sendMprisCommand("Next");
        });
        nextButton.setTooltip(Tooltip.create(Component.literal("Next Track")));

        ButtonWidget refreshButton = new ButtonWidget(rightPaneX + 8, panelY + 182, 180, 20, Component.literal("Refresh Metadata"), button -> {
            MediaDetector.refreshMetadata();
            updateMetadata();
        });
        refreshButton.setTooltip(Tooltip.create(Component.literal("Force a fresh metadata + album art fetch")));

        this.addComponent(panel);
        this.addComponent(panelBorder);
        this.addComponent(leftColumn);
        this.addComponent(rightColumn);
        this.addComponent(divider);
        this.addComponent(artFrame);
        this.addComponent(albumArtComponent);
        this.addComponent(titleText);
        this.addComponent(artistText);
        this.addComponent(sourceText);
        this.addComponent(statusText);
        this.addComponent(progressTrack);
        this.addComponent(progressFill);

        this.addWidget(prevButton);
        this.addWidget(playPauseButton);
        this.addWidget(nextButton);
        this.addWidget(refreshButton);

        updateMetadata();
        updateProgressBar();
        super.init();
    }

    @Override
    public void tick() {
        super.tick();

        // 1. Periodically check for metadata updates (every 20 ticks / 1 second)
        metadataTickCounter++;
        if (metadataTickCounter >= 20) {
            metadataTickCounter = 0;
            updateMetadata();
        }

        // 2. Process finished asynchronous album art loads
        if (albumArtFuture != null && albumArtFuture.isDone()) {
            try {
                AlbumArtPayload payload = albumArtFuture.join();
                
                // LOGGING: Check what we actually received
                if (payload != null) {
                    NowPlayingSys.LOGGER.info("Successfully loaded art: {}", payload.sourceUrl());
                    
                    if (payload.sourceUrl().equals(pendingArtUrl)) {
                        applyAlbumArt(payload);
                    } else {
                        NowPlayingSys.LOGGER.warn("Discarding stale payload for: {}", payload.sourceUrl());
                        closePayload(payload);
                    }
                }
            } catch (CompletionException e) {
                // LOGGING: Identify why the load failed (e.g., File not found, 404, etc.)
                NowPlayingSys.LOGGER.error("Album art load failed for URL: {}", pendingArtUrl);
                NowPlayingSys.LOGGER.error("Cause: ", e.getCause());
                setFallbackAlbumArt();
            } finally {
                albumArtFuture = null;
            }
        }

        // 3. Simple progress bar animation
        if (isPlaying) {
            currentProgress += 0.005f;
            if (currentProgress > 1f) currentProgress = 0f;
            updateProgressBar();
        }
    }

    private void updateMetadata() {
        MediaDetector.MediaMetadata metadata = MediaDetector.getCurrentlyPlayingMetadata();
        if (metadata == null || !metadata.hasTrack()) {
            titleText.setText(Component.literal("No media detected"));
            artistText.setText(Component.literal("Open a player to begin"));
            sourceText.setText(Component.literal("Source: None"));
            statusText.setText(Component.literal("Status: Idle"));
            isPlaying = false;
            playPauseButton.setMessage(Component.literal("Play"));
            setControlsEnabled(false);
            currentProgress = 0f;
            updateProgressBar();
            pendingArtUrl = "";
            setFallbackAlbumArt();
            return;
        }

        titleText.setText(Component.literal(metadata.title()));
        String artist = metadata.artist() == null ? "" : metadata.artist();
        artistText.setText(Component.literal(artist.isBlank() ? "Unknown Artist" : artist));

        String source = metadata.source() == null ? "Unknown" : metadata.source();
        sourceText.setText(Component.literal("Source: " + source));
        statusText.setText(Component.literal(isPlaying ? "Status: Playing" : "Status: Paused"));
        setControlsEnabled(true);

        if (metadata.hasArtUrl()) {
            queueAlbumArtLoad(metadata.artUrl());
        } else {
            pendingArtUrl = "";
            setFallbackAlbumArt();
        }
    }

    private void updateProgressBar() {
        int fillWidth = Math.max(0, Math.min(PROGRESS_WIDTH, Math.round(PROGRESS_WIDTH * currentProgress)));
        progressFill.setWidth(fillWidth);
        progressTrack.setWidth(PROGRESS_WIDTH);
    }

    private void queueAlbumArtLoad(String artUrl) {
        if (artUrl == null || artUrl.isBlank()) {
            pendingArtUrl = "";
            setFallbackAlbumArt();
            return;
        }

        if (artUrl.equals(appliedArtUrl)) {
            return;
        }

        if (artUrl.equals(pendingArtUrl) && albumArtFuture != null) {
            return;
        }

        if (albumArtFuture != null && !albumArtFuture.isDone()) {
            albumArtFuture.cancel(true);
        }

        pendingArtUrl = artUrl;
        albumArtFuture = CompletableFuture.supplyAsync(() -> loadAlbumArt(artUrl), ART_LOADER);
    }

    private AlbumArtPayload loadAlbumArt(String artUrl) {
        HttpURLConnection httpConnection = null;
        try {
            URL url = new URL(artUrl);
            httpConnection = (HttpURLConnection) url.openConnection();
            httpConnection.setConnectTimeout(3000);
            httpConnection.setReadTimeout(5000);
            httpConnection.setRequestProperty("User-Agent", "NowPlayingSys/1.0");
            httpConnection.connect();

            try (InputStream inputStream = httpConnection.getInputStream()) {
                BufferedImage original = ImageIO.read(inputStream);
                if (original == null) throw new IOException("Failed to decode image");

                // Set your desired smaller target size here
                int targetSize = 144;

                // 1. Progressive high-quality downscaling
                BufferedImage finalImage = createHighQualityThumbnail(original, targetSize, targetSize);

                // 2. Optimized NativeImage creation
                NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, targetSize, targetSize, false);
                
                // Transfer pixels directly from BufferedImage to NativeImage
                for (int y = 0; y < targetSize; y++) {
                    for (int x = 0; x < targetSize; x++) {
                        int argb = finalImage.getRGB(x, y);
                        
                        // Extract channels from standard Java ARGB format
                        int a = (argb >> 24) & 0xFF;
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;
                        
                        // Assemble into ABGR format for setPixelABGR
                        int abgrColor = (a << 24) | (b << 16) | (g << 8) | r;
                        
                        // Use the precise method for your Loom mapping
                        nativeImage.setPixelABGR(x, y, abgrColor);
                    }
                }
                return new AlbumArtPayload(artUrl, nativeImage, targetSize, targetSize);
            }
        } catch (IOException e) {
            throw new CompletionException(e);
        } finally {
            if (httpConnection != null) httpConnection.disconnect();
        }
    }

    private BufferedImage createHighQualityThumbnail(BufferedImage img, int targetWidth, int targetHeight) {
        // Preserve transparency if the original image has it
        int type = (img.getTransparency() == Transparency.OPAQUE) ? 
                BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        
        BufferedImage currentImage = img;
        int w = img.getWidth();
        int h = img.getHeight();

        // 1. Progressive downscaling by halves
        while (w > targetWidth * 2 && h > targetHeight * 2) {
            w /= 2;
            h /= 2;
            BufferedImage scratchImage = new BufferedImage(w, h, type);
            Graphics2D g2 = scratchImage.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(currentImage, 0, 0, w, h, null);
            g2.dispose();
            currentImage = scratchImage;
        }

        // 2. Final exact resize using bicubic
        BufferedImage finalImage = new BufferedImage(targetWidth, targetHeight, type);
        Graphics2D g2 = finalImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2.drawImage(currentImage, 0, 0, targetWidth, targetHeight, null);
        g2.dispose();

        return finalImage;
    }

    private void applyAlbumArt(AlbumArtPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || payload == null) {
            return;
        }

        releaseDynamicAlbumArt();

        String idSuffix = Integer.toUnsignedString(payload.sourceUrl().hashCode());
        Identifier textureId = Identifier.fromNamespaceAndPath(NowPlayingSys.MOD_ID, "dynamic/album_art_" + idSuffix);
        DynamicTexture texture = new DynamicTexture(() -> "NowPlayingAlbumArt", payload.image());
        client.getTextureManager().register(textureId, texture);

        dynamicArtTextureId = textureId;
        dynamicArtTexture = texture;
        appliedArtUrl = payload.sourceUrl();
        albumArtComponent.setTexture(textureId, payload.width(), payload.height());
    }

    private void setFallbackAlbumArt() {
        releaseDynamicAlbumArt();
        appliedArtUrl = "";
        if (albumArtComponent != null) {
            albumArtComponent.setTexture(FALLBACK_ALBUM_ART, ALBUM_SIZE, ALBUM_SIZE);
        }
    }

    private void releaseDynamicAlbumArt() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && dynamicArtTextureId != null) {
            client.getTextureManager().release(dynamicArtTextureId);
        }
        if (dynamicArtTexture != null) {
            dynamicArtTexture.close();
        }
        dynamicArtTextureId = null;
        dynamicArtTexture = null;
    }

    @Override
    public void removed() {
        if (albumArtFuture != null && !albumArtFuture.isDone()) {
            albumArtFuture.cancel(true);
        }
        albumArtFuture = null;
        releaseDynamicAlbumArt();
        super.removed();
    }

    private void setControlsEnabled(boolean enabled) {
        if (prevButton != null) {
            prevButton.active = enabled;
        }
        if (playPauseButton != null) {
            playPauseButton.active = enabled;
        }
        if (nextButton != null) {
            nextButton.active = enabled;
        }
    }

    private static void closePayload(AlbumArtPayload payload) {
        if (payload != null && payload.image() != null) {
            payload.image().close();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class AlbumArtComponent extends AbstractComponent {
        private Identifier texture;
        private int sourceWidth;
        private int sourceHeight;

        AlbumArtComponent(int x, int y, int width, int height, Identifier texture, int sourceWidth, int sourceHeight) {
            super(x, y, width, height);
            this.texture = texture;
            this.sourceWidth = Math.max(1, sourceWidth);
            this.sourceHeight = Math.max(1, sourceHeight);
        }

        void setTexture(Identifier texture, int sourceWidth, int sourceHeight) {
            this.texture = texture;
            this.sourceWidth = Math.max(1, sourceWidth);
            this.sourceHeight = Math.max(1, sourceHeight);
        }

        @Override
        public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
            int x = this.getTotalX();
            int y = this.getTotalY();
            int width = this.getWidth();
            int height = this.getHeight();

            guiGraphics.fill(x, y, x + width, y + height, 0xFF121A28);

            if (texture == null) {
                return;
            }

            float sourceAspect = (float) sourceWidth / (float) sourceHeight;
            float targetAspect = (float) width / (float) height;

            int drawWidth = width;
            int drawHeight = height;

            if (sourceAspect > targetAspect) {
                drawHeight = Math.max(1, Math.round(width / sourceAspect));
            } else {
                drawWidth = Math.max(1, Math.round(height * sourceAspect));
            }

            int drawX = x + (width - drawWidth) / 2;
            int drawY = y + (height - drawHeight) / 2;

            // Inside AlbumArtComponent.extractRenderState

            // Replace the entire guiGraphics.blit call at the bottom of the file with this:
            guiGraphics.blit(
                ART_PIPELINE,    // 1. Pipeline required by your UI framework
                texture,         // 2. The dynamic texture Identifier
                drawX,           // 3. X position on screen
                drawY,           // 4. Y position on screen
                0.0f,            // 5. U starting offset
                0.0f,            // 6. V starting offset
                drawWidth,       // 7. Width on screen (Stretches to 144)
                drawHeight,      // 8. Height on screen (Stretches to 144)
                drawWidth,       // 9. Texture coordinate scale width (Matches screen width to stretch)
                drawHeight       // 10. Texture coordinate scale height (Matches screen height to stretch)
            );
        }
    }
}