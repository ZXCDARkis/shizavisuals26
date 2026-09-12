package org.shizavisuals.context;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;

public final class RenderContext {
    public static long overlayStartTime = -1L;

    private static final State2D STATE_2D = new State2D();
    private static final State3D STATE_3D = new State3D();

    private RenderContext() {
    }

    public static void enter2D(Gui gui, GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker) {
        State2D state = STATE_2D;
        state.active = true;
        state.gui = gui;
        state.guiGraphicsExtractor = guiGraphicsExtractor;
        state.deltaTracker = deltaTracker;
    }

    public static void exit2D() {
        STATE_2D.clear();
    }

    public static boolean isIn2D() {
        return STATE_2D.isActive();
    }

    public static GuiGraphicsExtractor currentGuiGraphicsExtractor() {
        return STATE_2D.getGuiGraphicsExtractor();
    }

    public static Gui currentGui() {
        return STATE_2D.getGui();
    }

    public static DeltaTracker currentDeltaTracker() {
        return STATE_2D.getDeltaTracker();
    }

    public static void enter3D(GameRenderer gameRenderer, DeltaTracker deltaTracker) {
        State3D state = STATE_3D;
        state.active = true;
        state.gameRenderer = gameRenderer;
        state.deltaTracker = deltaTracker;
    }

    public static void exit3D() {
        STATE_3D.clear();
    }

    static State2D state2D() {
        return STATE_2D;
    }

    static State3D state3D() {
        return STATE_3D;
    }

    static final class State2D {
        private boolean active;
        private Gui gui;
        private GuiGraphicsExtractor guiGraphicsExtractor;
        private DeltaTracker deltaTracker;

        boolean isActive() {
            return this.active;
        }

        Gui getGui() {
            return this.gui;
        }

        GuiGraphicsExtractor getGuiGraphicsExtractor() {
            return this.guiGraphicsExtractor;
        }

        DeltaTracker getDeltaTracker() {
            return this.deltaTracker;
        }

        private void clear() {
            this.active = false;
            this.gui = null;
            this.guiGraphicsExtractor = null;
            this.deltaTracker = null;
        }
    }

    static final class State3D {
        private boolean active;
        private GameRenderer gameRenderer;
        private DeltaTracker deltaTracker;

        boolean isActive() {
            return this.active;
        }

        GameRenderer getGameRenderer() {
            return this.gameRenderer;
        }

        DeltaTracker getDeltaTracker() {
            return this.deltaTracker;
        }

        private void clear() {
            this.active = false;
            this.gameRenderer = null;
            this.deltaTracker = null;
        }
    }
}
