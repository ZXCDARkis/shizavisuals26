package org.shizavisuals.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.joml.Matrix3x2fStack;

public final class HotbarElement extends HudElement {
    private static final int SLOT_COUNT = 9;

    private static final float PADDING = 5.0F;
    private static final float CELL = 27.5F;
    private static final float CELL_GAP = 2.5F;
    private static final float ITEM_SIZE = 20.0F;
    private static final float CHIP_RADIUS = 6.0F;
    private static final float OFFHAND_GAP = 6.0F;
    private static final float OFFHAND_SIZE = 32.0F;
    private static final float OFFHAND_RADIUS = 8.0F;
    private static final float BOTTOM_MARGIN = 12.0F;
    private static final float NAME_TEXT_SIZE = 10.0F;
    private static final float ITEM_COUNT_TEXT_SIZE = 8.0F;
    private static final float ITEM_COUNT_RIGHT = 17.0F;
    private static final float ITEM_COUNT_BOTTOM = 17.0F;

    private static final long NAME_FADE_MS = 500L;

    private static final float XP_TEXT_SIZE = 10.0F;

    private static final int XP_COLOR = ColorUtil.rgb(128, 255, 32);

    private static final float VANILLA_HOTBAR_HEIGHT = 22.0F;
    private static final float VANILLA_STATS_ROW_CENTER = 12.5F;
    private static final float VANILLA_NAME_OFFSET = 49.0F;
    private static final float VANILLA_NAME_OFFSET_CREATIVE = 23.0F;

    private static final float STATS_ROW_DROP = 5.0F;

    private record Style(float radius) {
    }

    private static final Style DEFAULT = new Style(14.0F);
    private static final Style MINI = new Style(10.0F);

    private Style style = MINI;
    private float screenHeight;

    private float smoothedSlot;
    private boolean slotInitialized;
    private long lastFrameTime;

    private ItemStack lastHighlight = ItemStack.EMPTY;
    private long highlightEnd;

    private float renderedTop = Float.NaN;

    private float statsHalfWidth;

    public HotbarElement() {
        super("hotbar", "Hotbar");
    }

    public void setMini(boolean mini) {
        this.style = mini ? MINI : DEFAULT;
    }

    @Override
    protected boolean centerHorizontally() {
        return true;
    }

    @Override
    protected void layout(Minecraft mc, float unit) {
        this.screenHeight = mc.getWindow().getGuiScaledHeight();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) {
            this.width = 0.0F;
            this.height = 0.0F;
            this.statsHalfWidth = 0.0F;
            return;
        }
        this.width = (PADDING * 2.0F + SLOT_COUNT * CELL + (SLOT_COUNT - 1) * CELL_GAP) * unit;
        this.height = (PADDING * 2.0F + CELL) * unit;
        this.statsHalfWidth = this.width / 2.0F - PADDING * unit;
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
        this.renderedTop = this.y;
        float alpha = appearAlpha();

