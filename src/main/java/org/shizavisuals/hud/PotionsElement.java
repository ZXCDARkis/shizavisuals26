package org.shizavisuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.ArrayList;
import java.util.List;

public final class PotionsElement extends HudElement {

    private static final int CARD_BG = ColorUtil.rgba(22, 22, 22, 225);
    private static final int CHIP_BG = ColorUtil.rgba(35, 35, 35, 220);
    private static final int RED_ACCENT = ColorUtil.rgb(255, 59, 92);

    private static final float HEADER_HEIGHT = 22.0F;
    private static final float ROW_HEIGHT = 24.0F;
    private static final float TEXT_SIZE = 10.0F;
    private static final float HEADER_TEXT_SIZE = 11.0F;
    private static final float ICON_SIZE = 14.0F;
    private static final float PADDING_X = 8.0F;
    private static final float ROW_GAP = 4.0F;

    private static final String[] ROMAN = {"", " 2", " 3", " 4", " 5", " 6", " 7", " 8", " 9", " 10"};

    public record PotionRow(Identifier icon, String name, String timeText, boolean harmful) {
    }

    private final List<PotionRow> rows = new ArrayList<>();

    public PotionsElement() {
        super("potions", "Potions");
    }

    @Override
    protected float defaultX(float unit) {
        return 180.0F * unit;
    }

    @Override
    protected float defaultY(float unit) {
        return 75.0F * unit;
    }

    @Override
    protected void layout(Minecraft mc, float unit) {
        collectPotions(mc);
        if (this.rows.isEmpty()) {
            if (!showcase(mc)) {
                this.width = 0.0F;
                this.height = 0.0F;
                return;
            }
            MsdfFont font = UiFonts.sfProDisplay();
            float headerTextSize = HEADER_TEXT_SIZE * unit;
            this.width = (PADDING_X * 2.0F + ICON_SIZE + 6.0F) * unit
                    + font.measureWidth("Potions", headerTextSize, headerTextSize * UiFontStyle.MEDIUM.letterSpacingEm());
            this.height = HEADER_HEIGHT * unit;
            return;
        }

        MsdfFont font = UiFonts.sfProDisplay();
        float textSize = TEXT_SIZE * unit;
        float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();

        float maxRowWidth = 125.0F * unit;
        for (PotionRow row : this.rows) {
            float rowWidth = PADDING_X * 2.0F * unit
                    + ICON_SIZE * unit + 6.0F * unit
                    + font.measureWidth(row.name(), textSize, letterSpacing) + 12.0F * unit
                    + font.measureWidth(row.timeText(), textSize, letterSpacing) + 12.0F * unit;
            maxRowWidth = Math.max(maxRowWidth, rowWidth);
        }

        this.width = maxRowWidth;
        this.height = (HEADER_HEIGHT + 6.0F) * unit
                + this.rows.size() * ROW_HEIGHT * unit
                + (this.rows.size() - 1) * ROW_GAP * unit;
    }

    @Override
    protected void draw(Minecraft mc, float unit) {
        if (this.rows.isEmpty() && !showcase(mc)) {
            return;
        }
        float alpha = appearAlpha();

        MsdfFont font = UiFonts.sfProDisplay();
        float headerTextSize = HEADER_TEXT_SIZE * unit;
        float textSize = TEXT_SIZE * unit;
        float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();

        // Header pill "Potions"
        float headerY = this.y;
        float headerWidth = (PADDING_X * 2.0F + ICON_SIZE + 6.0F) * unit
                + font.measureWidth("Potions", headerTextSize, headerTextSize * UiFontStyle.MEDIUM.letterSpacingEm());
        Render2DUtil.rect(this.x, headerY, headerWidth, HEADER_HEIGHT * unit)
                .color(ColorUtil.multiplyAlpha(CARD_BG, alpha))
                .radius(6.0F * unit)
                .draw();

        float headerCenterY = headerY + HEADER_HEIGHT * unit / 2.0F;
        Render2DUtil.texture(this.x + PADDING_X * unit, headerCenterY - (ICON_SIZE * unit) / 2.0F,
                        ICON_SIZE * unit, ICON_SIZE * unit, Textures.Icons.SPARKLES)
                .color(ColorUtil.multiplyAlpha(Theme.getAccent(), alpha))
                .draw();

        Render2DUtil.text(this.x + (PADDING_X + ICON_SIZE + 6.0F) * unit,
                        font.centeredTextY(headerCenterY, headerTextSize), headerTextSize, "Potions")
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha))
                .draw();

        if (this.rows.isEmpty()) {
            return;
        }

        // Potion rows with actual effect icons
        float cursorY = headerY + (HEADER_HEIGHT + 6.0F) * unit;
        for (PotionRow row : this.rows) {
            float rowCenterY = cursorY + ROW_HEIGHT * unit / 2.0F;

            Render2DUtil.rect(this.x, cursorY, this.width, ROW_HEIGHT * unit)
                    .color(ColorUtil.multiplyAlpha(CARD_BG, alpha))
                    .radius(6.0F * unit)
                    .draw();

            float cursorX = this.x + PADDING_X * unit;
            float iconSize = ICON_SIZE * unit;

            // Actual Mob Effect Icon
            Render2DUtil.texture(cursorX, rowCenterY - iconSize / 2.0F, iconSize, iconSize, row.icon())
                    .managed()
                    .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha))
                    .draw();
            cursorX += iconSize + 6.0F * unit;

            // Name
            Render2DUtil.text(cursorX, font.centeredTextY(rowCenterY, textSize), textSize, row.name())
                    .style(UiFontStyle.MEDIUM)
                    .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha))
                    .draw();

            // Time chip on right
            float chipWidth = font.measureWidth(row.timeText(), textSize, letterSpacing) + 12.0F * unit;
            float chipHeight = 16.0F * unit;
            float chipX = this.x + this.width - PADDING_X * unit - chipWidth;
            float chipY = rowCenterY - chipHeight / 2.0F;

            Render2DUtil.rect(chipX, chipY, chipWidth, chipHeight)
                    .color(ColorUtil.multiplyAlpha(CHIP_BG, alpha))
                    .radius(4.0F * unit)
                    .draw();

            Render2DUtil.text(chipX + chipWidth / 2.0F, font.centeredTextY(rowCenterY, textSize), textSize, row.timeText())
                    .style(UiFontStyle.MEDIUM)
                    .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT, alpha))
                    .align(org.shizavisuals.utils.render.gui.TextAlign.CENTER)
                    .draw();

            cursorY += (ROW_HEIGHT + ROW_GAP) * unit;
        }
    }

    private void collectPotions(Minecraft mc) {
        this.rows.clear();
        if (mc.player != null) {
            for (MobEffectInstance effect : mc.player.getActiveEffects()) {
                boolean harmful = effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
                Identifier effectId = effect.getEffect().unwrapKey().orElseThrow().identifier();
                Identifier icon = effectIcon(effectId.getPath());
                String name = effect.getEffect().value().getDisplayName().getString() + roman(effect.getAmplifier());
                String time = effect.isInfiniteDuration() ? "\u221E" : formatTicks(effect.getDuration());
                this.rows.add(new PotionRow(icon, name, time, harmful));
            }
        }
    }

    private static Identifier effectIcon(String name) {
        return Identifier.withDefaultNamespace("textures/mob_effect/" + name + ".png");
    }

    private static String roman(int amp) {
        return amp >= 0 && amp < ROMAN.length ? ROMAN[amp] : " " + (amp + 1);
    }

    private static String formatTicks(int ticks) {
        int seconds = ticks / 20;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
