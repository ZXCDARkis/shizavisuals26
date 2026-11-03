package org.shizavisuals.menu.title;

import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.FrameClock;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.math.Spring;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.List;

/**
 * Wallpaper picker as a Windows 11 style horizontal carousel.
 *
 * <p>The previous picker was a scrolling text list, which reads as a debug menu.
 * Here every wallpaper is a real thumbnail card, the chosen one is centred and
 * scaled up, and its neighbours peek in from both sides — so the player can see
 * what else is available without opening anything.
 *
 * <p>Scroll position is a spring, so flicking the wheel or clicking a far card
 * carries momentum and settles on the nearest card instead of teleporting. That
 * is the same {@link Spring} the HUD inertia uses, which is why both surfaces
 * decelerate identically.
 */
public final class Win11WallpaperStrip {
    // Pill cards: iOS reads "native" off fully-rounded corners at any size.
    private static final float CARD_RADIUS_RATIO = 0.30F;
    private static final float SELECTED_W = 132.0F;
    private static final float SELECTED_H = 74.0F;
    private static final float CARD_GAP = 12.0F;
    /** Cards further than this from the centre are not drawn at all. */
    private static final float VISIBLE_SPAN = 3.4F;

    private final Spring scroll = new Spring(0.0F, 150.0F, 24.0F);
    /** 0..1 reveal of the whole strip. */
    private final Spring reveal = new Spring(0.0F, 190.0F, 26.0F);

    private int selectedIndex;
    private int hoveredIndex = -1;
    private float x;
    private float y;
    private float width;
    private float height;
    private float scale = 1.0F;
    private boolean open;
    private boolean laidOut;

    public void open(int selectedIndex) {
        this.open = true;
        this.selectedIndex = Math.max(0, selectedIndex);
        this.scroll.snap(this.selectedIndex);
        this.scroll.target(this.selectedIndex);
        this.reveal.target(1.0F);
    }

    public void close() {
        this.open = false;
        this.reveal.target(0.0F);
        this.hoveredIndex = -1;
    }

    /** Index of the card currently centred. */
    public int index() {
        return this.selectedIndex;
    }

    public void layout(float screenWidth, float screenHeight, float scale) {
        this.scale = scale;
        this.width = Math.min(screenWidth - 40.0F * scale, SELECTED_W * scale * 6.2F);
        this.height = SELECTED_H * scale + 34.0F * scale;
        this.x = (screenWidth - this.width) * 0.5F;
        this.y = screenHeight - this.height - 18.0F * scale;
        this.laidOut = true;
    }

