package org.shizavisuals.hud.island;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.lifecycle.WorldJoinEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.packet.PacketReceiveEvent;
import org.shizavisuals.utils.ColorUtil;

public class ObjectStatus extends TimerStatus implements MinecraftContext {
    private long duration;
    private long startedAt;
    private volatile long worldGeneration;
    private String currentName = "";
    private int rectColor = ColorUtil.rgba(180, 180, 180, 255);

    public ObjectStatus(DynamicIsland island) {
        super(island, "objects");
        EventManager.subscribe(this);
    }

    @EventTarget
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPhase() != PacketReceiveEvent.Phase.PRE
                || !this.isEnabled()
                || !(event.getPacket() instanceof ClientboundSoundPacket sound)
                || !isRelevantSound(sound)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Connection connection = event.getConnection();
        long generation = this.worldGeneration;
        if (mc.isSameThread()) {
            handleSound(mc, sound, connection, generation);
        } else {
            mc.execute(() -> handleSound(mc, sound, connection, generation));
        }
    }

    private void handleSound(Minecraft mc, ClientboundSoundPacket sound,
                             Connection connection, long generation) {
        if (!this.isEnabled() || generation != this.worldGeneration
                || !isCurrentConnection(connection) || mc.level == null) {
            return;
        }
        String soundName = sound.getSound().value().location().toString();
        float volume = sound.getVolume();
        float pitch = sound.getPitch();

        if (soundName.contains("piston.extend") || soundName.contains("piston.contract")) {
            if ((volume == 0.5F || volume == 0.7F) && pitch == 0.5F) {
                setupObject("Ловушка", 15000L, ColorUtil.rgba(180, 180, 180, 255));
            }
        } else if (soundName.contains("anvil.place")) {
            if ((volume == 0.5F || volume == 0.7F) && (pitch == 1.1F || pitch == 0.5F)) {
                setupObject("Слой", 20000L, ColorUtil.rgba(160, 160, 160, 255));
            }
        } else if (soundName.contains("entity.evoker_fangs.attack")) {
            if (volume == 0.5F || volume == 0.7F) {
                if (pitch == 1.0F) {
                    setupObject("Драконья ловушка", 30000L, ColorUtil.rgba(160, 32, 240, 255));
                } else if (pitch == 0.85F) {
                    setupObject("Драконья ловушка", 60000L, ColorUtil.rgba(160, 32, 240, 255));
                }
            }
        }
    }

    private static boolean isRelevantSound(ClientboundSoundPacket sound) {
        String soundName = sound.getSound().value().location().toString();
        return soundName.contains("piston.extend")
                || soundName.contains("piston.contract")
                || soundName.contains("anvil.place")
                || soundName.contains("entity.evoker_fangs.attack");
    }

    @Override
    public void setEnabled(boolean value) {
        if (this.isEnabled() != value) {
            this.worldGeneration++;
            if (!value) {
                clear();
            }
        }
        super.setEnabled(value);
    }

    @EventTarget
    public void onWorldJoin(WorldJoinEvent event) {
        this.worldGeneration++;
        clear();
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        this.worldGeneration++;
        clear();
    }

    private void clear() {
        this.duration = 0L;
        this.startedAt = 0L;
        this.currentName = "";
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
        int remained = Math.max(0, (int) ((this.duration - elapsed) / 1000L));
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
