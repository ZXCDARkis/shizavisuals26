package org.shizavisuals.utils.render.gui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.FloatSize;
import com.github.weisj.jsvg.view.ViewBox;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.shizavisuals.context.MinecraftContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiTexture {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuiTexture.class);
    private static final Map<Identifier, GuiTexture> CACHE = new ConcurrentHashMap<>();

    private static final int MAX_VARIANTS = 8;
    private static final int SUPERSAMPLE = 2;
    private static final int MAX_TEXTURE_SIZE = 2048;

    private final Identifier textureId;
    private final boolean svg;

    private volatile SVGDocument document;
    private TextureSetup staticSetup;
    private final LinkedHashMap<Long, TextureSetup> svgVariants = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, TextureSetup> eldest) {
            if (size() <= MAX_VARIANTS) {
                return false;
            }
            MinecraftContext.mc.getTextureManager().release(variantId(GuiTexture.this.textureId, eldest.getKey()));
            return true;
        }
    };

    private GuiTexture(Identifier textureId) {
        this.textureId = textureId;
        this.svg = textureId.getPath().endsWith(".svg");
    }

    public static GuiTexture load(Identifier textureId) {
        return CACHE.computeIfAbsent(textureId, GuiTexture::new);
    }

    public static void prewarm(Collection<Identifier> ids) {
        Thread thread = new Thread(() -> {
            for (Identifier id : ids) {
                try {
                    GuiTexture texture = load(id);
                    if (texture.svg) {
                        texture.document();
                    }
                } catch (Throwable throwable) {

                    LOGGER.warn("Failed to prewarm GUI texture {}", id, throwable);
                }
            }
        }, "ShizaVisuals SVG Prewarm");
        thread.setDaemon(true);
        thread.start();
    }

    public TextureSetup textureSetup(float deviceWidth, float deviceHeight) {
        if (!this.svg) {
            return staticSetup();
        }
        long key = packSize(quantize(deviceWidth), quantize(deviceHeight));
        TextureSetup cached = this.svgVariants.get(key);
        if (cached != null) {
            return cached;
        }
        TextureSetup created = createSvgSetup(key);
        this.svgVariants.put(key, created);
        return created;
    }

    public TextureSetup textureSetup() {
        if (!this.svg) {
            return staticSetup();
        }
        FloatSize size = documentSize();
        return textureSetup((float) size.getWidth(), (float) size.getHeight());
    }

    private TextureSetup staticSetup() {
        if (this.staticSetup == null) {
            DynamicTexture texture = new DynamicTexture(() -> this.textureId.toString(), readPng(this.textureId));
            texture.upload();
            MinecraftContext.mc.getTextureManager().register(this.textureId, texture);
            this.staticSetup = TextureSetup.singleTexture(
                    texture.getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false)
            );
        }
        return this.staticSetup;
    }

    private TextureSetup createSvgSetup(long key) {
        int width = Math.min(MAX_TEXTURE_SIZE, unpackWidth(key) * SUPERSAMPLE);
        int height = Math.min(MAX_TEXTURE_SIZE, unpackHeight(key) * SUPERSAMPLE);
        Identifier variantId = variantId(this.textureId, key);
        DynamicTexture texture = new DynamicTexture(variantId::toString, rasterizeSvg(this.textureId, document(), width, height));
        texture.upload();
        MinecraftContext.mc.getTextureManager().register(variantId, texture);
        return TextureSetup.singleTexture(
                texture.getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false)
        );
    }

    private SVGDocument document() {

        SVGDocument loaded = this.document;
        if (loaded == null) {
            loaded = loadDocument(this.textureId);
            this.document = loaded;
        }
        return loaded;
    }

    private FloatSize documentSize() {
        return document().size();
    }

    private static int quantize(float devicePx) {
        return Math.max(1, (int) Math.ceil(devicePx));
    }

    private static long packSize(int width, int height) {
        return ((long) width << 32) | (height & 0xFFFFFFFFL);
    }

    private static int unpackWidth(long key) {
        return (int) (key >>> 32);
    }

    private static int unpackHeight(long key) {
        return (int) key;
    }

    private static Identifier variantId(Identifier base, long key) {
        return base.withPath(path -> path + "/" + unpackWidth(key) + "x" + unpackHeight(key));
    }

    private static NativeImage readPng(Identifier textureId) {
        try (InputStream inputStream = MinecraftContext.mc.getResourceManager().open(textureId)) {
            return NativeImage.read(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read GUI texture: " + textureId, exception);
        }
    }

    private static SVGDocument loadDocument(Identifier textureId) {
        try (InputStream inputStream = MinecraftContext.mc.getResourceManager().open(textureId)) {
            URI documentUri = URI.create("resource://" + textureId.getNamespace() + "/" + textureId.getPath());
            SVGDocument document = new SVGLoader().load(inputStream, documentUri, LoaderContext.createDefault());
            if (document == null) {
                throw new IOException("Failed to parse SVG document: " + textureId);
            }
            return document;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read GUI texture: " + textureId, exception);
        }
    }

    private static NativeImage rasterizeSvg(Identifier textureId, SVGDocument document, int width, int height) {

        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics = bufferedImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            document.render(null, graphics, new ViewBox(0, 0, width, height));
        } finally {
            graphics.dispose();
        }

        int clearBorder = textureId.getPath().contains("logo_boot") ? Math.max(1, Math.round(4.0F * width / 512.0F)) : 0;
        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (clearBorder > 0 && (x < clearBorder || x >= width - clearBorder || y < clearBorder || y >= height - clearBorder)) {
                    nativeImage.setPixel(x, y, 0x00FFFFFF);
                    continue;
                }
                int argb = bufferedImage.getRGB(x, y);
                if (((argb >> 24) & 0xFF) == 0) {

                    argb = 0x00FFFFFF;
                }
                nativeImage.setPixel(x, y, argb);
            }
        }
        return nativeImage;
    }
}
