package org.shizavisuals.menu.pages;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.menu.core.MenuPage;
import org.shizavisuals.menu.ui.PageComponent;

public final class ConfigPage extends PageComponent {
    private MenuPage displayedPage = MenuPage.NONE;

    @Override
    protected void onLayout() {
        this.displayedPage = this.state.displayPage();
    }

    public void handleScroll(int mouseX, int mouseY, double vertical) {
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY) {
        return contentContains(mouseX, mouseY);
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (this.displayedPage != MenuPage.CONFIGURATIONS || this.progress <= 0.001F) {
            return;
        }
        pageHeader("Configurations", "Load, save, and manage your client presets.");
        emptyState((168 + 768) / 2.0F, "There are no configs here yet", "Coming in one of the next updates, maybe.");
    }
}
