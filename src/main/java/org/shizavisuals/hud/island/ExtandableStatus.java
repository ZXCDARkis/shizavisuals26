package org.shizavisuals.hud.island;

public class ExtandableStatus extends IslandStatus {

    public ExtandableStatus(DynamicIsland island, String name) {
        super(island, name);
    }

    @Override
    public boolean canShow() {
        return false;
    }
}
