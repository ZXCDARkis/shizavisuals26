package org.shizavisuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

public final class WatermarkElement extends HudElement {

    private static final int CARD_BG = ColorUtil.rgba(22, 22, 22, 230);

    private static final float ROW_HEIGHT = 26.0F;
    private static final float ICON_SIZE = 14.0F;
    private static final float ICON_TEXT_GAP = 5.0F;
    private static final float ITEM_GAP = 12.0F;
    private static final float PADDING_X = 10.0F;
    private static final float TEXT_SIZE = 10.5F;
    private static final float LOGO_SIZE = 18.0F;
    private static final float SEPARATOR_GAP = 8.0F;

    private String userName = "";
    private String fpsText = "";
    private String pingText = "";

    public WatermarkElement() {
        super("watermark", "Watermark");
    }

    @Override
    protected boolean preservePositionOnContentResize() {
        return true;
    }

    @Override
    protected float defaultX(float unit) {
        return 15.0F * unit;
    }

    @Override
    protected float defaultY(float unit) {
        return 15.0F * unit;
    }

    @Override
    protected void layout(Minecraft mc, float unit) {
        collectData(mc);

        MsdfFont font = UiFonts.sfProDisplay();
        float textSize = TEXT_SIZE * unit;
        float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();

        // Logo + sep + userIcon + userName + sep + fpsIcon + fpsText + sep + pingIcon + pingText
        float w = PADDING_X * 2.0F * unit;
        w += LOGO_SIZE * unit; // logo
        w += SEPARATOR_GAP * unit; // gap after logo
        w += ICON_SIZE * unit + ICON_TEXT_GAP * unit + font.measureWidth(this.userName, textSize, letterSpacing); // user
        w += ITEM_GAP * unit; // gap
        w += ICON_SIZE * unit + ICON_TEXT_GAP * unit + font.measureWidth(this.fpsText, textSize, letterSpacing); // fps
        w += ITEM_GAP * unit; // gap
        w += ICON_SIZE * unit + ICON_TEXT_GAP * unit + font.measureWidth(this.pingText, textSize, letterSpacing); // ping

        this.width = w;
        this.height = ROW_HEIGHT * unit;
    }

    @Override
    protected void draw(Minecraft mc, float unit) {
        float alpha = appearAlpha();
        MsdfFont font = UiFonts.sfProDisplay();
        float textSize = TEXT_SIZE * unit;

        // Single capsule background
        Render2DUtil.rect(this.x, this.y, this.width, this.height)
                .color(ColorUtil.multiplyAlpha(CARD_BG, alpha))
                .radius(8.0F * unit)
                .draw();

        float centerY = this.y + ROW_HEIGHT * unit / 2.0F;
        float cursorX = this.x + PADDING_X * unit;

        // Logo square with accent bg
        float logoSize = LOGO_SIZE * unit;
        Render2DUtil.rect(cursorX, centerY - logoSize / 2.0F, logoSize, logoSize)
                .color(ColorUtil.multiplyAlpha(Theme.getAccent(), alpha))
                .radius(4.0F * unit)
                .draw();
        Render2DUtil.texture(cursorX + 2.0F * unit, centerY - logoSize / 2.0F + 2.0F * unit,
                        logoSize - 4.0F * unit, logoSize - 4.0F * unit, Textures.Logos.BOLT)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha))
                .draw();
        cursorX += logoSize + SEPARATOR_GAP * unit;

        // Username with icon
        cursorX = drawItem(font, cursorX, centerY, textSize, Textures.Icons.USER_ROUND, this.userName, unit, alpha);
        cursorX += ITEM_GAP * unit;

        // FPS with icon
        cursorX = drawItem(font, cursorX, centerY, textSize, Textures.Icons.GAMEPAD, this.fpsText, unit, alpha);
        cursorX += ITEM_GAP * unit;

        // Ping with icon
        drawItem(font, cursorX, centerY, textSize, Textures.Icons.HARD_DRIVE, this.pingText, unit, alpha);
    }

    private float drawItem(MsdfFont font, float x, float centerY, float textSize, Identifier icon, String text, float unit, float alpha) {
        float iconSize = ICON_SIZE * unit;
        float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();

        Render2DUtil.texture(x, centerY - iconSize / 2.0F, iconSize, iconSize, icon)
                .color(ColorUtil.multiplyAlpha(Theme.getAccent(), alpha))
                .draw();
        x += iconSize + ICON_TEXT_GAP * unit;

        Render2DUtil.text(x, font.centeredTextY(centerY, textSize), textSize, text)
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha))
                .draw();
        x += font.measureWidth(text, textSize, letterSpacing);

        return x;
    }

    private void collectData(Minecraft mc) {
        this.userName = mc.getUser() != null ? mc.getUser().getName() : "Player";
        this.fpsText = mc.getFps() + "fps";
        this.pingText = latency(mc) + "ms";
    }

    private static int latency(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) {
            return 0;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info != null ? info.getLatency() : 0;
    }
}
