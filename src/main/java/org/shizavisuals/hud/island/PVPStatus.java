package org.shizavisuals.hud.island;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.AttackEvent;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.utils.ColorUtil;

public class PVPStatus extends TimerStatus {
    private static final long COMBAT_WINDOW_MS = 30_000L;

    private long lastCombatMs;

    public PVPStatus(DynamicIsland island) {
        super(island, "pvp");
        EventManager.subscribe(this);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.crosshairPickEntity == null) {
            return;
        }
        if (mc.crosshairPickEntity instanceof Player) {
            this.lastCombatMs = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        Minecraft mc = event.getClient();
        if (mc == null || mc.player == null) {
            return;
        }
        // Смерть закрывает бой сразу, а не висит остаток таймера.
        if (mc.player.isDeadOrDying()) {
            this.lastCombatMs = 0L;
            return;
        }
        if (mc.player.hurtTime <= 0) {
            return;
        }
        // Только урон от игрока: мобы/падение/огонь PVP-таймер не взводят.
        // getLastHurtByMob — ванильный API, отдельного события урона в клиенте нет.
        try {
            if (mc.player.getLastHurtByMob() instanceof Player) {
                this.lastCombatMs = System.currentTimeMillis();
            }
        } catch (Throwable ignored) {
        }
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        this.lastCombatMs = 0L;
    }

    @Override
    public void draw(float alpha) {
        long now = System.currentTimeMillis();
        int remained = (int) Math.max(0L, (this.lastCombatMs + COMBAT_WINDOW_MS - now) / 1000L);
        this.update("s", remained, "Вы в PVP режиме", ColorUtil.rgb(185, 28, 28));
        super.draw(alpha);
    }

    @Override
    public boolean canShow() {
        return System.currentTimeMillis() - this.lastCombatMs < COMBAT_WINDOW_MS;
    }
}
