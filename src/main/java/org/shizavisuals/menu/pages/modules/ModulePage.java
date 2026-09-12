package org.shizavisuals.menu.pages.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.MultiSelectSetting;
import org.shizavisuals.feature.setting.Setting;
import org.shizavisuals.menu.core.MenuOverlayState;
import org.shizavisuals.menu.core.MenuPage;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.MenuClipboard;
import org.shizavisuals.menu.ui.controls.SearchInputComponent;
import org.shizavisuals.menu.ui.SmoothScroll;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.shizavisuals.utils.render.Textures;

public final class ModulePage extends Component {
    private static final int PAGE_PADDING = 16;
    private static final int COLUMN_GAP = 16;
    private static final int CARD_GAP = 12;
    private static final int CATEGORY_DOCK_PADDING = 12;
    private static final int CATEGORY_DOCK_GAP = 12;
    private static final int CATEGORY_DOCK_BUTTON_SIZE = 32;
    private static final int CATEGORY_DOCK_HEIGHT = 56;
    private static final int CATEGORY_DOCK_Y = 640 - CATEGORY_DOCK_HEIGHT / 2;
    private static final int CATEGORY_DOCK_RADIUS = 12;
    private static final int CATEGORY_DOCK_BUTTON_RADIUS = 8;
    private static final float CATEGORY_DOCK_BORDER_WIDTH = 0.5F;
    private static final int CATEGORY_ICON_SIZE = 16;
    private static final int SEARCH_RESULTS_X = 256;
    private static final int SEARCH_RESULTS_Y = 370;
    private static final int SEARCH_RESULTS_WIDTH = 512;
    private static final int SEARCH_RESULT_HEIGHT = 34;
    private static final int SEARCH_RESULT_GAP = 8;
    private static final int SEARCH_RESULT_RADIUS = 10;
    private static final int SEARCH_RESULT_PADDING_X = 14;
    private static final int SEARCH_RESULT_ICON_SIZE = 12;
    private static final int SEARCH_RESULT_ICON_TEXT_GAP = 8;
    private static final float SEARCH_RESULT_TEXT_SIZE = 12.0F;
    private static final Identifier[] CATEGORY_ICONS = {
            Textures.Icons.EYE,
            Textures.Icons.BOXES
    };

    private final List<Component> children = new ArrayList<>();
    private final List<ModuleCard> cards = new ArrayList<>();
    private final StringBuilder searchQuery = new StringBuilder();
    private final SearchInputComponent searchInput = new SearchInputComponent(this.searchQuery::toString, this::searchSuggestionSuffix);
    private boolean searchAllSelected;
    private final Animation categoryAnimation = new Animation(150L, Animation.Easing.EASE_OUT_QUAD);
    private final Animation categoryIndicatorAnimation = new Animation(180L, Animation.Easing.EASE_OUT_QUAD);
    private MenuOverlayState state;
    private MenuPage displayedPage = MenuPage.NONE;
    private FeatureCategory selectedCategory = FeatureCategory.VISUAL;
    private FeatureCategory cardsCategory;
    private boolean searchFocused;
    private int mouseX;
    private int mouseY;
    private float transitionProgress;
    private int visualOffsetX;
    private float visualAlpha = 1.0F;
    private float dockProgress = 1.0F;
    private float categoryDockVisualY = CATEGORY_DOCK_Y;
    private final SmoothScroll scroll = new SmoothScroll();
    private float scrollOffset;
    private int maxContentBottom = 560;
    private int[] cardColumns;
    private Feature triggeredFeature;