        Render2DUtil.rect(this.x, this.y, this.width, this.height)
                .color(ColorUtil.multiplyAlpha(ColorUtil.rgba(22, 22, 22, 230), alpha))
                .radius(this.style.radius() * unit)
                .border(Math.max(0.5F, 0.5F * unit), ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_MEDIUM, alpha))
                .draw();

        float cell = CELL * unit;
        float gap = CELL_GAP * unit;
        float cellsX = this.x + PADDING * unit;
        float cellsY = this.y + PADDING * unit;

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Render2DUtil.rect(cellsX + slot * (cell + gap), cellsY, cell, cell)
                    .color(ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_SMALL, alpha))
                    .radius(CHIP_RADIUS * unit)
                    .draw();
        }

        float selection = smoothSelection(player.getInventory().getSelectedSlot());
        float selectionX = cellsX + selection * (cell + gap);
        Render2DUtil.rect(selectionX, cellsY, cell, cell)
                .color(ColorUtil.multiplyAlpha(ColorUtil.withAlpha(Theme.getAccent(), 40), alpha))
                .radius(CHIP_RADIUS * unit)
                .border(Math.max(0.5F, 1.0F * unit), ColorUtil.multiplyAlpha(Theme.getAccent(), alpha))
                .draw();

        drawXpLevel(mc, player, unit, alpha);
        drawSelectedItemName(mc, player, unit, alpha);

        ItemStack offhand = player.getOffhandItem();
        float offhandX = Float.NaN;
        if (!offhand.isEmpty()) {
            float capsuleSize = OFFHAND_SIZE * unit;
            float capsuleY = this.y + (this.height - capsuleSize) / 2.0F;
            boolean left = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
            float capsuleX = left
                    ? this.x - OFFHAND_GAP * unit - capsuleSize
                    : this.x + this.width + OFFHAND_GAP * unit;
            Render2DUtil.rect(capsuleX, capsuleY, capsuleSize, capsuleSize)
                    .color(ColorUtil.multiplyAlpha(ColorUtil.rgba(22, 22, 22, 230), alpha))
                    .radius(OFFHAND_RADIUS * unit)
                    .border(Math.max(0.5F, 0.5F * unit), ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_MEDIUM, alpha))
                    .draw();
            offhandX = capsuleX + (capsuleSize - ITEM_SIZE * unit) / 2.0F;
        }

        if (alpha < 0.75F) {
            return;
        }
        GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
        if (extractor == null) {
            return;
        }
        Render2DUtil.flush();

        float itemInset = (cell - ITEM_SIZE * unit) / 2.0F;
        float itemY = cellsY + itemInset;
        int seed = 1;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            drawItem(mc, extractor, player, player.getInventory().getItem(slot),
                    cellsX + slot * (cell + gap) + itemInset, itemY, unit, seed++);
        }
        if (!offhand.isEmpty()) {
            drawItem(mc, extractor, player, offhand,
                    offhandX, this.y + (this.height - ITEM_SIZE * unit) / 2.0F, unit, seed);
        }
    }

    public float decorationOffset(Minecraft mc) {
        if (Float.isNaN(this.renderedTop)) {
            return 0.0F;
        }
        return this.renderedTop - (mc.getWindow().getGuiScaledHeight() - VANILLA_HOTBAR_HEIGHT)
                + bandDrop(mc.player);
    }

    private static float bandDrop(LocalPlayer player) {
        boolean bandFree = player != null
                && player.jumpableVehicle() == null
                && !player.connection.getWaypointManager().hasWaypoints();
        return bandFree ? STATS_ROW_DROP : 0.0F;
    }

    public float statsHalfWidth() {
        return this.statsHalfWidth;
    }

    private void drawXpLevel(Minecraft mc, LocalPlayer player, float unit, float alpha) {
        if (!mc.gameMode.hasExperience() || player.experienceLevel <= 0) {
            return;
        }

        MsdfFont font = UiFonts.sfProDisplay();
        String level = Integer.toString(player.experienceLevel);
        float textSize = XP_TEXT_SIZE * unit;
        float centerX = this.x + this.width / 2.0F;
        float centerY = this.y - VANILLA_STATS_ROW_CENTER + bandDrop(player);

        Render2DUtil.text(centerX, font.centeredTextY(centerY, textSize), textSize, level)
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(XP_COLOR, alpha))
                .align(TextAlign.CENTER)
                .draw();
    }

    private void drawSelectedItemName(Minecraft mc, LocalPlayer player, float unit, float panelAlpha) {
        float alpha = highlightAlpha(mc, player) * panelAlpha;
        if (alpha <= 0.01F || this.lastHighlight.isEmpty()) {
            return;
        }

        String name = this.lastHighlight.getHoverName().getString();
        MsdfFont font = UiFonts.sfProDisplay();
        float textSize = NAME_TEXT_SIZE * unit;
        float centerX = this.x + this.width / 2.0F;
        float nameOffset = mc.gameMode.canHurtPlayer() ? VANILLA_NAME_OFFSET : VANILLA_NAME_OFFSET_CREATIVE;
        float centerY = this.y - nameOffset + bandDrop(player) + textSize / 2.0F;

        TextColor rarityColor = TextColor.fromLegacyFormat(this.lastHighlight.getRarity().color());
        int color = rarityColor != null ? 0xFF000000 | rarityColor.getValue() : Theme.Colors.TEXT_TITLE;

        Render2DUtil.text(centerX, font.centeredTextY(centerY, textSize), textSize, name)
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(color, alpha))
                .align(TextAlign.CENTER)
                .draw();
    }

    private float highlightAlpha(Minecraft mc, LocalPlayer player) {
        ItemStack selected = player.getInventory().getSelectedItem();
        long now = System.currentTimeMillis();
        if (selected.isEmpty()) {
            this.highlightEnd = 0L;
        } else if (this.lastHighlight.isEmpty()
                || !selected.is(this.lastHighlight.getItem())
                || !selected.getHoverName().equals(this.lastHighlight.getHoverName())) {
            this.highlightEnd = now + (long) (2000.0 * mc.options.notificationDisplayTime().get());
        }
        this.lastHighlight = selected;
        return MathUtil.clamp01((this.highlightEnd - now) / (float) NAME_FADE_MS);
    }

    private void drawItem(Minecraft mc, GuiGraphicsExtractor extractor, LocalPlayer player,
                          ItemStack stack, float x, float y, float unit, int seed) {
        if (stack.isEmpty()) {
            return;
        }

        Matrix3x2fStack pose = extractor.pose();
        float guiScale = (float) mc.getWindow().getGuiScale();

        float intendedSize = ITEM_SIZE * unit;
        float snappedSize;
        if (intendedSize < 15.99F) {
            snappedSize = intendedSize;
        } else {
            float texelPixels = Math.max(1.0F, Math.round(intendedSize * guiScale / 16.0F));
            snappedSize = texelPixels * 16.0F / guiScale;
        }
        float scale = snappedSize / 16.0F;
        float centerOffset = (intendedSize - snappedSize) / 2.0F;
        float itemX = Math.round((x + centerOffset) * guiScale) / guiScale;
        float itemY = Math.round((y + centerOffset) * guiScale) / guiScale;

        pose.pushMatrix();
        pose.translate(itemX, itemY);
        pose.scale(scale);

        float pop = popProgress(stack);
        if (pop > 0.0F) {
            float squeeze = 1.0F + pop / 5.0F;
            pose.pushMatrix();
            pose.translate(8.0F, 12.0F);
            pose.scale(1.0F / squeeze, (squeeze + 1.0F) / 2.0F);
            pose.translate(-8.0F, -12.0F);
        }
        extractor.item(player, stack, 0, 0, seed);
        if (pop > 0.0F) {
            pose.popMatrix();
        }

        extractor.itemDecorations(mc.font, stack, 0, 0, "");
        drawItemCount(stack);
        pose.popMatrix();
    }

    private static void drawItemCount(ItemStack stack) {
        if (stack.getCount() == 1) {
            return;
        }

        String amount = Integer.toString(stack.getCount());
        MsdfFont font = UiFonts.sfProDisplay();
        float textY = ITEM_COUNT_BOTTOM - font.textHeight(ITEM_COUNT_TEXT_SIZE);
        Render2DUtil.text(ITEM_COUNT_RIGHT, textY, ITEM_COUNT_TEXT_SIZE, amount)
                .font(font)
                .style(UiFontStyle.SEMIBOLD)
                .color(0xFFFFFFFF)
                .align(TextAlign.RIGHT)
                .draw();
    }

    private static float popProgress(ItemStack stack) {
        if (stack.getPopTime() <= 0) {
            return 0.0F;
        }
        DeltaTracker deltaTracker = RenderContext.currentDeltaTracker();
        float partial = deltaTracker != null ? deltaTracker.getGameTimeDeltaPartialTick(false) : 0.0F;
        return stack.getPopTime() - partial;
    }

    private float smoothSelection(int selectedSlot) {
        long now = System.currentTimeMillis();
        float delta = this.lastFrameTime == 0L ? 0.016F : Math.min(100L, now - this.lastFrameTime) / 1000.0F;
        this.lastFrameTime = now;

        if (!this.slotInitialized) {
            this.slotInitialized = true;
            this.smoothedSlot = selectedSlot;
        }
        float step = MathUtil.clamp01(delta * 20.0F);
        this.smoothedSlot += (selectedSlot - this.smoothedSlot) * step;
        if (Math.abs(this.smoothedSlot - selectedSlot) < 0.005F) {
            this.smoothedSlot = selectedSlot;
        }
        return this.smoothedSlot;
    }
}
