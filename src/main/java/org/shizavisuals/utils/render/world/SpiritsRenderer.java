package org.shizavisuals.utils.render.world;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.shizavisuals.feature.impl.visual.SpiritsFeature;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.esp.EspSpriteRenderer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Orbital spirits around the player. The implicit modes: idle orbit, shoulder
 * halo when standing still, comet stream behind sprinting flight, chase-and-dash
 * orbit around the last attacked entity, charge spiral while sneaking, nova
 * burst on a totem pop. Simulation runs a fixed-substep damped spring per
 * spirit; rendering goes through the shared esp_sprite additive pipeline with
 * four procedurally generated textures.
 */
public final class SpiritsRenderer {
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final int MAX_SPARKS = 900;
    private static final float SUBSTEP = 1.0f / 240.0f;
    private static final double MAX_SPIRIT_SPEED = 42.0;
    private static final double TELEPORT_DISTANCE = 12.0;
    private static final float RING_DURATION = 0.9f;
    private static final int MAX_RIBBON_POINTS = 170;
    // Worst case: 32 spirits' trails (2 x 160 segments x 6) plus links, pulses and
    // bodies - ~68k vertices, so the cap sits a little above; push() drops the rest.
    private static final int MESH_VERTEX_CAP = 71680;

    private static final int COLOR_NOVA = 0xFFD45A;
    private static final int COLOR_LIFE = 0x7CFF9B;
    private static final int COLOR_WHITE = 0xFFFFFF;

    private static final int[] PALETTE_FLAME = {0xFFF4B8, 0xFFB43C, 0xFF5A1F, 0xD81E3A};
    private static final int[] PALETTE_PHANTOM = {0x7DF9FF, 0xA9B8FF, 0xF2F7FF, 0x7DF9FF};
    private static final int[] PALETTE_VOID = {0x9B5CFF, 0x4B1BD6, 0xFF4FD8, 0x9B5CFF};

    private static final Identifier TEX_BLOOM = Identifier.parse("shizavisuals:spirits/bloom");
    private static final Identifier TEX_SPARKLE = Identifier.parse("shizavisuals:spirits/sparkle");
    private static final Identifier TEX_RING = Identifier.parse("shizavisuals:spirits/ring");
    private static final Identifier TEX_GLOW = Identifier.parse("shizavisuals:spirits/glow");

    private static boolean texturesReady;

    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();

    private enum Mood {NOVA, CHARGE, HUNT, STREAM, HALO, ORBIT}

    private final Random random = new Random();
    private final List<Spirit> spirits = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();

    private Mood mood = Mood.ORBIT;
    private float moodAge;
    private float time;
    private long lastFrameNanos;

    private boolean hasAnchor;
    private double px, py, pz;
    private double pvx, pvy, pvz;
    private float bodyHeight = 1.8f;
    private float yawRad;
    private boolean gliding;
    private float idleTime;

    private LivingEntity huntTarget;
    private float huntTimer;
    private float lastAttackAge = 99.0f;
    private int pendingDashes;
    private boolean hasHuntAnchor;
    private double hx, hy, hz;
    private double hvx, hvy, hvz;
    private float huntRadius = 1.0f;

    private float charge;
    private boolean charging;
    private boolean chargeFullAnnounced;

    private float novaTimer;
    private float novaGlow;

    private float linkAlpha;
    private float linkRange = 1.0f;

    private float ringAge = -1.0f;
    private float ringPower;
    private double ringX, ringY, ringZ;

    private final float[] ptX = new float[MAX_RIBBON_POINTS];
    private final float[] ptY = new float[MAX_RIBBON_POINTS];
    private final float[] ptZ = new float[MAX_RIBBON_POINTS];
    private final float[] ptT = new float[MAX_RIBBON_POINTS];
    private final float[] sideX = new float[MAX_RIBBON_POINTS];
    private final float[] sideY = new float[MAX_RIBBON_POINTS];
    private final float[] sideZ = new float[MAX_RIBBON_POINTS];

    // Camera state for the current frame; all geometry is emitted relative to it.
    private double camX, camY, camZ;
    private final Matrix4f viewPose = new Matrix4f();
    // World X/Z axes expressed in view space: grounded decals rotate with them.
    private final Vector4f groundEx = new Vector4f();
    private final Vector4f groundEz = new Vector4f();
    private final Vector4f view0 = new Vector4f();
    private final Vector4f view1 = new Vector4f();
    private final Vector4f viewSide = new Vector4f();
    private final Vector4f[] ribbonView = new Vector4f[MAX_RIBBON_POINTS];
    private final Vector4f[] ribbonSide = new Vector4f[MAX_RIBBON_POINTS];

    // Vertex budget of the mesh being assembled; guards against overflow.
    private int meshVertexCount;

    public SpiritsRenderer() {
        for (int i = 0; i < MAX_RIBBON_POINTS; i++) {
            ribbonView[i] = new Vector4f();
            ribbonSide[i] = new Vector4f();
        }
    }

    public void reset() {
        spirits.clear();
        sparks.clear();
        mood = Mood.ORBIT;
        moodAge = 0.0f;
        time = 0.0f;
        lastFrameNanos = 0L;
        hasAnchor = false;
        px = py = pz = 0.0;
        pvx = pvy = pvz = 0.0;
        idleTime = 0.0f;
        huntTarget = null;
        huntTimer = 0.0f;
        pendingDashes = 0;
        hasHuntAnchor = false;
        charge = 0.0f;
        charging = false;
        chargeFullAnnounced = false;
        novaTimer = 0.0f;
        novaGlow = 0.0f;
        linkAlpha = 0.0f;
        ringAge = -1.0f;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (!spirits.isEmpty()) {
                reset();
            }
            return;
        }
        if (huntTarget == null) {
            return;
        }

