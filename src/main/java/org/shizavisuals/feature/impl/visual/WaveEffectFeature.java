package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.AttackEvent;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.lifecycle.WorldJoinEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.packet.PacketReceiveEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.MultiSelectSetting;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.world.WorldMeshRenderer;
import org.shizavisuals.utils.render.world.WorldMeshRenderer.Line;
import org.shizavisuals.utils.render.world.WorldMeshRenderer.PlaneRect;
import org.shizavisuals.utils.render.world.WorldMeshRenderer.WorldMesh;

import java.util.ArrayList;
import java.util.List;

public final class WaveEffectFeature extends Feature implements MinecraftContext {
    private static final float MAX_RADIUS = 15.0F;
    private static final float WAVE_THICKNESS = 1.25F;
    private static final long DURATION_MS = 1500L;
    private static final int MAX_ACTIVE_WAVES = 4;
    private static final Vec3 AXIS_X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 NEGATIVE_AXIS_X = new Vec3(-1.0D, 0.0D, 0.0D);
    private static final Vec3 AXIS_Y = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 NEGATIVE_AXIS_Y = new Vec3(0.0D, -1.0D, 0.0D);
    private static final Vec3 AXIS_Z = new Vec3(0.0D, 0.0D, 1.0D);
    private static final Vec3 NEGATIVE_AXIS_Z = new Vec3(0.0D, 0.0D, -1.0D);

    private final MultiSelectSetting display = register(new MultiSelectSetting(
            "Display",
            List.of("Attack", "Totem"),
            "Attack", "Jump", "Totem"
    ));
    private final BooleanSetting syncWithTheme = register(new BooleanSetting("Sync Theme", true));
    private final ColorSetting attackColor = register(new ColorSetting("Attack Color", 0xFF6C5CE7)
            .visibleWhen(() -> !this.display.isSelected("Attack") || this.syncWithTheme.getValue()));
    private final ColorSetting jumpColor = register(new ColorSetting("Jump Color", 0xFF6C5CE7)
            .visibleWhen(() -> !this.display.isSelected("Jump") || this.syncWithTheme.getValue()));
    private final ColorSetting totemColor = register(new ColorSetting("Totem Color", 0xFFFFD700)
            .visibleWhen(() -> !this.display.isSelected("Totem") || this.syncWithTheme.getValue()));

    private final List<Wave> waves = new ArrayList<>();
    private final List<PlaneRect> fills = new ArrayList<>(1024);
    private final List<Line> outlines = new ArrayList<>(2048);
    private volatile long worldGeneration;
    private boolean wasOnGround = true;

    public WaveEffectFeature() {
        super("WaveEffect", "Волна из блоков: атака, прыжок, тотем", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static WaveEffectFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(WaveEffectFeature.class);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || !this.display.isSelected("Attack")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.crosshairPickEntity == null) {
            return;
        }
        spawn(mc.crosshairPickEntity.position(), triggerColor(this.attackColor.getValue()));
    }

    public static void onEntityAttacked(Vec3 targetPos) {
        WaveEffectFeature feature = getEnabled();
        if (feature == null || !feature.display.isSelected("Attack")) {
            return;
        }
        feature.spawn(targetPos, feature.triggerColor(feature.attackColor.getValue()));
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        this.waves.removeIf(wave -> wave.progress() >= 1.0F);
        if (!this.display.isSelected("Jump")) {
            this.wasOnGround = true;
            return;
        }
        Minecraft mc = event.getClient();
        if (mc.player == null) {
            return;
        }
        boolean onGround = mc.player.onGround();
        if (this.wasOnGround && !onGround && mc.player.getDeltaMovement().y > 0) {
            this.spawn(mc.player.position(), this.triggerColor(this.jumpColor.getValue()));
        }
        this.wasOnGround = onGround;
    }

