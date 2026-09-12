package org.shizavisuals.hud.island;

import net.minecraft.resources.Identifier;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.lifecycle.FeatureToggleEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;

import java.util.ArrayList;
import java.util.List;

public class NotificationStatus extends IslandStatus {

    private static final long LIFETIME_MS = 4000L;
    private static final int MAX_NOTES = 8;

    private static final Identifier ALERTS = Identifier.parse("shizavisuals:icons/hud/island/alerts.png");

    private final List<Entry> notes = new ArrayList<>();

    public NotificationStatus(DynamicIsland island) {
        super(island, "alerts");
        EventManager.subscribe(this);
    }

    @EventTarget
    public void onFeatureToggle(FeatureToggleEvent event) {
        Feature feature = event.getFeature();
        if (feature == null || MinecraftContext.mc.level == null) {
            return;
        }
        push(new Note(feature.getName(), event.isEnabled() ? 0 : 1, System.currentTimeMillis()));
    }

    private void push(Note note) {
        this.notes.add(new Entry(note, new IslandAnimation(500L, IslandEasing.BAKEK_SIZE)));
        while (this.notes.size() > MAX_NOTES) {
            this.notes.removeFirst();
        }
    }

    @Override
    public void draw(float alpha) {
        if (this.notes.isEmpty()) {
            return;
        }
        float s = this.island.scale();
        float x = this.island.x();
        float y = this.island.y();
        Entry active = this.notes.getLast();
        MsdfFont font = font(active.note().text(), UiFontStyle.MEDIUM);
        float size7 = 7.0F * s;
        float ls7 = spacing(size7);
        float textWidthPx = font.measureWidth(active.note().text(), size7, ls7);

        this.size.width = (25.0F * s + textWidthPx) / s;
        this.size.height = 20.0F;

        Render2DUtil.pushScissor(x, y, this.size.width * s, this.size.height * s);
        try {
            for (Entry entry : this.notes) {
                entry.showing().setDuration(500L);
                entry.showing().update(entry == active ? 1.0F : 0.0F);

                float showing = entry.showing().getValue();
                float a = alpha * showing;
                float moveOffset = 6.0F * this.animation.getValue() * showing;

                float bgSize = 10.0F * s;
                float iconX = x + (10.0F * s - bgSize / 2.0F) - 6.0F * s + moveOffset * s;
                float iconY = y + 10.0F * s - bgSize / 2.0F;

                Note n = entry.note();
                int circleColor = switch (n.kind()) {
                    case 0 -> ColorUtil.rgba(80, 220, 100, Math.round(255.0F * a));
                    case 1 -> ColorUtil.rgba(255, 59, 92, Math.round(255.0F * a));
                    default -> ColorUtil.multiplyAlpha(Theme.getAccent(), a);
                };
                Render2DUtil.rect(iconX, iconY, bgSize, bgSize)
                        .radius(bgSize / 2.0F)
                        .color(circleColor)
                        .draw();

                float symSize = 6.0F * s;
                float symX = iconX + (bgSize - symSize) / 2.0F;
                float symY = iconY + (bgSize - symSize) / 2.0F;

                Render2DUtil.texture(symX, symY, symSize, symSize, ALERTS)
                        .uv(n.kind() / 3.0F, 0.0F, (n.kind() + 1) / 3.0F, 1.0F)
                        .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, a))
                        .draw();

                float txtX = x + this.size.width * s - textWidthPx - moveOffset * s;
                float txtY = y + 7.5F * s;

                Render2DUtil.text(txtX, txtY, size7, n.text())
                        .font(font)
                        .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, a))
                        .draw();
            }
        } finally {
            Render2DUtil.popScissor();
        }
    }

    @Override
    public boolean canShow() {
        long now = System.currentTimeMillis();
        this.notes.removeIf(entry -> now - entry.note().createdAt() >= LIFETIME_MS);
        return !this.notes.isEmpty();
    }

    private record Note(String text, int kind, long createdAt) {
    }

    private record Entry(Note note, IslandAnimation showing) {
    }
}
