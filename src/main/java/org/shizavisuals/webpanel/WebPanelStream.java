package org.shizavisuals.webpanel;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefPaintEvent;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WebPanelStream {

    private WebPanelStream() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("ShizaVisuals-WebPanel");

    private record Profile(int width, float quality, long intervalMs) {
    }

    private static final Profile[] PROFILES = {
            new Profile(384, 0.52f, 400L),
            new Profile(512, 0.64f, 320L),
            new Profile(640, 0.74f, 250L),
            new Profile(864, 0.82f, 200L),
    };

    private static Profile profile() {
        WebPanelInstance.Quality quality = WebPanelInstance.getQuality();
        return PROFILES[quality == null ? 2 : quality.ordinal()];
    }

    private static final long FRAME_STALE_MS = 15_000L;

    private static final ExecutorService ENCODER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ShizaVisuals-WebPanelStream");
        thread.setDaemon(true);
        return thread;
    });

    public record Frame(int index, int width, int height, byte[] jpeg) {
    }

    private static final AtomicReference<Frame> PENDING = new AtomicReference<>();

    @Nullable
    private static volatile CefBrowser source;
    @Nullable
    private static volatile java.util.function.Consumer<CefPaintEvent> listener;

    private static volatile int sourceIndex;
    private static volatile long lastCapture;
    private static volatile int lastHash;
    private static volatile boolean encoding;

    public static void setSource(@Nullable WebPanelInstance panel, int index) {
        CefBrowser wanted = null;
        if (panel != null) {
            MCEFBrowser browser = panel.getBrowser();
            if (browser != null) {
                try {
                    wanted = browser.getCefBrowser();
                } catch (Throwable ignored) {
                }
            }
        }
        sourceIndex = index;
        if (wanted == source) {
            return;
        }
        detach();
        if (wanted == null) {
            return;
        }
        try {
            CefRenderHandler handler = wanted.getRenderHandler();
            if (handler == null) {
                return;
            }
            java.util.function.Consumer<CefPaintEvent> paint = WebPanelStream::onPaint;
            handler.setOnPaintListener(paint);
            source = wanted;
            listener = paint;
            lastHash = 0;
        } catch (Throwable t) {
            LOGGER.warn("WebPanel: не удалось подписаться на кадры CEF: {}", t.toString());
        }
    }

    private static void detach() {
        CefBrowser current = source;
        java.util.function.Consumer<CefPaintEvent> paint = listener;
        source = null;
        listener = null;
        PENDING.set(null);
        if (current == null || paint == null) {
            return;
        }
        try {
            CefRenderHandler handler = current.getRenderHandler();
            if (handler != null) {
                handler.removeOnPaintListener(paint);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void onPaint(CefPaintEvent event) {
        try {
            if (event.getPopup() || encoding) {
                return;
            }
            Profile profile = profile();
            long now = System.currentTimeMillis();
            if (now - lastCapture < profile.intervalMs()) {
                return;
            }
            int width = event.getWidth();
            int height = event.getHeight();
            ByteBuffer buffer = event.getRenderedFrame();
            if (width <= 0 || height <= 0 || buffer == null) {
                return;
            }
            int bytes = width * height * 4;
            if (buffer.capacity() < bytes) {
                return;
            }
            lastCapture = now;
            encoding = true;
            byte[] raw = scratch(bytes);
            buffer.get(0, raw, 0, bytes);
            int index = sourceIndex;
            ENCODER.execute(() -> encode(index, raw, width, height, profile));
        } catch (Throwable ignored) {
            encoding = false;
        }
    }

    private static byte[] rawFrame = new byte[0];
    private static int[] smallFrame = new int[0];

    private static byte[] scratch(int bytes) {
        if (rawFrame.length < bytes) {
            rawFrame = new byte[bytes];
        }
        return rawFrame;
    }

    private static void encode(int index, byte[] raw, int width, int height, Profile profile) {
        try {
            int targetW = Math.min(profile.width(), width);
            int targetH = Math.max(1, (int) Math.round((double) targetW * height / width));
            int[] pixels = downscale(raw, width, height, targetW, targetH);

            int hash = 1;
            int count = targetW * targetH;
            for (int i = 0; i < count; i++) {
                hash = hash * 31 + pixels[i];
            }
            if (hash == lastHash) {
                return;
            }
            lastHash = hash;

            BufferedImage image = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, targetW, targetH, pixels, 0, targetW);
            byte[] jpeg = toJpeg(image, profile.quality());
            if (jpeg != null) {
                PENDING.set(new Frame(index, targetW, targetH, jpeg));
            }
        } catch (Throwable t) {
            LOGGER.warn("WebPanel: кадр не сжался: {}", t.toString());
        } finally {
            encoding = false;
        }
    }

    private static int[] downscale(byte[] raw, int width, int height, int targetW, int targetH) {
        int count = targetW * targetH;
        if (smallFrame.length < count) {
            smallFrame = new int[count];
        }
        int[] out = smallFrame;
        for (int y = 0; y < targetH; y++) {
            int fromY = y * height / targetH;
            int toY = Math.max(fromY + 1, (y + 1) * height / targetH);
            int row = y * targetW;
            for (int x = 0; x < targetW; x++) {
                int fromX = x * width / targetW;
                int toX = Math.max(fromX + 1, (x + 1) * width / targetW);
                int r = 0;
                int g = 0;
                int b = 0;
                int taken = 0;
                for (int sy = fromY; sy < toY; sy++) {
                    int at = (sy * width + fromX) * 4;
                    for (int sx = fromX; sx < toX; sx++, at += 4) {
                        b += raw[at] & 0xFF;
                        g += raw[at + 1] & 0xFF;
                        r += raw[at + 2] & 0xFF;
                        taken++;
                    }
                }
                out[row + x] = 0xFF000000 | ((r / taken) << 16) | ((g / taken) << 8) | (b / taken);
            }
        }
        return out;
    }

    @Nullable
    private static byte[] toJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
             MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), params);
            stream.flush();
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    @Nullable
    public static Frame poll() {
        return PENDING.getAndSet(null);
    }

    private static final ExecutorService SENDER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ShizaVisuals-WebPanelSend");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile boolean sending;

    public static void dispatch(@Nullable Frame frame, java.util.function.Consumer<Frame> sink) {
        if (frame == null || sending) {
            return;
        }
        sending = true;
        try {
            SENDER.execute(() -> {
                try {
                    sink.accept(frame);
                } catch (Throwable t) {
                    LOGGER.warn("WebPanel: кадр не ушёл в шаринг: {}", t.toString());
                } finally {
                    sending = false;
                }
            });
        } catch (Throwable ignored) {
            sending = false;
        }
    }

    private record Received(Identifier id, DynamicTexture texture, long at, int width,
                            int height) {
    }

    private record Decoded(int width, int height, int[] pixels) {
    }

    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ShizaVisuals-WebPanelDecode");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicInteger QUEUED = new AtomicInteger();

    private static final Map<String, Decoded> DECODED = new ConcurrentHashMap<>();

    private static final Map<String, Received> FRAMES = new HashMap<>();

    private static String key(String owner, int index) {
        return owner + '#' + index;
    }

    @Nullable
    public static Identifier frameOf(String owner, int index) {
        Received received = FRAMES.get(key(owner, index));
        return received == null ? null : received.id();
    }

    public static void receive(String owner, int index, byte[] jpeg) {
        if (QUEUED.get() >= 3) {
            return;
        }
        String key = key(owner, index);
        QUEUED.incrementAndGet();
        DECODER.execute(() -> {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
                if (image == null) {
                    return;
                }
                int width = image.getWidth();
                int height = image.getHeight();
                int[] pixels = new int[width * height];
                image.getRGB(0, 0, width, height, pixels, 0, width);
                DECODED.put(key, new Decoded(width, height, pixels));
            } catch (Throwable t) {
                LOGGER.warn("WebPanel: кадр друга не прочитался: {}", t.toString());
            } finally {
                QUEUED.decrementAndGet();
            }
        });
    }

    private static void upload(String key, Decoded decoded) {
        int width = decoded.width();
        int height = decoded.height();
        Received previous = FRAMES.get(key);
        long now = System.currentTimeMillis();

        if (previous != null && previous.width() == width && previous.height() == height) {
            NativeImage target = previous.texture().getPixels();
            if (target != null) {
                copy(decoded, target);
                previous.texture().upload();
                FRAMES.put(key, new Received(previous.id(), previous.texture(), now, width, height));
                return;
            }
        }

        NativeImage pixels = new NativeImage(width, height, false);
        copy(decoded, pixels);
        Identifier id = Identifier.fromNamespaceAndPath("shizavisuals",
                "webpanel/stream/" + Integer.toUnsignedString(key.hashCode()));
        DynamicTexture texture = new DynamicTexture(() -> id.toString(), pixels);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        FRAMES.put(key, new Received(id, texture, now, width, height));
    }

    private static void copy(Decoded from, NativeImage to) {
        int width = Math.min(from.width(), to.getWidth());
        int height = Math.min(from.height(), to.getHeight());
        int[] pixels = from.pixels();
        for (int y = 0; y < height; y++) {
            int row = y * from.width();
            for (int x = 0; x < width; x++) {
                int argb = 0xFF000000 | pixels[row + x];
                int abgr = (argb & 0xFF00FF00) | ((argb & 0x000000FF) << 16) | ((argb & 0x00FF0000) >>> 16);
                to.setPixelABGR(x, y, abgr);
            }
        }
    }

    public static void tick() {
        if (!DECODED.isEmpty()) {
            for (Map.Entry<String, Decoded> entry : DECODED.entrySet()) {
                Decoded decoded = DECODED.remove(entry.getKey());
                if (decoded == null) {
                    continue;
                }
                try {
                    upload(entry.getKey(), decoded);
                } catch (Throwable t) {
                    LOGGER.warn("WebPanel: кадр друга не лёг в текстуру: {}", t.toString());
                }
            }
        }
        long now = System.currentTimeMillis();
        FRAMES.entrySet().removeIf(entry -> {
            if (now - entry.getValue().at() < FRAME_STALE_MS) {
                return false;
            }
            release(entry.getValue());
            return true;
        });
    }

    public static void clear() {
        detach();
        DECODED.clear();
        for (Received received : FRAMES.values()) {
            release(received);
        }
        FRAMES.clear();
        lastHash = 0;
    }

    public static void shutdown() {
        clear();
        try { ENCODER.shutdownNow(); } catch (Throwable ignored) {}
        try { SENDER.shutdownNow(); } catch (Throwable ignored) {}
        try { DECODER.shutdownNow(); } catch (Throwable ignored) {}
    }

    private static void release(Received received) {
        try {
            Minecraft.getInstance().getTextureManager().release(received.id());
        } catch (Throwable ignored) {
        }
    }
}