    @EventTarget
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPhase() != PacketReceiveEvent.Phase.PRE
                || !this.isEnabled()
                || !(event.getPacket() instanceof ClientboundEntityEventPacket packet)
                || packet.getEventId() != 35) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Connection connection = event.getConnection();
        long generation = this.worldGeneration;
        if (mc.isSameThread()) {
            handleTotemPacket(mc, packet, connection, generation);
        } else {
            mc.execute(() -> handleTotemPacket(mc, packet, connection, generation));
        }
    }

    private void handleTotemPacket(Minecraft mc, ClientboundEntityEventPacket packet,
                                   Connection connection, long generation) {
        if (!this.isEnabled() || !this.display.isSelected("Totem")
                || generation != this.worldGeneration || !isCurrentConnection(connection)
                || mc.player == null || mc.level == null || packet.getEntity(mc.level) != mc.player) {
            return;
        }
        spawn(mc.player.position(), this.triggerColor(this.totemColor.getValue()), connection, generation);
    }

    @EventTarget
    public void onWorldJoin(WorldJoinEvent event) {
        this.worldGeneration++;
        this.waves.clear();
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        this.worldGeneration++;
        this.waves.clear();
    }

    @Override
    protected void onEnable() {
        this.worldGeneration++;
        this.waves.clear();
        this.wasOnGround = true;
    }

    @Override
    protected void onDisable() {
        this.worldGeneration++;
        this.waves.clear();
        this.wasOnGround = true;
    }

    private int triggerColor(int settingColor) {
        return this.syncWithTheme.getValue() ? Theme.getAccent() : settingColor;
    }

    private void spawn(Vec3 pos, int color) {
        spawn(pos, color, null, this.worldGeneration);
    }

    private void spawn(Vec3 pos, int color, Connection connection, long generation) {
        if (pos == null || !this.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Runnable action = () -> {
            if (!this.isEnabled() || generation != this.worldGeneration
                    || !isCurrentConnection(connection) || mc.level == null) {
                return;
            }
            while (this.waves.size() >= MAX_ACTIVE_WAVES) {
                this.waves.remove(0);
            }
            this.waves.add(new Wave(mc, pos, color));
        };
        if (mc.isSameThread()) {
            action.run();
        } else {
            mc.execute(action);
        }
    }

    public void renderWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (!this.isEnabled() || mc.player == null || mc.level == null || this.waves.isEmpty()) {
            return;
        }
        this.fills.clear();
        this.outlines.clear();
        try {
            Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
            for (Wave wave : this.waves) {
                wave.render(cameraPos, this.fills, this.outlines);
            }
            if (!this.outlines.isEmpty()) {
                WorldMeshRenderer.render(new WorldMesh(this.outlines, List.of(), this.fills), false);
            }
        } finally {
            this.fills.clear();
            this.outlines.clear();
        }
    }

    private final class Wave {
        private final Vec3 centerPos;
        private final int color;
        private final long startTime;
        private final List<BlockMark> blocks = new ArrayList<>();

        private Wave(Minecraft mc, Vec3 pos, int color) {
            this.centerPos = pos;
            this.color = color;
            this.startTime = System.currentTimeMillis();
            if (mc.level == null || pos == null) {
                return;
            }
            BlockPos centerBlock = BlockPos.containing(pos);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = -15; x <= 15; x++) {
                for (int z = -15; z <= 15; z++) {
                    double distance = Math.sqrt(x * (double) x + z * (double) z);
                    if (distance > 15.0D || distance < 1.0D) {
                        continue;
                    }
                    for (int y = 3; y >= -10; y--) {
                        cursor.set(centerBlock.getX() + x, centerBlock.getY() + y, centerBlock.getZ() + z);
                        BlockState state = mc.level.getBlockState(cursor);
                        if (state.isAir()) {
                            continue;
                        }
                        if (state.getCollisionShape(mc.level, cursor).isEmpty()
                                && state.getFluidState().isEmpty()) {
                            continue;
                        }
                        this.blocks.add(new BlockMark(cursor.immutable(), distance));
                        break;
                    }
                }
            }
        }

        private float progress() {
            return Math.clamp((System.currentTimeMillis() - this.startTime) / (float) DURATION_MS, 0.0F, 1.0F);
        }

        private void render(Vec3 camPos, List<PlaneRect> fills, List<Line> outlines) {
            float progress = progress();
            if (progress >= 1.0F || this.blocks.isEmpty()) {
                return;
            }

            float currentRadius = progress * MAX_RADIUS;
            float fadeOut = progress > 0.8F ? (1.0F - (progress - 0.8F) / 0.2F) : 1.0F;

            for (BlockMark block : this.blocks) {
                float distDiff = Math.abs((float) block.distance() - currentRadius);
                if (distDiff > WAVE_THICKNESS) {
                    continue;
                }

                float alpha = (float) Math.pow(1.0F - distDiff / WAVE_THICKNESS, 0.55D) * fadeOut;
                if (alpha <= 0.04F) {
                    continue;
                }

                float scale = progress > 0.8F ? (0.65F + 0.35F * fadeOut) : 1.0F;
                double half = 0.5D * scale;
                double cx = block.pos().getX() + 0.5D;
                double cy = block.pos().getY() + 0.5D;
                double cz = block.pos().getZ() + 0.5D;

                int lineColor = ColorUtil.multiplyAlpha(this.color, alpha);
                int fillColor = ColorUtil.multiplyAlpha(this.color, alpha * 0.3F);
                Vec3 center = new Vec3(cx, cy, cz);

                addFace(fills, camPos, center, AXIS_X, AXIS_Y, AXIS_Z, half, fillColor);
                addFace(fills, camPos, center, NEGATIVE_AXIS_X, AXIS_Y, AXIS_Z, half, fillColor);
                addFace(fills, camPos, center, AXIS_Y, AXIS_X, AXIS_Z, half, fillColor);
                addFace(fills, camPos, center, NEGATIVE_AXIS_Y, AXIS_X, AXIS_Z, half, fillColor);
                addFace(fills, camPos, center, AXIS_Z, AXIS_X, AXIS_Y, half, fillColor);
                addFace(fills, camPos, center, NEGATIVE_AXIS_Z, AXIS_X, AXIS_Y, half, fillColor);

                Vec3 c1 = new Vec3(cx - half, cy - half, cz - half);
                Vec3 c2 = new Vec3(cx + half, cy - half, cz - half);
                Vec3 c3 = new Vec3(cx + half, cy - half, cz + half);
                Vec3 c4 = new Vec3(cx - half, cy - half, cz + half);
                Vec3 c5 = new Vec3(cx - half, cy + half, cz - half);
                Vec3 c6 = new Vec3(cx + half, cy + half, cz - half);
                Vec3 c7 = new Vec3(cx + half, cy + half, cz + half);
                Vec3 c8 = new Vec3(cx - half, cy + half, cz + half);
                outlines.add(new Line(c1, c2, lineColor));
                outlines.add(new Line(c2, c3, lineColor));
                outlines.add(new Line(c3, c4, lineColor));
                outlines.add(new Line(c4, c1, lineColor));
                outlines.add(new Line(c5, c6, lineColor));
                outlines.add(new Line(c6, c7, lineColor));
                outlines.add(new Line(c7, c8, lineColor));
                outlines.add(new Line(c8, c5, lineColor));
                outlines.add(new Line(c1, c5, lineColor));
                outlines.add(new Line(c2, c6, lineColor));
                outlines.add(new Line(c3, c7, lineColor));
                outlines.add(new Line(c4, c8, lineColor));
            }
        }

        private static final double FILL_EPSILON = 0.005D;

        private static void addFace(List<PlaneRect> fills, Vec3 camPos, Vec3 center,
                                    Vec3 outward, Vec3 tangentA, Vec3 tangentB, double half, int color) {
            double surfaceOffset = half + FILL_EPSILON;
            Vec3 faceCenter = new Vec3(
                    center.x + outward.x * surfaceOffset,
                    center.y + outward.y * surfaceOffset,
                    center.z + outward.z * surfaceOffset
            );
            double toCameraX = camPos.x - faceCenter.x;
            double toCameraY = camPos.y - faceCenter.y;
            double toCameraZ = camPos.z - faceCenter.z;
            if (toCameraX * outward.x + toCameraY * outward.y + toCameraZ * outward.z <= 0.0D) {
                return;
            }
            fills.add(new PlaneRect(faceCenter, tangentA, tangentB, half, half, color));
        }
    }

    private record BlockMark(BlockPos pos, double distance) {}
}
