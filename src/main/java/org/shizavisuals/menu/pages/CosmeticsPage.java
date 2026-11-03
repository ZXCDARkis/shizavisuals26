package org.shizavisuals.menu.pages;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.impl.visual.CosmeticsFeature;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.menu.core.MenuPage;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.PageComponent;
import org.shizavisuals.menu.ui.controls.MarqueeText;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.cosmetic.PulseCosmeticCatalog;
import org.shizavisuals.utils.render.cosmetic.PulseCosmeticCategory;
import org.shizavisuals.utils.render.cosmetic.PulsePreview;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cosmetics tab: collapsed category rows that expand into a scrollable grid of
 * 3D previews. Picking a preview writes the choice straight into the cosmetic
 * mode setting, so the feature itself no longer needs its own settings rows.
 */
public final class CosmeticsPage extends PageComponent {
    private static final int ROW_X = 16;
    private static final int ROW_WIDTH = 976;
    private static final int FIRST_ROW_Y = 234;
    private static final int ROW_HEIGHT = 52;
    private static final int ROW_GAP = 8;
    private static final int HEADER_HEIGHT = Theme.Sizes.MODULE_CARD_HEADER_HEIGHT;
    private static final int PADDING = Theme.Sizes.MODULE_CARD_PADDING;
    private static final int TILE_SIZE = 104;
    private static final int TILE_GAP = 10;
    private static final int MAX_LIST_HEIGHT = 292;
    private static final int LIST_TOP_PADDING = 14;
    private static final int LIST_ROW_HEIGHT = TILE_SIZE + TILE_GAP;

    private final List<CategorySection> sections = new ArrayList<>();

