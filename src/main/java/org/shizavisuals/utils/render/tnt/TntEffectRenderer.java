package org.shizavisuals.utils.render.tnt;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.shizavisuals.feature.impl.visual.TntEffectFeature;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.post.FullscreenQuad;
import org.shizavisuals.utils.render.post.PostFx;
import org.shizavisuals.utils.render.post.PostPipelines;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Explosion dressing: expanding ground ring, additive fireball, screen-space
 * refraction ring, smoke burst and camera shake.
 *
 * <p>The world geometry reuses the plain POSITION_COLOR passthrough shader and
 * the shockwave re-samples a copy of the framebuffer, so nothing here needs a
 * bespoke vertex shader or an FBO blit - two world passes plus one full-screen
 * pass per live blast.
 */
public final class TntEffectRenderer {
    private static final int MAX_BLASTS = 12;
    private static final int WAVE_SEGMENTS = 96;
    private static final int FIREBALL_BANDS = 12;
    private static final int FIREBALL_SEGMENTS = 36;

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static TntEffectRenderer active;

    private final List<Blast> blasts = new ArrayList<>();
    private final List<Blast> pendingSmoke = new ArrayList<>();
    private final Matrix4f viewProj = new Matrix4f();
    private final Vector4f cornerA = new Vector4f();
    private final Vector4f cornerB = new Vector4f();
    private final Vector4f cornerC = new Vector4f();
    private final Vector4f cornerD = new Vector4f();

    private TextureTarget sceneCopy;
    private GpuBuffer uniforms;
    private GpuBuffer vertexBuffer;

    private double shakeX;
    private double shakeY;
    private double shakeZ;

    public static TntEffectRenderer active() {
        return active;
    }

    public double shakeX() {
        return this.shakeX;
    }

    public double shakeY() {
        return this.shakeY;
    }

    public double shakeZ() {
        return this.shakeZ;
    }

    public void onExplosion(double x, double y, double z, float radius) {
        Blast blast = new Blast(x, y, z, Math.max(1.8F, radius), System.currentTimeMillis());
        this.blasts.add(blast);
        while (this.blasts.size() > MAX_BLASTS) {
            this.blasts.remove(0);
        }
        this.pendingSmoke.add(blast);
    }

    public void tick(TntEffectFeature feature) {
        Minecraft mc = Minecraft.getInstance();
        if (!this.pendingSmoke.isEmpty()) {
            if (mc.level != null && feature.smoke.getValue()) {
                for (Blast blast : this.pendingSmoke) {
                    spawnSmoke(feature, blast);
                }
            }
            this.pendingSmoke.clear();
        }

        if (mc.level == null) {
            this.blasts.clear();
            clearShake();
            return;
        }

        long now = System.currentTimeMillis();
        long lifeMs = feature.lifetime.getValue().longValue();
        this.blasts.removeIf(blast -> now - blast.startTime > lifeMs);

        if (this.blasts.isEmpty() || !feature.shake.getValue()) {
            clearShake();
            return;
        }
        // Amplitude sums the decayed envelopes of every live blast, so a chain
        // reaction kicks harder than a single charge but still saturates at 1.
        float power = feature.shakePower.getValue().floatValue();
        float sum = 0.0F;
        for (Blast blast : this.blasts) {
            float t = clamp01((now - blast.startTime) / (float) lifeMs);
            sum += (1.0F - t) * (1.0F - t);
        }
        float amp = Math.min(1.0F, sum) * power * 0.3F;
        double phase = now / 24.0;
        this.shakeX = Math.sin(phase) * amp;
        this.shakeY = Math.cos(phase * 1.37) * amp;
        this.shakeZ = Math.sin(phase * 0.83) * amp;
    }