        boolean dead = huntTarget.isDeadOrDying() || huntTarget.getHealth() <= 0.0f;
        boolean vanishedAfterHit = huntTarget.isRemoved() && lastAttackAge < 1.0f;
        if (dead || vanishedAfterHit) {
            onHuntKill();
            huntTarget = null;
        } else if (huntTarget.isRemoved()
                || huntTarget.distanceToSqr(mc.player) > 24.0 * 24.0) {
            huntTarget = null;
        }
    }

    public void onAttack(Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity == Minecraft.getInstance().player) return;

        if (huntTarget != living) hasHuntAnchor = false;
        huntTarget = living;
        huntTimer = 4.0f;
        lastAttackAge = 0.0f;
        pendingDashes = Mth.clamp(spirits.size() / 3, 1, 4);
    }

    public void onTotemPop() {
        if (spirits.isEmpty()) return;

        novaTimer = 0.85f;
        novaGlow = 1.0f;
        charging = false;
        charge = 0.0f;

        for (Spirit s : spirits) {
            double[] dir = randomDirection(0.45);
            double speed = 10.0 + random.nextDouble() * 7.0;
            s.vx = dir[0] * speed;
            s.vy = dir[1] * speed;
            s.vz = dir[2] * speed;
            s.stun = 0.55f + random.nextFloat() * 0.25f;
            s.recover = 0.0f;
            s.dashTime = 0.0f;
            s.flash = 1.0f;
        }

        burst(px, py + bodyHeight * 0.55, pz, 150, 9.0f, COLOR_NOVA, COLOR_LIFE, 0.35f, true, 2.4f);
        spawnRing(px, py + 0.03, pz, 1.35f);
    }

    private void onHuntKill() {
        if (!hasHuntAnchor || huntTarget == null) return;
        double x = hx;
        double y = hy - huntTarget.getBbHeight() * 0.55;
        double z = hz;
        for (Spirit s : spirits) {
            s.flash = 1.0f;
            s.dashTime = 0.0f;
        }
        int n = Math.max(1, spirits.size());
        for (int i = 0; i < 80; i++) {
            double a = random.nextDouble() * TWO_PI;
            double r = random.nextDouble() * 1.4;
            Spark p = spark(x + Math.cos(a) * 0.2, y, z + Math.sin(a) * 0.2);
            if (p == null) break;
            p.vx = Math.cos(a) * r;
            p.vz = Math.sin(a) * r;
            p.vy = 3.5 + random.nextDouble() * 5.0;
            p.life = 1.1f + random.nextFloat() * 0.8f;
            p.size = 0.05f + random.nextFloat() * 0.07f;
            p.gravity = 4.0f;
            p.drag = 1.1f;
            p.star = random.nextFloat() < 0.35f;
            p.color = lerpColor(spiritColor0(n), COLOR_WHITE, random.nextFloat() * 0.5f);
        }
        spawnRing(x, y + 0.03, z, 0.9f);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 1.4f);
    }

    private void frame(float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0f : (now - lastFrameNanos) / 1.0E9f;
        lastFrameNanos = now;
        dt = Mth.clamp(dt, 0.0f, 0.05f);
        if (mc.isPaused()) dt = 0.0f;

        Vec3 pos = Render3DUtil.interpolatedPosition(player, tickDelta);
        bodyHeight = player.getBbHeight();
        yawRad = (float) Math.toRadians(player.getViewYRot(tickDelta));
        gliding = player.isFallFlying();

        boolean teleported = !hasAnchor
                || sq(pos.x - px, pos.y - py, pos.z - pz) > TELEPORT_DISTANCE * TELEPORT_DISTANCE;
        if (teleported) {
            pvx = pvy = pvz = 0.0;
        } else if (dt > 0.0f) {
            float a = 1.0f - (float) Math.exp(-dt * 10.0f);
            pvx += ((pos.x - px) / dt - pvx) * a;
            pvy += ((pos.y - py) / dt - pvy) * a;
            pvz += ((pos.z - pz) / dt - pvz) * a;
        }
        px = pos.x;
        py = pos.y;
        pz = pos.z;
        hasAnchor = true;

        syncCount();
        if (teleported) snapAll();
        if (dt <= 0.0f) return;

        time += dt;
        updateHunt(tickDelta, dt);
        updateStates(player, dt);
        simulate(dt);
        updateSparks(dt);

        if (ringAge >= 0.0f) {
            ringAge += dt;
            if (ringAge > RING_DURATION) ringAge = -1.0f;
        }
    }

    private void syncCount() {
        int want = Mth.clamp((int) getSafeCount(), 1, 32);
        boolean fresh = spirits.isEmpty();
        while (spirits.size() < want) {
            Spirit s = new Spirit(random);
            placeAtChest(s);
            s.spawnDelay = fresh ? 0.02f + spirits.size() * 0.075f : 0.01f;
            spirits.add(s);
        }
        while (spirits.size() > want) {
            Spirit s = spirits.remove(spirits.size() - 1);
            if (s.spawn > 0.2f) {
                burst(s.x, s.y, s.z, 10, 2.2f, spiritColor(spirits.size(), want + 1), COLOR_WHITE, 0.2f, true, 0.8f);
            }
        }
    }

    private void placeAtChest(Spirit s) {
        s.x = px + (random.nextDouble() - 0.5) * 0.1;
        s.y = py + bodyHeight * 0.6;
        s.z = pz + (random.nextDouble() - 0.5) * 0.1;
        s.vx = s.vy = s.vz = 0.0;
        s.trail.clear();
    }

    private void snapAll() {
        for (Spirit s : spirits) {
            placeAtChest(s);
            s.dashTime = 0.0f;
            s.stun = 0.0f;
        }
        sparks.clear();
    }

    private void updateHunt(float tickDelta, float dt) {
        lastAttackAge += dt;
        if (huntTimer > 0.0f) huntTimer -= dt;
        if (huntTarget == null || huntTimer <= 0.0f) {
            huntTarget = null;
            hasHuntAnchor = false;
            pendingDashes = 0;
            return;
        }

        Vec3 tp = Render3DUtil.interpolatedPosition(huntTarget, tickDelta);
        double cx = tp.x;
        double cy = tp.y + huntTarget.getBbHeight() * 0.55;
        double cz = tp.z;
        if (hasHuntAnchor) {
            float a = 1.0f - (float) Math.exp(-dt * 10.0f);
            hvx += ((cx - hx) / dt - hvx) * a;
            hvy += ((cy - hy) / dt - hvy) * a;
            hvz += ((cz - hz) / dt - hvz) * a;
        } else {
            hvx = hvy = hvz = 0.0;
        }
        hx = cx;
        hy = cy;
        hz = cz;
        hasHuntAnchor = true;
        huntRadius = huntTarget.getBbWidth() * 0.7f + 0.5f * configRadius();

        if (pendingDashes > 0) launchDashes();
    }

    private void launchDashes() {
        for (int k = 0; k < pendingDashes; k++) {
            Spirit best = null;
            double bestDist = Double.MAX_VALUE;
            for (Spirit s : spirits) {
                if (s.dashTime > 0.0f || s.spawn < 0.9f || s.stun > 0.0f) continue;
                double d = sq(s.x - hx, s.y - hy, s.z - hz);
                if (d < bestDist) {
                    bestDist = d;
                    best = s;
                }
            }
            if (best == null) break;

            double dx = hx - best.x, dy = hy - best.y, dz = hz - best.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.2) {
                double[] r = randomDirection(0.0);
                dx = r[0];
                dy = r[1];
                dz = r[2];
                len = 1.0;
            }
            dx /= len;
            dy /= len;
            dz /= len;
            double pierce = huntRadius * 0.9 + 0.6;
            best.dashX = hx + dx * pierce;
            best.dashY = hy + dy * pierce * 0.5;
            best.dashZ = hz + dz * pierce;
            best.dashTime = 0.32f;
            best.dashHit = false;
            best.vx += dx * 6.0;
            best.vy += dy * 6.0;
            best.vz += dz * 6.0;
        }
        pendingDashes = 0;
    }

    private void updateStates(LocalPlayer player, float dt) {
        double horizontal = Math.sqrt(pvx * pvx + pvz * pvz);
        boolean grounded = player.onGround();

        if (horizontal < 0.8 && grounded) idleTime += dt;
        else idleTime = 0.0f;

        updateCharge(player, horizontal, grounded, dt);

        if (novaTimer > 0.0f) novaTimer -= dt;
        novaGlow = Math.max(0.0f, novaGlow - dt * 0.55f);

        Mood next;
        if (novaTimer > 0.0f) next = Mood.NOVA;
        else if (charging) next = Mood.CHARGE;
        else if (huntTarget != null && hasHuntAnchor) next = Mood.HUNT;
        else if ((player.isSprinting() && horizontal > 4.5) || (gliding && horizontal > 3.0) || horizontal > 8.0) next = Mood.STREAM;
        else if (idleTime > 1.6f) next = Mood.HALO;
        else next = Mood.ORBIT;

        if (next != mood) {
            mood = next;
            moodAge = 0.0f;
        } else {
            moodAge += dt;
        }

        float linkTarget = switch (mood) {
            case HALO -> 1.0f;
            case CHARGE -> 0.75f;
            default -> 0.0f;
        };
        linkAlpha = approach(linkAlpha, linkTarget, dt * 2.5f);
    }

    private void updateCharge(LocalPlayer player, double horizontal, boolean grounded, float dt) {
        boolean want = player.isCrouching() && grounded && horizontal < 1.2 && novaTimer <= 0.0f;
        if (want) {
            charging = true;
            charge = Math.min(1.0f, charge + dt / 1.25f);

            if (charge >= 1.0f && !chargeFullAnnounced) {
                chargeFullAnnounced = true;
                for (Spirit s : spirits) s.flash = Math.max(s.flash, 0.6f);
                burst(px, py + bodyHeight * 0.45, pz, 24, 2.5f, spiritColor(0, spirits.size()), COLOR_WHITE, 0.6f, true, 0.8f);
                playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.6f);
            }

            if (random.nextFloat() < dt * 40.0f * charge * configSparkDensity()) {
                double a = random.nextDouble() * TWO_PI;
                double r = 0.6 + charge * 1.3;
                Spark p = spark(px + Math.cos(a) * r, py + 0.05, pz + Math.sin(a) * r);
                if (p != null) {
                    p.vx = -Math.cos(a) * r * 2.2;
                    p.vz = -Math.sin(a) * r * 2.2;
                    p.vy = 0.6 + random.nextDouble() * 1.2;
                    p.life = 0.45f;
                    p.size = 0.04f + random.nextFloat() * 0.03f;
                    p.gravity = 0.0f;
                    p.drag = 0.4f;
                    p.color = spiritColor(random.nextInt(Math.max(1, spirits.size())), spirits.size());
                }
            }
        } else {
            if (charging && charge > 0.3f) release(charge);
            charging = false;
            chargeFullAnnounced = false;
            charge = Math.max(0.0f, charge - dt * 4.0f);
        }
    }

    private void release(float power) {
        int n = spirits.size();
        for (int i = 0; i < n; i++) {
            Spirit s = spirits.get(i);
            double a = TWO_PI * i / Math.max(1, n) + random.nextDouble() * 0.3;
            double speed = 7.0 + power * 11.0;
            s.vx = Math.cos(a) * speed;
            s.vz = Math.sin(a) * speed;
            s.vy = 2.0 + power * 3.0;
            s.stun = 0.25f + power * 0.2f;
            s.recover = 0.0f;
            s.flash = 1.0f;
            s.dashTime = 0.0f;
        }

        int count = (int) ((30 + 70 * power) * configSparkDensity());
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * TWO_PI;
            double speed = (5.0 + random.nextDouble() * 6.0) * power;
            Spark p = spark(px + Math.cos(a) * 0.3, py + 0.1 + random.nextDouble() * 0.2, pz + Math.sin(a) * 0.3);
            if (p == null) break;
            p.vx = Math.cos(a) * speed;
            p.vz = Math.sin(a) * speed;
            p.vy = random.nextDouble() * 1.5;
            p.life = 0.6f + random.nextFloat() * 0.4f;
            p.size = 0.05f + random.nextFloat() * 0.05f;
            p.gravity = 2.0f;
            p.drag = 2.0f;
            p.star = random.nextFloat() < 0.25f;
            p.color = lerpColor(spiritColor(random.nextInt(Math.max(1, n)), n), COLOR_WHITE, random.nextFloat() * 0.4f);
        }

        spawnRing(px, py + 0.03, pz, 0.5f + power * 0.8f);
        playSound(SoundEvents.EVOKER_CAST_SPELL, 0.6f + power * 0.4f, 1.5f - power * 0.4f);
        charge = 0.0f;
    }

    private void simulate(float dt) {
        int n = spirits.size();
        if (n == 0) return;

        float baseK = 46.0f * (0.55f + 0.45f * configSpeed());
        float ramp = 0.3f + 0.7f * smooth(Math.min(1.0f, moodAge / 0.6f));
        float separation = 0.28f * configSize() * (1.0f - charge * 0.6f);
        float sepSq = separation * separation;

        for (int i = 0; i < n; i++) {
            formation(spirits.get(i), i, n);
        }
        linkRange = computeLinkRange(n);

        int steps = Math.max(1, Math.min(12, (int) Math.ceil(dt / SUBSTEP)));
        float h = dt / steps;

        for (int step = 0; step < steps; step++) {
            for (int i = 0; i < n; i++) {
                Spirit s = spirits.get(i);
                if (s.spawnDelay > 0.0f) continue;

                double ax, ay, az;
                if (s.dashTime > 0.0f) {
                    float k = 380.0f;
                    float c = 2.0f * Mth.sqrt(k) * 0.42f;
                    ax = k * (s.dashX - s.x) - c * s.vx;
                    ay = k * (s.dashY - s.y) - c * s.vy;
                    az = k * (s.dashZ - s.z) - c * s.vz;
                } else if (s.stun > 0.0f) {
                    ax = -1.2 * s.vx;
                    ay = -1.2 * s.vy - 6.0;
                    az = -1.2 * s.vz;
                } else {
                    float k = baseK * s.kMul * ramp * (0.12f + 0.88f * s.recover);
                    float c = 2.0f * Mth.sqrt(k) * 0.7f;
                    ax = k * (s.tx - s.x) + c * (s.tvx - s.vx);
                    ay = k * (s.ty - s.y) + c * (s.tvy - s.vy);
                    az = k * (s.tz - s.z) + c * (s.tvz - s.vz);
                }

                for (int j = 0; j < n; j++) {
                    if (j == i) continue;
                    Spirit o = spirits.get(j);
                    if (o.spawnDelay > 0.0f) continue;
                    double dx = s.x - o.x, dy = s.y - o.y, dz = s.z - o.z;
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 >= sepSq || d2 < 1.0E-8) continue;
                    double d = Math.sqrt(d2);
                    double f = (separation - d) / separation * 14.0 / d;
                    ax += dx * f;
                    ay += dy * f;
                    az += dz * f;
                }

                s.vx += ax * h;
                s.vy += ay * h;
                s.vz += az * h;
                double v = s.speed();
                if (v > MAX_SPIRIT_SPEED) {
                    double scale = MAX_SPIRIT_SPEED / v;
                    s.vx *= scale;
                    s.vy *= scale;
                    s.vz *= scale;
                }
                s.x += s.vx * h;
                s.y += s.vy * h;
                s.z += s.vz * h;

                if (s.dashTime > 0.0f && !s.dashHit && hasHuntAnchor) {
                    double hitR = Math.max(0.45, huntRadius * 0.45);
                    if (sq(s.x - hx, s.y - hy, s.z - hz) < hitR * hitR) {
                        s.dashHit = true;
                        s.flash = 1.0f;
                        dashImpact(s, i, n);
                    }
                }
            }
        }

        for (int i = 0; i < n; i++) {
            Spirit s = spirits.get(i);
            if (s.spawnDelay > 0.0f) {
                s.spawnDelay -= dt;
                placeAtChest(s);
                if (s.spawnDelay <= 0.0f) {
                    double[] dir = randomDirection(0.5);
                    s.vx = dir[0] * 4.0;
                    s.vy = dir[1] * 4.0;
                    s.vz = dir[2] * 4.0;
                    s.flash = 1.0f;
                    burst(s.x, s.y, s.z, 6, 1.8f, spiritColor(i, n), COLOR_WHITE, 0.3f, false, 0.6f);
                }
                continue;
            }

            s.spawn = Math.min(1.0f, s.spawn + dt * 2.2f);
            s.dashTime = Math.max(0.0f, s.dashTime - dt);
            if (s.stun > 0.0f) s.stun -= dt;
            else if (s.recover < 1.0f) s.recover = Math.min(1.0f, s.recover + dt / 0.9f);
            s.flash = Math.max(0.0f, s.flash - dt * 2.2f);
            s.flareRot += dt * (s.flareSpin + (float) s.speed() * 0.25f);

            s.trail.push(s.x, s.y, s.z, time);
            s.trail.trim(time, configTrailLength());

            emitEmbers(s, i, n, dt);
        }
    }

    private void formation(Spirit s, int i, int n) {
        float sp = configSpeed();
        float radius = configRadius();
        float t = time;
        float base = TWO_PI * i / n;
        double cx = px, cy = py + bodyHeight * 0.55, cz = pz;

        s.tvx = pvx;
        s.tvy = pvy;
        s.tvz = pvz;
        s.kMul = 1.0f;

        switch (mood) {
            case HALO -> {
                float a = t * 0.85f * sp + base;
                float r = 0.34f + 0.025f * n;
                s.tx = px + Mth.cos(a) * r;
                s.ty = py + bodyHeight + 0.3f + Mth.sin(t * 2.4f + base * 2.0f) * 0.035f;
                s.tz = pz + Mth.sin(a) * r;
                s.kMul = 0.9f;
            }
            case STREAM -> {
                double dx, dy, dz;
                double horizontal = Math.sqrt(pvx * pvx + pvz * pvz);
                if (gliding) {
                    double len = Math.sqrt(pvx * pvx + pvy * pvy + pvz * pvz);
                    if (len < 0.1) {
                        dx = -Mth.sin(yawRad); dy = 0.0; dz = Mth.cos(yawRad);
                    } else {
                        dx = pvx / len; dy = pvy / len; dz = pvz / len;
                    }
                } else if (horizontal > 0.1) {
                    dx = pvx / horizontal; dy = 0.0; dz = pvz / horizontal;
                } else {
                    dx = -Mth.sin(yawRad); dy = 0.0; dz = Mth.cos(yawRad);
                }
                double rx = -dz, ry = 0.0, rz = dx;
                double rl = Math.sqrt(rx * rx + rz * rz);
                if (rl < 1.0E-4) { rx = 1.0; rz = 0.0; rl = 1.0; }
                rx /= rl; rz /= rl;
                double ux = dy * rz - dz * ry;
                double uy = dz * rx - dx * rz;
                double uz = dx * ry - dy * rx;

                int k = i + 1;
                double back = 0.45 + k * 0.3 * (0.65 + radius * 0.3);
                float ang = t * 7.5f * sp + i * 1.7f;
                float rad = 0.1f + 0.045f * k;
                float c = Mth.cos(ang) * rad, si = Mth.sin(ang) * rad;
                s.tx = cx - dx * back + rx * c + ux * si;
                s.ty = cy - 0.05 - dy * back + ry * c + uy * si;
                s.tz = cz - dz * back + rz * c + uz * si;
                s.kMul = 1.6f;
            }
            case HUNT -> {
                float dir = (i & 1) == 0 ? 1.0f : -1.0f;
                float a = dir * t * 3.4f * sp * s.speedMul + base;
                orbit(s, hx, hy, hz, a, huntRadius * s.radiusMul, 0.35f + s.tilt * 1.2f, s.azimuth + t * 0.9f);
                s.tvx = hvx;
                s.tvy = hvy;
                s.tvz = hvz;
                s.kMul = 1.7f;
            }
            case CHARGE -> {
                float c = charge;
                float a = t * sp * (1.4f + c * 8.0f) + base;
                float r = radius * (1.0f - c * 0.7f) * (0.9f + 0.1f * Mth.sin(t * 5.0f + s.phase));
                s.tx = px + Mth.cos(a) * r;
                s.ty = py + 0.2f + c * bodyHeight * 0.42f + Mth.sin(a * 3.0f) * 0.04f * (1.0f - c);
                s.tz = pz + Mth.sin(a) * r;
                s.kMul = 2.2f;
            }
            case NOVA, ORBIT -> {
                float a = t * 1.35f * sp * s.speedMul + base;
                orbit(s, cx, cy, cz, a, radius * s.radiusMul, s.tilt, s.azimuth + t * 0.25f * sp);
                s.ty += Mth.sin(t * 1.6f + s.phase) * 0.12f;
                s.kMul = mood == Mood.NOVA ? 0.6f : 1.0f;
            }
        }

        float w = s.wander * (mood == Mood.HALO ? 0.4f : 1.0f);
        s.tx += Mth.sin(t * 1.9f + s.phase * 5.0f) * 0.05f * w;
        s.ty += Mth.sin(t * 2.3f + s.phase * 3.0f) * 0.04f * w;
        s.tz += Mth.cos(t * 1.7f + s.phase * 4.0f) * 0.05f * w;
    }

    private static void orbit(Spirit s, double cx, double cy, double cz, float angle, float r, float tilt, float azimuth) {
        double lx = Mth.cos(angle) * r;
        double lz = Mth.sin(angle) * r;
        double y1 = lz * Mth.sin(tilt);
        double z1 = lz * Mth.cos(tilt);
        float ca = Mth.cos(azimuth), sa = Mth.sin(azimuth);
        s.tx = cx + lx * ca - z1 * sa;
        s.ty = cy + y1;
        s.tz = cz + lx * sa + z1 * ca;
    }

    private float computeLinkRange(int n) {
        return switch (mood) {
            case HALO -> 2.0f * (0.34f + 0.025f * n) * Mth.sin((float) Math.PI / n) * 1.45f + 0.05f;
            case CHARGE -> 2.0f * configRadius() * (1.0f - charge * 0.7f) * Mth.sin((float) Math.PI / n) * 1.6f + 0.1f;
            default -> linkRange;
        };
    }

    private void dashImpact(Spirit s, int i, int n) {
        double v = Math.max(0.1, s.speed());
        int count = (int) (16 * configSparkDensity());
        for (int k = 0; k < count; k++) {
            Spark p = spark(s.x, s.y, s.z);
            if (p == null) break;
            double[] r = randomDirection(0.0);
            p.vx = s.vx / v * 3.0 + r[0] * 3.0;
            p.vy = s.vy / v * 3.0 + r[1] * 3.0 + 1.0;
            p.vz = s.vz / v * 3.0 + r[2] * 3.0;
            p.life = 0.35f + random.nextFloat() * 0.35f;
            p.size = 0.04f + random.nextFloat() * 0.05f;
            p.gravity = 5.0f;
            p.drag = 2.5f;
            p.star = random.nextFloat() < 0.3f;
            p.color = lerpColor(spiritColor(i, n), COLOR_WHITE, random.nextFloat() * 0.6f);
        }
    }

    private void emitEmbers(Spirit s, int i, int n, float dt) {
        if (s.spawn < 0.5f) return;
        float rate = (1.5f + (float) s.speed() * 0.9f) * configSparkDensity();
        s.sparkAccumulator += rate * dt;
        while (s.sparkAccumulator >= 1.0f) {
            s.sparkAccumulator -= 1.0f;
            Spark p = spark(
                    s.x + (random.nextDouble() - 0.5) * 0.08,
                    s.y + (random.nextDouble() - 0.5) * 0.08,
                    s.z + (random.nextDouble() - 0.5) * 0.08);
            if (p == null) return;
            p.vx = -s.vx * 0.04 + (random.nextDouble() - 0.5) * 0.35;
            p.vy = -s.vy * 0.04 + (random.nextDouble() - 0.5) * 0.35 + 0.25;
            p.vz = -s.vz * 0.04 + (random.nextDouble() - 0.5) * 0.35;
            p.life = 0.45f + random.nextFloat() * 0.45f;
            p.size = (0.03f + random.nextFloat() * 0.03f) * configSize();
            p.gravity = -0.35f;
            p.drag = 2.2f;
            p.star = random.nextFloat() < 0.1f;
            p.color = lerpColor(spiritColor(i, n), COLOR_WHITE, random.nextFloat() * 0.35f);
        }
    }

    private void updateSparks(float dt) {
        Iterator<Spark> it = sparks.iterator();
        while (it.hasNext()) {
            Spark p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            double damp = Math.exp(-p.drag * dt);
            p.vx *= damp;
            p.vy = p.vy * damp - p.gravity * dt;
            p.vz *= damp;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.rot += p.spin * dt;
        }
    }

    private void burst(double x, double y, double z, int count, float speed, int colorA, int colorB, float upBias, boolean stars, float life) {
        count = (int) (count * configSparkDensity());
        for (int i = 0; i < count; i++) {
            Spark p = spark(x, y, z);
            if (p == null) return;
            double[] dir = randomDirection(upBias);
            double v = speed * (0.35 + random.nextDouble() * 0.65);
            p.vx = dir[0] * v;
            p.vy = dir[1] * v;
            p.vz = dir[2] * v;
            p.life = life * (0.6f + random.nextFloat() * 0.6f);
            p.size = 0.04f + random.nextFloat() * 0.06f;
            p.gravity = 2.5f;
            p.drag = 1.8f;
            p.star = stars && random.nextFloat() < 0.4f;
            p.color = lerpColor(colorA, colorB, random.nextFloat() * 0.6f);
        }
    }

    private Spark spark(double x, double y, double z) {
        if (sparks.size() >= MAX_SPARKS) return null;
        Spark p = new Spark();
        p.x = x;
        p.y = y;
        p.z = z;
        p.life = 0.5f;
        p.rot = random.nextFloat() * TWO_PI;
        p.spin = (random.nextFloat() - 0.5f) * 8.0f;
        sparks.add(p);
        return p;
    }

    private void spawnRing(double x, double y, double z, float power) {
        ringX = x;
        ringY = y;
        ringZ = z;
        ringAge = 0.0f;
        ringPower = power;
    }

    private double[] randomDirection(double upBias) {
        double u = random.nextDouble() * TWO_PI;
        double v = random.nextDouble() * 2.0 - 1.0;
        double r = Math.sqrt(1.0 - v * v);
        double x = r * Math.cos(u), y = v + upBias, z = r * Math.sin(u);
        double len = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / len, y / len, z / len};
    }

    private void playSound(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        // Sim runs on the render thread; playback must land on the client thread.
        mc.execute(() -> mc.level.playLocalSound(player, sound, SoundSource.PLAYERS, volume, pitch));
    }

    private int spiritColor(int i, int n) {
        float pos = n <= 1 ? 0.0f : (float) i / n;
        int c = paletteColor(pos);
        if (novaGlow > 0.001f) c = lerpColor(c, COLOR_NOVA, novaGlow * 0.8f);
        return c | 0xFF000000;
    }

    /** Untinted by nova glow: used for burst particles before the nova fires. */
    private int spiritColor0(int n) {
        return paletteColor(n <= 1 ? 0.0f : 0.5f) | 0xFF000000;
    }

    private int tailColor(int i, int n) {
        float pos = (n <= 1 ? 0.0f : (float) i / n) + 0.35f;
        int c = paletteColor(pos);
        if (novaGlow > 0.001f) c = lerpColor(c, COLOR_LIFE, novaGlow * 0.6f);
        return c | 0xFF000000;
    }

    private int paletteColor(float pos) {
        SpiritsFeature feature = SpiritsFeature.getEnabled();
        String mode = feature != null ? feature.palette.getValue() : "Rainbow";
        return switch (mode) {
            case "Rainbow" -> hsv(fract(pos + time * 0.12f), 0.62f, 1.0f);
            case "Flame" -> gradient(PALETTE_FLAME, pingPong(pos + time * 0.2f + Mth.sin(time * 3.1f + pos * 9.0f) * 0.08f));
            case "Ghost" -> gradient(PALETTE_PHANTOM, fract(pos * 0.7f + time * 0.08f));
            case "Abyss" -> gradient(PALETTE_VOID, fract(pos + time * 0.1f));
            default -> {
                // feature is always present here: the renderer only lives while enabled.
                int a = feature.color1.getValue() & 0xFFFFFF;
                int b = feature.color2.getValue() & 0xFFFFFF;
                yield lerpColor(a, b, pingPong(pos + time * 0.15f));
            }
        };
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int gradient(int[] stops, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f) * (stops.length - 1);
        int i = Math.min((int) t, stops.length - 2);
        return lerpColor(stops[i], stops[i + 1], t - i);
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s;
        float hp = (h - Mth.floor(h / 6.0f) * 6.0f) * 6.0f;
        float x = c * (1.0f - Math.abs(hp % 2.0f - 1.0f));
        float r, g, b;
        if (hp < 1) { r = c; g = x; b = 0; }
        else if (hp < 2) { r = x; g = c; b = 0; }
        else if (hp < 3) { r = 0; g = c; b = x; }
        else if (hp < 4) { r = 0; g = x; b = c; }
        else if (hp < 5) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        float m = v - c;
        return ((int) ((r + m) * 255.0f) << 16) | ((int) ((g + m) * 255.0f) << 8) | (int) ((b + m) * 255.0f);
    }

    // ===== Settings accessors shield the sim from null-feature frames. =====

    private static float configSize() {
        SpiritsFeature f = SpiritsFeature.getEnabled();
        return f == null ? 1.0f : f.size.getValue().floatValue();
    }

    private static float configSpeed() {
        SpiritsFeature f = SpiritsFeature.getEnabled();
        return f == null ? 1.0f : f.speed.getValue().floatValue();
    }

    private static float configRadius() {
        SpiritsFeature f = SpiritsFeature.getEnabled();
        return f == null ? 1.15f : f.radius.getValue().floatValue();
    }

    private static float configTrailLength() {
        SpiritsFeature f = SpiritsFeature.getEnabled();
        return f == null ? 0.55f : f.trailLength.getValue().floatValue();
    }

    private static float configTrailWidth() {
        SpiritsFeature f = SpiritsFeature.getEnabled();
        return f == null ? 1.0f : f.trailWidth.getValue().floatValue();
    }

    private static float configSparkDensity() {
        SpiritsFeature f = SpiritsFeature.getEnabled();
        return f == null ? 1.0f : f.sparkDensity.getValue().floatValue();
    }

    private static double getSafeCount() {
        SpiritsFeature f = SpiritsFeature.getEnabled();
        return f == null ? 8.0D : f.count.getValue();
    }

    // ===== Rendering =====

    public void render(float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        SpiritsFeature feature = SpiritsFeature.getEnabled();
        if (mc.player == null || mc.level == null || feature == null) {
            return;
        }
        // The whole effect is contained so a failure never breaks the world pass.
        try {
            ensureTextures();
            frame(tickDelta);

            var target = mc.gameRenderer.mainRenderTarget();
            GpuTextureView colorView = target != null ? target.getColorTextureView() : null;
            if (colorView == null) {
                return;
            }
            Camera camera = mc.gameRenderer.mainCamera();
            if (camera == null) {
                return;
            }
            Vec3 cameraPos = camera.position();
            this.camX = cameraPos.x;
            this.camY = cameraPos.y;
            this.camZ = cameraPos.z;
            this.viewPose.set(Render3DUtil.cameraViewPose(camera));
            this.groundEx.set(1.0f, 0.0f, 0.0f, 0.0f).mul(this.viewPose);
            this.groundEz.set(0.0f, 0.0f, 1.0f, 0.0f).mul(this.viewPose);

            float bright = feature.brightness.getValue().floatValue();
            float size = feature.size.getValue().floatValue();
            float trailWidth = feature.trailWidth.getValue().floatValue() * size;

            if (charge > 0.01f || ringAge >= 0.0f) {
                drawPass(mc, TEX_GLOW, target, bright, builder -> drawGroundGlow(builder, bright));
                drawPass(mc, TEX_RING, target, bright, builder -> drawGroundRings(builder, bright));
            }
            drawPass(mc, TEX_BLOOM, target, bright, builder -> {
                drawTrails(builder, bright, trailWidth);
                drawLinks(builder, bright);
                drawBodies(builder, bright, size);
                drawSparks(builder, bright, false);
            });
            drawPass(mc, TEX_SPARKLE, target, bright, builder -> {
                drawFlares(builder, bright, size);
                drawSparks(builder, bright, true);
            });
        } catch (Throwable ignored) {
        }
    }

    private void drawPass(Minecraft mc,
                          Identifier textureId,
                          com.mojang.blaze3d.pipeline.RenderTarget target,
                          float brightness,
                          java.util.function.Consumer<BufferBuilder> consumer) {
        AbstractTexture texture = mc.getTextureManager().getTexture(textureId);
        if (texture == null || texture.getTextureView() == null) {
            return;
        }

        this.meshVertexCount = 0;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(
                MESH_VERTEX_CAP * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            consumer.accept(builder);
            if (this.meshVertexCount == 0) {
                return;
            }

            int vertexCount = this.meshVertexCount;
            GpuBuffer vertexBuffer = null;
            GpuBuffer uniformBuffer = null;
            try {
                MeshData mesh = builder.buildOrThrow();
                try {
                    var device = RenderSystem.getDevice();
                    vertexBuffer = device.createBuffer(
                            () -> "ShizaVisuals Spirits Vertices",
                            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                            mesh.vertexBuffer()
                    );
                    uniformBuffer = uploadBrightness(brightness);

                    GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                    GpuTextureView depthView = target.getDepthTextureView();
                    // Depth-tested so spirits hide behind terrain like world geometry.
                    try (RenderPass pass = depthView != null
                            ? device.createCommandEncoder().createRenderPass(
                            () -> "ShizaVisuals Spirits", target.getColorTextureView(),
                            java.util.Optional.empty(), depthView, java.util.OptionalDouble.empty())
                            : device.createCommandEncoder().createRenderPass(
                            () -> "ShizaVisuals Spirits", target.getColorTextureView(),
                            java.util.Optional.empty())) {
                        pass.setPipeline(EspSpriteRenderer.additivePipeline());
                        pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                        pass.setUniform("EspSpriteUniforms", uniformBuffer);
                        pass.bindTexture("EspSpriteSampler", texture.getTextureView(), sampler);
                        pass.setVertexBuffer(0, vertexBuffer.slice());
                        pass.draw(vertexCount, 1, 0, 0);
                    }
                } finally {
                    mesh.close();
                }
            } catch (Throwable ignored) {
            } finally {
                if (uniformBuffer != null) {
                    uniformBuffer.close();
                }
                if (vertexBuffer != null) {
                    vertexBuffer.close();
                }
            }
        }
    }

    private GpuBuffer uploadBrightness(float brightness) {
        var device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(
                () -> "ShizaVisuals Spirits UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE
        );
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putVec4(brightness, 0.0F, 0.0F, 0.0F)
                    .get();
            device.createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
        return buffer;
    }

    private void viewTo(Vector4f dst, double wx, double wy, double wz) {
        dst.set((float) (wx - this.camX), (float) (wy - this.camY), (float) (wz - this.camZ), 1.0F)
                .mul(this.viewPose);
    }

    private void viewRel(Vector4f dst, float rx, float ry, float rz) {
        dst.set(rx, ry, rz, 1.0F).mul(this.viewPose);
    }

    private void push(BufferBuilder buf, float x, float y, float z, float u, float v, int color, float alpha) {
        if (alpha <= 0.004f || this.meshVertexCount >= MESH_VERTEX_CAP) {
            return;
        }
        this.meshVertexCount++;
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        buf.addVertex(x, y, z).setUv(u, v).setColor((color & 0xFFFFFF) | (a << 24));
    }

    /** Camera-facing quad in view space with an optional roll. */
    private void billboard(BufferBuilder buf, float rx, float ry, float rz, float size, float roll, int color, float alpha) {
        if (alpha <= 0.004f || size <= 0.001f) return;
        viewRel(view0, rx, ry, rz);
        float c = Mth.cos(roll), s = Mth.sin(roll);
        float x00 = -size * c + size * s;
        float y00 = -size * s - size * c;
        float x01 = -size * c - size * s;
        float y01 = -size * s + size * c;
        float x11 = size * c - size * s;
        float y11 = size * s + size * c;
        float x10 = size * c + size * s;
        float y10 = size * s - size * c;
        push(buf, view0.x + x00, view0.y + y00, view0.z, 0.0f, 0.0f, color, alpha);
        push(buf, view0.x + x01, view0.y + y01, view0.z, 0.0f, 1.0f, color, alpha);
        push(buf, view0.x + x11, view0.y + y11, view0.z, 1.0f, 1.0f, color, alpha);
        push(buf, view0.x + x00, view0.y + y00, view0.z, 0.0f, 0.0f, color, alpha);
        push(buf, view0.x + x11, view0.y + y11, view0.z, 1.0f, 1.0f, color, alpha);
        push(buf, view0.x + x10, view0.y + y10, view0.z, 1.0f, 0.0f, color, alpha);
    }

    /** Circle decal on the world XZ plane (ground rings, charge glow). */
    private void flat(BufferBuilder buf, double wx, double wy, double wz, float size, float rotation, int color, float alpha) {
        if (alpha <= 0.004f || size <= 0.001f) return;
        viewTo(view0, wx, wy, wz);
        // Local square corners rotated before hitting the ground basis: scaling
        // the basis axes by (cos, sin) directly collapsed the quad to a line at
        // rotation 0 and 90deg, making the ground ring pulse out of existence.
        float c = Mth.cos(rotation), s = Mth.sin(rotation);
        corner(-size, -size, c, s, buf, view0, 0.0f, 0.0f, color, alpha);
        corner(-size, size, c, s, buf, view0, 0.0f, 1.0f, color, alpha);
        corner(size, size, c, s, buf, view0, 1.0f, 1.0f, color, alpha);
        corner(-size, -size, c, s, buf, view0, 0.0f, 0.0f, color, alpha);
        corner(size, size, c, s, buf, view0, 1.0f, 1.0f, color, alpha);
        corner(size, -size, c, s, buf, view0, 1.0f, 0.0f, color, alpha);
    }

    /** Maps one rotated local corner onto the world XZ plane for {@link #flat}. */
    private void corner(float ox, float oz, float c, float s, BufferBuilder buf, Vector4f center,
                        float u, float v, int color, float alpha) {
        pushGround(buf, center, ox * c - oz * s, ox * s + oz * c, u, v, color, alpha);
    }

    private void pushGround(BufferBuilder buf, Vector4f center, float ox, float oz, float u, float v, int color, float alpha) {
        push(buf,
                center.x + groundEx.x * ox + groundEz.x * oz,
                center.y + groundEx.y * ox + groundEz.y * oz,
                center.z + groundEx.z * ox + groundEz.z * oz,
                u, v, color, alpha);
    }

    private void drawGroundGlow(BufferBuilder buf, float bright) {
        int n = Math.max(1, spirits.size());
        if (charge > 0.01f) {
            flat(buf, px, py + 0.02, pz, 0.8f + charge * 1.6f, 0.0f, spiritColor(0, n), charge * 0.45f * bright);
        }
        if (ringAge >= 0.0f) {
            float t = ringAge / RING_DURATION;
            float a = (1.0f - t) * (1.0f - t) * Math.min(1.0f, ringPower) * 0.55f * bright;
            flat(buf, ringX, ringY, ringZ, (0.8f + easeOut(t) * 3.0f) * ringPower, 0.0f, spiritColor(0, n), a);
        }
    }

    private void drawGroundRings(BufferBuilder buf, float bright) {
        int n = Math.max(1, spirits.size());
        if (charge > 0.01f) {
            float spin = time * (0.6f + charge * 4.0f);
            float size = 0.6f + charge * 1.2f;
            int colA = spiritColor(0, n);
            int colB = tailColor(n / 2, n);
            flat(buf, px, py + 0.025, pz, size, spin, colA, charge * 0.85f * bright);
            flat(buf, px, py + 0.028, pz, size * 0.62f, -spin * 1.7f, colB, charge * 0.7f * bright);
            flat(buf, px, py + 0.031, pz, size * 1.25f * (0.97f + 0.03f * Mth.sin(time * 9.0f)), spin * 0.4f,
                    lerpColor(colA, COLOR_WHITE, 0.5f), charge * charge * 0.35f * bright);
        }
        if (ringAge >= 0.0f) {
            float t = ringAge / RING_DURATION;
            float size = (0.5f + easeOut(t) * 4.2f) * ringPower;
            float a = (1.0f - t) * (1.0f - t) * Math.min(1.2f, ringPower) * 0.9f * bright;
            flat(buf, ringX, ringY + 0.01, ringZ, size, t * 2.0f, spiritColor(0, n), a);
            flat(buf, ringX, ringY + 0.014, ringZ, size * 0.7f, -t * 3.0f, COLOR_WHITE, a * 0.5f);
        }
    }

    private void drawTrails(BufferBuilder buf, float bright, float trailWidthBase) {
        float maxAge = configTrailLength();
        if (maxAge <= 0.01f) return;
        float width = 0.11f * trailWidthBase;
        int n = spirits.size();

        for (int i = 0; i < n; i++) {
            Spirit s = spirits.get(i);
            if (s.spawnDelay > 0.0f || s.spawn <= 0.01f) continue;
            int count = collectRibbon(s, maxAge);
            if (count < 2) continue;

            int head = spiritColor(i, n);
            int tail = tailColor(i, n);
            float w = width * s.sizeMul * s.spawn * (1.0f + s.flash * 0.6f) * (1.0f + charge * 0.3f);
            ribbon(buf, count, w, head, tail, 0.5f * bright);
            ribbon(buf, count, w * 0.32f, lerpColor(head, COLOR_WHITE, 0.65f), lerpColor(tail, COLOR_WHITE, 0.3f), 0.85f * bright);
        }
    }

    /** Head point plus history: camera-relative positions and normalized per-point age. */
    private int collectRibbon(Spirit s, float maxAge) {
        int count = 0;
        ptX[0] = (float) s.x;
        ptY[0] = (float) s.y;
        ptZ[0] = (float) s.z;
        ptT[0] = 0.0f;
        count++;

        Trail trail = s.trail;
        for (int k = 0; k < trail.size() && count < MAX_RIBBON_POINTS; k++) {
            float x = (float) trail.x(k);
            float y = (float) trail.y(k);
            float z = (float) trail.z(k);
            float dx = x - ptX[count - 1], dy = y - ptY[count - 1], dz = z - ptZ[count - 1];
            if (dx * dx + dy * dy + dz * dz < 1.0E-4f) continue;
            float t = Mth.clamp(trail.age(k, time) / maxAge, 0.0f, 1.0f);
            ptX[count] = x;
            ptY[count] = y;
            ptZ[count] = z;
            ptT[count] = t;
            count++;
            if (t >= 1.0f) break;
        }
        return count;
    }

    private void ribbon(BufferBuilder buf, int count, float width, int headColor, int tailColor, float alpha) {
        // Ribbon widths come from the camera-relative curve, so the side vector
        // is perpendicular to both the curve and the view direction.
        float lastX = 0.0f, lastY = 1.0f, lastZ = 0.0f;
        for (int k = 0; k < count; k++) {
            int a = Math.max(0, k - 1), b = Math.min(count - 1, k + 1);
            float tx = ptX[a] - ptX[b], ty = ptY[a] - ptY[b], tz = ptZ[a] - ptZ[b];
            float cx = ty * ptZ[k] - tz * ptY[k];
            float cy = tz * ptX[k] - tx * ptZ[k];
            float cz = tx * ptY[k] - ty * ptX[k];
            float len = Mth.sqrt(cx * cx + cy * cy + cz * cz);
            if (len > 1.0E-6f) {
                lastX = cx / len;
                lastY = cy / len;
                lastZ = cz / len;
            }
            float w = width * (float) Math.pow(1.0f - ptT[k], 0.7f);
            sideX[k] = lastX * w;
            sideY[k] = lastY * w;
            sideZ[k] = lastZ * w;

            viewRel(ribbonView[k], ptX[k], ptY[k], ptZ[k]);
            viewRel(ribbonSide[k], sideX[k], sideY[k], sideZ[k]);
        }

        for (int k = 0; k < count - 1; k++) {
            float t0 = ptT[k], t1 = ptT[k + 1];
            int c0 = lerpColor(headColor, tailColor, t0);
            int c1 = lerpColor(headColor, tailColor, t1);
            float a0 = alpha * (float) Math.pow(1.0f - t0, 1.6f) * nearFadeRel(ptX[k], ptY[k], ptZ[k]);
            float a1 = alpha * (float) Math.pow(1.0f - t1, 1.6f) * nearFadeRel(ptX[k + 1], ptY[k + 1], ptZ[k + 1]);
            if (a0 <= 0.004f && a1 <= 0.004f) continue;

            Vector4f v0 = ribbonView[k], s0 = ribbonSide[k];
            Vector4f v1 = ribbonView[k + 1], s1 = ribbonSide[k + 1];
            push(buf, v0.x - s0.x, v0.y - s0.y, v0.z - s0.z, 0.0f, 0.5f, c0, a0);
            push(buf, v0.x + s0.x, v0.y + s0.y, v0.z + s0.z, 1.0f, 0.5f, c0, a0);
            push(buf, v1.x + s1.x, v1.y + s1.y, v1.z + s1.z, 1.0f, 0.5f, c1, a1);
            push(buf, v0.x - s0.x, v0.y - s0.y, v0.z - s0.z, 0.0f, 0.5f, c0, a0);
            push(buf, v1.x + s1.x, v1.y + s1.y, v1.z + s1.z, 1.0f, 0.5f, c1, a1);
            push(buf, v1.x - s1.x, v1.y - s1.y, v1.z - s1.z, 0.0f, 0.5f, c1, a1);
        }
    }

    private void drawLinks(BufferBuilder buf, float bright) {
        if (linkAlpha <= 0.01f || linkRange <= 0.01f) return;
        int n = spirits.size();

        for (int i = 0; i < n; i++) {
            Spirit a = spirits.get(i);
            if (a.spawn < 0.5f) continue;
            for (int j = i + 1; j < n; j++) {
                Spirit b = spirits.get(j);
                if (b.spawn < 0.5f) continue;
                double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d > linkRange || d < 0.01f) continue;

                float strength = Mth.sqrt(1.0f - d / linkRange) * linkAlpha * Math.min(a.spawn, b.spawn);
                float alpha = strength * 0.45f * bright;
                int ca = spiritColor(i, n), cb = spiritColor(j, n);

                float ax = (float) (a.x - camX), ay = (float) (a.y - camY), az = (float) (a.z - camZ);
                float bx = (float) (b.x - camX), by = (float) (b.y - camY), bz = (float) (b.z - camZ);
                line(buf, ax, ay, az, bx, by, bz, 0.022f, ca, cb, alpha);

                float f = fract(time * 0.7f + (i * 7 + j * 13) * 0.137f);
                float fx = ax + (bx - ax) * f, fy = ay + (by - ay) * f, fz = az + (bz - az) * f;
                float pulse = Mth.sin(f * (float) Math.PI);
                billboard(buf, fx, fy, fz, 0.09f, 0.0f, lerpColor(lerpColor(ca, cb, f), COLOR_WHITE, 0.5f), alpha * 1.6f * pulse);
            }
        }
    }

    private void line(BufferBuilder buf, float ax, float ay, float az, float bx, float by, float bz, float width, int ca, int cb, float alpha) {
        // Perpendicular to the segment through the camera axis, so the quad
        // always faces the eye when the two ends are transformed.
        float tx = bx - ax, ty = by - ay, tz = bz - az;
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        float sx = ty * mz - tz * my, sy = tz * mx - tx * mz, sz = tx * my - ty * mx;
        float len = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1.0E-6f) return;
        sx = sx / len * width;
        sy = sy / len * width;
        sz = sz / len * width;

        viewRel(view0, ax, ay, az);
        viewRel(view1, bx, by, bz);
        viewRel(viewSide, sx, sy, sz);
        float fa = alpha * nearFadeRel(ax, ay, az);
        float fb = alpha * nearFadeRel(bx, by, bz);
        push(buf, view0.x - viewSide.x, view0.y - viewSide.y, view0.z - viewSide.z, 0.0f, 0.5f, ca, fa);
        push(buf, view0.x + viewSide.x, view0.y + viewSide.y, view0.z + viewSide.z, 1.0f, 0.5f, ca, fa);
        push(buf, view1.x + viewSide.x, view1.y + viewSide.y, view1.z + viewSide.z, 1.0f, 0.5f, cb, fb);
        push(buf, view0.x - viewSide.x, view0.y - viewSide.y, view0.z - viewSide.z, 0.0f, 0.5f, ca, fa);
        push(buf, view1.x + viewSide.x, view1.y + viewSide.y, view1.z + viewSide.z, 1.0f, 0.5f, cb, fb);
        push(buf, view1.x - viewSide.x, view1.y - viewSide.y, view1.z - viewSide.z, 0.0f, 0.5f, cb, fb);
    }

    private void drawBodies(BufferBuilder buf, float bright, float size) {
        int n = spirits.size();
        float chargeBoost = 1.0f + charge * 0.35f;

        for (int i = 0; i < n; i++) {
            Spirit s = spirits.get(i);
            if (s.spawnDelay > 0.0f || s.spawn <= 0.01f) continue;

            float x = (float) (s.x - camX), y = (float) (s.y - camY), z = (float) (s.z - camZ);
            float fade = nearFadeRel(x, y, z);
            if (fade <= 0.01f) continue;

            int col = spiritColor(i, n);
            float flicker = 0.88f + 0.12f * Mth.sin(time * 13.0f + s.phase * 7.0f) * Mth.sin(time * 7.3f + s.phase);
            float scale = size * s.sizeMul * popScale(s.spawn) * chargeBoost;
            float a = bright * fade * Math.min(1.0f, s.spawn * 1.5f) * (1.0f + charge * 0.4f);

            billboard(buf, x, y, z, 1.3f * scale, 0.0f, col, 0.11f * a);
            billboard(buf, x, y, z, 0.58f * scale * (1.0f + s.flash * 0.9f) * flicker, 0.0f, col, 0.5f * a);
            billboard(buf, x, y, z, 0.18f * scale * (1.0f + s.flash * 0.5f), 0.0f, lerpColor(col, COLOR_WHITE, 0.75f), Math.min(1.0f, 0.95f * a));
        }
    }

    private void drawFlares(BufferBuilder buf, float bright, float size) {
        int n = spirits.size();
        for (int i = 0; i < n; i++) {
            Spirit s = spirits.get(i);
            if (s.spawnDelay > 0.0f || s.spawn <= 0.01f) continue;
            float x = (float) (s.x - camX), y = (float) (s.y - camY), z = (float) (s.z - camZ);
            float fade = nearFadeRel(x, y, z);
            if (fade <= 0.01f) continue;

            int col = lerpColor(spiritColor(i, n), COLOR_WHITE, 0.5f);
            float scale = size * s.sizeMul * popScale(s.spawn) * (1.0f + charge * 0.5f);
            float twinkle = 0.6f + 0.4f * Mth.sin(time * 5.0f + s.phase * 3.0f);
            float a = bright * fade * s.spawn;

            billboard(buf, x, y, z, 0.5f * scale * (1.0f + s.flash * 1.3f), s.flareRot, col, 0.55f * a * twinkle + s.flash * 0.4f * a);
            billboard(buf, x, y, z, 0.3f * scale * (1.0f + s.flash), -s.flareRot * 1.6f + 0.785f, col, 0.35f * a);
        }
    }

    private void drawSparks(BufferBuilder buf, float bright, boolean stars) {
        for (Spark p : sparks) {
            if (p.star != stars) continue;
            float t = p.age / p.life;
            float a = (t < 0.1f ? t / 0.1f : (float) Math.pow(1.0f - t, 1.3f)) * bright;
            if (a <= 0.01f) continue;
            float x = (float) (p.x - camX), y = (float) (p.y - camY), z = (float) (p.z - camZ);
            a *= nearFadeRel(x, y, z);
            float size = p.size * (stars ? 3.2f : 2.0f) * (0.6f + 0.4f * (1.0f - t));
            billboard(buf, x, y, z, size, stars ? p.rot : 0.0f, p.color, a * (stars ? 0.9f : 0.8f));
        }
    }

    private static float nearFadeRel(float rx, float ry, float rz) {
        float d = Mth.sqrt(rx * rx + ry * ry + rz * rz);
        float t = Mth.clamp((d - 0.3f) / 0.8f, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float popScale(float spawn) {
        float t = Mth.clamp(spawn, 0.0f, 1.0f);
        float c = 1.70158f * 1.4f;
        return 1.0f + (c + 1.0f) * (float) Math.pow(t - 1.0f, 3.0) + c * (float) Math.pow(t - 1.0f, 2.0);
    }

    private static void ensureTextures() {
        if (texturesReady) return;
        texturesReady = true;
        int size = 128;
        registerTex(TEX_BLOOM, size, (x, y, r) -> {
            float g = (float) ((Math.exp(-r * r * 5.0) - Math.exp(-5.0)) / (1.0 - Math.exp(-5.0)));
            return Math.max(0.0f, g);
        });
        registerTex(TEX_GLOW, size, (x, y, r) -> {
            float g = 1.0f - r;
            return g <= 0.0f ? 0.0f : (float) Math.pow(g, 2.2);
        });
        registerTex(TEX_SPARKLE, size, (x, y, r) -> {
            float beamX = (float) (Math.exp(-y * y * 600.0) * Math.pow(Math.max(0.0f, 1.0f - Math.abs(x)), 2.0));
            float beamY = (float) (Math.exp(-x * x * 600.0) * Math.pow(Math.max(0.0f, 1.0f - Math.abs(y)), 2.0));
            float core = (float) Math.exp(-r * r * 40.0);
            float halo = (float) Math.exp(-r * r * 8.0) * 0.25f;
            return Math.min(1.0f, beamX + beamY + core + halo) * (r < 1.0f ? 1.0f : 0.0f);
        });
        registerTex(TEX_RING, size, (x, y, r) -> {
            float outer = smoothstep(0.80f, 0.84f, r) * (1.0f - smoothstep(0.92f, 0.96f, r));
            float inner = smoothstep(0.55f, 0.57f, r) * (1.0f - smoothstep(0.59f, 0.61f, r)) * 0.8f;
            float angle = (float) Math.atan2(y, x);
            float tick = (float) Math.pow(Math.max(0.0, Math.cos(angle * 16.0)), 40.0);
            float ticks = tick * smoothstep(0.62f, 0.64f, r) * (1.0f - smoothstep(0.76f, 0.78f, r)) * 0.9f;
            float fill = (1.0f - smoothstep(0.0f, 0.96f, r)) * 0.08f;
            return Math.min(1.0f, outer + inner + ticks + fill);
        });
    }

    private interface AlphaShape {
        float alpha(float x, float y, float r);
    }

    private static void registerTex(Identifier id, int size, AlphaShape shape) {
        Minecraft mc = Minecraft.getInstance();
        // Idempotent: TextureManager.register overwrites, so re-enabling the
        // feature does not leak an old instance.
        NativeImage image = new NativeImage(size, size, false);
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                float x = (px + 0.5f) / size * 2.0f - 1.0f;
                float y = (py + 0.5f) / size * 2.0f - 1.0f;
                float r = Mth.sqrt(x * x + y * y);
                int a = Mth.clamp((int) (shape.alpha(x, y, r) * 255.0f), 0, 255);
                int argb = (a << 24) | 0xFFFFFF;
                int abgr = (argb & 0xFF00FF00) | ((argb & 0x000000FF) << 16) | ((argb & 0x00FF0000) >>> 16);
                image.setPixelABGR(px, py, abgr);
            }
        }
        DynamicTexture texture = new DynamicTexture(() -> id.toString(), image);
        mc.getTextureManager().register(id, texture);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float fract(float x) {
        return x - Mth.floor(x);
    }

    private static float pingPong(float x) {
        x = fract(x);
        return x < 0.5f ? x * 2.0f : 2.0f - x * 2.0f;
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    private static float smooth(float x) {
        return x * x * (3.0f - 2.0f * x);
    }

    private static float easeOut(float t) {
        float r = 1.0f - t;
        return 1.0f - r * r * r;
    }

    private static double sq(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    public void release() {
        reset();
    }

    private static final class Spirit {
        final float phase, tilt, azimuth, radiusMul, speedMul, flareSpin, wander, sizeMul;

        double x, y, z;
        double vx, vy, vz;
        double tx, ty, tz;
        double tvx, tvy, tvz;
        float kMul = 1.0f;

        float spawn;
        float spawnDelay;
        float flash;

        float dashTime;
        double dashX, dashY, dashZ;
        boolean dashHit;

        float stun;
        float recover = 1.0f;

        float flareRot;
        float sparkAccumulator;

        final Trail trail = new Trail();

        Spirit(Random random) {
            phase = random.nextFloat() * TWO_PI;
            tilt = 0.25f + random.nextFloat() * 0.55f;
            azimuth = random.nextFloat() * TWO_PI;
            radiusMul = 0.85f + random.nextFloat() * 0.3f;
            speedMul = 0.8f + random.nextFloat() * 0.45f;
            flareSpin = (random.nextBoolean() ? 1.0f : -1.0f) * (0.6f + random.nextFloat() * 1.4f);
            wander = 0.6f + random.nextFloat() * 0.8f;
            sizeMul = 0.85f + random.nextFloat() * 0.3f;
            flareRot = random.nextFloat() * TWO_PI;
        }

        double speed() {
            return Math.sqrt(vx * vx + vy * vy + vz * vz);
        }
    }

    private static final class Spark {
        double x, y, z;
        double vx, vy, vz;
        float age, life, size, gravity, drag, rot, spin;
        int color;
        boolean star;
    }

    private static final class Trail {
        private static final int CAPACITY = 160;
        private static final double MIN_SPACING_SQ = 0.025 * 0.025;

        private final double[] xs = new double[CAPACITY];
        private final double[] ys = new double[CAPACITY];
        private final double[] zs = new double[CAPACITY];
        private final float[] times = new float[CAPACITY];
        private int head = -1;
        private int size;

        void clear() {
            head = -1;
            size = 0;
        }

        void push(double x, double y, double z, float time) {
            if (size > 0) {
                double dx = x - xs[head], dy = y - ys[head], dz = z - zs[head];
                if (dx * dx + dy * dy + dz * dz < MIN_SPACING_SQ) return;
            }
            head = (head + 1) % CAPACITY;
            xs[head] = x;
            ys[head] = y;
            zs[head] = z;
            times[head] = time;
            if (size < CAPACITY) size++;
        }

        void trim(float now, float maxAge) {
            while (size > 0 && now - times[index(size - 1)] > maxAge) size--;
        }

        int size() {
            return size;
        }

        double x(int i) {
            return xs[index(i)];
        }

        double y(int i) {
            return ys[index(i)];
        }

        double z(int i) {
            return zs[index(i)];
        }

        float age(int i, float now) {
            return now - times[index(i)];
        }

        private int index(int i) {
            return Math.floorMod(head - i, CAPACITY);
        }
    }
}