    public CosmeticsPage() {
        for (PulseCosmeticCategory category : PulseCosmeticCategory.values()) {
            this.sections.add(new CategorySection(category));
        }
    }

@Override
    protected void onLayout() {
        int y = FIRST_ROW_Y;
        for (CategorySection section : this.sections) {
            section.place(this, ROW_X, y, ROW_WIDTH, this.mouseX, this.mouseY);
            // Collapsed rows are 52 tall, but an expanded one has to push the
            // sections below it down, otherwise the tile grid overlaps the next
            // category header.
            y += ROW_HEIGHT + section.expandedHeight();
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY) {
        if (this.progress <= 0.001F) {
            return false;
        }
        for (CategorySection section : this.sections) {
            if (section.handleClick(mouseX, mouseY)) {
                return true;
            }
        }
        return contentContains(mouseX, mouseY);
    }

    public void handleScroll(int mouseX, int mouseY, double vertical) {
        if (!contentContains(mouseX, mouseY)) {
            return;
        }
        for (CategorySection section : this.sections) {
            if (section.listScrollTarget(mouseX, mouseY) > 0.0F) {
                section.scrollList(vertical);
                return;
            }
        }
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (this.progress <= 0.001F) {
            return;
        }
        pageHeader(
                MenuText.ui("Cosmetics"),
                MenuText.ui("Open a category and pick a cosmetic.")
        );

        Render2DUtil.pushScissor(x(), sy(200), width(), y() + height() - sy(200));
        for (CategorySection section : this.sections) {
            section.renderCollapsed(minecraft, guiGraphicsExtractor);
            section.renderExpanded(minecraft, guiGraphicsExtractor);
        }
        Render2DUtil.popScissor();
    }

    private float alpha() {
        return this.progress;
    }

    private ModeSetting setting(PulseCosmeticCategory category) {
        CosmeticsFeature feature = FeatureManager.INSTANCE.getFeature(CosmeticsFeature.class);
        if (feature == null) {
            return null;
        }
        if (!feature.isEnabled()) {
            feature.setEnabled(true);
        }
        return feature.setting(category);
    }

    private void drawPanel(int y, int height, float alpha, int radius) {
        Render2DUtil.rect(sx(ROW_X), sy(y), px(ROW_WIDTH), px(height))
                .color(ColorUtil.multiplyAlpha(Theme.Colors.MODULE_CARD, alpha))
                .radius(px(radius))
                .draw();
    }

private final class CategorySection {
        private final org.shizavisuals.menu.ui.SmoothScroll listScrollState =
                new org.shizavisuals.menu.ui.SmoothScroll();
        private final PulseCosmeticCategory category;
        private final List<PulseCosmeticCatalog.PulseCosmetic> cosmetics;
        private final Animation expand = new Animation(180L, Animation.Easing.EASE_OUT_QUAD);
        private final Map<String, MarqueeText> labels = new HashMap<>();
        private int rowY;
        private boolean open;

        private CategorySection(PulseCosmeticCategory category) {
            this.category = category;
            this.cosmetics = PulseCosmeticCatalog.byCategory(category);
        }

        private void place(Component owner, int rowX, int top, int width, int mouseX, int mouseY) {
            this.rowY = top;
        }

        private String title() {
            return switch (this.category) {
                case WINGS -> MenuText.ui("Wings");
                case HAT -> MenuText.ui("Hats");
                case BODYWEAR -> MenuText.ui("Bodywear");
                case PET -> MenuText.ui("Pets");
            };
        }

        private String selectedLabel() {
            ModeSetting setting = setting(this.category);
            if (setting == null) {
                return MenuText.option("Unavailable");
            }
            String value = setting.getValue();
            return value == null || value.equals(PulseCosmeticCatalog.NONE)
                    ? MenuText.option("None")
                    : MenuText.option(value);
        }

        private int selectedIndex() {
            ModeSetting setting = setting(this.category);
            if (setting == null) {
                return -1;
            }
            return this.cosmetics.indexOf(
                    PulseCosmeticCatalog.resolve(this.category, setting.getValue()));
        }

        private boolean headerHit(int mouseX, int mouseY) {
            return hit(mouseX, mouseY, ROW_X, this.rowY, ROW_WIDTH, ROW_HEIGHT);
        }

        private boolean handleClick(int mouseX, int mouseY) {
            if (alpha() <= 0.001F) {
                return false;
            }
            if (this.open && tileClick(mouseX, mouseY)) {
                return true;
            }
            if (headerHit(mouseX, mouseY)) {
                this.open = !this.open;
                this.expand.animate(this.expand.getValue(), this.open ? 1.0F : 0.0F, 180L,
                        Animation.Easing.EASE_OUT_QUAD);
                if (!this.open) {
                    this.listScrollState.scroll(0.0D, 0.0F);
                }
                return true;
            }
            return false;
        }

private float progress() {
            return this.expand.getValue();
        }

        /** Extra design height this section currently occupies below its header. */
        private float expandedHeight() {
            float p = progress();
            if (p <= 0.001F || this.cosmetics.isEmpty()) {
                return 0.0F;
            }
            return (ROW_GAP + listHeight() * p) + ROW_GAP;
        }

        private int listHeight() {
            if (this.cosmetics.isEmpty()) {
                return 0;
            }
            int columns = Math.max(1, (ROW_WIDTH - PADDING * 2) / (TILE_SIZE + TILE_GAP));
            int rows = (this.cosmetics.size() + columns - 1) / columns;
            return LIST_TOP_PADDING * 2 + rows * LIST_ROW_HEIGHT - TILE_GAP + 12;
        }

        private float listScroll() {
            return this.listScrollState.update(maxScroll());
        }

        private float maxScroll() {
            return Math.max(0.0F, listHeight() - maxVisible());
        }

        /** Expanded list viewport, in design units, clamped so it never eats the page. */
        private float maxVisible() {
            return Math.min(MAX_LIST_HEIGHT,
                    designY(y() + height()) - (this.rowY + ROW_HEIGHT + ROW_GAP) - 16.0F);
        }

        private float listScrollTarget(int mouseX, int mouseY) {
            if (progress() <= 0.001F || this.cosmetics.isEmpty()) {
                return 0.0F;
            }
            int listY = this.rowY + ROW_HEIGHT + ROW_GAP;
            return mouseY >= sy(listY) ? maxScroll() : 0.0F;
        }

        private void scrollList(double vertical) {
            this.listScrollState.scroll(vertical, maxScroll());
        }

        private int columns() {
            return Math.max(1, (ROW_WIDTH - PADDING * 2) / (TILE_SIZE + TILE_GAP));
        }

        private boolean tileClick(int mouseX, int mouseY) {
            float offset = listScroll();
            int columns = columns();
            int gridX = ROW_X + PADDING;
            int gridY = this.rowY + ROW_HEIGHT + ROW_GAP + LIST_TOP_PADDING;
            for (int index = 0; index < this.cosmetics.size(); index++) {
                int column = index % columns;
                int row = index / columns;
                int tileX = gridX + column * (TILE_SIZE + TILE_GAP);
                int tileY = (int) (gridY + row * LIST_ROW_HEIGHT - offset);
                if (!hit(mouseX, mouseY, tileX, tileY, TILE_SIZE, TILE_SIZE)) {
                    continue;
                }
                PulseCosmeticCatalog.PulseCosmetic cosmetic = this.cosmetics.get(index);
                ModeSetting mode = setting(this.category);
                if (mode != null) {
                    mode.setValue(cosmetic.mode());
                }
                return true;
            }
            return false;
        }

        private void renderCollapsed(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
            float alpha = alpha();
            boolean hovered = headerHit(CosmeticsPage.this.mouseX, CosmeticsPage.this.mouseY);
            CosmeticsPage.this.drawPanel(this.rowY, ROW_HEIGHT, alpha, Theme.Sizes.MODULE_CARD_RADIUS);

            int textX = ROW_X + PADDING;
            int centerY = (int) centeredTextY(CosmeticsPage.this.sy(this.rowY + ROW_HEIGHT / 2.0F), px(13.0F));
            label(textX, centerY - px(9.0F), 13.0F, title(), Theme.Colors.TEXT_TITLE, alpha,
                    UiFontStyle.SEMIBOLD);
            label(textX, centerY + px(9.0F), 10.0F,
                    selectedLabel() + "  (" + this.cosmetics.size() + ")",
                    Theme.Colors.SECONDARY, alpha, UiFontStyle.MEDIUM);

            int badgeX = ROW_X + ROW_WIDTH - PADDING - 24;
            int badgeY = this.rowY + ROW_HEIGHT / 2 - 10;
            Render2DUtil.rect(sx(badgeX), sy(badgeY), px(24.0F), px(20.0F))
                    .color(ColorUtil.multiplyAlpha(
                            hovered ? Theme.Colors.CONTROL_HOVER : Theme.Colors.CONTROL, alpha))
                    .radius(px(6.0F))
                    .draw();
            float chevron = this.open ? 1.0F : -1.0F;
            int cy = badgeY + 10 + (int) Math.round(3.0F * chevron);
            for (int i = -1; i <= 1; i++) {
                Render2DUtil.rect(sx(badgeX + 12 + i * 4.0F), sy(cy - i), px(3.0F), px(3.0F))
                        .color(ColorUtil.multiplyAlpha(Theme.Colors.ICON, alpha))
                        .radius(px(1.0F))
                        .draw();
            }
        }

        private void renderExpanded(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
            float expandProgress = progress();
            if (expandProgress <= 0.001F || this.cosmetics.isEmpty()) {
                return;
            }
            float alpha = alpha();
            int listY = this.rowY + ROW_HEIGHT + ROW_GAP;
            int fullHeight = listHeight();
            int shown = (int) Math.round(fullHeight * expandProgress);
            CosmeticsPage.this.drawPanel(listY, Math.max(shown, ROW_HEIGHT), alpha,
                    Theme.Sizes.MODULE_CARD_RADIUS);

            Render2DUtil.pushScissor(sx(ROW_X), sy(listY), px(ROW_WIDTH), px(Math.max(1.0F, shown)));
            float offset = listScroll();
            int columns = columns();
            int gridX = ROW_X + PADDING;
            int gridY = listY + LIST_TOP_PADDING;
            int selected = selectedIndex();
            for (int index = 0; index < this.cosmetics.size(); index++) {
                PulseCosmeticCatalog.PulseCosmetic cosmetic = this.cosmetics.get(index);
                int column = index % columns;
                int row = index / columns;
                int tileX = gridX + column * (TILE_SIZE + TILE_GAP);
                int tileY = (int) (gridY + row * LIST_ROW_HEIGHT - offset);
                if (tileY > listY + shown || tileY + TILE_SIZE < listY) {
                    continue;
                }
                renderTile(cosmetic, tileX, tileY, index == selected, alpha);
            }
            Render2DUtil.popScissor();
        }

        private void renderTile(PulseCosmeticCatalog.PulseCosmetic cosmetic,
                                int tileX, int tileY, boolean selected, float alpha) {
            boolean hovered = hit(CosmeticsPage.this.mouseX, CosmeticsPage.this.mouseY, tileX, tileY, TILE_SIZE, TILE_SIZE);
            int background = selected ? Theme.Colors.CONTROL_HOVER
                    : hovered ? Theme.Colors.CONTROL : Theme.Colors.BACKGROUND_SURFACE_S;
            Render2DUtil.rect(sx(tileX), sy(tileY), px(TILE_SIZE), px(TILE_SIZE))
                    .color(ColorUtil.multiplyAlpha(background, alpha))
                    .radius(px(8.0F))
                    .draw();
            if (selected) {
                Render2DUtil.rect(sx(tileX), sy(tileY), px(TILE_SIZE), px(TILE_SIZE))
                        .color(ColorUtil.multiplyAlpha(Theme.getAccent(), alpha * 0.9F))
                        .radius(px(8.0F))
                        .border(px(1.5F), ColorUtil.multiplyAlpha(Theme.getAccent(), alpha))
                        .draw();
            }

            int previewSize = TILE_SIZE - 22;
            int previewX = tileX + (TILE_SIZE - previewSize) / 2;
            int previewY = tileY + 6;
            PulsePreview.render(cosmetic, sx(previewX), sy(previewY), px(previewSize), alpha * progress());

            String title = MenuText.option(cosmetic.title());
            MarqueeText text = this.labels.computeIfAbsent(cosmetic.mode(),
                    ignored -> new MarqueeText(() -> MenuText.option(cosmetic.title())));
            text.placeAt(CosmeticsPage.this, sx(tileX + 6), sy(tileY + TILE_SIZE - 15),
                            px(TILE_SIZE - 12.0F), px(12.0F), CosmeticsPage.this.mouseX, CosmeticsPage.this.mouseY)
                    .style(9.5F, UiFontStyle.MEDIUM,
                            ColorUtil.multiplyAlpha(
                                    selected ? Theme.Colors.PRIMARY : Theme.Colors.SECONDARY, alpha),
                            alpha)
                    .align(org.shizavisuals.utils.render.gui.TextAlign.CENTER)
                    .render(Minecraft.getInstance(), null);
        }

        private void label(int x, float y, float size, String value, int color, float alpha,
                           UiFontStyle style) {
            Render2DUtil.text(sx(x), y, px(size), value)
                    .style(style)
                    .color(ColorUtil.multiplyAlpha(color, alpha))
                    .draw();
        }
    }
}