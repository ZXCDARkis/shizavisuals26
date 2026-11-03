package org.shizavisuals.utils.render.cosmetic;

import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.gui.Render2DUtil;

/**
 * Thumbnail for a cosmetic tile: the item's own texture, cropped to its
 * non-transparent bounding box so the grid reads as distinct items instead of
 * 39 copies of the same empty square.
 */
public final class PulsePreview {
    private PulsePreview() {
    }

    public static Identifier texture(PulseCosmeticCatalog.PulseCosmetic cosmetic) {
        return Identifier.parse("shizavisuals:cosmetics/pulse/"
                + cosmetic.category().id() + "/" + cosmetic.id() + "/texture.png");
    }

    public static void render(PulseCosmeticCatalog.PulseCosmetic cosmetic,
                               float x, float y, float size, float alpha) {
        if (cosmetic == null || size <= 0.0F || alpha <= 0.004F) {
            return;
        }
        Render2DUtil.texture(x, y, size, size, texture(cosmetic))
                .color(ColorUtil.multiplyAlpha(0xFFFFFFFF, alpha))
                .radius(size * 0.18F)
                .draw();
    }
}