    public void renderWorld(TntEffectFeature feature, CameraRenderState cameraState, Matrix4fc projection) {
        // The explosion-packet and camera mixins reach the renderer through this
        // holder, so it has to be published even on frames without any blasts.
        active = this;
        if (cameraState == null || !cameraState.initialized || this.blasts.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        GpuTextureView colorView = target != null ? target.getColorTextureView() : null;
        if (colorView == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long lifeMs = feature.lifetime.getValue().longValue();

        if (feature.wave.getValue()) {
            drawWorldGeometry(feature, mc, target, colorView, cameraState, now, lifeMs, false);
        }
        if (feature.flash.getValue()) {
            drawWorldGeometry(feature, mc, target, colorView, cameraState, now, lifeMs, true);
        }
        if (feature.screenWave.getValue()) {
            drawScreenWave(feature, target, colorView, cameraState, projection, now, lifeMs);
        }
    }

    private void drawWorldGeometry(TntEffectFeature feature,
                                   Minecraft mc,
                                   RenderTarget target,
                                   GpuTextureView colorView,
                                   CameraRenderState cameraState,
                                   long now,
                                   long lifeMs,
                                   boolean fireball) {
        Camera camera = mc.gameRenderer.mainCamera();
        Matrix4f pose = Render3DUtil.cameraViewPose(camera);
        Vec3 cameraPos = cameraState.pos;
        int color = resolveColor(feature);

        int estimate = fireball
                ? FIREBALL_BANDS * FIREBALL_SEGMENTS * 6
                : 2 * WAVE_SEGMENTS * 6;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(
                Math.max(estimate, 64) * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(
                    bytes,
                    PrimitiveTopology.TRIANGLES,
                    DefaultVertexFormat.POSITION_COLOR
            );

            int vertexCount = 0;
            for (Blast blast : this.blasts) {
                float t = clamp01((now - blast.startTime) / (float) lifeMs);
                if (t >= 1.0F) {
                    continue;
                }
                vertexCount += fireball
                        ? addFireball(builder, cameraPos, pose, blast, t)
                        : addGroundWave(builder, cameraPos, pose, blast, t, color);
            }
            if (vertexCount <= 0) {
                return;
            }

            try (MeshData mesh = builder.buildOrThrow()) {
                this.vertexBuffer = uploadVertices(this.vertexBuffer, mesh.vertexBuffer());
                if (this.vertexBuffer == null) {
                    return;
                }
                int byteSize = vertexCount * DefaultVertexFormat.POSITION_COLOR.getVertexSize();
                GpuTextureView depthView = target.getDepthTextureView();
                var device = RenderSystem.getDevice();
                RenderPass pass = depthView != null
                        ? device.createCommandEncoder().createRenderPass(
                        () -> "ShizaVisuals TNT World", colorView, Optional.empty(), depthView, OptionalDouble.empty())
                        : device.createCommandEncoder().createRenderPass(
                        () -> "ShizaVisuals TNT World", colorView, Optional.empty());
                try {
                    pass.setPipeline(fireball
                            ? PostPipelines.WORLD_COLOR_ADDITIVE
                            : PostPipelines.WORLD_COLOR_TRANSLUCENT);
                    pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                    pass.setVertexBuffer(0, this.vertexBuffer.slice(0, byteSize));
                    pass.draw(vertexCount, 1, 0, 0);
                } finally {
                    pass.close();
                }
            }
        }
    }

    private static GpuBuffer uploadVertices(GpuBuffer existing, ByteBuffer data) {
        int byteSize = data.remaining();
        GpuBuffer buffer = existing;
        if (buffer == null || buffer.size() < byteSize) {
            if (buffer != null) {
                buffer.close();
            }
            buffer = RenderSystem.getDevice().createBuffer(
                    () -> "ShizaVisuals TNT Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    Math.max(byteSize * 2, 16 * 1024)
            );
        }
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, byteSize), data);
        return buffer;
    }

