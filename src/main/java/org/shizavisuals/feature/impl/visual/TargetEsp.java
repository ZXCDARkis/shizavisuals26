package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ColorMode;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.utils.TargetSelector;
import org.shizavisuals.utils.render.HurtUtil;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.esp.EspSpriteRenderer;
import org.shizavisuals.utils.render.world.WorldMeshRenderer;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class TargetEsp extends Feature {
    private static final int BASE_COLOR = 0xFFE54BD0;

    private final ModeSetting mode = register(new ModeSetting(
            "Mode", "TriangleMosaic", "Контур", "Бархат", "Молнии", "Крутые Души", "TriangleMosaic", "Скан"
    ));
    private final ModeSetting colorMode = register(ColorMode.setting());
    private final ColorSetting color = register(new ColorSetting(
            "Color", BASE_COLOR
    ).configKey("esp.target.color").visibleWhen(() -> ColorMode.isCustom(this.colorMode)));

    private final NumberSetting velvetSize = register(new NumberSetting(
            "Velvet Size", 1.65, 0.5, 3.0, 0.05, ""
    ).configKey("esp.target.velvetSize").visibleWhen(() -> mode.is("Бархат")));

    private final NumberSetting soulRadius = register(new NumberSetting(
            "Soul Radius", 1.15, 0.5, 2.5, 0.05, ""
    ).configKey("esp.target.soulRadius").visibleWhen(() -> mode.is("Крутые Души")));
    private final NumberSetting soulRotateSpeed = register(new NumberSetting(
            "Soul Speed", 1.0, 0.2, 3.0, 0.1, ""
    ).configKey("esp.target.soulSpeed").visibleWhen(() -> mode.is("Крутые Души")));
    private final NumberSetting soulCircles = register(new NumberSetting(
            "Soul Circles", 2.0, 1.0, 5.0, 0.5, ""
    ).configKey("esp.target.soulCircles").visibleWhen(() -> mode.is("Крутые Души")));
    private final NumberSetting soulSpeedY = register(new NumberSetting(
            "Soul Speed Y", 1.0, 0.2, 3.0, 0.1, ""
    ).configKey("esp.target.soulSpeedY").visibleWhen(() -> mode.is("Крутые Души")));
    private final NumberSetting soulGlowWidth = register(new NumberSetting(
            "Soul Glow Width", 0.7, 0.1, 2.0, 0.05, ""
    ).configKey("esp.target.soulGlowWidth").visibleWhen(() -> mode.is("Крутые Души")));

    private final NumberSetting mosaicSize = register(new NumberSetting(
            "Mosaic Size", 1.2, 0.3, 3.0, 0.1, ""
    ).configKey("esp.target.mosaicSize").visibleWhen(() -> mode.is("TriangleMosaic")));
    private final NumberSetting mosaicSpeed = register(new NumberSetting(
            "Mosaic Speed", 1.0, 0.2, 3.0, 0.1, ""
    ).configKey("esp.target.mosaicSpeed").visibleWhen(() -> mode.is("TriangleMosaic")));

    private final NumberSetting scanSaturation = register(new NumberSetting(
            "Scan Saturation", 100.0, 0.0, 200.0, 5.0, "%"
    ).configKey("esp.target.scanSaturation").visibleWhen(() -> mode.is("Скан")));
    private final NumberSetting scanSpeed = register(new NumberSetting(
            "Scan Speed", 1.0, 0.25, 3.0, 0.05, ""
    ).configKey("esp.target.scanSpeed").visibleWhen(() -> mode.is("Скан")));
    private final BooleanSetting scanFlow = register(new BooleanSetting(
            "Color Flow", false
    ).configKey("esp.target.scanFlow").visibleWhen(() -> mode.is("Скан")));
    private final ColorSetting scanSecondColor = register(new ColorSetting(
            "Second Color", 0xFFBE4BFF
    ).configKey("esp.target.scanSecondColor").visibleWhen(() -> mode.is("Скан") && scanFlow.getValue() && ColorMode.isCustom(this.colorMode)));
    private final NumberSetting scanGlow = register(new NumberSetting(
            "Scan Glow", 100.0, 25.0, 200.0, 5.0, "%"
    ).configKey("esp.target.scanGlow").visibleWhen(() -> mode.is("Скан")));

    private static final Random RANDOM = new Random();
    private static final int SOUL_COUNT = 3;
    private static final int TRAIL_LENGTH = 25;
    private static final int MAX_BOLTS = 16;
    private static final int SPAWN_PER_TICK = 2;
    private static final long SPAWN_INTERVAL_MS = 42;
    private static final int LIGHTNING_DEPTH = 3;

    private final EspSpriteRenderer glowRenderer = new EspSpriteRenderer(Textures.Esp.GLOW);
    private final EspSpriteRenderer velvetRenderer = new EspSpriteRenderer(Textures.Esp.VELVET);

    private final Vec3[] soulPos = new Vec3[SOUL_COUNT];
    private final Vec3[] soulVel = new Vec3[SOUL_COUNT];
    @SuppressWarnings("unchecked")
    private final List<Vec3>[] soulTrails = new List[SOUL_COUNT];
    private long lastSoulUpdate = 0;

    private final List<Vec3>[] boltPoints = new List[MAX_BOLTS];
    private final long[] boltSpawnTimes = new long[MAX_BOLTS];
    private final long[] boltLifetimes = new long[MAX_BOLTS];
    private int boltCount = 0;
    private long lastBoltSpawn = 0;

    private long renderRotation = 0;
    private LivingEntity lastTarget;
    private long targetSince = 0;
    private LivingEntity lingerTarget;
    private long lastSeenTime = 0L;
    private LivingEntity scanTarget;

    public TargetEsp() {
        super("TargetESP", "Highlights the entity you are currently looking at", FeatureCategory.VISUAL, GLFW.GLFW_KEY_UNKNOWN);
        for (int i = 0; i < SOUL_COUNT; i++) {
            soulTrails[i] = new ArrayList<>();
        }
    }

    public static TargetEsp getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(TargetEsp.class);
    }

    @Override
    protected void onDisable() {
        resetSoulState();
        boltCount = 0;
        lastTarget = null;
        lingerTarget = null;
        scanTarget = null;
    }

    private LivingEntity findTarget(Minecraft mc) {
        LivingEntity living = TargetSelector.pick(mc);
        if (living == null) return null;
        if (living.isRemoved() || !living.isAlive()) return null;
        if (living == mc.player && mc.options.getCameraType().isFirstPerson()) return null;
        return living;
    }

    private int resolveColor() {
        return ColorMode.resolve(this.colorMode, this.color);
    }

    public boolean isScanMode() {
        return mode.is("Скан");
    }

    public boolean isScanTarget(Entity entity) {
        return isScanMode() && entity != null && entity == scanTarget;
    }

    public int scanColor() {
        return resolveColor() & 0x00FFFFFF;
    }

    public int scanSecondColor() {
        if (!scanFlow.getValue()) return scanColor();
        return ColorMode.resolve(this.colorMode, this.scanSecondColor) & 0x00FFFFFF;
    }

    public float scanSaturation() {
        return scanSaturation.getValue().floatValue() / 100.0F;
    }

    public float scanSpeed() {
        return scanSpeed.getValue().floatValue();
    }

    public float scanGlow() {
        return scanGlow.getValue().floatValue() / 100.0F;
    }

    public float scanAnimation() {
        if (lastTarget == null) return 0.0F;
        float appear = Math.clamp((System.currentTimeMillis() - targetSince) / 250.0F, 0.0F, 1.0F);
        return appear * appear * (3.0F - 2.0F * appear);
    }

    public void renderWorld(float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) return;

        LivingEntity picked = findTarget(mc);
        long now = System.currentTimeMillis();
        LivingEntity entity;
        float lingerFade = 1.0F;
        if (picked != null) {
            entity = picked;
            lingerTarget = picked;
            lastSeenTime = now;
        } else if (lingerTarget != null
                && lingerTarget.isAlive()
                && !lingerTarget.isRemoved()
                && lingerTarget.level() == mc.level
                && now - lastSeenTime < 3500L) {
            entity = lingerTarget;
            lingerFade = 1.0F - (now - lastSeenTime) / 3500.0F;
        } else {
            lingerTarget = null;
            scanTarget = null;
            resetSoulState();
            boltCount = 0;
            lastTarget = null;
            return;
        }
        scanTarget = entity;

        if (entity != lastTarget) {
            lastTarget = entity;
            targetSince = now;
        }
        float appear = Math.clamp((now - targetSince) / 250.0F, 0.0F, 1.0F);
        appear = appear * appear * (3.0F - 2.0F * appear) * lingerFade;
        if (appear <= 0.01F) return;

        int argb = HurtUtil.blend(resolveColor(), entity, appear);
        int rgb = argb & 0x00FFFFFF;
        float alphaMul = ((argb >>> 24) & 0xFF) / 255.0F;

        Vec3 pos = Render3DUtil.interpolatedPosition(entity, tickDelta);

        switch (mode.getValue()) {
            case "Контур" -> renderContour(entity, pos, rgb, alphaMul);
            case "Бархат" -> renderVelvet(entity, pos, rgb, alphaMul, now);
            case "Молнии" -> renderLightning(entity, pos, rgb, alphaMul, now);
            case "Крутые Души" -> renderCoolSouls(entity, pos, rgb, alphaMul, now);
            case "Скан" -> {
            }
            default -> renderTriangleMosaic(entity, pos, rgb, alphaMul, now);
        }
    }

    private void renderContour(LivingEntity entity, Vec3 pos, int rgb, float alphaMul) {
        double hw = entity.getBbWidth() / 2.0 + 0.04;
        double y0 = pos.y - 0.04;
        double y1 = pos.y + entity.getBbHeight() + 0.04;

        Vec3 c000 = new Vec3(pos.x - hw, y0, pos.z - hw);
        Vec3 c100 = new Vec3(pos.x + hw, y0, pos.z - hw);
        Vec3 c110 = new Vec3(pos.x + hw, y0, pos.z + hw);
        Vec3 c010 = new Vec3(pos.x - hw, y0, pos.z + hw);
        Vec3 c001 = new Vec3(pos.x - hw, y1, pos.z - hw);
        Vec3 c101 = new Vec3(pos.x + hw, y1, pos.z - hw);
        Vec3 c111 = new Vec3(pos.x + hw, y1, pos.z + hw);
        Vec3 c011 = new Vec3(pos.x - hw, y1, pos.z + hw);

        int edgeColor = withAlpha(rgb, (int) (255 * alphaMul));
        List<WorldMeshRenderer.Line> lines = List.of(
                new WorldMeshRenderer.Line(c000, c100, edgeColor),
                new WorldMeshRenderer.Line(c100, c110, edgeColor),
                new WorldMeshRenderer.Line(c110, c010, edgeColor),
                new WorldMeshRenderer.Line(c010, c000, edgeColor),
                new WorldMeshRenderer.Line(c001, c101, edgeColor),
                new WorldMeshRenderer.Line(c101, c111, edgeColor),
                new WorldMeshRenderer.Line(c111, c011, edgeColor),
                new WorldMeshRenderer.Line(c011, c001, edgeColor),
                new WorldMeshRenderer.Line(c000, c001, edgeColor),
                new WorldMeshRenderer.Line(c100, c101, edgeColor),
                new WorldMeshRenderer.Line(c110, c111, edgeColor),
                new WorldMeshRenderer.Line(c010, c011, edgeColor)
        );

        int fillColor = withAlpha(rgb, (int) (0x66 * alphaMul));
        List<WorldMeshRenderer.FilledTriangle> fill = new ArrayList<>(12);
        fill.add(new WorldMeshRenderer.FilledTriangle(c000, c100, c101, fillColor));
        fill.add(new WorldMeshRenderer.FilledTriangle(c000, c101, c001, fillColor));
        fill.add(new WorldMeshRenderer.FilledTriangle(c110, c010, c011, fillColor));
        fill.add(new WorldMeshRenderer.FilledTriangle(c110, c011, c111, fillColor));
        fill.add(new WorldMeshRenderer.FilledTriangle(c010, c000, c001, fillColor));
        fill.add(new WorldMeshRenderer.FilledTriangle(c010, c001, c011, fillColor));
        fill.add(new WorldMeshRenderer.FilledTriangle(c100, c110, c111, fillColor));
        fill.add(new WorldMeshRenderer.FilledTriangle(c100, c111, c101, fillColor));
        fill.add(new WorldMeshRenderer.FilledTriangle(c001, c101, c111, fillColor));
        fill.add(new WorldMeshRenderer.FilledTriangle(c001, c111, c011, fillColor));
        fill.add(new WorldMeshRenderer.FilledTriangle(c010, c110, c100, fillColor));
        fill.add(new WorldMeshRenderer.FilledTriangle(c010, c100, c000, fillColor));

        WorldMeshRenderer.render(new WorldMeshRenderer.WorldMesh(lines, List.of(), List.of(), fill));
    }

    private void renderVelvet(LivingEntity entity, Vec3 pos, int rgb, float alphaMul, long now) {
        Vec3 center = pos.add(0, entity.getBbHeight() * 0.55, 0);
        float size = velvetSize.getValue().floatValue();
        float pulse = 0.88F + 0.12F * (float) Math.sin(now / 260.0);
        float spin = (float) Math.toRadians(-(now / 12.0 % 360.0));

        int alpha = (int) (220 * pulse * alphaMul);
        List<EspSpriteRenderer.Sprite> sprites = List.of(new EspSpriteRenderer.Sprite(
                center, size * 0.5F, withAlpha(0xFFFFFF, alpha), spin));
        velvetRenderer.render(sprites, 1.0F, true, false);
    }

    private void renderLightning(LivingEntity entity, Vec3 basePos, int rgb, float alphaMul, long now) {
        if (now - lastBoltSpawn > SPAWN_INTERVAL_MS && boltCount < MAX_BOLTS) {
            for (int s = 0; s < SPAWN_PER_TICK && boltCount < MAX_BOLTS; s++) {
                boltPoints[boltCount] = generateBolt(entity, basePos);
                boltSpawnTimes[boltCount] = now;
                boltLifetimes[boltCount] = 130 + RANDOM.nextInt(140);
                boltCount++;
            }
            lastBoltSpawn = now;
        }

        List<WorldMeshRenderer.Line> lines = new ArrayList<>();
        List<EspSpriteRenderer.Sprite> sprites = new ArrayList<>();
        for (int i = 0; i < boltCount; i++) {
            if (now - boltSpawnTimes[i] > boltLifetimes[i]) {
                boltPoints[i] = boltPoints[--boltCount];
                boltSpawnTimes[i] = boltSpawnTimes[boltCount];
                boltLifetimes[i] = boltLifetimes[boltCount];
                i--;
                continue;
            }

            float life = (now - boltSpawnTimes[i]) / (float) boltLifetimes[i];
            float fade = 1.0F - life;
            float flicker = 0.65F + RANDOM.nextFloat() * 0.35F;
            float boltAlpha = fade * flicker * alphaMul;
            if (boltAlpha <= 0.02F) continue;

            List<Vec3> pts = boltPoints[i];
            if (pts == null) continue;

            int glowAlpha = (int) (boltAlpha * 150);
            int glowColor = (glowAlpha << 24) | rgb;
            for (Vec3 point : pts) {
                float blobScale = 0.16F + RANDOM.nextFloat() * 0.10F;
                sprites.add(new EspSpriteRenderer.Sprite(point, blobScale * 0.5F, glowColor));
            }

            int coreAlpha = (int) (boltAlpha * 255);
            int coreColor = (coreAlpha << 24) | 0xFFFFFF;
            for (int j = 0; j < pts.size() - 1; j++) {
                lines.add(new WorldMeshRenderer.Line(pts.get(j), pts.get(j + 1), coreColor));
            }
        }

        if (!sprites.isEmpty()) {
            glowRenderer.render(sprites, 1.0F, true, true);
        }
        if (!lines.isEmpty()) {
            WorldMeshRenderer.render(new WorldMeshRenderer.WorldMesh(lines, List.of(), List.of(), List.of()));
        }
    }

    private void renderCoolSouls(LivingEntity entity, Vec3 feetPos, int rgb, float alphaMul, long now) {
        if (lastSoulUpdate == 0) lastSoulUpdate = now;
        float dt = Math.min((now - lastSoulUpdate) / 1000.0F, 0.1F);
        if (dt <= 0) dt = 0.001F;
        lastSoulUpdate = now;

        float baseRadius = (float) (entity.getBbWidth() * soulRadius.getValue());
        float speedY = soulSpeedY.getValue().floatValue();
        float circles = soulCircles.getValue().floatValue();

        float moveUpDuration = 1000.0F / speedY;
        float topSpinDuration = circles * 1000.0F;
        float moveDownDuration = 1000.0F / speedY;
        float bottomSpinDuration = circles * 1000.0F;
        float totalCycle = moveUpDuration + topSpinDuration + moveDownDuration + bottomSpinDuration;
        float cycleTime = (now % (long) totalCycle);

        double heightProgress;
        double angleOffset;

        if (cycleTime < moveUpDuration) {
            float p = cycleTime / moveUpDuration;
            heightProgress = easeInOut(p);
            angleOffset = p * 360 * 2;
        } else if (cycleTime < moveUpDuration + topSpinDuration) {
            heightProgress = 1.0;
            float spinT = cycleTime - moveUpDuration;
            angleOffset = (spinT / topSpinDuration) * 360 * circles;
        } else if (cycleTime < moveUpDuration + topSpinDuration + moveDownDuration) {
            float spinT = cycleTime - moveUpDuration - topSpinDuration;
            float p = spinT / moveDownDuration;
            heightProgress = 1.0 - easeInOut(p);
            angleOffset = 360 * circles + p * 360 * 2;
        } else {
            heightProgress = 0.0;
            float spinT = cycleTime - moveUpDuration - topSpinDuration - moveDownDuration;
            angleOffset = 360 * circles + 360 * 2 + (spinT / bottomSpinDuration) * 360 * circles;
        }

        float time = (now % 100000L) / 1000.0F;
        double entityHeight = entity.getBbHeight();
        float trailWidth = soulGlowWidth.getValue().floatValue();

        List<EspSpriteRenderer.Sprite> sprites = new ArrayList<>(SOUL_COUNT * TRAIL_LENGTH);

        for (int j = 0; j < SOUL_COUNT; j++) {
            double baseAngle = j * 120;
            double totalAngle = baseAngle + (now / 50.0 * soulRotateSpeed.getValue()) + angleOffset;
            double radians = Math.toRadians(totalAngle % 360);

            float dynR = baseRadius * (0.95F + 0.12F * (float) Math.sin(time * 0.8 + j));
            double tx = Math.cos(radians) * dynR;
            double tz = Math.sin(radians) * dynR;
            double ty = heightProgress * entityHeight;

            Vec3 targetWorldPos = feetPos.add(tx, ty, tz);

            if (soulPos[j] == null || soulPos[j].distanceTo(targetWorldPos) > 15) {
                soulPos[j] = targetWorldPos;
                soulVel[j] = Vec3.ZERO;
            }

            Vec3 diff = targetWorldPos.subtract(soulPos[j]);
            double spring = 15.0;
            double damping = 0.85;
            soulVel[j] = soulVel[j].add(diff.scale(spring * dt)).scale(Math.pow(damping, dt * 60));
            soulPos[j] = soulPos[j].add(soulVel[j].scale(dt * 60));

            if (soulTrails[j].isEmpty() || soulTrails[j].get(0).distanceTo(soulPos[j]) > 0.015) {
                soulTrails[j].add(0, soulPos[j]);
                while (soulTrails[j].size() > TRAIL_LENGTH) {
                    soulTrails[j].remove(soulTrails[j].size() - 1);
                }
            }

            for (int i = 0; i < soulTrails[j].size(); i++) {
                Vec3 p = soulTrails[j].get(i);
                float offset = 1.0F - (float) i / TRAIL_LENGTH;
                float opacity = (float) Math.pow(offset, 1.5) * 0.85F * alphaMul;
                if (opacity <= 0.02F) continue;
                float scale = Math.max(0.28F * offset, 0.15F) * 0.65F * trailWidth;
                sprites.add(new EspSpriteRenderer.Sprite(
                        p, scale, withAlpha(rgb, (int) (opacity * 255))));
            }
        }

        if (!sprites.isEmpty()) {
            glowRenderer.render(sprites, 1.0F, true, true);
        }
    }

    private void renderTriangleMosaic(LivingEntity entity, Vec3 pos, int rgb, float alphaMul, long now) {
        renderRotation++;

        float size = mosaicSize.getValue().floatValue();
        float speed = mosaicSpeed.getValue().floatValue();
        double tSec = now / 1000.0;

        float bob = (float) Math.sin(tSec * 2.4) * 0.08F;
        float pulse = 1.0F + 0.04F * (float) Math.sin(tSec * 3.1);
        float sc = size * pulse * appearScale(now);

        // Triangle floats above the player's head, like in the reference.
        Vec3 center = pos.add(0, entity.getBbHeight() + 0.85 + bob, 0);

        float[][] a = {{-0.95F, 0.60F}};
        float[][] b = {{0.95F, 0.60F}};
        float[][] c = {{0.00F, -1.05F}};
        int n = 5;

        float[][][] p = new float[n + 1][][];
        for (int i = 0; i <= n; i++) {
            float ti = (float) i / n;
            float lx = a[0][0] + (c[0][0] - a[0][0]) * ti;
            float ly = a[0][1] + (c[0][1] - a[0][1]) * ti;
            float rx = b[0][0] + (c[0][0] - b[0][0]) * ti;
            float ry = b[0][1] + (c[0][1] - b[0][1]) * ti;
            int cols = n - i;
            p[i] = new float[cols + 1][2];
            for (int j = 0; j <= cols; j++) {
                float tj = (cols == 0) ? 0.0F : (float) j / cols;
                p[i][j][0] = lx + (rx - lx) * tj;
                p[i][j][1] = ly + (ry - ly) * tj;
            }
        }

        List<float[][]> tris = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int cols = n - i;
            for (int j = 0; j < cols; j++) {
                tris.add(new float[][]{p[i][j], p[i][j + 1], p[i + 1][j]});
                if (j < cols - 1) {
                    tris.add(new float[][]{p[i][j + 1], p[i + 1][j + 1], p[i + 1][j]});
                }
            }
        }

        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 toCamera = camera.position().subtract(center);
        if (toCamera.lengthSqr() < 0.001) return;
        toCamera = toCamera.normalize();
        Vec3 axis = billboardAxis(toCamera);
        Vec3 normal = axis.cross(toCamera).normalize();

        float t = (float) (renderRotation * 0.002 * speed);
        t = t - (float) Math.floor(t);

        int white = 0xFFFFFF;
        int lightBase = lerpRgb(rgb, white, 0.55F);
        int shimTarget = lerpRgb(rgb, white, 0.85F);

        List<WorldMeshRenderer.FilledTriangle> filledTris = new ArrayList<>(tris.size());
        List<WorldMeshRenderer.Line> edgeLines = new ArrayList<>();

        for (int k = 0; k < tris.size(); k++) {
            float[][] tri = tris.get(k);
            float tcx = (tri[0][0] + tri[1][0] + tri[2][0]) / 3.0F;
            float tcy = (tri[0][1] + tri[1][1] + tri[2][1]) / 3.0F;

            float[] st = tileState(tcx, t);
            float edge = st[2];
            float shim = st[3];

            float v = Math.max(0.0F, Math.min(1.0F, (0.60F - tcy) / 1.65F));
            int colRgb = lerpRgb(lightBase, rgb, v);
            float shade = (hash(k) - 0.5F) * 0.16F;
            if (shade >= 0) {
                colRgb = lerpRgb(colRgb, white, shade);
            } else {
                colRgb = lerpRgb(colRgb, rgb, -shade);
            }
            if (shim > 0.0F) {
                colRgb = lerpRgb(colRgb, shimTarget, Math.max(0.0F, Math.min(1.0F, shim * 0.7F)));
            }

            Vec3 wp1 = localToWorld(tri[0], center, sc, axis, normal);
            Vec3 wp2 = localToWorld(tri[1], center, sc, axis, normal);
            Vec3 wp3 = localToWorld(tri[2], center, sc, axis, normal);
            filledTris.add(new WorldMeshRenderer.FilledTriangle(
                    wp1, wp2, wp3, withAlpha(colRgb, (int) (255 * alphaMul))));

            if (edge > 0.08F) {
                int edgeColor = withAlpha(lerpRgb(rgb, white, 0.9F), (int) ((80 + 175 * edge) * alphaMul));
                edgeLines.add(new WorldMeshRenderer.Line(wp1, wp2, edgeColor));
                edgeLines.add(new WorldMeshRenderer.Line(wp2, wp3, edgeColor));
                edgeLines.add(new WorldMeshRenderer.Line(wp3, wp1, edgeColor));
            }
        }

        List<EspSpriteRenderer.Sprite> sprites = new ArrayList<>(1);
        sprites.add(new EspSpriteRenderer.Sprite(
                center, sc * 1.05F, withAlpha(lerpRgb(rgb, white, 0.3F), (int) (45 * alphaMul))));
        glowRenderer.render(sprites, 1.0F, true, true);

        WorldMeshRenderer.render(new WorldMeshRenderer.WorldMesh(
                edgeLines, List.of(), List.of(), filledTris));
    }

    private float appearScale(long now) {
        float appear = Math.clamp((now - targetSince) / 250.0F, 0.0F, 1.0F);
        float eased = appear * appear * (3.0F - 2.0F * appear);
        float x = Math.clamp(eased, 0.0F, 1.0F);
        float overshoot = 1.70158F;
        float shifted = x - 1.0F;
        return Math.max(0.01F, 1.0F + (overshoot + 1.0F) * shifted * shifted * shifted + overshoot * shifted * shifted);
    }

    private float[] tileState(float cx, float t) {
        float pos = 1.0F - Math.max(0.0F, Math.min(1.0F, (cx + 0.95F) / 1.9F));
        float active = 0.35F;
        float sweep = 2.2F;
        float band = 0.45F;
        float tailW = 1.3F;

        if (t >= active) return new float[]{0.0F, 0.0F, 0.0F, 0.0F};

        float front = (t / active) * sweep;
        float w = (front - pos) / band;

        if (w > 0.0F && w < 1.0F) {
            float we = w * w * (3.0F - 2.0F * w);
            float ang = (float) (Math.PI * 2.0 * we);
            float bump = (float) Math.sin(Math.PI * w);
            return new float[]{ang, bump * 0.15F, Math.abs((float) Math.sin(ang)), 0.0F};
        }
        if (w >= 1.0F && w < 1.0F + tailW) {
            float stt = (w - 1.0F) / tailW;
            float envelope = 1.0F - stt;
            float ripple = 0.5F + 0.5F * (float) Math.sin(stt * Math.PI * 3.0F);
            return new float[]{0.0F, 0.0F, 0.0F, envelope * ripple};
        }
        return new float[]{0.0F, 0.0F, 0.0F, 0.0F};
    }

    private Vec3 localToWorld(float[] local, Vec3 center, float size, Vec3 axis, Vec3 normal) {
        return center.add(axis.scale(local[0] * size)).add(normal.scale(local[1] * size));
    }

    private Vec3 billboardAxis(Vec3 toCamera) {
        if (Math.abs(toCamera.y) > 0.999) {
            return new Vec3(1, 0, 0);
        }
        return toCamera.cross(Vec3.Y_AXIS).normalize();
    }

    private double easeInOut(double t) {
        return t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;
    }

    private int withAlpha(int rgb, int alpha) {
        return ((Math.clamp(alpha, 0, 255)) << 24) | (rgb & 0x00FFFFFF);
    }

    private int lerpRgb(int a, int b, float t) {
        if (t <= 0.0F) return a & 0x00FFFFFF;
        if (t >= 1.0F) return b & 0x00FFFFFF;
        int rA = (a >> 16) & 0xFF, gA = (a >> 8) & 0xFF, bA = a & 0xFF;
        int rB = (b >> 16) & 0xFF, gB = (b >> 8) & 0xFF, bB = b & 0xFF;
        int r = (int) (rA + (rB - rA) * t);
        int g = (int) (gA + (gB - gA) * t);
        int bl = (int) (bA + (bB - bA) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private float hash(int id) {
        float x = (float) Math.sin(id * 12.9898F) * 43758.5453F;
        return x - (float) Math.floor(x);
    }

    private List<Vec3> generateBolt(LivingEntity target, Vec3 basePos) {
        double width = target.getBbWidth();
        double height = target.getBbHeight();
        double surfaceRadius = width * 0.5 + 0.05;

        double angle = RANDOM.nextDouble() * Math.PI * 2;
        double h = RANDOM.nextDouble() * height;
        Vec3 start = basePos.add(Math.cos(angle) * surfaceRadius, h, Math.sin(angle) * surfaceRadius);

        double theta = RANDOM.nextDouble() * Math.PI * 2;
        double phi = Math.toRadians((RANDOM.nextDouble() - 0.5) * 140.0);
        double dx = Math.cos(phi) * Math.cos(theta);
        double dy = Math.sin(phi);
        double dz = Math.cos(phi) * Math.sin(theta);
        double spikeLength = 0.25 + RANDOM.nextDouble() * 0.3;
        Vec3 end = start.add(dx * spikeLength, dy * spikeLength, dz * spikeLength);

        return generateBoltPath(start, end, LIGHTNING_DEPTH, spikeLength * 0.4);
    }

    private List<Vec3> generateBoltPath(Vec3 start, Vec3 end, int depth, double maxOffset) {
        if (depth <= 0) {
            List<Vec3> result = new ArrayList<>(2);
            result.add(start);
            result.add(end);
            return result;
        }

        Vec3 dir = end.subtract(start);
        Vec3 mid = start.add(dir.scale(0.5));
        Vec3 perp = randomPerpendicular(dir);
        double offset = (RANDOM.nextDouble() - 0.5) * 2.0 * maxOffset;
        Vec3 displacedMid = mid.add(perp.scale(offset));

        List<Vec3> left = generateBoltPath(start, displacedMid, depth - 1, maxOffset * 0.5);
        List<Vec3> right = generateBoltPath(displacedMid, end, depth - 1, maxOffset * 0.5);
        left.remove(left.size() - 1);
        left.addAll(right);
        return left;
    }

    private Vec3 randomPerpendicular(Vec3 dir) {
        Vec3 normDir = dir.normalize();
        Vec3 arbitrary = Math.abs(normDir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perp1 = normDir.cross(arbitrary).normalize();
        Vec3 perp2 = normDir.cross(perp1).normalize();
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        return perp1.scale(Math.cos(angle)).add(perp2.scale(Math.sin(angle)));
    }

    private void resetSoulState() {
        for (int i = 0; i < SOUL_COUNT; i++) {
            soulPos[i] = null;
            soulVel[i] = Vec3.ZERO;
            soulTrails[i].clear();
        }
        lastSoulUpdate = 0;
    }
}
