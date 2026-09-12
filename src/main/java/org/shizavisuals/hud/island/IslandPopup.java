package org.shizavisuals.hud.island;

import org.shizavisuals.context.RenderContext;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IslandPopup {

    public static final float WIDTH = 110.0F;
    private static final float TITLE_H = 18.0F;
    private static final float ROW_H = 18.0F;
    private static final float SEPARATOR_H = 4.0F;
    private static final float HINT_H = 18.0F;
    private static final float BG_ROW_H = 18.0F;
    private static final float COMPONENT_GAP = 0.5F;
    private static final float BOTTOM_PAD = 2.0F;
    private static final float RADIUS = 6.0F;

    private static final int PANEL_COLOR = ColorUtil.rgb(8, 8, 8);
    private static final int TRACK_OFF_COLOR = Theme.Colors.CONTROL;
    private static final int SEPARATOR_COLOR = ColorUtil.rgba(255, 255, 255, 15);
    private static final int HAIRLINE_COLOR = Theme.Colors.TEXT_TEXT;

    private final DynamicIsland island;
    private final IslandAnimation appearAnim = new IslandAnimation(300L, 0.0F, IslandEasing.BAKEK_SIZE);
    private final IslandAnimation blurAnim = new IslandAnimation(300L, 0.0F, IslandEasing.FIGMA_EASE_IN_OUT);
    private final Map<String, IslandAnimation> enableAnims = new HashMap<>();
    private final Map<String, IslandColorAnimation> trackAnims = new HashMap<>();
    private final Map<String, IslandAnimation> circleAnims = new HashMap<>();
    private final Map<String, IslandAnimation> hoverAnims = new HashMap<>();

    private boolean showing;
    private IslandAnimation[] rowAnims = new IslandAnimation[0];
    private float boundX;
    private float boundY;
    private float bgRowTop = -1.0F;
    private int pressedIndex = -1;
    private float pressY;

    public IslandPopup(DynamicIsland island) {
        this.island = island;
    }

    public void setOpen(boolean value) {
        this.showing = value;
    }

    public boolean isShowing() {
        return this.showing;
    }

    public boolean isAnimating() {
        return this.showing || this.appearAnim.getValue() > 0.001F;
    }

    public float widthScreen() {
        return WIDTH * this.island.scale();
    }

    public float heightScreen() {
        return height(this.island.toggleable().size()) * this.island.scale();
    }

    public float rowHeightScreen() {
        return ROW_H * this.island.scale();
    }

    public boolean isInside(double mx, double my) {
        return mx >= this.boundX && mx <= this.boundX + widthScreen()
                && my >= this.boundY && my <= this.boundY + heightScreen();
    }

    public int rowAt(double my) {
        int count = this.island.toggleable().size();
        if (count == 0) {
            return -1;
        }
        float s = this.island.scale();
        float rowsTop = this.boundY + (TITLE_H + COMPONENT_GAP + SEPARATOR_H + COMPONENT_GAP) * s;
        if (my < rowsTop || my >= rowsTop + count * ROW_H * s) {
            return -1;
        }
        return (int) ((float) (my - rowsTop) / (ROW_H * s));
    }

    public boolean backgroundRowAt(double my) {
        if (this.bgRowTop < 0.0F) {
            return false;
        }
        float s = this.island.scale();
        return my >= this.bgRowTop && my < this.bgRowTop + BG_ROW_H * s;
    }

    public void setPressedRow(int index, float pressY) {
        this.pressedIndex = index;
        this.pressY = pressY;
    }

    public void render(double mouseX, double mouseY, float centerX, float topY, float s) {
        this.appearAnim.setEasing(this.showing ? IslandEasing.BAKEK : IslandEasing.BAKEK_BACK);
        float appear = this.appearAnim.update(this.showing ? 1.0F : 0.0F);
        this.blurAnim.update(appear >= 0.6F ? 1.0F : 0.0F);
        if (appear <= 0.001F && !this.showing) {
            return;
        }

        List<IslandStatus> rows = this.island.toggleable();
        ensureRowState(rows.size());

        float width = WIDTH * s;
        float height = height(rows.size()) * s;
        float x = centerX - width / 2.0F;
        float y = topY;
        this.boundX = x;
        this.boundY = y;

        float cursor = y;
        float titleY = cursor;
        cursor += (TITLE_H + COMPONENT_GAP) * s;
        float separatorTop = cursor;
        cursor += (SEPARATOR_H + COMPONENT_GAP) * s;
        float rowsTop = cursor;
        for (int i = 0; i < rows.size(); i++) {
            float slotY = rowsTop + i * ROW_H * s;
            if (this.rowAnims[i].getValue() == 0.0F) {
                this.rowAnims[i].setValue(slotY);
            }
            this.rowAnims[i].update(slotY);
        }
        cursor = rowsTop + rows.size() * ROW_H * s + COMPONENT_GAP * s;
        float separatorBottom = cursor;
        cursor += (SEPARATOR_H + COMPONENT_GAP) * s;
        this.bgRowTop = cursor;
        cursor += (BG_ROW_H + COMPONENT_GAP) * s;
        float hintY = cursor;

        var extractor = RenderContext.currentGuiGraphicsExtractor();
        if (extractor != null) {
            var pose = extractor.pose();
            float scale = 0.5F + 0.5F * appear;
            float pivotX = x + width / 2.0F;
            float pivotY = y + height / 2.0F;
            pose.pushMatrix();
            pose.translate(pivotX, pivotY);
            pose.scale(scale, scale);
            pose.translate(-pivotX, -pivotY);
            try {
                drawChrome(x, y, width, height, s, appear);
                drawTitle(x, titleY, s, appear);
                drawSeparator(x, separatorTop, width, s, appear);
                drawRows(rows, x, rowsTop, width, s, mouseX, mouseY, appear);
                drawSeparator(x, separatorBottom, width, s, appear);
                drawBackgroundRow(x, this.bgRowTop, width, s, mouseX, mouseY, appear);
                drawHint(x, hintY, s, appear);
            } finally {
                pose.popMatrix();
            }
        }
    }

    private void drawChrome(float x, float y, float width, float height, float s, float appear) {
        Render2DUtil.rect(x, y, width, height)
                .radius(RADIUS * s)
                .shadow(ColorUtil.withAlpha(ColorUtil.BLACK, Math.round(127.5F * appear)), 15.0F * s)
                .color(ColorUtil.TRANSPARENT)
                .draw();

        Render2DUtil.rect(x, y, width, height)
                .radius(RADIUS * s)
                .blur(45.0F * s)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, appear))
                .draw();

        Render2DUtil.rect(x, y, width, height)
                .radius(RADIUS * s)
                .color(ColorUtil.withAlpha(PANEL_COLOR, appear))
                .draw();
    }

    private void drawTitle(float x, float y, float s, float appear) {
        String text = "Dynamic Island";
        MsdfFont font = UiFonts.getFontForText(text, UiFontStyle.REGULAR.weight());
        float size = 8.0F * s;
        Render2DUtil.text(x + 8.0F * s, y + middle(font, size, TITLE_H * s), size, text)
                .font(font)
                .style(UiFontStyle.REGULAR)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, appear))
                .draw();
    }

    private void drawSeparator(float x, float y, float width, float s, float appear) {
        Render2DUtil.rect(x, y, width, SEPARATOR_H * s)
                .color(ColorUtil.multiplyAlpha(SEPARATOR_COLOR, appear))
                .draw();
    }

    private void drawRows(List<IslandStatus> rows, float x, float rowsTop, float width, float s,
                          double mouseX, double mouseY, float appear) {
        for (int i = 0; i < rows.size(); i++) {
            IslandStatus status = rows.get(i);
            String name = displayName(status);

            float rowH = ROW_H * s;
            float rowX = x;
            float rowW = width;
            boolean draggedVisual = this.pressedIndex >= 0
                    && Math.abs((float) mouseY - this.pressY) > rowH * 0.6F;
            float rowY = draggedVisual && this.pressedIndex == i
                    ? clamp((float) mouseY - rowH / 2.0F, rowsTop,
                            rowsTop + (rows.size() - 1) * rowH)
                    : this.rowAnims[i].getValue();
            if (i > 0) {
                Render2DUtil.rect(x, this.rowAnims[i].getValue() - 1.0F * s, width, 0.5F * s)
                        .color(ColorUtil.multiplyAlpha(HAIRLINE_COLOR, 0.02F * appear))
                        .draw();
            }

            boolean hovered = !draggedVisual
                    && mouseX >= rowX && mouseX <= rowX + rowW
                    && mouseY >= rowY && mouseY <= rowY + rowH;

            boolean enabled = status.isEnabled();
            IslandAnimation enableAnim = anim(this.enableAnims, status.getName(),
                    new IslandAnimation(300L, IslandEasing.FIGMA_EASE_IN_OUT));
            enableAnim.update(enabled);
            float enable = enableAnim.getValue();

            IslandAnimation hoverAnim = anim(this.hoverAnims, status.getName(),
                    new IslandAnimation(300L, IslandEasing.FIGMA_EASE_IN_OUT));
            hoverAnim.update(hovered || (this.pressedIndex == i && draggedVisual));

            MsdfFont font = UiFonts.getFontForText(name, UiFontStyle.REGULAR.weight());
            float size = 8.0F * s;
            float ls = size * UiFontStyle.REGULAR.letterSpacingEm();
            String clipped = font.ellipsize(name, size, ls, (WIDTH - 13.0F - 20.0F) * s);
            int nameAlpha = Math.round(255.0F
                    * (0.75F + 0.25F * enable + 0.25F * hoverAnim.getValue()) * appear);
            Render2DUtil.text(rowX + 10.0F * s, rowY + middle(font, size, ROW_H * s), size, clipped)
                    .font(font)
                    .style(UiFontStyle.REGULAR)
                    .color(ColorUtil.withAlpha(Theme.Colors.TEXT_TEXT, nameAlpha))
                    .draw();

            IslandColorAnimation trackAnim = this.trackAnims.computeIfAbsent(status.getName(),
                    key -> new IslandColorAnimation(300L, TRACK_OFF_COLOR, IslandEasing.FIGMA_EASE_IN_OUT));
            trackAnim.update(enabled ? Theme.getAccent() : TRACK_OFF_COLOR);

            float checkW = 13.0F * s;
            float checkH = 8.0F * s;
            float trackX = rowX + rowW - checkW - 9.0F * s;
            float trackY = rowY + 5.0F * s;
            Render2DUtil.rect(trackX, trackY, checkW, checkH)
                    .radius(3.0F * s)
                    .color(ColorUtil.multiplyAlpha(trackAnim.getColor(), appear))
                    .draw();

            IslandAnimation circleAnim = anim(this.circleAnims, status.getName(),
                    new IslandAnimation(300L, 0.75F, IslandEasing.FIGMA_EASE_IN_OUT));
            circleAnim.update(enabled ? 1.0F : 0.75F);

            float knobSize = 6.0F * s;
            float knobX = rowX + rowW - checkW - 8.0F * s + 5.0F * s * enable;
            float knobY = rowY + 6.0F * s;
            Render2DUtil.rect(knobX, knobY, knobSize, knobSize)
                    .radius(knobSize / 2.0F)
                    .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, circleAnim.getValue() * appear))
                    .draw();
        }
    }

    private void drawBackgroundRow(float x, float rowY, float width, float s,
                                   double mouseX, double mouseY, float appear) {
        String mode = org.shizavisuals.menu.core.MenuConfigStore.getString("hud.island.bg", "minimal");
        String modeName = switch (mode) {
            case "gradient" -> "Градиент";
            case "glass" -> "Стекло";
            default -> "Минимал";
        };
        String text = "Фон: " + modeName;
        float rowH = BG_ROW_H * s;
        boolean hovered = backgroundRowAt(mouseY) && mouseX >= x && mouseX <= x + width;

        IslandAnimation hoverAnim = anim(this.hoverAnims, "background",
                new IslandAnimation(300L, IslandEasing.FIGMA_EASE_IN_OUT));
        hoverAnim.update(hovered);

        if (hovered) {
            Render2DUtil.rect(x + 8.0F * s, rowY + 2.0F * s, width - 16.0F * s, rowH - 4.0F * s)
                    .radius(4.0F * s)
                    .color(ColorUtil.multiplyAlpha(Theme.getAccent(), 0.14F * hoverAnim.getValue() * appear))
                    .draw();
        }

        MsdfFont font = UiFonts.getFontForText(text, UiFontStyle.REGULAR.weight());
        float size = 8.0F * s;
        float ls = size * UiFontStyle.REGULAR.letterSpacingEm();
        String clipped = font.ellipsize(text, size, ls, (WIDTH - 16.0F) * s);
        Render2DUtil.text(x + 10.0F * s, rowY + middle(font, size, rowH), size, clipped)
                .font(font)
                .style(UiFontStyle.REGULAR)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, 0.85F * appear))
                .draw();
    }

    private void drawHint(float x, float y, float s, float appear) {
        String text = "Drag — priority · Click — toggle";
        MsdfFont font = UiFonts.getFontForText(text, UiFontStyle.REGULAR.weight());
        float size = 7.0F * s;
        float ls = size * UiFontStyle.REGULAR.letterSpacingEm();
        String clipped = font.ellipsize(text, size, ls, (WIDTH - 8.0F) * s);
        Render2DUtil.text(x + 4.0F * s, y + middle(font, size, HINT_H * s), size, clipped)
                .font(font)
                .style(UiFontStyle.REGULAR)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, 0.6F * appear))
                .draw();
    }

    private void ensureRowState(int count) {
        if (this.rowAnims.length != count) {
            IslandAnimation[] anims = new IslandAnimation[count];
            for (int i = 0; i < count; i++) {
                anims[i] = new IslandAnimation(200L, 0.0F, IslandEasing.BAKEK_SMALLER);
            }
            this.rowAnims = anims;
        }
    }

    private static IslandAnimation anim(Map<String, IslandAnimation> map, String key, IslandAnimation fallback) {
        return map.computeIfAbsent(key, ignored -> fallback);
    }

    private static float middle(MsdfFont font, float size, float box) {
        return (box - font.textHeight(size)) / 2.0F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float height(int rowCount) {
        int components = rowCount + 6;
        return TITLE_H + SEPARATOR_H + rowCount * ROW_H + SEPARATOR_H + BG_ROW_H + HINT_H
                + components * COMPONENT_GAP + BOTTOM_PAD;
    }

    static String displayName(IslandStatus status) {
        return switch (status.getName()) {
            case "music" -> "Музыка";
            case "alerts", "notification" -> "Уведомления";
            case "mine" -> "Шахта";
            case "pvp" -> "PVP";
            case "events", "event" -> "Ивенты";
            case "objects" -> "Объекты";
            case "default" -> "Логотип";
            default -> status.getName();
        };
    }
}
