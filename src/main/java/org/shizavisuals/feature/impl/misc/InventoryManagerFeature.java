package org.shizavisuals.feature.impl.misc;

import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.utils.inventory.InventoryManager;

/**
 * Inventory Manager: save the current inventory as a named preset and let the
 * client re-assemble it later with server-side hotbar swaps. The overlay lives
 * inside the player inventory screen (chest button in the top-right corner).
 */
public final class InventoryManagerFeature extends Feature {
    private static final int TICK_MS = 50;

    public InventoryManagerFeature() {
        super("InvManager", "Сохраняет и собирает раскладки инвентаря",
                FeatureCategory.MISC, BindSetting.UNBOUND);
    }

    public static InventoryManagerFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(InventoryManagerFeature.class);
    }

    /** Static disable for UI code that can't hold a feature reference. */
    public static void disableNow() {
        var f = getEnabled();
        if (f != null) {
            f.setEnabled(false);
        }
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        InventoryManager.get().setEnabled(true);
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        InventoryManager.get().setEnabled(false);
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (!isEnabled()) {
            return;
        }
        InventoryManager m = InventoryManager.get();
        m.setArrangeDelayMs(this.delay.getValue());
        m.onClientTick();
    }

    public final NumberSetting delay = register(new NumberSetting("Delay", 160.0D, 60.0D, 500.0D, 5.0D, " ms"));
    public final BooleanSetting showMissing = register(new BooleanSetting("Show Missing", true));
    public final BooleanSetting highlight = register(new BooleanSetting("Highlight Slots", true));

    @Override
    protected void onStateChanged() {
        super.onStateChanged();
        InventoryManager m = InventoryManager.get();
        m.setShowMissing(this.showMissing.getValue());
        m.setHighlightSlots(this.highlight.getValue());
    }
}
