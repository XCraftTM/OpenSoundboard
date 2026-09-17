package de.xcrafttm.opensoundboard.ui;

import com.mojang.blaze3d.platform.NativeImage;
import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads video thumbnails, turns them into dynamic textures, and draws them. Images are
 * pre-scaled with smooth filtering because GUI textures are sampled with nearest-neighbour.
 */
public final class Thumbnails {

    public static final int TEXTURE_W = 256;
    public static final int TEXTURE_H = 144;
    private static final int MAX_CACHED = 80;

    private enum Status { LOADING, READY, FAILED }

    private static final class Entry {
        volatile Status status = Status.LOADING;
        //? if >=1.21.11 {
        net.minecraft.resources.Identifier id;
        //?} else {
        /*net.minecraft.resources.ResourceLocation id;
        *///?}
        DynamicTexture texture;
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Deque<String> ORDER = new ArrayDeque<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "OpenSoundboard Thumbnails");
        thread.setDaemon(true);
        return thread;
    });

    private Thumbnails() {
    }

    /**
     * Draw the thumbnail for {@code key} (loading it from {@code url} on first use). Returns false
     * while it is not available yet so callers can draw a placeholder.
     */
    public static boolean draw(UiCanvas c, String key, String url, int x, int y, int w, int h) {
        Entry entry = CACHE.get(key);
        if (entry == null) {
            entry = new Entry();
            CACHE.put(key, entry);
            load(key, url, entry);
        }
        if (entry.status != Status.READY || entry.id == null) return false;
        //? if >=1.21.11 {
        c.g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, entry.id, x, y, 0F, 0F, w, h,
                TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);
        //?} else {
        /*c.g.blit(entry.id, x, y, w, h, 0F, 0F, TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);
        *///?}
        return true;
    }

    private static void load(String key, String url, Entry entry) {
        EXECUTOR.execute(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
                HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
                BufferedImage source = ImageIO.read(new ByteArrayInputStream(response.body()));
                if (source == null) throw new IllegalStateException("unsupported image");

                BufferedImage scaled = new BufferedImage(TEXTURE_W, TEXTURE_H, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(source, 0, 0, TEXTURE_W, TEXTURE_H, null);
                g.dispose();

                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(scaled, "png", png);
                byte[] bytes = png.toByteArray();
                Minecraft.getInstance().execute(() -> upload(key, entry, bytes));
            } catch (Exception e) {
                entry.status = Status.FAILED;
            }
        });
    }

    private static void upload(String key, Entry entry, byte[] png) {
        if (CACHE.get(key) != entry) return;
        try {
            NativeImage image = NativeImage.read(png);
            String path = "thumbnail/" + key.toLowerCase().replaceAll("[^a-z0-9_-]", "_") + "_" + Integer.toHexString(key.hashCode());
            //? if >=1.21.11 {
            entry.id = net.minecraft.resources.Identifier.fromNamespaceAndPath(OpenSoundboardClient.MOD_ID, path);
            entry.texture = new DynamicTexture(() -> "OpenSoundboard thumbnail", image);
            //?} else {
            /*entry.id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(OpenSoundboardClient.MOD_ID, path);
            entry.texture = new DynamicTexture(image);
            *///?}
            Minecraft.getInstance().getTextureManager().register(entry.id, entry.texture);
            entry.status = Status.READY;
            remember(key);
        } catch (Exception e) {
            entry.status = Status.FAILED;
        }
    }

    private static void remember(String key) {
        ORDER.remove(key);
        ORDER.addLast(key);
        while (ORDER.size() > MAX_CACHED) {
            String oldest = ORDER.removeFirst();
            Entry old = CACHE.remove(oldest);
            if (old != null && old.id != null) Minecraft.getInstance().getTextureManager().release(old.id);
        }
    }
}
