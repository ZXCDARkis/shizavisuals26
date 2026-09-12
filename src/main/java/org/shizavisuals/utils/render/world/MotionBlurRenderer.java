package org.shizavisuals.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.shizavisuals.feature.impl.visual.MotionBlurFeature;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.post.FullscreenQuad;
import org.shizavisuals.utils.render.post.PostPipelines;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Velocity-reprojection motion blur, ported from the MotionReBlur reference.
 * Runs as the last world effect (everything world-space gets blurred,
 * hands and GUI stay sharp since they render afterwards).
 *
 * <p>Matrix capture follows the Rockstar approach: the real projection matrix
 * comes straight from {@code LevelRenderer.render} params (not a stale copy),
 * and the view matrix is <b>rotation-only</b> ({@code viewRotationMatrix}),
 * exactly like the reference mod's {@code positionMatrix}. Camera translation
 * is carried separately by the {@code cameraPos} uniforms — baking translation
 * into the view matrix AND adding the camera delta in the shader would count
 * movement twice and smear the whole screen ("drug effect") when running.</p>
 */
public final class MotionBlurRenderer {
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putVec3()
            .putVec3()
            .putVec2()
            .putFloat()
            .putFloat()
            .putFloat()
            .putInt()
            .putInt()
            .putInt()
            .get();

    private final GpuBuffer uniforms = RenderSystem.getDevice().createBuffer(
            () -> "ShizaVisuals Motion Blur UBO",
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
            UNIFORM_SIZE
    );
    private TextureTarget sceneCopy;

    private final Matrix4f prevView = new Matrix4f();
    private final Matrix4f prevProjection = new Matrix4f();
    private float prevCamX;
    private float prevCamY;
    private float prevCamZ;
    private boolean hasPrevious;
    private long lastNano = System.nanoTime();
    private float currentFps = 60.0F;
    // Сырые координаты прошлого кадра — только для детекта телепорта.
    private double lastRawX;
    private double lastRawY;
    private double lastRawZ;
    private long lastPosNano;

    public void render(MotionBlurFeature feature, CameraRenderState cameraState, Matrix4fc projectionParam) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        if (!valid(target) || cameraState == null || !cameraState.initialized) {
            return;
        }
        // Реальная проекция этого кадра из LevelRenderer.render (как захват
        // матриц в rockstar через locals renderWorld), фолбэк — копия из GameRenderer.
        Matrix4f projection = projectionParam != null
                ? new Matrix4f(projectionParam)
                : Render3DUtil.levelProjectionCopy();
        if (projection == null) {
            return;
        }

        updateFps();
        // Rotation-only, как positionMatrix в референсе: трансляция камеры
        // живёт отдельно в cameraPos/prevCameraPos uniform'ах. Если впихнуть
        // сюда translate(-cam), шейдер посчитает движение дважды
        // (mvInverse уже содержит трансляцию + явный + (cam - prevCam)),
        // и при беге весь экран плывёт — тот самый «эффект нарика».
        Matrix4f view = currentView(cameraState);
        float camX = modPosition(cameraState.pos.x());
        float camY = modPosition(cameraState.pos.y());
        float camZ = modPosition(cameraState.pos.z());

        float strength = Math.abs(feature.strength.getValue().floatValue());
        if (!this.hasPrevious || strength <= 0.001F) {
            storePrevious(view, projection, camX, camY, camZ);
            trackRawPos(cameraState);
            return;
        }

        // Телепорт / смена измерения / респаун = огромный скачок ->
        // этот кадр не блурим вообще, иначе весь экран превращается в кашу.
        // modPosition рвётся на границе 30000 — дельту считаем по-честному,
        // через немодифицированные координаты.
        double rawDx = cameraState.pos.x() - this.lastRawX;
        double rawDy = cameraState.pos.y() - this.lastRawY;
        double rawDz = cameraState.pos.z() - this.lastRawZ;
        boolean firstPos = this.lastPosNano == 0L;
        double instantDist = firstPos ? 0.0D
                : Math.sqrt(rawDx * rawDx + rawDy * rawDy + rawDz * rawDz);
        trackRawPos(cameraState);

        if (!firstPos && instantDist > 8.0D) {
            // Телепорт: матрицы прошлого кадра больше невалидны.
            storePrevious(view, projection, camX, camY, camZ);
            return;
        }

        // Как в референсе: сила применяется напрямую, без лагового сглаживания.
        // Сглаживание (smoothedStrength EMA + speedDamp) давало шлейф на 2-3 кадра
        // и пульсацию силы при прыжках — тоже часть «нарика». Скорость движения
        // уже закодирована в длине velocity-вектора в шейдере, давить её на CPU
        // не нужно. Осталась только мягкая компенсация потери межкадрового
        // смещения на высокой герцовке, с потолком x1.35 (в референсе было
        // strength * fps/refresh без крышки — на 240 Гц блур удесятерялся).
        float targetStrength = strength;
        if (feature.refreshScaling.getValue()) {
            int refreshRate = mc.getWindow().getRefreshRate();
            if (refreshRate > 0 && this.currentFps > 1.0F) {
                float fpsOverRefresh = this.currentFps / refreshRate;
                targetStrength = strength * Math.min(1.35F, Math.max(0.6F,
                        (float) Math.sqrt(Math.max(0.25F, fpsOverRefresh))));
            }
        }
        if (targetStrength <= 0.002F) {
            storePrevious(view, projection, camX, camY, camZ);
            return;
        }