    public ModulePage() {
        this.categoryAnimation.animate(1.0F, 1.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
        FeatureCategory[] visible = getVisibleCategories();
        this.selectedCategory = visible.length > 0 ? visible[0] : FeatureCategory.VISUAL;
        float index = categoryIndex(this.selectedCategory);
        this.categoryIndicatorAnimation.animate(index, index, 0L, Animation.Easing.EASE_OUT_QUAD);
    }

    public void layout(Component frame, MenuOverlayState state, int mouseX, int mouseY) {
        attach(frame, frame.x(), frame.y(), frame.width(), frame.height());
        this.state = state;
        this.displayedPage = state.displayPage();
        this.transitionProgress = state.contentProgress();
        if (state.page() != MenuPage.SEARCH) {
            this.searchFocused = false;
            this.searchAllSelected = false;
        }
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        this.dockProgress = state.isClosing()
                ? 1.0F
                : MathUtil.clamp01((state.openProgress() - 0.35F) / 0.65F);
        float screenBottomDesign = designY(Minecraft.getInstance().getWindow().getGuiScaledHeight());
        this.categoryDockVisualY = CATEGORY_DOCK_Y
                + (1.0F - this.dockProgress) * Math.max(0.0F, screenBottomDesign - CATEGORY_DOCK_Y);
        updateCategoryVisuals();
        this.scrollOffset = this.scroll.update(Math.max(0, this.maxContentBottom - 560));
        ensureCards();
        layoutCards();
        this.searchInput.place(this, 256, 310, 512, 48, mouseX, mouseY)
                .alpha(this.transitionProgress)
                .focused(this.searchFocused)
                .selected(this.searchAllSelected);
    }

    public boolean handleMouseButton(int mouseX, int mouseY, int button) {
        if (this.displayedPage == MenuPage.SEARCH && this.transitionProgress > 0.05F) {
            if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT && this.searchInput.contains(mouseX, mouseY)) {
                this.searchFocused = !this.searchFocused;
                this.searchAllSelected = false;
                return true;
            }
            Feature result = resultAt(mouseX, mouseY);
            if (result != null) {
                openSearchResult(result);
            }
            return contains(mouseX, mouseY) && mouseY >= y() + px(Theme.Sizes.HEADER_HEIGHT);
        }
        if (this.displayedPage != MenuPage.NONE || mouseY < y() + px(Theme.Sizes.HEADER_HEIGHT)) {
            return false;
        }

        FeatureCategory category = categoryAt(mouseX, mouseY);
        if (categoryDockContains(mouseX, mouseY)) {
            if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && category != null
                    && category != this.selectedCategory) {
                float from = this.categoryIndicatorAnimation.getValue();
                this.selectedCategory = category;
                this.categoryIndicatorAnimation.animate(from, categoryIndex(category), 180L, Animation.Easing.EASE_OUT_QUAD);
                this.categoryAnimation.animate(0.0F, 1.0F, 150L, Animation.Easing.EASE_OUT_QUAD);
                rebuildCards();
            }
            return true;
        }

        for (ModuleCard card : this.cards) {
            if (card.handleBindPopupClick(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (!contains(mouseX, mouseY)) {
            return false;
        }

        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            for (ModuleCard card : this.cards) {
                if (!card.handleMiddleClick(mouseX, mouseY)) {
                    continue;
                }
                closeBindPopupsExcept(card);
                return true;
            }
            return contains(mouseX, mouseY);
        }

        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            for (ModuleCard card : this.cards) if (card.handleRightClick(mouseX, mouseY)) return true;
            return contains(mouseX, mouseY);
        }

        if (button != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return contains(mouseX, mouseY);
        }

        for (ModuleCard card : this.cards) {
            if (card.handleDropdownPopupClick(mouseX, mouseY)) {
                return true;
            }
        }

        for (ModuleCard card : this.cards) {
            if (card.handleClick(mouseX, mouseY)) {
                return true;
            }
        }
        return true;
    }

    public void drag(int mouseX) {
        for (ModuleCard card : this.cards) {
            card.drag(mouseX);
        }
    }

    public void releasePointer() {
        for (ModuleCard card : this.cards) {
            card.releasePointer();
        }
    }

