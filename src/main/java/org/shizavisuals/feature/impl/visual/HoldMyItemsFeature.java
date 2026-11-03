package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.lifecycle.ResourceReloadEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.menu.core.MenuOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HoldMyItemsFeature extends Feature {
    private static final Logger LOGGER = LoggerFactory.getLogger(HoldMyItemsFeature.class);
    private static final String RESOURCE_PACK_ID = "holdmyitems:pack_test";
    private static final ReloadCompletionListener RELOAD_COMPLETION = new ReloadCompletionListener();

    public HoldMyItemsFeature() {
        super("HoldMyItems", "Анимированные руки и предметы от первого лица", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static boolean isActive() {
        return FeatureManager.INSTANCE.getEnabled(HoldMyItemsFeature.class) != null;
    }

    public static void syncOnClientStart() {
        syncResourcePack(isActive());
    }

    @Override
    protected void onEnable() {
        syncResourcePack(true);
    }

    @Override
    protected void onDisable() {
        syncResourcePack(false);
    }

    private static void syncResourcePack(boolean enabled) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        client.execute(() -> {
            // A broken/corrupt external pack in run/resourcepacks must not crash the
            // whole client — swallow reload errors and simply skip our pack if so.
            PackRepository repository;
            try {
                repository = client.getResourcePackRepository();
                repository.reload();
            } catch (RuntimeException reloadError) {
                LOGGER.warn("HoldMyItems pack reload failed", reloadError);
                return;
            }
            boolean selected;
            try {
                selected = repository.getSelectedIds().contains(RESOURCE_PACK_ID);
            } catch (RuntimeException lookupError) {
                LOGGER.warn("HoldMyItems pack lookup failed", lookupError);
                return;
            }
            boolean changed;
            try {
                changed = enabled
                        ? !selected && repository.addPack(RESOURCE_PACK_ID)
                        : selected && repository.removePack(RESOURCE_PACK_ID);
            } catch (RuntimeException packError) {
                LOGGER.warn("HoldMyItems pack add/remove failed", packError);
                return;
            }
            if (changed) {
                boolean restoreMenu = MenuOverlay.suspendForReload(client);
                if (restoreMenu) {
                    RELOAD_COMPLETION.arm();
                }
                try {
                    client.options.updateResourcePacks(repository);
                } catch (RuntimeException exception) {
                    if (restoreMenu) {
                        RELOAD_COMPLETION.complete(client);
                    }
                    throw exception;
                }
            }
        });
    }

    private static final class ReloadCompletionListener {
        private boolean armed;

        private void arm() {
            if (this.armed) {
                return;
            }
            this.armed = true;
            EventManager.subscribe(this);
        }

        private void complete(Minecraft client) {
            if (!this.armed) {
                return;
            }
            this.armed = false;
            EventManager.unsubscribe(this);
            MenuOverlay.resumeAfterReload(client);
        }

        @EventTarget
        public void onResourceReload(ResourceReloadEvent event) {
            complete(event.getClient());
        }
    }
}


