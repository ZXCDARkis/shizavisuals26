package org.shizavisuals.utils.inventory;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import net.minecraft.client.player.LocalPlayer;

/**
 * Central controller: preset store + auto-arranger + overlay interaction
 * state (click regions are public and refreshed by the renderer each frame).
 */
public final class InventoryManager {
    private static InventoryManager instance;

    public static InventoryManager get() {
        if (instance == null) {
            instance = new InventoryManager();
        }
        return instance;
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final PresetStore store = new PresetStore();
    private final AutoArranger arranger = new AutoArranger();

    private boolean enabled;
    private boolean overlayHints = true;
    private boolean showMissing = true;
    private boolean highlightSlots = true;

    /** Click regions of the overlay, refreshed every rendered frame. */
    public final float[] chestBtnRect = {-999, -999, 0, 0};
    public final float[] arrangeBtnRect = {-999, -999, 0, 0};
    public final float[] missingPanelRect = {-999, -999, 0, 0};
    public int missingScroll;
    public int missingScrollMax;
    public final ArrayList<float[]> missingRowRects = new ArrayList<>();
    public final ArrayList<String> missingRowNames = new ArrayList<>();

    private InventoryManager() {
        store.load();
    }

    public PresetStore store() {
        return store;
    }

    public AutoArranger arranger() {
        return arranger;
    }

    public void setEnabled(boolean v) {
        this.enabled = v;
        if (!v) {
            arranger.stop();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setOverlayHints(boolean v) {
        this.overlayHints = v;
    }

    public boolean overlayHints() {
        return overlayHints;
    }

    public void setShowMissing(boolean v) {
        this.showMissing = v;
    }

    public boolean showMissing() {
        return showMissing;
    }

    public void setHighlightSlots(boolean v) {
        this.highlightSlots = v;
    }

    public boolean highlightSlots() {
        return highlightSlots;
    }

    public void setArrangeDelayMs(double ms) {
        arranger.setBaseDelayMs(ms);
    }

    public void onClientTick() {
        arranger.tick();
    }

    public void saveCurrent(String name) {
        LocalPlayer player = mc.player;
        if (player == null || name == null || name.isBlank()) {
            return;
        }
        InventoryPreset preset = InventoryPreset.capture(name.trim(), player);
        store.put(preset);
        store.setActive(preset.name);
    }

    public void replaceCurrent(String name) {
        LocalPlayer player = mc.player;
        if (player == null || name == null || name.isBlank() || !store.exists(name)) {
            return;
        }
        store.put(InventoryPreset.capture(name.trim(), player));
        arranger.stop();
    }

    public void setActive(String name) {
        store.setActive(name);
        arranger.stop();
    }

    public void clearActive() {
        store.clearActive();
        arranger.stop();
    }

    public void startArrange() {
        InventoryPreset active = store.active();
        if (active != null) {
            arranger.start(active);
        }
    }

    public void stopArrange() {
        arranger.stop();
    }

    public InventoryDiff diff() {
        LocalPlayer player = mc.player;
        if (player == null) {
            return new InventoryDiff();
        }
        return InventoryDiff.compute(player.inventoryMenu, store.active());
    }

    /** Toggles arrange on the arrange-button click; returns true if handled. */
    public boolean toggleArrange() {
        if (arranger.isRunning()) {
            stopArrange();
        } else {
            startArrange();
        }
        return true;
    }

    /** Opens the auction search for a missing-item row click. */
    public void searchAuction(String displayName) {
        LocalPlayer player = mc.player;
        if (player == null || player.connection == null) {
            return;
        }
        String name = InvItem.cleanName(displayName);
        if (name.isEmpty()) {
            return;
        }
        mc.setScreenAndShow(null);
        player.connection.sendCommand("ah search " + name);
    }

    public static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static boolean inside(double mx, double my, float[] r) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    public static void rect(float[] out, float x, float y, float w, float h) {
        out[0] = x;
        out[1] = y;
        out[2] = w;
        out[3] = h;
    }

    public static void clearRect(float[] out) {
        out[0] = -999;
        out[1] = -999;
        out[2] = 0;
        out[3] = 0;
    }
}
