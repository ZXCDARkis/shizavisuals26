package org.shizavisuals.command.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.command.ClientCommand;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.packet.PacketReceiveEvent;
import org.shizavisuals.event.events.render.Render2DEvent;
import org.shizavisuals.menu.core.MenuConfigStore;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.shizavisuals.utils.text.ChatUtil;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.shizavisuals.utils.render.Textures;

public final class GpsCommand extends ClientCommand implements MinecraftContext {
    private static final int DIVIDER_COLOR = ColorUtil.rgba(255, 255, 255, 25);
    private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final Pattern COORD_PATTERN = Pattern.compile("\\[([-\\d]+)\\s+([-\\d]+)\\s+([-\\d]+)\\]");
    private static final long EVENT_TIMEOUT_MS = 3000L;

    private static final float SCALE = 1.2F;
    private static final float PILL_HEIGHT = 36.0F;
    private static final float PADDING = 10.0F;
    private static final float ICON_SIZE = 16.0F;
    private static final float GAP = 8.0F;
    private static final float DIVIDER_HEIGHT = 16.0F;
    private static final float NAME_SIZE = 12.0F;
    private static final float DISTANCE_SIZE = 10.0F;
    private static final float DOT_SIZE = 6.0F;
    private static final float DOT_GAP = 8.0F;

    private String markName;
    private Double targetX;
    private Double targetZ;

    private boolean waitingForEvent;
    private boolean receivedEventHeader;
    private long eventRequestAt;

    public GpsCommand() {
        super("gps", "GPS marker: set <x> <z> | set w/m/z/e/r | clear | info", ":round_pushpin:");
        loadMarker();
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        builder.executes(context -> {
            ChatUtil.usage("gps set <x> <z>  •  set w/m/z/e/r  •  clear  •  info");
            return 1;
        });
        builder.then(LiteralArgumentBuilder.literal("set")
                .then(RequiredArgumentBuilder.<Object, Double>argument("x", DoubleArgumentType.doubleArg())
                        .then(RequiredArgumentBuilder.<Object, Double>argument("z", DoubleArgumentType.doubleArg())
                                .executes(context -> setMarker("GPS",
                                        DoubleArgumentType.getDouble(context, "x"),
                                        DoubleArgumentType.getDouble(context, "z")))))
                .then(LiteralArgumentBuilder.literal("w").executes(context -> setMarker("Warden", 2000, 2000)))
                .then(LiteralArgumentBuilder.literal("m").executes(context -> setMarker("Copper", -2000, -2000)))
                .then(LiteralArgumentBuilder.literal("z").executes(context -> setMarker("Zamok", 0, 0)))
                .then(LiteralArgumentBuilder.literal("e").executes(context -> requestEvent()))
                .then(LiteralArgumentBuilder.literal("r").executes(context -> setRandom())));
        builder.then(LiteralArgumentBuilder.literal("clear").executes(context -> clearMarker()));
        builder.then(LiteralArgumentBuilder.literal("info").executes(context -> showInfo()));
    }

    private int setRandom() {
        double x = (Math.random() * (2500 - 1800) + 1800) * (Math.random() < 0.5 ? 1 : -1);
        double z = (Math.random() * (2500 - 1800) + 1800) * (Math.random() < 0.5 ? 1 : -1);
        return setMarker("Random", Math.round(x), Math.round(z));
    }

    private int requestEvent() {
        if (player() == null) {
            return 0;
        }
        this.waitingForEvent = true;
        this.receivedEventHeader = false;
        this.eventRequestAt = System.currentTimeMillis();
        player().connection.sendCommand("event delay");
        return 1;
    }

