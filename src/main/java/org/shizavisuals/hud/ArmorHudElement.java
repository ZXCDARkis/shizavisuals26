package org.shizavisuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.joml.Matrix3x2fStack;

public final class ArmorHudElement extends HudElement {
    private static final float PADDING = 5.0F;
    private static final float CELL_SIZE = 27.5F;
    private static final float CELL_GAP = 2.5F;
    private static final float ITEM_SIZE = 20.0F;
    private static final float ITEM_TOP = 2.0F;
    private static final float BAR_WIDTH = 16.0F;
    private static final float BAR_HEIGHT = 2.5F;
    private static final float BAR_BOTTOM = 2.5F;
    private static final float PANEL_RADIUS = 10.0F;
    private static final float CELL_RADIUS = 5.0F;
    private static final float BOTTOM_MARGIN = 82.0F;

    private static final EquipmentSlot[] ARMOR_ORDER = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private float screenHeight;

    public ArmorHudElement() {
        super("armor_hud", "ArmorHud");
    }

    @Override
    protected void layout(Minecraft mc, float unit) {
        this.screenHeight = mc.getWindow().getGuiScaledHeight();
        this.width = (PADDING * 2.0F + CELL_SIZE * ARMOR_ORDER.length
                + CELL_GAP * (ARMOR_ORDER.length - 1)) * unit;
        this.height = (PADDING * 2.0F + CELL_SIZE) * unit;
    }

    @Override
    protected float defaultY(float unit) {
        return this.screenHeight - this.height - BOTTOM_MARGIN * unit;
    }

    @Override
    protected void draw(Minecraft mc, float unit) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        float alpha = appearAlpha();
        Render2DUtil.rect(this.x, this.y, this.width, this.height)
                .color(ColorUtil.multiplyAlpha(ColorUtil.rgba(22, 22, 22, 230), alpha))
                .radius(PANEL_RADIUS * unit)
                .draw();

        float cellSize = CELL_SIZE * unit;
        float cellGap = CELL_GAP * unit;
        float cellsX = this.x + PADDING * unit;
        float cellsY = this.y + PADDING * unit;

        for (int index = 0; index < ARMOR_ORDER.length; index++) {
            float cellX = cellsX + index * (cellSize + cellGap);
            Render2DUtil.rect(cellX, cellsY, cellSize, cellSize)
                    .color(ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_SMALL, alpha))
                    .radius(CELL_RADIUS * unit)
                    .draw();

            ItemStack stack = player.getItemBySlot(ARMOR_ORDER[index]);
            drawDurability(stack, cellX, cellsY, cellSize, unit, alpha);
        }

        if (alpha <= 0.05F) {
            return;
        }
        GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
        if (extractor == null) {
            return;
        }

        Render2DUtil.flush();
        Matrix3x2fStack pose = extractor.pose();
        float guiScale = (float) mc.getWindow().getGuiScale();
        float itemSize = ITEM_SIZE * unit;
        float itemScale = itemSize / 16.0F;

        for (int index = 0; index < ARMOR_ORDER.length; index++) {
            ItemStack stack = player.getItemBySlot(ARMOR_ORDER[index]);
            if (stack.isEmpty()) {
                continue;
            }

            float cellX = cellsX + index * (cellSize + cellGap);
            float itemX = cellX + (cellSize - itemSize) / 2.0F;
            float itemY = cellsY + ITEM_TOP * unit;
            itemX = Math.round(itemX * guiScale) / guiScale;
            itemY = Math.round(itemY * guiScale) / guiScale;

            pose.pushMatrix();
            pose.translate(itemX, itemY);
            pose.scale(itemScale);
            extractor.item(stack, 0, 0);
            pose.popMatrix();
        }
    }

    private static void drawDurability(
            ItemStack stack,
            float cellX,
            float cellY,
            float cellSize,
            float unit,
            float alpha
    ) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return;
        }

        float remaining = Math.clamp(
                (stack.getMaxDamage() - stack.getDamageValue()) / (float) Math.max(1, stack.getMaxDamage()),
                0.0F,
                1.0F
        );
        float barWidth = BAR_WIDTH * unit;
        float barHeight = BAR_HEIGHT * unit;
        float barX = cellX + (cellSize - barWidth) / 2.0F;
        float barY = cellY + cellSize - BAR_BOTTOM * unit - barHeight;

        Render2DUtil.rect(barX, barY, barWidth, barHeight)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_MEDIUM, alpha))
                .radius(barHeight)
                .draw();

        float fillWidth = barWidth * remaining;
        if (fillWidth <= 0.0F) {
            return;
        }
        int color = remaining > 0.5F
                ? Theme.Colors.TRAFFIC_MAXIMIZE
                : remaining > 0.25F
                ? Theme.Colors.TRAFFIC_MINIMIZE
                : Theme.Colors.TRAFFIC_CLOSE;
        Render2DUtil.rect(barX, barY, Math.max(barHeight, fillWidth), barHeight)
                .color(ColorUtil.multiplyAlpha(color, alpha))
                .radius(barHeight)
                .draw();
    }
}
