package org.shizavisuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.lifecycle.FeatureToggleEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class KeybindsElement extends HudElement {

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

    private static final Identifier[] CATEGORY_ICONS = {
            Textures.Icons.EYE,              // VISUAL
            Textures.Icons.BOXES             // MISC
    };

    public record BindRow(Identifier icon, String name, String bindKey, boolean enabled) {
    }

    private final java.util.Map<String, Long> toggleTimes = new java.util.HashMap<>();
    private final List<BindRow> rows = new ArrayList<>();

    public KeybindsElement() {
        super("keybinds", "Keybinds");
        EventManager.subscribe(this);
    }

    @EventTarget
    public void onFeatureToggle(FeatureToggleEvent event) {
        if (event.isEnabled()) {
            this.toggleTimes.put(event.getFeature().getName(), System.currentTimeMillis());
        }
    }

    @Override
    protected float defaultX(float unit) {
        return 340.0F * unit;
    }

    @Override
    protected float defaultY(float unit) {
        return 165.0F * unit;
    }

    @Override
    protected void layout(Minecraft mc, float unit) {
        collectBinds(mc);
        if (this.rows.isEmpty()) {
            if (!showcase(mc)) {
                this.width = 0.0F;
                this.height = 0.0F;
                return;
            }
            MsdfFont font = UiFonts.sfProDisplay();
            float headerTextSize = HEADER_TEXT_SIZE * unit;
            this.width = (PADDING_X * 2.0F + ICON_SIZE + 6.0F) * unit
                    + font.measureWidth("Binds", headerTextSize, headerTextSize * UiFontStyle.MEDIUM.letterSpacingEm());
            this.height = HEADER_HEIGHT * unit;
            return;
        }

        MsdfFont font = UiFonts.sfProDisplay();
        float textSize = TEXT_SIZE * unit;
        float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();

        float maxRowWidth = 115.0F * unit;
        for (BindRow row : this.rows) {
            float rowWidth = PADDING_X * 2.0F * unit
                    + ICON_SIZE * unit + 6.0F * unit
                    + font.measureWidth(row.name(), textSize, letterSpacing) + 12.0F * unit
                    + font.measureWidth(row.bindKey(), textSize, letterSpacing) + 12.0F * unit;
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

        // Header pill "Binds"
        float headerY = this.y;
        float headerWidth = (PADDING_X * 2.0F + ICON_SIZE + 6.0F) * unit
                + font.measureWidth("Binds", headerTextSize, headerTextSize * UiFontStyle.MEDIUM.letterSpacingEm());
        Render2DUtil.rect(this.x, headerY, headerWidth, HEADER_HEIGHT * unit)
                .color(ColorUtil.multiplyAlpha(CARD_BG, alpha))
                .radius(6.0F * unit)
                .draw();

        float headerCenterY = headerY + HEADER_HEIGHT * unit / 2.0F;
        Render2DUtil.texture(this.x + PADDING_X * unit, headerCenterY - (ICON_SIZE * unit) / 2.0F,
                        ICON_SIZE * unit, ICON_SIZE * unit, Textures.Icons.KEYBOARD)
                .color(ColorUtil.multiplyAlpha(Theme.getAccent(), alpha))
                .draw();

        Render2DUtil.text(this.x + (PADDING_X + ICON_SIZE + 6.0F) * unit,
                        font.centeredTextY(headerCenterY, headerTextSize), headerTextSize, "Binds")
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha))
                .draw();

        if (this.rows.isEmpty()) {
            return;
        }

        // Bind rows with category icons and smooth entrance animation
        long now = System.currentTimeMillis();
        float cursorY = headerY + (HEADER_HEIGHT + 6.0F) * unit;
        for (BindRow row : this.rows) {
            Long startTime = this.toggleTimes.get(row.name());
            long age = startTime != null ? (now - startTime) : 1000L;
            float rowAlpha = 1.0F;
            float slideOffsetX = 0.0F;

            if (age < 250L) {
                float enterProgress = age / 250.0F;
                rowAlpha = enterProgress * enterProgress * (3.0F - 2.0F * enterProgress);
                slideOffsetX = (1.0F - enterProgress) * 24.0F * unit;
            }

            float finalAlpha = alpha * rowAlpha;
            float drawX = this.x + slideOffsetX;
            float rowCenterY = cursorY + ROW_HEIGHT * unit / 2.0F;

            Render2DUtil.rect(drawX, cursorY, this.width, ROW_HEIGHT * unit)
                    .color(ColorUtil.multiplyAlpha(CARD_BG, finalAlpha))
                    .radius(6.0F * unit)
                    .draw();

            float cursorX = drawX + PADDING_X * unit;
            float iconSize = ICON_SIZE * unit;

            // ClickGUI Category Icon for each feature
            Render2DUtil.texture(cursorX, rowCenterY - iconSize / 2.0F, iconSize, iconSize, row.icon())
                    .color(ColorUtil.multiplyAlpha(Theme.getAccent(), finalAlpha))
                    .draw();
            cursorX += iconSize + 6.0F * unit;

            // Name
            Render2DUtil.text(cursorX, font.centeredTextY(rowCenterY, textSize), textSize, row.name())
                    .style(UiFontStyle.MEDIUM)
                    .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, finalAlpha))
                    .draw();

            // Key chip on right
            float chipWidth = font.measureWidth(row.bindKey(), textSize, letterSpacing) + 10.0F * unit;
            chipWidth = Math.max(18.0F * unit, chipWidth);
            float chipHeight = 16.0F * unit;
            float chipX = drawX + this.width - PADDING_X * unit - chipWidth;
            float chipY = rowCenterY - chipHeight / 2.0F;

            Render2DUtil.rect(chipX, chipY, chipWidth, chipHeight)
                    .color(ColorUtil.multiplyAlpha(CHIP_BG, finalAlpha))
                    .radius(4.0F * unit)
                    .draw();

            Render2DUtil.text(chipX + chipWidth / 2.0F, font.centeredTextY(rowCenterY, textSize), textSize, row.bindKey())
                    .style(UiFontStyle.MEDIUM)
                    .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT, finalAlpha))
                    .align(org.shizavisuals.utils.render.gui.TextAlign.CENTER)
                    .draw();

            cursorY += (ROW_HEIGHT + ROW_GAP) * unit;
        }
    }

    private void collectBinds(Minecraft mc) {
        this.rows.clear();
        for (Feature feature : FeatureManager.INSTANCE.getFeatures()) {
            if (!feature.isEnabled()) {
                continue;
            }
            StringJoiner binds = new StringJoiner(" + ");
            List<Integer> bindCodes = feature.getBinds();
            for (int i = 0; i < bindCodes.size(); i++) {
                int bindCode = bindCodes.get(i);
                if (bindCode != BindSetting.UNBOUND && feature.isBindVisibleAt(i)) {
                    binds.add(BindSetting.describe(bindCode));
                }
            }
            if (binds.length() > 0) {
                Identifier icon = categoryIcon(feature.getCategory());
                this.rows.add(new BindRow(icon, feature.getName(), binds.toString(), true));
            }
        }
    }

    private static Identifier categoryIcon(FeatureCategory category) {
        int idx = category != null ? category.ordinal() : 0;
        return CATEGORY_ICONS[idx >= 0 && idx < CATEGORY_ICONS.length ? idx : 0];
    }
}