    public boolean handleKey(int key) {
        if (this.displayedPage == MenuPage.SEARCH) {
            if (this.searchFocused && MenuClipboard.shortcutDown()) {
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_A) {
                    this.searchAllSelected = !this.searchQuery.isEmpty();
                    return true;
                }
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_C) {
                    if (this.searchAllSelected) {
                        MenuClipboard.set(this.searchQuery.toString());
                    }
                    return true;
                }
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_X) {
                    if (this.searchAllSelected) {
                        MenuClipboard.set(this.searchQuery.toString());
                        replaceSearchText("");
                    }
                    return true;
                }
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
                    String base = this.searchAllSelected
                            ? ""
                            : this.searchQuery.toString();
                    replaceSearchText(base + MenuClipboard.get());
                    return true;
                }
            }
            if (this.searchFocused && key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
                acceptSearchSuggestion();
                return true;
            }
            if (this.searchFocused && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                List<Feature> results = searchResults();
                if (!results.isEmpty()) {
                    openSearchResult(results.getFirst());
                }
                return true;
            }
            if (this.searchFocused
                    && key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                backspace();
                return true;
            }
            return false;
        }
        if (this.displayedPage != MenuPage.NONE) {
            return false;
        }
        for (ModuleCard card : this.cards) {
            if (card.handleKey(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCapturingBind() {
        if (this.displayedPage != MenuPage.NONE) {
            return false;
        }
        for (ModuleCard card : this.cards) {
            if (card.isCapturingBind()) {
                return true;
            }
        }
        return false;
    }

    public boolean handleCharacter(int codePoint) {
        if (this.displayedPage != MenuPage.NONE) {
            return false;
        }
        for (ModuleCard card : this.cards) {
            if (card.handleCharacter(codePoint)) {
                return true;
            }
        }
        return false;
    }

    public void handleScroll(int mouseX, int mouseY, double vertical) {
        if (categoryDockContains(mouseX, mouseY)) {
            return;
        }
        if (this.displayedPage == MenuPage.NONE && contains(mouseX, mouseY) && mouseY > y() + px(Theme.Sizes.HEADER_HEIGHT)) {
            for (ModuleCard card : this.cards) {
                if (card.handleScroll(mouseX, mouseY, vertical)) {
                    return;
                }
            }
            this.scroll.scroll(vertical, Math.max(0, this.maxContentBottom - 560));

            for (ModuleCard card : this.cards) {
                card.closeOverlays();
            }
        }
    }

    public boolean isSearchOpen() {
        return this.state != null && this.state.page() == MenuPage.SEARCH;
    }

    public boolean isSearchFocused() {
        return isSearchOpen() && this.searchFocused;
    }

    public void appendCodePoint(int codePoint) {
        if (!isSearchFocused()
                || MenuClipboard.shortcutDown()
                || Character.isISOControl(codePoint)) {
            return;
        }
        if (this.searchAllSelected) {
            this.searchQuery.setLength(0);
            this.searchAllSelected = false;
        }
        if (this.searchQuery.codePointCount(0, this.searchQuery.length()) >= 40) {
            return;
        }
        this.searchQuery.appendCodePoint(codePoint);
    }

    public void backspace() {
        if (!isSearchFocused() || this.searchQuery.isEmpty()) {
            return;
        }
        if (this.searchAllSelected) {
            replaceSearchText("");
            return;
        }
        int lastCodePoint = this.searchQuery.codePointBefore(this.searchQuery.length());
        this.searchQuery.delete(this.searchQuery.length() - Character.charCount(lastCodePoint), this.searchQuery.length());
    }

    private void replaceSearchText(String value) {
        String resolved = value == null ? "" : value.replaceAll("\\R", " ");
        int codePoints = resolved.codePointCount(0, resolved.length());
        if (codePoints > 40) {
            resolved = resolved.substring(0, resolved.offsetByCodePoints(0, 40));
        }
        this.searchQuery.setLength(0);
        this.searchQuery.append(resolved);
        this.searchAllSelected = false;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (this.displayedPage != MenuPage.NONE && this.displayedPage != MenuPage.SEARCH) {
            return;
        }

        updateCategoryVisuals();
        Render2DUtil.pushScissor(x(), y() + px(Theme.Sizes.HEADER_HEIGHT), width(), height() - px(Theme.Sizes.HEADER_HEIGHT));
        for (Component child : this.children) child.render(minecraft, guiGraphicsExtractor);
        if (this.children.isEmpty()) {
            textCentered(512, 290, 17,
                    MenuText.ui("No modules in this category yet"),
                    Theme.Colors.PRIMARY);
            textCentered(512, 320, 11,
                    MenuText.ui("Modules registered in this category will appear here automatically."),
                    Theme.Colors.SECONDARY_DARK);
        }
        Render2DUtil.popScissor();

        for (ModuleCard card : this.cards) card.renderOverlay(minecraft, guiGraphicsExtractor);
        this.visualAlpha = 1.0F;
        this.visualOffsetX = 0;
        renderCategoryDock();

        if (this.displayedPage == MenuPage.SEARCH && this.transitionProgress > 0.001F) {
            Render2DUtil.flush();
            guiGraphicsExtractor.nextStratum();
            renderSearch(minecraft, guiGraphicsExtractor);
        }
    }

    private void ensureCards() {
        if (this.cardsCategory != this.selectedCategory) {
            rebuildCards();
        }
    }

    private void rebuildCards() {
        this.cards.clear();
        for (Feature feature : FeatureManager.INSTANCE.getFeatures(this.selectedCategory)) {
            this.cards.add(new ModuleCard(feature));
        }
        this.cardsCategory = this.selectedCategory;

        int[] tops = computeMasonry();
        if (this.triggeredFeature == null) {
            return;
        }
        for (int index = 0; index < this.cards.size(); index++) {
            ModuleCard card = this.cards.get(index);
            if (card.feature() != this.triggeredFeature) {
                continue;
            }
            card.flashHighlight();
            float maxScroll = Math.max(0.0F, this.maxContentBottom - 560.0F);

            float scrollTarget = tops[index] - 68.0F - (560.0F - card.designHeight()) / 2.0F;
            this.scroll.setTarget(net.minecraft.util.Mth.clamp(scrollTarget, 0.0F, maxScroll));
            break;
        }
        this.triggeredFeature = null;
    }

    private int[] computeMasonry() {
        int[] tops = new int[this.cards.size()];
        if (this.cardColumns == null || this.cardColumns.length != this.cards.size()) {
            this.cardColumns = new int[this.cards.size()];
        }
        int[] columnBottom = {68, 68, 68};
        for (int index = 0; index < this.cards.size(); index++) {
            int column = shortestColumn(columnBottom);
            this.cardColumns[index] = column;
            tops[index] = columnBottom[column];
            columnBottom[column] += this.cards.get(index).designHeight() + CARD_GAP;
        }
        this.maxContentBottom = Math.max(columnBottom[0], Math.max(columnBottom[1], columnBottom[2]));
        return tops;
    }

    private void closeBindPopupsExcept(ModuleCard except) {
        for (ModuleCard card : this.cards) {
            if (card != except) {
                card.closeBindPopup();
            }
        }
    }

    private void layoutCards() {
        this.children.clear();
        int columnWidth = (1024 - PAGE_PADDING * 2 - COLUMN_GAP * 2) / 3;
        boolean dockHovered = categoryDockContains(this.mouseX, this.mouseY);
        int cardMouseX = dockHovered ? Integer.MIN_VALUE : this.mouseX;
        int cardMouseY = dockHovered ? Integer.MIN_VALUE : this.mouseY;
        int[] tops = computeMasonry();
        for (int index = 0; index < this.cards.size(); index++) {
            ModuleCard card = this.cards.get(index);
            int x = PAGE_PADDING + this.cardColumns[index] * (columnWidth + COLUMN_GAP);
            int y = tops[index] - Math.round(this.scrollOffset);
            card.place(this, x, y, columnWidth, this.visualAlpha, cardMouseX, cardMouseY, this.visualOffsetX,
                    CATEGORY_DOCK_Y);
            this.children.add(card);
        }
    }

    private void renderCategoryDock() {
        int dockX = categoryDockX();
        float dockY = this.categoryDockVisualY;
        Render2DUtil.rect(sx(dockX), sy(dockY), px(categoryDockWidth()), px(CATEGORY_DOCK_HEIGHT))
                .color(Theme.Colors.BACKGROUND_PRIMARY_50)
                .radius(px(CATEGORY_DOCK_RADIUS))
                .border(Math.max(0.5F, px(CATEGORY_DOCK_BORDER_WIDTH)), Theme.Colors.OUTLINES_MEDIUM)
                .blur(px(8.0F))
                .draw();

        FeatureCategory[] categories = getVisibleCategories();
        float indicatorIndex = this.categoryIndicatorAnimation.getValue();
        float indicatorX = categoryButtonX(dockX, 0) + indicatorIndex * (CATEGORY_DOCK_BUTTON_SIZE + CATEGORY_DOCK_GAP);
        float buttonY = dockY + CATEGORY_DOCK_PADDING;
        Render2DUtil.rect(sx(indicatorX), sy(buttonY), px(CATEGORY_DOCK_BUTTON_SIZE), px(CATEGORY_DOCK_BUTTON_SIZE))
                .color(Theme.Colors.OUTLINES_MEDIUM)
                .radius(px(CATEGORY_DOCK_BUTTON_RADIUS))
                .border(Math.max(0.5F, px(CATEGORY_DOCK_BORDER_WIDTH)), Theme.Colors.OUTLINES_MEDIUM)
                .draw();

        for (int index = 0; index < categories.length; index++) {
            int buttonX = categoryButtonX(dockX, index);
            boolean active = categories[index] == this.selectedCategory;
            float iconX = buttonX + (CATEGORY_DOCK_BUTTON_SIZE - CATEGORY_ICON_SIZE) / 2.0F;
            float iconY = buttonY + (CATEGORY_DOCK_BUTTON_SIZE - CATEGORY_ICON_SIZE) / 2.0F;
            texture(iconX, iconY, CATEGORY_ICON_SIZE, categoryIcon(categories[index]), active ? Theme.Colors.TEXT_TITLE : Theme.Colors.ICON);
        }
    }

    public void focusSearch() {
        this.searchFocused = true;
        this.searchAllSelected = true;
    }

    private void renderSearch(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        Render2DUtil.rect(x(), y(), width(), height())
                .color(ColorUtil.TRANSPARENT)
                .radius(px(12))
                .shadow(Theme.Colors.PANEL_SHADOW, px(8))
                .draw();
        Render2DUtil.rect(x(), y() + px(Theme.Sizes.HEADER_HEIGHT), width(), height() - px(Theme.Sizes.HEADER_HEIGHT))
                .color(ColorUtil.withAlpha(Theme.Colors.OVERLAY, Math.round(218.0F * this.transitionProgress)))
                .radius(0, 0, px(12), px(12))
                .blur(px(16.0F * this.transitionProgress))
                .draw();

        float titleScale = Animation.Easing.EASE_OUT_BACK.ease(this.transitionProgress);
        float subtitleScale = Animation.Easing.EASE_OUT_CUBIC.ease(this.transitionProgress);

        float titleSlideY = 250.0F - (1.0F - this.transitionProgress) * 16.0F;
        float subtitleSlideY = 282.0F - (1.0F - this.transitionProgress) * 10.0F;

        textCenteredScaled(512, titleSlideY, 22,
                MenuText.ui("Search"),
                Theme.Colors.PRIMARY,
                this.transitionProgress,
                titleScale);
        textCenteredScaled(512, subtitleSlideY, 11,
                MenuText.ui("Start typing to find a module or setting."),
                Theme.Colors.SECONDARY_DARK,
                this.transitionProgress,
                subtitleScale);
        this.searchInput.render(minecraft, guiGraphicsExtractor);

        float chipX = SEARCH_RESULTS_X;
        List<Feature> results = searchResults();
        for (int i = 0; i < results.size(); i++) {
            Feature feature = results.get(i);
            float chipWidth = searchResultWidth(feature);
            if (chipX + chipWidth > SEARCH_RESULTS_X + SEARCH_RESULTS_WIDTH) {
                break;
            }
            float staggerProgress = Math.clamp((this.transitionProgress - i * 0.06F) / 0.7F, 0.0F, 1.0F);
            float springBounce = Animation.Easing.EASE_OUT_BACK.ease(staggerProgress);
            float animY = SEARCH_RESULTS_Y + (1.0F - springBounce) * 12.0F;
            boolean hovered = hit(this.mouseX, this.mouseY, chipX, animY, chipWidth, SEARCH_RESULT_HEIGHT);
            renderSearchResult(feature, chipX, animY, chipWidth, hovered, this.transitionProgress * staggerProgress);
            chipX += chipWidth + SEARCH_RESULT_GAP;
        }
    }

    private void renderSearchResult(Feature feature, float x, float y, float width, boolean hovered, float cardAlpha) {
        int contentColor = hovered ? Theme.Colors.PRIMARY : Theme.Colors.ICON;
        rect(x, y, width, SEARCH_RESULT_HEIGHT,
                hovered ? Theme.Colors.OUTLINES_MEDIUM : Theme.Colors.OUTLINES_SMALL,
                SEARCH_RESULT_RADIUS,
                cardAlpha);
        float iconX = x + SEARCH_RESULT_PADDING_X;
        float iconY = y + (SEARCH_RESULT_HEIGHT - SEARCH_RESULT_ICON_SIZE) / 2.0F;
        texture(iconX, iconY, SEARCH_RESULT_ICON_SIZE, categoryIcon(feature.getCategory()), contentColor, cardAlpha);
        text(iconX + SEARCH_RESULT_ICON_SIZE + SEARCH_RESULT_ICON_TEXT_GAP,
                centeredTextY(y + SEARCH_RESULT_HEIGHT / 2.0F, SEARCH_RESULT_TEXT_SIZE),
                SEARCH_RESULT_TEXT_SIZE,
                feature.getName(),
                contentColor,
                cardAlpha,
                UiFontStyle.MEDIUM);
    }

    private FeatureCategory categoryAt(int mouseX, int mouseY) {
        if (this.dockProgress < 0.98F || !categoryDockContains(mouseX, mouseY)) {
            return null;
        }
        int dockX = categoryDockX();
        float dockY = this.categoryDockVisualY + CATEGORY_DOCK_PADDING;
        FeatureCategory[] categories = getVisibleCategories();
        for (int index = 0; index < categories.length; index++) {
            int buttonX = categoryButtonX(dockX, index);
            if (hit(mouseX, mouseY, buttonX, dockY, CATEGORY_DOCK_BUTTON_SIZE, CATEGORY_DOCK_BUTTON_SIZE)) {
                return categories[index];
            }
        }
        return null;
    }

    private boolean categoryDockContains(float mouseX, float mouseY) {
        return this.displayedPage == MenuPage.NONE
                && this.dockProgress > 0.001F
                && hit(mouseX, mouseY, categoryDockX(), this.categoryDockVisualY,
                categoryDockWidth(), CATEGORY_DOCK_HEIGHT);
    }

    private List<Feature> searchResults() {
        String query = this.searchQuery.toString().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return List.of();
        }
        return FeatureManager.INSTANCE.getFeatures().stream()
                .filter(feature -> matches(feature, query))
                .sorted(Comparator.comparingInt((Feature feature) -> matchPriority(feature, query))
                        .thenComparing(Feature::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(5)
                .toList();
    }

    private String searchSuggestionSuffix() {
        String query = this.searchQuery.toString();
        if (query.isEmpty() || !query.equals(query.trim())) {
            return "";
        }

        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return FeatureManager.INSTANCE.getFeatures().stream()
                .map(Feature::getName)
                .filter(name -> name.length() > query.length()
                        && name.toLowerCase(Locale.ROOT).startsWith(normalizedQuery))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> name.substring(query.length()))
                .findFirst()
                .orElse("");
    }

    private void acceptSearchSuggestion() {
        String suffix = searchSuggestionSuffix();
        if (!suffix.isEmpty()) {
            this.searchQuery.append(suffix);
        }
    }

    private void openSearchResult(Feature result) {
        if (result.getCategory() != this.selectedCategory) {
            float from = this.categoryIndicatorAnimation.getValue();
            this.selectedCategory = result.getCategory();
            this.categoryIndicatorAnimation.animate(from, categoryIndex(this.selectedCategory), 180L, Animation.Easing.EASE_OUT_QUAD);
        }
        this.triggeredFeature = result;
        this.searchQuery.setLength(0);
        this.searchFocused = false;
        this.searchAllSelected = false;
        this.state.openPage(MenuPage.NONE);
        rebuildCards();
    }

    private Feature resultAt(int mouseX, int mouseY) {
        float chipX = SEARCH_RESULTS_X;
        for (Feature feature : searchResults()) {
            float chipWidth = searchResultWidth(feature);
            if (chipX + chipWidth > SEARCH_RESULTS_X + SEARCH_RESULTS_WIDTH) {
                break;
            }
            if (hit(mouseX, mouseY, chipX, SEARCH_RESULTS_Y, chipWidth, SEARCH_RESULT_HEIGHT)) {
                return feature;
            }
            chipX += chipWidth + SEARCH_RESULT_GAP;
        }
        return null;
    }

    private static float searchResultWidth(Feature feature) {
        MsdfFont font = UiFonts.sfProDisplay();
        float letterSpacing = SEARCH_RESULT_TEXT_SIZE * UiFontStyle.MEDIUM.letterSpacingEm();
        float textWidth = Math.round(font.measureWidth(feature.getName(), SEARCH_RESULT_TEXT_SIZE, letterSpacing));
        return SEARCH_RESULT_PADDING_X * 2 + SEARCH_RESULT_ICON_SIZE + SEARCH_RESULT_ICON_TEXT_GAP + textWidth;
    }

    private static Identifier categoryIcon(FeatureCategory category) {
        return switch (category) {
            case VISUAL -> Textures.Icons.EYE;
            case MISC -> Textures.Icons.BOXES;
            default -> CATEGORY_ICONS[0];
        };
    }

    private static boolean matches(Feature feature, String query) {
        return matchPriority(feature, query) < Integer.MAX_VALUE;
    }

    private static int matchPriority(Feature feature, String query) {
        String name = feature.getName().toLowerCase(Locale.ROOT);
        if (name.startsWith(query)) {
            return 0;
        }
        if (name.contains(query)) {
            return 1;
        }
        for (Setting<?> setting : feature.getSettings()) {
            String canonicalSettingName = setting.getName().toLowerCase(Locale.ROOT);
            String settingName = MenuText.setting(feature.getName(), setting.getName())
                    .toLowerCase(Locale.ROOT);
            if (settingName.startsWith(query) || canonicalSettingName.startsWith(query)) {
                return 2;
            }
            if (settingName.contains(query) || canonicalSettingName.contains(query)) {
                return 3;
            }
            List<String> options = setting instanceof ModeSetting mode
                    ? mode.getModes()
                    : setting instanceof MultiSelectSetting multi ? multi.getOptions() : List.of();
            for (String option : options) {
                if (option.toLowerCase(Locale.ROOT).contains(query)
                        || MenuText.option(option).toLowerCase(Locale.ROOT).contains(query)) {
                    return 3;
                }
            }
        }
        if (feature.getDescription().toLowerCase(Locale.ROOT).contains(query)
                || MenuText.featureDescription(feature.getDescription())
                .toLowerCase(Locale.ROOT).contains(query)) {
            return 4;
        }
        return Integer.MAX_VALUE;
    }

    private static int shortestColumn(int[] values) {
        int column = 0;
        for (int index = 1; index < values.length; index++) {
            if (values[index] < values[column]) {
                column = index;
            }
        }
        return column;
    }

    private void updateCategoryVisuals() {
        float categoryProgress = this.categoryAnimation.getValue();
        this.visualAlpha = categoryProgress;
        this.visualOffsetX = Math.round(14.0F * (1.0F - categoryProgress));
    }

    private static int categoryDockX() {
        return (1024 - categoryDockWidth()) / 2;
    }

    private static int categoryDockWidth() {
        int count = getVisibleCategories().length;
        return CATEGORY_DOCK_PADDING * 2 + count * CATEGORY_DOCK_BUTTON_SIZE + (count - 1) * CATEGORY_DOCK_GAP;
    }

    private static int categoryButtonX(int dockX, int index) {
        return dockX + CATEGORY_DOCK_PADDING + index * (CATEGORY_DOCK_BUTTON_SIZE + CATEGORY_DOCK_GAP);
    }

    private static float categoryIndex(FeatureCategory category) {
        FeatureCategory[] categories = getVisibleCategories();
        for (int index = 0; index < categories.length; index++) {
            if (categories[index] == category) {
                return index;
            }
        }
        return 0.0F;
    }

    private static FeatureCategory[] getVisibleCategories() {
        List<FeatureCategory> visible = new ArrayList<>();
        for (FeatureCategory category : FeatureCategory.values()) {
            if (!FeatureManager.INSTANCE.getFeatures(category).isEmpty()) {
                visible.add(category);
            }
        }
        return visible.toArray(new FeatureCategory[0]);
    }

}