    private int addGroundWave(BufferBuilder builder,
                              Vec3 cameraPos,
                              Matrix4f pose,
                              Blast blast,
                              float t,
                              int color) {
        float radius = t * blast.radius;
        float alpha = (float) Math.pow(1.0F - t, 1.6F) * 0.85F;
        if (alpha <= 0.01F || radius <= 0.05F) {
            return 0;
        }

        int vertexCount = 0;
        for (int band = 0; band < 2; band++) {
            float width = band == 0 ? 0.30F : 0.75F;
            float bandAlpha = band == 0 ? alpha : alpha * 0.35F;
            float inner = Math.max(0.01F, radius - width);
            float outer = radius + width;
            int bandColor = ColorUtil.withAlpha(color, Math.round(bandAlpha * 255.0F));
            for (int s = 0; s < WAVE_SEGMENTS; s++) {
                double a0 = s * Math.PI * 2.0 / WAVE_SEGMENTS;
                double a1 = (s + 1) * Math.PI * 2.0 / WAVE_SEGMENTS;
                float c0 = (float) Math.cos(a0);
                float s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1);
                float s1 = (float) Math.sin(a1);
                quad(builder, cameraPos, pose, bandColor,
                        blast.x + c0 * inner, blast.y + 0.04, blast.z + s0 * inner,
                        blast.x + c0 * outer, blast.y + 0.04, blast.z + s0 * outer,
                        blast.x + c1 * outer, blast.y + 0.04, blast.z + s1 * outer,
                        blast.x + c1 * inner, blast.y + 0.04, blast.z + s1 * inner);
                vertexCount += 6;
            }
        }
        return vertexCount;
    }

    private int addFireball(BufferBuilder builder,
                            Vec3 cameraPos,
                            Matrix4f pose,
                            Blast blast,
                            float t) {
        float local = clamp01(t * 2.5F);
        if (local >= 1.0F) {
            return 0;
        }
        float radius = (0.4F + local * 1.3F) * (blast.radius * 0.32F);
        float alpha = (1.0F - local) * (1.0F - local) * 0.7F;
        if (alpha <= 0.01F) {
            return 0;
        }

        int vertexCount = 0;
        for (int band = 0; band < FIREBALL_BANDS; band++) {
            double phi0 = (band / (double) FIREBALL_BANDS - 0.5D) * Math.PI;
            double phi1 = ((band + 1) / (double) FIREBALL_BANDS - 0.5D) * Math.PI;
            float y0 = (float) Math.sin(phi0) * radius;
            float y1 = (float) Math.sin(phi1) * radius;
            float rad0 = (float) Math.cos(phi0) * radius;
            float rad1 = (float) Math.cos(phi1) * radius;
            float mix = (float) (Math.abs(band / (double) (FIREBALL_BANDS - 1) - 0.5D) * 2.0D);
            int color = ColorUtil.pack(
                    Math.round(lerp(mix, 1.0F, 1.0F) * 255.0F),
                    Math.round(lerp(mix, 0.95F, 0.55F) * 255.0F),
                    Math.round(lerp(mix, 0.8F, 0.12F) * 255.0F),
                    Math.round(alpha * 255.0F)
            );
            for (int s = 0; s < FIREBALL_SEGMENTS; s++) {
                double a0 = s * Math.PI * 2.0 / FIREBALL_SEGMENTS;
                double a1 = (s + 1) * Math.PI * 2.0 / FIREBALL_SEGMENTS;
                float c0 = (float) Math.cos(a0);
                float s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1);
                float s1 = (float) Math.sin(a1);
                quad(builder, cameraPos, pose, color,
                        blast.x + c0 * rad0, blast.y + y0, blast.z + s0 * rad0,
                        blast.x + c0 * rad1, blast.y + y1, blast.z + s0 * rad1,
                        blast.x + c1 * rad1, blast.y + y1, blast.z + s1 * rad1,
                        blast.x + c1 * rad0, blast.y + y0, blast.z + s1 * rad0);
                vertexCount += 6;
            }
        }
        return vertexCount;
    }

    /** Emits one quad as two triangles, transforming each corner in place. */
    private void quad(BufferBuilder builder, Vec3 cameraPos, Matrix4f pose, int color,
                      double ax, double ay, double az,
                      double bx, double by, double bz,
                      double cx, double cy, double cz,
                      double dx, double dy, double dz) {
        toView(this.cornerA, cameraPos, pose, ax, ay, az);
        toView(this.cornerB, cameraPos, pose, bx, by, bz);
        toView(this.cornerC, cameraPos, pose, cx, cy, cz);
        toView(this.cornerD, cameraPos, pose, dx, dy, dz);
        builder.addVertex(this.cornerA.x, this.cornerA.y, this.cornerA.z).setColor(color);
        builder.addVertex(this.cornerB.x, this.cornerB.y, this.cornerB.z).setColor(color);
        builder.addVertex(this.cornerC.x, this.cornerC.y, this.cornerC.z).setColor(color);
        builder.addVertex(this.cornerA.x, this.cornerA.y, this.cornerA.z).setColor(color);
        builder.addVertex(this.cornerC.x, this.cornerC.y, this.cornerC.z).setColor(color);
        builder.addVertex(this.cornerD.x, this.cornerD.y, this.cornerD.z).setColor(color);
    }

    private static void toView(Vector4f out, Vec3 cameraPos, Matrix4f pose,
                               double x, double y, double z) {
        out.set(
                (float) (x - cameraPos.x),
                (float) (y - cameraPos.y),
                (float) (z - cameraPos.z),
                1.0F
        ).mul(pose);
    }

    private void drawScreenWave(TntEffectFeature feature,
                                RenderTarget target,
                                GpuTextureView colorView,
                                CameraRenderState cameraState,
                                Matrix4fc projection,
                                long now,
                                long lifeMs) {
        if (target.width <= 0 || target.height <= 0 || projection == null) {
            return;
        }
        ensureResources(target.width, target.height);
        if (this.sceneCopy == null || this.sceneCopy.getColorTexture() == null || this.uniforms == null) {
            return;
        }

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                target.getColorTexture(),
                this.sceneCopy.getColorTexture(),
                0, 0, 0, 0, 0,
                target.width,
                target.height
        );

        this.viewProj.set(projection).mul(cameraState.viewRotationMatrix);
        int width = target.width;
        int height = target.height;
        float power = feature.wavePower.getValue().floatValue();

        for (Blast blast : this.blasts) {
            float t = clamp01((now - blast.startTime) / (float) lifeMs);
            float st = clamp01(t * 1.8F);
            if (st >= 1.0F) {
                continue;
            }
            float strength = power * (1.0F - st) * (1.0F - st);
            // Cut scales with power, so a subtle ring still gets its brief kick
            // instead of never reaching the old fixed threshold at all.
            if (strength <= Math.max(0.01F, power * 0.01F)) {
                continue;
            }
            Vector4f origin = projectToScreen(cameraState, blast);
            if (origin == null) {
                continue;
            }

            float radius = st * Math.max(width, height) * 0.85F;
            // Pixel-space numbers are tuned at 1080p; scale with the frame height
            // so the ring keeps its proportions on high-res windows.
            float scale = height / 1080.0F;
            float thickness = (90.0F + 140.0F * (1.0F - st)) * Math.max(0.5F, scale);
            writeUniforms(width, height,
                    (origin.x * 0.5F + 0.5F) * width,
                    (origin.y * 0.5F + 0.5F) * height,
                    radius, thickness, strength);

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "ShizaVisuals TNT Shockwave", colorView, Optional.empty())) {
                pass.setPipeline(PostPipelines.TNT_SHOCKWAVE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("TntShockwaveUniforms", this.uniforms);
                pass.bindTexture("SceneSampler", this.sceneCopy.getColorTextureView(), PostFx.linearSampler());
                pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
                pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
            }
        }
    }

    /**
     * Explosion origin in NDC, or null when it is behind the camera or far
     * outside the viewport (in which case the ring cannot be seen anyway).
     */
    private Vector4f projectToScreen(CameraRenderState cameraState, Blast blast) {
        this.cornerA.set(
                (float) (blast.x - cameraState.pos.x()),
                (float) (blast.y - cameraState.pos.y()),
                (float) (blast.z - cameraState.pos.z()),
                1.0F
        );
        this.viewProj.transform(this.cornerA);
        if (this.cornerA.w <= 0.05F) {
            return null;
        }
        float invW = 1.0F / this.cornerA.w;
        float ndcX = this.cornerA.x * invW;
        float ndcY = this.cornerA.y * invW;
        if (Math.abs(ndcX) > 1.6F || Math.abs(ndcY) > 1.6F) {
            return null;
        }
        this.cornerA.set(ndcX, ndcY, 0.0F, 1.0F);
        return this.cornerA;
    }

    private void writeUniforms(int width, int height,
                               float originX, float originY,
                               float radius, float thickness, float strength) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putVec4(width, height, 0.0F, 0.0F)
                    .putVec4(originX, originY, radius, thickness)
                    .putVec4(strength, 0.0F, 0.0F, 0.0F)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniforms.slice(), data);
        }
    }

    private void ensureResources(int width, int height) {
        if (this.sceneCopy != null
                && this.sceneCopy.width == width
                && this.sceneCopy.height == height) {
            return;
        }
        if (this.sceneCopy != null) {
            this.sceneCopy.destroyBuffers();
        }
        this.sceneCopy = new TextureTarget(
                "shizavisuals-tnt-scene",
                width,
                height,
                false,
                PostPipelines.EFFECT_FORMAT
        );
        if (this.uniforms == null) {
            this.uniforms = PostFx.createUniforms("shizavisuals-tnt", UNIFORM_SIZE);
        }
    }

    private void spawnSmoke(TntEffectFeature feature, Blast blast) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.particleEngine == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = feature.smokeAmount.getValue().intValue();
        float radius = blast.radius * 0.7F;

        for (int i = 0; i < count; i++) {
            double dirX = random.nextDouble(-1.0D, 1.0D);
            double dirZ = random.nextDouble(-1.0D, 1.0D);
            double dirY = random.nextDouble(-0.3D, 1.0D);
            double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (len < 0.001D) {
                len = 1.0D;
            }
            dirX /= len;
            dirY /= len;
            dirZ /= len;
            double speed = 0.35D + random.nextDouble() * 0.55D;
            addParticle(mc,
                    random.nextFloat() < 0.45F ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    blast.x + dirX * radius * 0.35D,
                    blast.y + dirY * radius * 0.35D,
                    blast.z + dirZ * radius * 0.35D,
                    dirX * speed, dirY * speed + 0.12D, dirZ * speed);
        }

        int fire = Math.max(6, count / 4);
        for (int i = 0; i < fire; i++) {
            double dirX = random.nextDouble(-1.0D, 1.0D);
            double dirY = random.nextDouble(-0.2D, 1.0D);
            double dirZ = random.nextDouble(-1.0D, 1.0D);
            double speed = 0.25D + random.nextDouble() * 0.4D;
            addParticle(mc,
                    random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.LAVA,
                    blast.x + dirX * radius * 0.3D,
                    blast.y + dirY * radius * 0.3D,
                    blast.z + dirZ * radius * 0.3D,
                    dirX * speed, dirY * speed + 0.1D, dirZ * speed);
        }
    }

    private static void addParticle(Minecraft mc, ParticleOptions effect,
                                    double x, double y, double z,
                                    double vx, double vy, double vz) {
        try {
            mc.particleEngine.createParticle(effect, x, y, z, vx, vy, vz);
        } catch (Throwable ignored) {
            // Particle creation must never take the render loop down.
        }
    }

    private static int resolveColor(TntEffectFeature feature) {
        return switch (feature.colorMode.getValue()) {
            case "Orange" -> 0xFFFF8C1A;
            case "Red" -> 0xFFFF4444;
            case "Blue" -> 0xFF4444FF;
            case "Green" -> 0xFF44FF44;
            case "White" -> 0xFFFFFFFF;
            case "Purple" -> 0xFFA080FF;
            default -> Theme.getAccent();
        };
    }

    private void clearShake() {
        this.shakeX = 0.0D;
        this.shakeY = 0.0D;
        this.shakeZ = 0.0D;
    }

    public void release() {
        if (active == this) {
            active = null;
        }
        this.blasts.clear();
        this.pendingSmoke.clear();
        clearShake();
        if (this.sceneCopy != null) {
            this.sceneCopy.destroyBuffers();
            this.sceneCopy = null;
        }
        if (this.uniforms != null) {
            this.uniforms.close();
            this.uniforms = null;
        }
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
            this.vertexBuffer = null;
        }
    }

    private static float clamp01(float value) {
        return Math.clamp(value, 0.0F, 1.0F);
    }

    private static float lerp(float delta, float from, float to) {
        return from + (to - from) * delta;
    }

    private static final class Blast {
        final double x;
        final double y;
        final double z;
        final float radius;
        final long startTime;

        Blast(double x, double y, double z, float radius, long startTime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.startTime = startTime;
        }
    }
}