        int samples = sampleAmount(feature, this.currentFps);
        writeUniforms(view, projection, camX, camY, camZ,
                target.width, target.height, targetStrength, samples,
                feature.handThreshold.getValue().floatValue());

        ensureSceneCopy(target.width, target.height);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                target.getColorTexture(),
                this.sceneCopy.getColorTexture(),
                0, 0, 0, 0, 0,
                target.width,
                target.height
        );

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuTextureView colorView = target.getColorTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "ShizaVisuals Motion Blur", colorView, Optional.empty())) {
            pass.setPipeline(PostPipelines.MOTION_BLUR);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("MotionBlurUniforms", this.uniforms);
            pass.bindTexture("SceneSampler", this.sceneCopy.getColorTextureView(), linear);
            pass.bindTexture("DepthSampler", target.getDepthTextureView(), nearest);
            pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
            pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
        }

        storePrevious(view, projection, camX, camY, camZ);
    }

    private void writeUniforms(Matrix4f view, Matrix4f projection,
                               float camX, float camY, float camZ,
                               int width, int height,
                               float strength, int samples, float handThreshold) {
        Matrix4f mvInverse = new Matrix4f(view).invert();
        Matrix4f projInverse = new Matrix4f(projection).invert();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putMat4f(mvInverse)
                    .putMat4f(projInverse)
                    .putMat4f(this.prevView)
                    .putMat4f(this.prevProjection)
                    .putVec3(camX, camY, camZ)
                    .putVec3(this.prevCamX, this.prevCamY, this.prevCamZ)
                    .putVec2(width, height)
                    .putFloat(strength)
                    .putFloat(1.0F / samples)
                    .putFloat(handThreshold)
                    .putInt(samples)
                    .putInt(samples / 2)
                    .putInt(1)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniforms.slice(), data);
        }
    }

    private void storePrevious(Matrix4f view, Matrix4f projection, float camX, float camY, float camZ) {
        this.prevView.set(view);
        this.prevProjection.set(projection);
        this.prevCamX = camX;
        this.prevCamY = camY;
        this.prevCamZ = camZ;
        this.hasPrevious = true;
    }

    private void updateFps() {
        long now = System.nanoTime();
        float delta = (now - this.lastNano) / 1_000_000_000.0F;
        this.lastNano = now;
        if (delta > 0.0F && delta < 1.0F) {
            float instant = 1.0F / delta;
            if (this.currentFps <= 1.0F) {
                this.currentFps = instant;
            } else {
                // EMA: одиночный лаг-кадр больше не дёргает семплы/силу.
                this.currentFps += (instant - this.currentFps) * 0.08F;
            }
        }
    }

    private static Matrix4f currentView(CameraRenderState cameraState) {
        // Rotation-only: полный аналог positionMatrix из референса.
        // Трансляция НЕ вшивается — она отдельно идёт через
        // cameraPos/prevCameraPos (см. reproject() в шейдере).
        return new Matrix4f(cameraState.viewRotationMatrix);
    }

    private void trackRawPos(CameraRenderState cameraState) {
        this.lastRawX = cameraState.pos.x();
        this.lastRawY = cameraState.pos.y();
        this.lastRawZ = cameraState.pos.z();
        this.lastPosNano = System.nanoTime();
    }

    private static float modPosition(double value) {
        return (float) (value % 30000.0D);
    }

    private static int sampleAmount(MotionBlurFeature feature, float fps) {
        int base = switch (feature.quality.getValue()) {
            case "Low" -> 8;
            case "Medium" -> 12;
            case "Ultra" -> 24;
            default -> 16;
        };
        if (fps < 30.0F) {
            return Math.max(6, base / 2);
        }
        if (fps < 60.0F) {
            return Math.max(8, (int) (base * 0.75F));
        }
        if (fps > 144.0F) {
            return (int) (base * 1.25F);
        }
        return base;
    }

    private void ensureSceneCopy(int width, int height) {
        if (this.sceneCopy != null
                && this.sceneCopy.width == width
                && this.sceneCopy.height == height) {
            return;
        }
        if (this.sceneCopy != null) {
            this.sceneCopy.destroyBuffers();
        }
        this.sceneCopy = new TextureTarget(
                "shizavisuals-motion-blur-scene",
                width,
                height,
                false,
                PostPipelines.EFFECT_FORMAT
        );
    }

    private static boolean valid(RenderTarget target) {
        return target != null
                && target.width > 0
                && target.height > 0
                && target.getColorTexture() != null
                && target.getColorTextureView() != null
                && target.getDepthTexture() != null
                && target.getDepthTextureView() != null;
    }

    public void release() {
        if (this.sceneCopy != null) {
            this.sceneCopy.destroyBuffers();
            this.sceneCopy = null;
        }
        this.uniforms.close();
        this.hasPrevious = false;
        this.lastPosNano = 0L;
    }
}
