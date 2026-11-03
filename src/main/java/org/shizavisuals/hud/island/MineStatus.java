package org.shizavisuals.hud.island;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.utils.ColorUtil;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MineStatus extends TimerStatus {
    private static final Pattern TIMER = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final double RANGE_SQR = 900.0D;
    private static final long SCAN_INTERVAL_MS = 500L;

    private long nextScanAt;
    private boolean visible;

    private static final List<String> SHAFT_KEYWORDS = List.of(
            "шахта", "осталось", "обычная", "редкая", "эпическая", "легендарная", "мифическая");

    public MineStatus(DynamicIsland island) {
        super(island, "mine");
    }

    @Override
    public void draw(float alpha) {
        if (refresh()) {
            super.draw(alpha);
        }
    }

    @Override
    public boolean canShow() {
        return refresh();
    }

    /**
     * Scans nearby armor stands for the mine timer. Called twice per frame
     * (canShow + draw), so the result is throttled and reused in between.
     */
    private boolean refresh() {
        long now = System.currentTimeMillis();
        if (now < this.nextScanAt) {
            return this.visible;
        }
        this.nextScanAt = now + SCAN_INTERVAL_MS;
        this.visible = scan();
        return this.visible;
    }

    private boolean scan() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null || !isHolyworld()) {
            return false;
        }
        Vec3 playerPos = mc.player.position();
        ArmorStand anchor = null;
        double anchorDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.isAlive() || stand.getCustomName() == null) {
                continue;
            }
            double distance = flatDistSqr(stand.position(), playerPos);
            if (distance > RANGE_SQR || distance >= anchorDistance) {
                continue;
            }
            if (containsShaftKeyword(nameOf(stand))) {
                anchor = stand;
                anchorDistance = distance;
            }
        }
        if (anchor == null) {
            return false;
        }
        Vec3 anchorPos = anchor.position();

        String rarity = null;
        Integer seconds = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.isAlive() || stand.getCustomName() == null) {
                continue;
            }
            if (flatDistSqr(stand.position(), anchorPos) > 64.0D) {
                continue;
            }
            String name = nameOf(stand);
            if (rarity == null) {
                rarity = matchRarity(name);
            }
            if (seconds == null) {
                seconds = parseTimer(name);
            }
            if (rarity != null && seconds != null) {
                break;
            }
        }

        if (rarity == null || seconds == null || seconds <= 0) {
            return false;
        }
        this.update(seconds / 60 + ":", "", seconds % 60, rarity, rarityColor(rarity));
        return true;
    }

    private static String nameOf(ArmorStand stand) {
        return stand.getCustomName() == null ? "" : stand.getCustomName().getString().toLowerCase(Locale.ROOT);
    }

    private static boolean containsShaftKeyword(String name) {
        if (name.contains("энда") || name.contains("ада") || name.contains("энд") || name.contains("ад")) {
            return false;
        }
        for (String keyword : SHAFT_KEYWORDS) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String matchRarity(String name) {
        if (name.contains("мифическая")) return "Мифическая";
        if (name.contains("легендарная")) return "Легендарная";
        if (name.contains("эпическая")) return "Эпическая";
        if (name.contains("редкая")) return "Редкая";
        if (name.contains("обычная")) return "Обычная";
        return null;
    }

    private static int rarityColor(String rarity) {
        return switch (rarity) {
            case "Мифическая" -> ColorUtil.rgb(252, 84, 252);
            case "Легендарная" -> ColorUtil.rgb(0, 128, 250);
            case "Эпическая" -> ColorUtil.rgb(231, 0, 250);
            default -> ColorUtil.rgb(243, 151, 250);
        };
    }

    private static Integer parseTimer(String name) {
        Matcher matcher = TIMER.matcher(name);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double flatDistSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private static boolean isHolyworld() {
        var server = MinecraftContext.mc.getCurrentServer();
        return server != null && server.ip.toLowerCase(Locale.ROOT).contains("holy");
    }
}