    /** Opens the strip centred on whatever wallpaper is currently active. */
    public void ensureOpen() {
        if (!this.open) {
            int index = 0;
            List<WallpaperStore.Wallpaper> all = WallpaperStore.all();
            WallpaperStore.Wallpaper selected = WallpaperStore.selected();
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).storageId().equals(selected.storageId())) {
                    index = i;
                    break;
                }
            }
            open(index);
        }
    }

    public void update(List<WallpaperStore.Wallpaper> entries, double mouseX, double mouseY) {
        float dt = FrameClock.delta();
        int count = entries.size();
        if (count > 0) {
            this.scroll.target(MathUtil.clamp(this.scroll.target(), 0.0F, count - 1.0F));
        }
        this.scroll.update(dt);
        this.reveal.update(dt);
        this.hoveredIndex = -1;
        if (count == 0 || !this.laidOut) {
            return;
        }
        // Snap to the nearest card once the flick has died down. Without this the
        // strip rests between two cards and nothing looks selected.
        if (this.scroll.settled()) {
            float nearest = Math.round(this.scroll.value());
            if (Math.abs(nearest - this.scroll.value()) > 0.001F) {
                this.scroll.snap(nearest);
            }
        }
        this.selectedIndex = Math.clamp((int) Math.round(this.scroll.value()), 0, count - 1);
        this.hoveredIndex = hitCard(entries, mouseX, mouseY);
    }

    /** Moves the strip by whole cards; used by the wheel and the arrow keys. */
    public void scrollBy(List<WallpaperStore.Wallpaper> entries, float cards) {
        int count = entries.size();
        if (count == 0) {
            return;
        }
        float next = MathUtil.clamp(Math.round(this.scroll.target()) + cards, 0.0F, count - 1.0F);
        this.scroll.target(next);
        this.selectedIndex = Math.clamp((int) next, 0, count - 1);
    }

    public void focus(int index, int count) {
        if (count == 0) {
            return;
        }
        int target = Math.clamp(index, 0, count - 1);
        this.selectedIndex = target;
        this.scroll.target(target);
    }

    public void render(List<WallpaperStore.Wallpaper> entries) {
        if (!this.laidOut || entries.isEmpty()) {
            return;
        }
        float reveal = MathUtil.clamp01(this.reveal.value());
        if (reveal <= 0.002F) {
            return;
        }
        int count = entries.size();
        // Painter's order: neighbours first so the selected card overlaps them.
        for (int i = 0; i < count; i++) {
            float distance = i - this.scroll.value();
            if (Math.abs(distance) > VISIBLE_SPAN) {
                continue;
            }
            drawCard(entries, i, distance, reveal, this.hoveredIndex == i);
        }
        drawCaption(entries, reveal);
    }

    private void drawCard(List<WallpaperStore.Wallpaper> entries, int index, float distance,
                          float reveal, boolean hovered) {
        WallpaperStore.Wallpaper wallpaper = entries.get(index);
        float[] rect = cardRect(index);
        // Neighbours are smaller; that depth is what makes it read as a carousel
        // instead of a scrolling list.
        float depth = MathUtil.clamp01(1.0F - Math.abs(distance) / VISIBLE_SPAN);
        float cardScale = 0.72F + 0.28F * depth + (hovered ? 0.05F : 0.0F);
        float width = rect[2] * cardScale;
        float height = rect[3] * cardScale;
        float drawX = rect[0] + rect[2] * 0.5F - width * 0.5F;
        float drawY = rect[1] + rect[3] * 0.5F - height * 0.5F;

        boolean current = Math.abs(distance) < 0.5F;
        // The active wallpaper stays fully opaque so it never looks disabled.
        float alpha = current ? reveal : reveal * (0.35F + 0.65F * depth);
        if (alpha <= 0.01F) {
            return;
        }

        WallpaperStore.drawable(wallpaper);
        WallpaperStore.Wallpaper drawable = wallpaper.ready() ? wallpaper : WallpaperStore.fallback();
        Cover cover = cover(drawable.aspectRatio(), width, height);
        Render2DUtil.TextureBuilder texture =
                Render2DUtil.texture(drawX, drawY, width, height, drawable.textureId())
                        .uv(cover.u0(), cover.v0(), cover.u1(), cover.v1())
                        .radius(CARD_RADIUS_RATIO * height)
                        .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha));
        if (drawable.managed()) {
            texture.managed();
        }
        texture.draw();

        int borderAlpha = Math.round((current ? 190.0F : 60.0F + depth * 40.0F) * alpha);
        Render2DUtil.rect(drawX, drawY, width, height)
                .color(ColorUtil.TRANSPARENT)
                .radius(CARD_RADIUS_RATIO * height)
                .border(Math.max(0.5F, this.scale), ColorUtil.rgba(255, 255, 255, borderAlpha))
                .draw();
    }

    private void drawCaption(List<WallpaperStore.Wallpaper> entries, float reveal) {
        int index = Math.clamp((int) Math.round(this.scroll.value()), 0, entries.size() - 1);
        WallpaperStore.Wallpaper wallpaper = entries.get(index);
        MsdfFont font = UiFonts.getFontForText(wallpaper.displayName(), UiFontStyle.MEDIUM.weight());
        float size = 9.0F * this.scale;
        float letterSpacing = size * UiFontStyle.MEDIUM.letterSpacingEm();
        String name = font.ellipsize(wallpaper.displayName(), size, letterSpacing, this.width * 0.7F);
        Render2DUtil.text(this.x + this.width * 0.5F,
                        font.centeredTextY(this.y + this.height - 15.0F * this.scale, size),
                        size, name)
                .font(font)
                .style(UiFontStyle.MEDIUM)
                .align(TextAlign.CENTER)
                .color(ColorUtil.multiplyAlpha(ColorUtil.rgba(235, 238, 248, 220), reveal))
                .draw();
    }

    private float[] cardRect(int index) {
        float cardW = SELECTED_W * this.scale;
        float cardH = SELECTED_H * this.scale;
        float pitch = cardW + CARD_GAP * this.scale;
        float cx = this.x + this.width * 0.5F + (index - this.scroll.value()) * pitch;
        return new float[]{
                cx - cardW * 0.5F,
                this.y + (this.height - cardH) * 0.5F - 12.0F * this.scale,
                cardW,
                cardH
        };
    }

    /** Topmost card under the cursor, or -1. */
    public int hitCard(List<WallpaperStore.Wallpaper> entries, double mouseX, double mouseY) {
        if (!this.laidOut) {
            return -1;
        }
        // Reverse order: the card drawn last (the selected one) wins.
        for (int i = entries.size() - 1; i >= 0; i--) {
            float[] rect = cardRect(i);
            float depth = MathUtil.clamp01(1.0F - Math.abs(i - this.scroll.value()) / VISIBLE_SPAN);
            float cardScale = 0.72F + 0.28F * depth + (this.hoveredIndex == i ? 0.05F : 0.0F);
            float w = rect[2] * cardScale;
            float h = rect[3] * cardScale;
            float cx = rect[0] + rect[2] * 0.5F;
            float cy = rect[1] + rect[3] * 0.5F;
            if (mouseX >= cx - w * 0.5F && mouseX <= cx + w * 0.5F
                    && mouseY >= cy - h * 0.5F && mouseY <= cy + h * 0.5F) {
                return i;
            }
        }
        return -1;
    }

    private static Cover cover(float sourceAspect, float targetWidth, float targetHeight) {
        float targetAspect = targetWidth / Math.max(1.0F, targetHeight);
        if (sourceAspect > targetAspect) {
            float visible = targetAspect / Math.max(0.0001F, sourceAspect);
            float offset = (1.0F - visible) * 0.5F;
            return new Cover(offset, 0.0F, offset + visible, 1.0F);
        }
        float visible = sourceAspect / Math.max(0.0001F, targetAspect);
        float offset = (1.0F - visible) * 0.5F;
        return new Cover(0.0F, offset, 1.0F, offset + visible);
    }

    private record Cover(float u0, float v0, float u1, float v1) {
    }

    public float reveal() {
        return MathUtil.clamp01(this.reveal.value());
    }

    public boolean visible() {
        return this.open && this.reveal() > 0.002F;
    }
}