    @EventTarget
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!this.waitingForEvent || mc.player == null || mc.level == null) {
            return;
        }
        if (event.getPhase() != PacketReceiveEvent.Phase.PRE
                || !(event.getPacket() instanceof ClientboundSystemChatPacket chatPacket)) {
            return;
        }

        if (System.currentTimeMillis() - this.eventRequestAt > EVENT_TIMEOUT_MS) {
            this.waitingForEvent = false;
            this.receivedEventHeader = false;
            mc.execute(() -> ChatUtil.error("Event not found  •  GPS not changed"));
            return;
        }

        String content = chatPacket.content().getString();
        if (content.contains("[Ивенты]")) {
            this.receivedEventHeader = true;
            return;
        }
        if (!this.receivedEventHeader) {
            return;
        }

        Matcher matcher = COORD_PATTERN.matcher(content);
        if (matcher.find()) {
            double x = Double.parseDouble(matcher.group(1));
            double z = Double.parseDouble(matcher.group(3));
            this.waitingForEvent = false;
            this.receivedEventHeader = false;
            mc.execute(() -> setMarker("Event", x, z));
        } else if (content.contains("следующего ивента")) {
            this.waitingForEvent = false;
            this.receivedEventHeader = false;
            mc.execute(() -> ChatUtil.error("Event has not started yet  •  GPS not changed"));
        }
    }

    private int setMarker(String name, double x, double z) {
        this.markName = name;
        this.targetX = x;
        this.targetZ = z;
        persistMarker();
        ChatUtil.success("GPS marker " + name + "  •  X " + format(x) + "  •  Z " + format(z));
        if (player() != null) {
            ChatUtil.info("Distance  •  " + format(distanceTo(player())) + " blocks");
        }
        return 1;
    }

    private int clearMarker() {
        if (!hasMarker()) {
            ChatUtil.error("No GPS marker set");
            return 0;
        }
        this.markName = null;
        this.targetX = null;
        this.targetZ = null;
        persistMarker();
        ChatUtil.success("GPS marker cleared");
        return 1;
    }

    private int showInfo() {
        if (!hasMarker()) {
            ChatUtil.error("No GPS marker set");
            return 0;
        }
        ChatUtil.header("GPS marker  •  " + this.markName);
        ChatUtil.entry(":round_pushpin:", "Coordinates",
                "X " + format(this.targetX) + "  •  Z " + format(this.targetZ));
        if (player() != null) {
            ChatUtil.entry(":compass:", "Navigation",
                    format(distanceTo(player())) + " blocks  •  " + directionTo(player()));
        }
        return 1;
    }

    private boolean hasMarker() {
        return this.targetX != null && this.targetZ != null;
    }

    private double distanceTo(LocalPlayer player) {
        double dx = this.targetX - player.getX();
        double dz = this.targetZ - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private String directionTo(LocalPlayer player) {
        double dx = this.targetX - player.getX();
        double dz = this.targetZ - player.getZ();
        double bearing = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0D) % 360.0D;
        return DIRECTIONS[(int) Math.round(bearing / 45.0D) % DIRECTIONS.length];
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!hasMarker() || mc.player == null || mc.level == null) {
            return;
        }

        Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, anchorPos());
        if (anchor == null) {
            return;
        }

        float unit = (float) (SCALE / mc.getWindow().getGuiScale());
        MsdfFont font = UiFonts.sfProDisplay();
        float nameSize = NAME_SIZE * unit;
        float distanceSize = DISTANCE_SIZE * unit;
        String distance = formatDistance(distanceTo(mc.player));

        float nameWidth = font.measureWidth(this.markName, nameSize, nameSize * UiFontStyle.MEDIUM.letterSpacingEm());
        float distanceWidth = font.measureWidth(distance, distanceSize, distanceSize * UiFontStyle.REGULAR.letterSpacingEm());
        float dividerWidth = Math.max(0.5F, 0.5F * unit);

        float pillWidth = (PADDING + ICON_SIZE + GAP) * unit + nameWidth
                + GAP * unit + dividerWidth + GAP * unit
                + distanceWidth + PADDING * unit;
        float pillHeight = PILL_HEIGHT * unit;
        float dotSize = DOT_SIZE * unit;

        float dotX = anchor.x() - dotSize / 2.0F;
        float dotY = anchor.y() - dotSize / 2.0F;
        float pillX = anchor.x() - pillWidth / 2.0F;
        float pillY = dotY - DOT_GAP * unit - pillHeight;

        Render2DUtil.rect(dotX, dotY, dotSize, dotSize)
                .color(Theme.getAccent())
                .radius(dotSize / 2.0F)
                .draw();

        Render2DUtil.rect(pillX, pillY, pillWidth, pillHeight)
                .color(Theme.Colors.BACKGROUND_PRIMARY_50)
                .radius(pillHeight / 2.0F)
                .border(Math.max(0.5F, 0.5F * unit), Theme.Colors.OUTLINES_SMALL)
                .blur(8.0F * unit)
                .draw();

        float centerY = pillY + pillHeight / 2.0F;
        float cursor = pillX + PADDING * unit;

        float iconSize = ICON_SIZE * unit;
        Render2DUtil.texture(cursor, centerY - iconSize / 2.0F, iconSize, iconSize, Textures.Icons.GIFT)
                .color(Theme.getAccent())
                .draw();
        cursor += iconSize + GAP * unit;

        Render2DUtil.text(cursor, font.centeredTextY(centerY, nameSize), nameSize, this.markName)
                .style(UiFontStyle.MEDIUM)
                .color(Theme.Colors.TEXT_TEXT)
                .draw();
        cursor += nameWidth + GAP * unit;

        Render2DUtil.rect(cursor, centerY - DIVIDER_HEIGHT * unit / 2.0F, dividerWidth, DIVIDER_HEIGHT * unit)
                .color(DIVIDER_COLOR)
                .draw();
        cursor += dividerWidth + GAP * unit;

        Render2DUtil.text(cursor, font.centeredTextY(centerY, distanceSize), distanceSize, distance)
                .style(UiFontStyle.REGULAR)
                .color(Theme.Colors.TEXT_TEXT)
                .draw();
    }

    private Vec3 anchorPos() {
        int blockX = (int) Math.floor(this.targetX);
        int blockZ = (int) Math.floor(this.targetZ);
        if (mc.level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ))) {
            return new Vec3(this.targetX, mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ), this.targetZ);
        }
        return new Vec3(this.targetX, mc.player.getEyeY(), this.targetZ);
    }

    private static String formatDistance(double blocks) {
        if (blocks >= 1000.0D) {
            return String.format(Locale.ROOT, "%.1fkm", blocks / 1000.0D);
        }
        return Math.round(blocks) + "m";
    }

    private void loadMarker() {
        String name = MenuConfigStore.getString("gps.name", "");
        float x = MenuConfigStore.getFloat("gps.x", Float.NaN);
        float z = MenuConfigStore.getFloat("gps.z", Float.NaN);
        if (!name.isEmpty() && !Float.isNaN(x) && !Float.isNaN(z)) {
            this.markName = name;
            this.targetX = (double) x;
            this.targetZ = (double) z;
        }
    }

    private void persistMarker() {
        String name = this.markName == null ? "" : this.markName;
        double x = this.targetX == null ? Double.NaN : this.targetX;
        double z = this.targetZ == null ? Double.NaN : this.targetZ;
        MenuConfigStore.save(data -> {
            if (name.isEmpty() || Double.isNaN(x) || Double.isNaN(z)) {
                data.remove("gps.name");
                data.remove("gps.x");
                data.remove("gps.z");
            } else {
                data.addProperty("gps.name", name);
                data.addProperty("gps.x", x);
                data.addProperty("gps.z", z);
            }
        });
    }
}
