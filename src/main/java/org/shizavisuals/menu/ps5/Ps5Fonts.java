package org.shizavisuals.menu.ps5;

import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.render.gui.MsdfFont;

/**
 * MSDF atlases ported from the PS5 (deluxe) menu: the exact typography of
 * the original screens, including its icon glyph atlas (omega et al).
 */
public final class Ps5Fonts {
    public static final MsdfFont THIN = font("thin");
    public static final MsdfFont MINI = font("mini");
    public static final MsdfFont SEMI = font("semi");
    public static final MsdfFont SEMI_BOLD = font("semi_bold");
    public static final MsdfFont BOLD = font("bold");
    public static final MsdfFont SF = sfFont("sf-pro-regular");
    public static final MsdfFont SF_MEDIUM = sfFont("sf-pro-medium");
    public static final MsdfFont ICONS = font("icons");

    private Ps5Fonts() {
    }

    private static MsdfFont font(String name) {
        return MsdfFont.load(Identifier.parse("shizavisuals:fonts/ps5/" + name + ".json"));
    }

    private static MsdfFont sfFont(String name) {
        return MsdfFont.load(Identifier.parse("shizavisuals:fonts/ps5/" + name + ".json"));
    }
}
