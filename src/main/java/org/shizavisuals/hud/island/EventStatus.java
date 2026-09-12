package org.shizavisuals.hud.island;

import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.packet.PacketReceiveEvent;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.shizavisuals.utils.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventStatus extends TimerStatus {
    private static final Pattern EVENT_NAME = Pattern.compile("\\[([^\\]]+)\\]");
    private static final Pattern APPEAR_COORDS = Pattern.compile("координатах\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)");
    private static final Pattern COORDS_LINE = Pattern.compile("координаты:?\\s*(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)", Pattern.CASE_INSENSITIVE);

    private final List<ActiveEvent> activeEvents = new ArrayList<>();
    private long startedAt = System.currentTimeMillis();
    private String pendingEvent;

    private String manualLabel;
    private int manualColor;
    private int manualSeconds;
    private long manualUntil;

    public EventStatus(DynamicIsland island) {
        super(island, "events");
        EventManager.subscribe(this);
    }

    @EventTarget
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPacket() instanceof ClientboundSystemChatPacket packet)) {
            return;
        }
        String raw = packet.content().getString();
        String message = raw.replaceAll("\\n", " ")
                .replaceAll("[^\\p{L}\\p{N}\\s\\[\\]:.-]", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (raw.contains("Появился")) {
            Matcher eventMatcher = EVENT_NAME.matcher(message);
            Matcher coordMatcher = APPEAR_COORDS.matcher(message);
            if (eventMatcher.find() && coordMatcher.find()) {
                String eventName = eventMatcher.group(1);
                for (EventType value : EventType.values()) {
                    if (!eventName.toLowerCase().contains(value.getName().toLowerCase())) {
                        continue;
                    }
                    this.activeEvents.removeIf(e -> e.type() == value);
                    this.activeEvents.add(new ActiveEvent(value));
                    this.startedAt = System.currentTimeMillis();
                    break;
                }
            }
        } else {
            for (EventType value : EventType.values()) {
                if (!message.equalsIgnoreCase(value.getName())) {
                    continue;
                }
                this.pendingEvent = value.getName();
                break;
            }
            Matcher coordMatcher = COORDS_LINE.matcher(message);
            if (message.toLowerCase().startsWith("координаты") && coordMatcher.find() && this.pendingEvent != null) {
                for (EventType value : EventType.values()) {
                    if (!this.pendingEvent.equalsIgnoreCase(value.getName())) {
                        continue;
                    }
                    this.activeEvents.removeIf(e -> e.type() == value);
                    this.activeEvents.add(new ActiveEvent(value));
                    this.startedAt = System.currentTimeMillis();
                    break;
                }
                this.pendingEvent = null;
            }
        }
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        this.activeEvents.clear();
        this.pendingEvent = null;
        this.manualUntil = 0L;
    }

    public void updateManual(String label, int seconds, int color) {
        this.manualLabel = label;
        this.manualColor = color;
        this.manualSeconds = Math.max(1, seconds);
        this.manualUntil = System.currentTimeMillis() + this.manualSeconds * 1000L;
    }

    @Override
    public void draw(float alpha) {
        long now = System.currentTimeMillis();
        this.activeEvents.removeIf(active -> now - this.startedAt >= active.type().getTime());

        if (!this.activeEvents.isEmpty()) {
            ActiveEvent current = this.activeEvents.getFirst();
            long elapsed = now - this.startedAt;
            long remaining = current.type().getTime() - elapsed;
            if (remaining <= 0L) {
                return;
            }
            int timer = (int) (remaining / 1000L);
            this.update(timer / 60 + ":", "", timer % 60, current.type().getName(), eventColor(current.type()));
            super.draw(alpha);
            return;
        }

        if (now < this.manualUntil && this.manualLabel != null) {
            int timer = (int) ((this.manualUntil - now) / 1000L);
            this.update(timer / 60 + ":", "", timer % 60, this.manualLabel, this.manualColor);
            super.draw(alpha);
        }
    }

    private static int eventColor(EventType eventType) {
        return switch (eventType) {
            case ALTAR -> ColorUtil.rgb(138, 43, 226);
            case BEACON -> ColorUtil.rgb(255, 69, 0);
            case VULCAN -> ColorUtil.rgb(255, 140, 0);
            case METEOR -> ColorUtil.rgb(70, 130, 180);
            case PACKAGE -> ColorUtil.rgb(243, 196, 82);
            case BOSS -> ColorUtil.rgb(139, 222, 221);
            case CONTAINER -> ColorUtil.rgb(141, 99, 184);
            case GRUZ -> ColorUtil.rgb(41, 253, 5);
            case MYSTERIOUS_SHIP -> ColorUtil.rgb(90, 158, 152);
        };
    }

    @Override
    public boolean canShow() {
        if (!this.activeEvents.isEmpty()) {
            return true;
        }
        return this.manualLabel != null
                && System.currentTimeMillis() < this.manualUntil
                && this.manualSeconds > 0;
    }

    private record ActiveEvent(EventType type) {
    }

    enum EventType {
        ALTAR("Мистический Алтарь", 360_000L),
        BEACON("Маяк Убийца", 360_000L),
        VULCAN("Вулкан", 300_000L),
        METEOR("Метеоритный дождь", 180_000L),
        PACKAGE("Посылка", 180_000L),
        BOSS("Босс", 180_000L),
        CONTAINER("Контейнер", 180_000L),
        GRUZ("Груз", 180_000L),
        MYSTERIOUS_SHIP("Таинственный корабль", 300_000L);

        private final String name;
        private final long time;

        EventType(String name, long time) {
            this.name = name;
            this.time = time;
        }

        public String getName() {
            return this.name;
        }

        public long getTime() {
            return this.time;
        }
    }
}
