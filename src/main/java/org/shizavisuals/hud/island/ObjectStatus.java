package org.shizavisuals.hud.island;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.packet.PacketReceiveEvent;
import org.shizavisuals.utils.ColorUtil;

public class ObjectStatus extends TimerStatus {
    private long duration = 0L;
    private long startedAt;
    private String currentName = "";
    private int rectColor = ColorUtil.rgba(180, 180, 180, 255);

    public ObjectStatus(DynamicIsland island) {
        super(island, "objects");
        EventManager.subscribe(this);
    }

    @EventTarget
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPacket() instanceof ClientboundSoundPacket sound)) {
            return;
        }
        String soundName = sound.getSound().value().location().toString();
        float vol = sound.getVolume();
        float pitch = sound.getPitch();

        if (soundName.contains("piston.extend") || soundName.contains("piston.contract")) {
            if ((vol == 0.5F || vol == 0.7F) && pitch == 0.5F) {
                setupObject("Ловушка", 15000L, ColorUtil.rgba(180, 180, 180, 255));
            }
        } else if (soundName.contains("anvil.place")) {
            if ((vol == 0.5F || vol == 0.7F) && (pitch == 1.1F || pitch == 0.5F)) {
                setupObject("Слой", 20000L, ColorUtil.rgba(160, 160, 160, 255));
            }
        } else if (soundName.contains("entity.evoker_fangs.attack")) {
            if (vol == 0.5F || vol == 0.7F) {
                if (pitch == 1.0F) {
                    setupObject("Драконья ловушка", 30000L, ColorUtil.rgba(160, 32, 240, 255));
                } else if (pitch == 0.85F) {
                    setupObject("Драконья ловушка", 60000L, ColorUtil.rgba(160, 32, 240, 255));
                }
            }
        }
    }

    private void setupObject(String name, long time, int color) {
        this.currentName = name;
        this.duration = time;
        this.startedAt = System.currentTimeMillis();
        this.rectColor = color;
        this.update("s", (int) (time / 1000L), name, color);
    }

    @Override
    public void draw(float alpha) {
        if (!canShow()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - this.startedAt;
        int remained = (int) ((this.duration - elapsed) / 1000L);
        if (remained < 0) {
            remained = 0;
        }
        this.update("s", remained, this.currentName, this.rectColor);
        super.draw(alpha);
    }

    @Override
    public int getColor() {
        return ColorUtil.rgba(20, 20, 20, 200);
    }

    @Override
    public boolean canShow() {
        return this.duration > 0
                && System.currentTimeMillis() - this.startedAt < this.duration;
    }
}
