package org.shizavisuals.hud.island;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.utils.ColorUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MineStatus extends TimerStatus {
    private static final Pattern TIMER = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final double RANGE_SQR = 900.0D;

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

    private boolean refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null || !isHolyworld()) {
            return false;
        }
        Vec3 playerPos = mc.player.position();
        List<ArmorStand> stands = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.isAlive() || stand.getCustomName() == null) {
                continue;
            }
            if (flatDistSqr(stand.position(), playerPos) > RANGE_SQR) {
                continue;
            }
            stands.add(stand);
        }
        if (stands.isEmpty()) {
            return false;
        }
        ArmorStand anchor = stands.stream()
                .filter(stand -> containsShaftKeyword(nameOf(stand)))
                .min(Comparator.comparingDouble(stand -> flatDistSqr(stand.position(), playerPos)))
                .orElse(null);
        if (anchor == null) {
            return false;
        }
        List<ArmorStand> nearby = new ArrayList<>();
        for (ArmorStand stand : stands) {
            if (flatDistSqr(stand.position(), anchor.position()) <= 64.0D) {
                nearby.add(stand);
            }
        }

        String rarity = null;
        for (ArmorStand stand : nearby) {
            String rarityName = matchRarity(nameOf(stand));
            if (rarityName != null) {
                rarity = rarityName;
                break;
            }
        }
        if (rarity == null) {
            return false;
        }
        Integer seconds = null;
        for (ArmorStand stand : nearby) {
            seconds = parseTimer(nameOf(stand));
            if (seconds != null) {
                break;
            }
        }
        if (seconds == null || seconds <= 0) {
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
