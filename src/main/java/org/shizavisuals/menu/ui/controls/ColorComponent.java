package org.shizavisuals.menu.ui.controls;

import org.shizavisuals.menu.ui.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class ColorComponent extends Component {
    private static final long ANIMATION_MS = 150L;
    private static final long COLOR_ANIMATION_MS = 180L;
    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFECECEC, 0xFFD5D5D5, 0xFFC1C1C1, 0xFFADADAD, 0xFF999999, 0xFF858585, 0xFF717171, 0xFF5D5D5D, 0xFF474747, 0xFF333333, 0xFF000000,
            0xFF00384C, 0xFF001E5A, 0xFF14053C, 0xFF2C053A, 0xFF3D081A, 0xFF5D0803, 0xFF591C00, 0xFF583300, 0xFF583E01, 0xFF666200, 0xFF505302, 0xFF263C0D,
            0xFF024E66, 0xFF01317B, 0xFF1B0A51, 0xFF460B5B, 0xFF540E26, 0xFF811000, 0xFF7C2900, 0xFF7B4B01, 0xFF785900, 0xFF8E8502, 0xFF6E750B, 0xFF39571B,
            0xFF016E8F, 0xFF0241AA, 0xFF2F097A, 0xFF61197D, 0xFF791A3A, 0xFFB41900, 0xFFAC3E00, 0xFFA96A03, 0xFFA57D04, 0xFFC4BA00, 0xFF9DA60D, 0xFF4D7924,
            0xFF028BB7, 0xFF0356DA, 0xFF3B1794, 0xFF7A1F9E, 0xFF992451, 0xFFE12300, 0xFFDA5200, 0xFFD38405, 0xFFD49D02, 0xFFF8EC00, 0xFFC2D018, 0xFF649D36,
            0xFF00A0D7, 0xFF0260FE, 0xFF4D21B2, 0xFF992BBE, 0xFFB82F5D, 0xFFFF4013, 0xFFFE6902, 0xFFFFAD01, 0xFFFDC703, 0xFFFFFB3F, 0xFFD9ED39, 0xFF76BB40,
            0xFF03C6FC, 0xFF3C86FF, 0xFF5E30EA, 0xFFC038F2, 0xFFE63C7B, 0xFFFF6251, 0xFFFE864A, 0xFFFFB240, 0xFFFFCB3D, 0xFFFEF66D, 0xFFE4F066, 0xFF99D362,
            0xFF50D6F9, 0xFF76A8FF, 0xFF8650FE, 0xFFD257FF, 0xFFF0719C, 0xFFFE8C81, 0xFFFFA67E, 0xFFFFC877, 0xFFFFD979, 0xFFFCF992, 0xFFEBF191, 0xFFB1DD88,
            0xFF93E2FF, 0xFFA8C6FF, 0xFFB18BFC, 0xFFE194FE, 0xFFF4A4BF, 0xFFFFB5B0, 0xFFFDC5AC, 0xFFFFD9AA, 0xFFFDE3A6, 0xFFFFFCB8, 0xFFF2F6B9, 0xFFCCE8B7,
            0xFFCBF0FF, 0xFFD4E3FF, 0xFFDCC9FF, 0xFFEFC8FD, 0xFFF8D2DF, 0xFFFDDAD8, 0xFFFEE2D7, 0xFFFCECD3, 0xFFFFF2D5, 0xFFFFFADD, 0xFFF6FBDB, 0xFFE0EED5
    };

    private final IntSupplier colorSupplier;
    private final IntConsumer colorConsumer;
    private final Animation openAnimation = new Animation(ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    private final Animation colorAnimation = new Animation(COLOR_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    private float alpha = 1.0F;
    private int mouseX;
    private int mouseY;
    private boolean open;
    private int displayedColor;
    private int colorFrom;
    private int colorTo;

    public ColorComponent(IntSupplier colorSupplier, IntConsumer colorConsumer) {
        this.colorSupplier = colorSupplier;
        this.colorConsumer = colorConsumer;
        int initial = colorSupplier.getAsInt();
        this.displayedColor = initial;
        this.colorFrom = initial;
        this.colorTo = initial;
        this.openAnimation.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
        this.colorAnimation.animate(1.0F, 1.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
    }

    public ColorComponent place(Component owner, int x, int y, int width, int height) {
        attach(owner, owner.sx(x), owner.sy(y), owner.px(width), owner.px(height));
        return this;
    }

    public ColorComponent alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    public ColorComponent mouse(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        return this;
    }

    public boolean handleClick(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (this.open) {
            close();
        } else {
            this.open = true;
            this.openAnimation.animate(0.0F, 1.0F, ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
        }
        return true;
    }

    public boolean handlePopupClick(int mouseX, int mouseY) {
        if (!this.open) {
            return false;
        }
        int index = colorIndex(mouseX, mouseY);
        if (index >= 0) {
            setColor(PALETTE[index]);
            close();
            return true;
        }
        if (contains(mouseX, mouseY)) {
            return false;
        }
        if (containsPopup(mouseX, mouseY)) {
            return true;
        }
        close();
        return false;
    }

    public void close() {
        if (this.open) {
            this.openAnimation.animate(this.openAnimation.getValue(), 0.0F, 120L, Animation.Easing.EASE_OUT_QUAD);
        }
        this.open = false;
    }

    public void closeImmediately() {
        this.open = false;
        this.openAnimation.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
    }

    public boolean isOpen() {
        return this.open;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        updateDisplayedColor();
        float padding = px(Theme.Sizes.COLOR_PREVIEW_PADDING);
        float outlineRadius = px(Theme.Sizes.COLOR_PREVIEW_RADIUS);
        float innerRadius = px(Theme.Sizes.COLOR_BLOCK_RADIUS);

        Render2DUtil.rect(x(), y(), width(), height())
                .color(ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_SMALL, this.alpha))
                .radius(outlineRadius)
                .draw();

        float innerWidth = Math.max(0, width() - padding * 2);
        float innerHeight = Math.max(0, height() - padding * 2);

        Render2DUtil.rect(x() + padding, y() + padding, innerWidth, innerHeight)
                .color(ColorUtil.multiplyAlpha(this.displayedColor, this.alpha))
                .radius(innerRadius)
                .draw();
    }

    public void renderOverlay() {
        float progress = this.openAnimation.getValue();
        if (progress <= 0.001F) {
            return;
        }
        float popupAlpha = this.alpha * progress;
        float lift = -6.0F * (1.0F - progress);
        float popupX = popupX();
        float popupY = popupY() + lift;
        float popupWidth = popupWidth();
        float popupHeight = popupHeight();

        glassPanel(popupX, popupY, popupWidth, popupHeight,
                px(Theme.Sizes.COLOR_PICKER_RADIUS), px(40.0F), popupAlpha);

        float gridX = gridX();
        float gridY = gridY() + lift;
        float gridWidth = gridWidth();
        float gridHeight = gridHeight();
        if (gridWidth <= 0.0F || gridHeight <= 0.0F) {
            return;
        }

        int selectedIndex = selectedIndex();
        int hoveredIndex = this.open ? colorIndex(this.mouseX, this.mouseY) : -1;
        if (hoveredIndex == selectedIndex) {
            hoveredIndex = -1;
        }

        Render2DUtil.colorGrid(gridX, gridY, cellSize(), Theme.Sizes.COLOR_PICKER_COLUMNS, PALETTE)
                .radius(Math.min(cellSize(), px(Theme.Sizes.COLOR_PICKER_GRID_RADIUS)))
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, popupAlpha))
                .draw();

        if (hoveredIndex >= 0) {
            renderCellOutline(hoveredIndex, px(1), popupAlpha, lift);
        }
        if (selectedIndex >= 0) {
            renderCellOutline(selectedIndex, px(2), popupAlpha, lift);
        }
    }

    private void setColor(int color) {
        this.colorConsumer.accept(color);
        this.colorFrom = this.displayedColor;
        this.colorTo = color;
        this.colorAnimation.animate(0.0F, 1.0F, COLOR_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    }

    private void updateDisplayedColor() {
        int target = this.colorSupplier.getAsInt();
        if (ColorUtil.withAlpha(target, 255) != ColorUtil.withAlpha(this.colorTo, 255)) {
            this.colorFrom = this.displayedColor;
            this.colorTo = target;
            this.colorAnimation.animate(0.0F, 1.0F, COLOR_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
        }
        this.displayedColor = ColorUtil.lerp(this.colorFrom, this.colorTo, this.colorAnimation.getValue());
    }

    private int selectedIndex() {
        int current = ColorUtil.withAlpha(this.colorSupplier.getAsInt(), 255);
        for (int index = 0; index < PALETTE.length; index++) {
            if (ColorUtil.withAlpha(PALETTE[index], 255) == current) {
                return index;
            }
        }
        return -1;
    }

    private int colorIndex(int mouseX, int mouseY) {
        float gridX = gridX();
        float gridY = gridY();
        float gridWidth = gridWidth();
        float gridHeight = gridHeight();
        if (mouseX < gridX || mouseX >= gridX + gridWidth || mouseY < gridY || mouseY >= gridY + gridHeight) {
            return -1;
        }
        float cell = cellSize();
        int column = (int) ((mouseX - gridX) / cell);
        int row = (int) ((mouseY - gridY) / cell);
        int index = row * Theme.Sizes.COLOR_PICKER_COLUMNS + column;
        return index >= 0 && index < PALETTE.length ? index : -1;
    }

    private boolean containsPopup(int mouseX, int mouseY) {
        float popupX = popupX();
        float popupY = popupY();
        return mouseX >= popupX
                && mouseX <= popupX + popupWidth()
                && mouseY >= popupY
                && mouseY <= popupY + popupHeight();
    }

    private float[] cellBounds(int index, float lift) {
        int column = index % Theme.Sizes.COLOR_PICKER_COLUMNS;
        int row = index / Theme.Sizes.COLOR_PICKER_COLUMNS;
        float gridX = gridX();
        float gridY = gridY() + lift;
        float cell = cellSize();
        return new float[]{gridX + column * cell, gridY + row * cell, cell, cell};
    }

    private void renderCellOutline(int index, float thickness, float popupAlpha, float lift) {
        int[] outlineColors = new int[PALETTE.length];
        outlineColors[index] = ColorUtil.WHITE;
        Render2DUtil.colorGrid(gridX(), gridY() + lift, cellSize(), Theme.Sizes.COLOR_PICKER_COLUMNS, outlineColors)
                .radius(Math.min(cellSize(), px(Theme.Sizes.COLOR_PICKER_GRID_RADIUS)))
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, popupAlpha))
                .draw();

        float[] cell = cellBounds(index, lift);
        float innerSize = Math.max(0.0F, cell[2] - thickness * 2.0F);
        if (innerSize <= 0.0F) {
            return;
        }

        Render2DUtil.RectBuilder inner = Render2DUtil.rect(cell[0] + thickness, cell[1] + thickness, innerSize, innerSize)
                .color(ColorUtil.multiplyAlpha(PALETTE[index], popupAlpha));
        float[] radii = innerCornerRadii(index, thickness, innerSize);
        inner.radius(radii[0], radii[1], radii[2], radii[3]).draw();
    }

    private float[] innerCornerRadii(int index, float thickness, float innerSize) {
        int column = index % Theme.Sizes.COLOR_PICKER_COLUMNS;
        int row = index / Theme.Sizes.COLOR_PICKER_COLUMNS;
        int lastColumn = Theme.Sizes.COLOR_PICKER_COLUMNS - 1;
        int lastRow = PALETTE.length / Theme.Sizes.COLOR_PICKER_COLUMNS - 1;
        float radius = Math.min(innerSize, Math.max(0.0F, Math.min(cellSize(), px(Theme.Sizes.COLOR_PICKER_GRID_RADIUS)) - thickness));
        return new float[]{
                column == 0 && row == 0 ? radius : 0.0F,
                column == lastColumn && row == 0 ? radius : 0.0F,
                column == lastColumn && row == lastRow ? radius : 0.0F,
                column == 0 && row == lastRow ? radius : 0.0F
        };
    }

    private float popupX() {
        return x() + width() - popupWidth();
    }

    private float popupY() {
        return y() + height() + px(Theme.Sizes.COLOR_PICKER_OFFSET_Y);
    }

    private float popupWidth() {
        return gridWidth() + px(Theme.Sizes.COLOR_PICKER_PADDING) * 2;
    }

    private float popupHeight() {
        return gridHeight() + px(Theme.Sizes.COLOR_PICKER_PADDING) * 2;
    }

    private float gridX() {
        return popupX() + px(Theme.Sizes.COLOR_PICKER_PADDING);
    }

    private float gridY() {
        return popupY() + px(Theme.Sizes.COLOR_PICKER_PADDING);
    }

    private float gridWidth() {
        return cellSize() * Theme.Sizes.COLOR_PICKER_COLUMNS;
    }

    private float gridHeight() {
        return cellSize() * (PALETTE.length / Theme.Sizes.COLOR_PICKER_COLUMNS);
    }

    private float cellSize() {
        return px(Theme.Sizes.COLOR_PICKER_CELL_SIZE);
    }

}
