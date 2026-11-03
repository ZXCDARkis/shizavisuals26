package org.shizavisuals.menu.ps4;

import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.render.gui.MsdfFont;

/**
 * PS4 menu typography. Deluxe's FontFace.GAMESTATION maps onto our own
 * SF Pro metasdf atlases: thePS4 msdf atlases cannot be sampled by the
 * client's text pipeline, while SF Pro matches the PS4 system font.
 */
public final class Ps4Fonts {
    /** Primary face used for every label in the PS4 menu. */
    public static final MsdfFont UI = MsdfFont.load(Identifier.parse("shizavisuals:fonts/sf_pro_semibold.json"));

    private Ps4Fonts() {
    }
}
