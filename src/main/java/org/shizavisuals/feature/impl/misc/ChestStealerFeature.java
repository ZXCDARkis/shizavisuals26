package org.shizavisuals.feature.impl.misc;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.utils.inventory.ContainerLootService;
import org.shizavisuals.utils.inventory.InventoryUtil;

public final class ChestStealerFeature extends Feature {
    public final NumberSetting delay = register(new NumberSetting("Delay", 80.0, 0.0, 1000.0, 10.0, "ms"));

    private long lastMoveAt;

    public ChestStealerFeature() {
        super("ChestStealer", "Automatically takes items from containers", FeatureCategory.MISC, BindSetting.UNBOUND);
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (!InventoryUtil.isContainerScreenOpen()) {
            return;
        }
        AbstractContainerMenu menu = InventoryUtil.getOpenMenu();
        if (menu == null || menu == event.getClient().player.inventoryMenu) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastMoveAt < delay.getValue().longValue()) {
            return;
        }
        if (ContainerLootService.quickMoveFirst(menu, stack -> !stack.isEmpty())) {
            lastMoveAt = now;
        }
    }
}
