package org.shizavisuals.utils.render.spatial;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.impl.visual.SpatialInventoryFeature;
import org.shizavisuals.mixin.accessor.GameRendererAccessor;
import org.shizavisuals.mixin.accessor.GuiRendererAccessor;
import org.shizavisuals.mixin.accessor.MouseHandlerAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Контроллер 3D-инвентаря: изолированный extract экрана, offscreen-таргет,
 * рисование квада в мире, маппинг мыши и состояние камеры.
 */
public final class SpatialInventory {
    private SpatialInventory() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("ShizaVisuals-Spatial");

    private static final SpatialScreenTexture TEXTURE = new SpatialScreenTexture();
    private static final StagedVertexBuffer SCREEN_BUFFER =
            new StagedVertexBuffer(() -> "ShizaVisuals Spatial GUI Screen Buffer", 786432);
    private static final PoseStack WORLD_POSE_STACK = new PoseStack();
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();
    private static final Vector3f ZERO_VECTOR = new Vector3f();
    private static final Vector4f COLOR_MODULATOR = new Vector4f();

    private static GuiRenderState screenRenderState;
    private static GuiRenderer screenGuiRenderer;
    private static boolean renderingIsolatedGui;

    private static Screen hookedScreen;
    private static boolean rotationAnchored;
    private static float anchoredYaw;
    private static float anchoredPitch;
    private static long screenOpenTime;

    private static boolean closing;
    private static long closeStartTime;
    private static long closeStartElapsed;
    private static long closeDurationMs;
    private static float closeYaw;

    private static Vec3 screenAnchorDelta = Vec3.ZERO;
    private static Vec3 captureStartPos;
    private static float captureStartYRot;
    private static float captureStartXRot;
    private static boolean captureStartStored;

    private static GpuBufferSlice capturedProjectionBuffer;
    private static ProjectionType capturedProjectionType;
    private static final PoseStack capturedPoseStack = new PoseStack();
    private static boolean hasCapturedPerspective;
    private static boolean hasDrawnThisFrame;

    private static SpatialRenderUtil.QuadBasis quadBasis;
    private static long basisFrame = -1L;
    private static long frameCounter;

    private static double physicalX = Double.NaN;
    private static double physicalY = Double.NaN;
    private static double lastPhysicalX = Double.NaN;
    private static double lastPhysicalY = Double.NaN;
    private static double mappedX = Double.NaN;
    private static double mappedY = Double.NaN;
    private static int heldHoverX = Integer.MIN_VALUE;
    private static int heldHoverY = Integer.MIN_VALUE;
    private static double preReleaseX;
    private static double preReleaseY;

    // ===================== Состояние =====================

    public static SpatialInventoryFeature feature() {
        return FeatureManager.INSTANCE.getEnabled(SpatialInventoryFeature.class);
    }

    public static boolean isActive() {
        return feature() != null && Minecraft.getInstance().level != null;
    }

    public static Screen getHookedScreen() {
        return hookedScreen;
    }

    public static boolean isClosing() {
        return closing;
    }

    /** v1 — только третье лицо, поэтому закрытие всегда с плавным возвратом камеры. */
    public static boolean isClosingWithCameraTransition() {
        return closing;
    }

    public static boolean shouldHook(Screen screen) {
        if (screen == null || !isActive()) {
            return false;
        }
        if (!(screen instanceof AbstractContainerScreen)) {
            return false;
        }
        String id = screen.getClass().getName();
        if (id.startsWith("org.shizavisuals.") || id.contains("Chat") || id.contains("Title")) {
            return false;
        }
        return true;
    }

    public static boolean shouldCapture() {
        Minecraft mc = Minecraft.getInstance();
        boolean active = shouldHook(hookedScreen) && hookedScreen == mc.gui.screen();
        if (!active) {
            captureStartStored = false;
            captureStartPos = null;
            return false;
        }
        if (!captureStartStored) {
            Camera camera = mc.gameRenderer.mainCamera();
            captureStartPos = camera.position();
            captureStartYRot = camera.yRot();
            captureStartXRot = camera.xRot();
            captureStartStored = true;
        }
        return true;
    }

    public static Vec3 getCaptureStartPos() {
        return captureStartPos;
    }

    public static float getCaptureStartYRot() {
        return captureStartYRot;
    }

    public static float getCaptureStartXRot() {
        return captureStartXRot;
    }

    public static float anchoredYaw() {
        if (rotationAnchored) {
            return anchoredYaw;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.getYRot() : 0.0F;
    }

    public static float anchoredPitch() {
        if (rotationAnchored) {
            return anchoredPitch;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.getXRot() : 0.0F;
    }

    public static Vec3 getScreenAnchorDelta() {
        return screenAnchorDelta;
    }

    public static void setScreenAnchorDelta(Vec3 delta) {
        screenAnchorDelta = delta;
    }

    public static boolean hasHotbarToHide() {
        SpatialInventoryFeature f = feature();
        if (f == null || !f.hideHotbar.getValue()) {
            return false;
        }
        return shouldCapture() || closing;
    }

    public static float orbitYaw(SpatialInventoryFeature f) {
        return f.cameraOrbitYaw.getValue().floatValue();
    }

    // ===================== Хук экрана =====================

    public static void hookScreen(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (hookedScreen == screen || screen == null || screen != mc.gui.screen()) {
            return;
        }
        boolean swapping = hookedScreen != null;
        if (hookedScreen != null) {
            onScreenRemoved(false);
        }
        if (!shouldHook(screen)) {
            return;
        }
        hookedScreen = screen;
        LocalPlayer player = mc.player;
        if (!(swapping && rotationAnchored) && player != null) {
            anchoredYaw = player.getYRot();
            anchoredPitch = player.getXRot();
        }
        rotationAnchored = true;
        closing = false;
        if (!swapping) {
            screenOpenTime = System.currentTimeMillis();
        }
        resetMouseState();
        prepareTarget();
        LOGGER.info("Spatial inventory hooked: {}", screen.getClass().getSimpleName());
    }

    public static void onScreenRemoved(boolean animate) {
        if (hookedScreen == null) {
            return;
        }
        hookedScreen = null;
        Minecraft mc = Minecraft.getInstance();
        boolean startClosing = animate && mc.level != null && beginClosing();
        if (!startClosing) {
            finishClosing();
        }
        LOGGER.info("Spatial inventory removed (animate={})", animate);
    }

    private static boolean beginClosing() {
        Minecraft mc = Minecraft.getInstance();
        SpatialInventoryFeature f = feature();
        if (!hasCapturedPerspective || mc.player == null || !TEXTURE.hasTexture() || f == null) {
            return false;
        }
        long fadeMs = f.fadeMs.getValue().longValue();
        long scaleMs = f.scaleAnimMs.getValue().longValue();
        long transitionMs = f.transitionMs.getValue().longValue();
        long animationMs = Math.max(fadeMs, scaleMs);
        closeDurationMs = Math.max(animationMs, transitionMs);
        if (closeDurationMs <= 0L) {
            return false;
        }
        closing = true;
        closeStartTime = System.currentTimeMillis();
        closeStartElapsed = animationMs;
        closeYaw = anchoredYaw() + orbitYaw(f);
        hasDrawnThisFrame = false;
        return true;
    }

    public static void finishClosing() {
        closing = false;
        hasCapturedPerspective = false;
        hasDrawnThisFrame = false;
        rotationAnchored = false;
        screenAnchorDelta = Vec3.ZERO;
        TEXTURE.clearTarget();
    }

    private static boolean isCloseFinished() {
        return System.currentTimeMillis() - closeStartTime >= closeDurationMs;
    }

    private static long animationElapsedMs() {
        long now = System.currentTimeMillis();
        return closing
                ? Math.max(0L, closeStartElapsed - (now - closeStartTime))
                : now - screenOpenTime;
    }

    public static float getCloseBlend() {
        SpatialInventoryFeature f = feature();
        if (f == null) {
            return 1.0F;
        }
        long transitionMs = Math.max(1L, f.transitionMs.getValue().longValue());
        if (isCloseFinished()) {
            return 1.0F;
        }
        float openProgress = Math.max(0.0F, 1.0F - (float) getCloseElapsedMs() / transitionMs);
        return 1.0F - easeInOutSine(openProgress);
    }

    private static float getCloseElapsedMs() {
        return Math.max(0L, System.currentTimeMillis() - closeStartTime);
    }

    // ===================== Кадр =====================

    public static void updateFrame() {
        frameCounter++;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (hookedScreen != null && (screen != hookedScreen || !shouldHook(hookedScreen))) {
            onScreenRemoved(screen != hookedScreen);
        }
        if (closing && (mc.level == null || isCloseFinished())) {
            finishClosing();
        }
    }

    public static void onFrameStart() {
        hasDrawnThisFrame = false;
    }

    public static void prepareTarget() {
        TEXTURE.prepareTarget();
    }

    public static void clearTarget() {
        TEXTURE.clearTarget();
    }

    // ===================== Изолированный GUI =====================

    private static List<PictureInPictureRenderer<?>> vanillaPipRenderers(Minecraft mc) {
        GuiRenderer main = ((GameRendererAccessor) mc.gameRenderer).shizavisuals$getGuiRenderer();
        List<PictureInPictureRenderer<?>> out = new ArrayList<>();
        for (PictureInPictureRenderer<?> renderer : ((GuiRendererAccessor) main).shizavisuals$getPipRenderers().values()) {
            if (renderer.getClass().getName().startsWith("net.minecraft.")) {
                out.add(renderer);
            }
        }
        return out;
    }

    private static void ensureScreenGuiRenderer() {
        if (screenGuiRenderer == null) {
            Minecraft mc = Minecraft.getInstance();
            screenRenderState = new GuiRenderState();
            screenGuiRenderer = new GuiRenderer(screenRenderState, mc.gameRenderer.featureRenderDispatcher(), vanillaPipRenderers(mc));
        }
    }

    public static GuiGraphicsExtractor createIsolatedGraphics() {
        ensureScreenGuiRenderer();
        Minecraft mc = Minecraft.getInstance();
        return new GuiGraphicsExtractor(mc, screenRenderState, getHoverX(), getHoverY());
    }

    /** Для GuiRendererMixin: рисует ли сейчас изолят. */
    public static boolean isIsolatedGuiRenderer(GuiRenderer renderer) {
        return renderingIsolatedGui && renderer == screenGuiRenderer;
    }

    public static com.mojang.blaze3d.pipeline.RenderTarget isolatedRenderTarget() {
        return TEXTURE.getTarget();
    }

    public static void renderIsolatedGui() {
        ensureScreenGuiRenderer();
        clearTarget();
        renderingIsolatedGui = true;
        try {
            screenGuiRenderer.render();
            screenGuiRenderer.endFrame();
        } finally {
            renderingIsolatedGui = false;
        }
    }

    // ===================== Мышь =====================

    public static void resetMouseState() {
        Minecraft mc = Minecraft.getInstance();
        mappedX = Double.NaN;
        mappedY = Double.NaN;
        heldHoverX = Integer.MIN_VALUE;
        heldHoverY = Integer.MIN_VALUE;
        if (((MouseHandlerAccessor) mc.mouseHandler).getMouseGrabbed()) {
            physicalX = mc.getWindow().getScreenWidth() / 2.0;
            physicalY = mc.getWindow().getScreenHeight() / 2.0;
        } else {
            physicalX = mc.mouseHandler.xpos();
            physicalY = mc.mouseHandler.ypos();
        }
        lastPhysicalX = physicalX;
        lastPhysicalY = physicalY;
    }

    private static double centerX() {
        return Minecraft.getInstance().getWindow().getScreenWidth() / 2.0;
    }

    private static double centerY() {
        return Minecraft.getInstance().getWindow().getScreenHeight() / 2.0;
    }

    public static double getPhysicalX(double fallback) {
        return Double.isNaN(physicalX) ? fallback : physicalX;
    }

    public static double getPhysicalY(double fallback) {
        return Double.isNaN(physicalY) ? fallback : physicalY;
    }

    public static double[] captureMove(double x, double y, boolean mouseGrabbed) {
        double[] delta = {x - lastPhysicalX, y - lastPhysicalY};
        lastPhysicalX = x;
        lastPhysicalY = y;
        if (!mouseGrabbed) {
            physicalX = x;
            physicalY = y;
        }
        return delta;
    }

    /** Вызывается из MouseHandlerMixin.onMove. Возвращает remapped raw-позицию или null. */
    public static Vector2d remapMove(long handle, double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        if (handle != mc.getWindow().handle() || !mouseOverrideActive()) {
            return null;
        }
        MouseHandlerAccessor accessor = (MouseHandlerAccessor) mc.mouseHandler;
        boolean grabbed = accessor.getMouseGrabbed();
        if (x == -50.0 && y == -50.0 && !grabbed) {
            return null;
        }
        captureMove(x, y, grabbed);
        return updateMousePosition(getPhysicalX(centerX()), getPhysicalY(centerY()));
    }

    public static boolean mouseOverrideActive() {
        Minecraft mc = Minecraft.getInstance();
        if (!isActive() || mc.level == null) {
            return false;
        }
        return hookedScreen != null && hookedScreen == mc.gui.screen() && shouldHook(hookedScreen);
    }

    public static double getLastPosX(double fallback) {
        return Double.isNaN(mappedX) ? fallback : mappedX;
    }

    public static double getLastPosY(double fallback) {
        return Double.isNaN(mappedY) ? fallback : mappedY;
    }

    public static int getHoverX() {
        double v = getLastPosX(-1.0);
        if (heldHoverX != Integer.MIN_VALUE && Math.abs(v - heldHoverX) < 1.5) {
            return heldHoverX;
        }
        heldHoverX = (int) v;
        return heldHoverX;
    }

    public static int getHoverY() {
        double v = getLastPosY(-1.0);
        if (heldHoverY != Integer.MIN_VALUE && Math.abs(v - heldHoverY) < 1.5) {
            return heldHoverY;
        }
        heldHoverY = (int) v;
        return heldHoverY;
    }

    /** Маппинг источника (физический курсор) на квад; обновляет raw-позицию MouseHandler. */
    public static Vector2d updateMousePosition(double sourceXPos, double sourceYPos) {
        if (!shouldCapture()) {
            return null;
        }
        SpatialRenderUtil.QuadBasis basis = getQuadBasis();
        if (basis == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        double guiScale = mc.getWindow().getGuiScale();
        Vector2d mouse = SpatialRenderUtil.getQuadScreenPosition(sourceXPos, sourceYPos, basis);
        if (mouse == null) {
            return null;
        }
        mappedX = mouse.x / guiScale;
        mappedY = mouse.y / guiScale;
        double rawX = mouse.x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double rawY = mouse.y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        MouseHandlerAccessor accessor = (MouseHandlerAccessor) mc.mouseHandler;
        accessor.setRawXpos(rawX);
        accessor.setRawYpos(rawY);
        return new Vector2d(rawX, rawY);
    }

    /** Вызывается из GameRenderer.extract HEAD. */
    public static void refreshMousePosition() {
        if (shouldCapture()) {
            updateMousePosition(getPhysicalX(centerX()), getPhysicalY(centerY()));
        }
    }

    public static void onPreReleaseMouse() {
        MouseHandlerAccessor accessor = (MouseHandlerAccessor) Minecraft.getInstance().mouseHandler;
        preReleaseX = accessor.getRawXpos();
        preReleaseY = accessor.getRawYpos();
    }

    public static void onPostReleaseMouse() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null || !shouldHook(screen)) {
            return;
        }
        var window = mc.getWindow();
        if (preReleaseX >= 0.0 && preReleaseY >= 0.0
                && preReleaseX <= window.getScreenWidth() && preReleaseY <= window.getScreenHeight()) {
            MouseHandlerAccessor accessor = (MouseHandlerAccessor) mc.mouseHandler;
            accessor.setRawXpos(preReleaseX);
            accessor.setRawYpos(preReleaseY);
        }
    }

    // ===================== Базис квада =====================

    public static SpatialRenderUtil.QuadBasis getQuadBasis() {
        if (basisFrame != frameCounter) {
            updateQuadBasis();
            basisFrame = frameCounter;
        }
        return quadBasis;
    }

    private static SpatialRenderUtil.ScreenTransformConfig screenConfig(SpatialInventoryFeature f) {
        return new SpatialRenderUtil.ScreenTransformConfig(
                f.screenDistance.getValue().floatValue(),
                f.screenSideOffset.getValue().floatValue(),
                f.screenHeightOffset.getValue().floatValue(),
                f.screenYawOffset.getValue().floatValue(),
                f.screenPitchOffset.getValue().floatValue(),
                f.screenScale.getValue().floatValue());
    }

    private static float animatedScale(SpatialInventoryFeature f) {
        float base = f.screenScale.getValue().floatValue();
        long scaleMs = f.scaleAnimMs.getValue().longValue();
        if (scaleMs <= 0L) {
            return base;
        }
        float progress = Math.min(1.0F, (float) animationElapsedMs() / scaleMs);
        float eased = easeOutBack(progress);
        float startPercent = f.startScalePercent.getValue().floatValue() / 100.0F;
        return lerp(base * startPercent, base, eased);
    }

    private static void updateQuadBasis() {
        Minecraft mc = Minecraft.getInstance();
        SpatialInventoryFeature f = feature();
        if (f == null || mc.player == null) {
            return;
        }
        computeScreenPose(null, f);
        TextureTarget target = TEXTURE.getTarget();
        float aspect = target != null && target.height > 0 ? (float) target.width / target.height : 1.0F;
        quadBasis = SpatialRenderUtil.computeQuadBasis(WORLD_POSE_STACK.last().pose(), aspect, animatedScale(f));
    }

    /** Заполняет WORLD_POSE_STACK позой квада в координатах камеры; matrices может быть null. */
    private static void computeScreenPose(PoseStack matrices, SpatialInventoryFeature f) {
        float yaw = closing ? closeYaw : anchoredYaw() + orbitYaw(f);
        float yawRadians = (float) Math.toRadians(yaw);
        float scale = animatedScale(f);
        Vec3 anchor = screenAnchorDelta;
        if (matrices != null) {
            matrices.translate(anchor.x, anchor.y, anchor.z);
            SpatialRenderUtil.applyScreenTransform(matrices, yawRadians, screenConfig(f));
            matrices.scale(scale, scale, scale);
        }
        WORLD_POSE_STACK.setIdentity();
        WORLD_POSE_STACK.translate(anchor.x, anchor.y, anchor.z);
        SpatialRenderUtil.applyScreenTransform(WORLD_POSE_STACK, yawRadians, screenConfig(f));
        WORLD_POSE_STACK.scale(scale, scale, scale);
    }

    // ===================== Захват перспективы =====================

    public static void capturePerspectiveState(GpuBufferSlice buffer, ProjectionType type, PoseStack poseStack) {
        capturedProjectionBuffer = buffer;
        capturedProjectionType = type;
        capturedPoseStack.setIdentity();
        if (poseStack != null) {
            capturedPoseStack.last().pose().set(poseStack.last().pose());
            capturedPoseStack.last().normal().set(poseStack.last().normal());
        }
        hasCapturedPerspective = true;
    }

    // ===================== Рисование квада =====================

    public static void renderInWorldPost() {
        Minecraft mc = Minecraft.getInstance();
        SpatialInventoryFeature f = feature();
        if (f == null) {
            return;
        }
        if (hasCapturedPerspective && !hasDrawnThisFrame && TEXTURE.hasTexture() && mc.player != null) {
            hasDrawnThisFrame = true;
            drawQuadPass(f);
        }
    }

    private static void drawQuadPass(SpatialInventoryFeature f) {
        GpuBufferSlice prevBuffer = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType prevType = RenderSystem.getProjectionType();
        try {
            if (capturedProjectionBuffer != null && capturedProjectionType != null) {
                RenderSystem.setProjectionMatrix(capturedProjectionBuffer, capturedProjectionType);
            }
            renderInWorld(capturedPoseStack, f);
        } finally {
            if (prevBuffer != null) {
                RenderSystem.setProjectionMatrix(prevBuffer, prevType);
            }
        }
    }

    private static boolean canDraw(SpatialInventoryFeature f) {
        return closing ? animationElapsedMs() > 0L : shouldCapture();
    }

    private static void renderInWorld(PoseStack matrices, SpatialInventoryFeature f) {
        Minecraft mc = Minecraft.getInstance();
        if (!TEXTURE.hasTexture() || !canDraw(f) || mc.player == null) {
            return;
        }
        GpuTextureView texture = TEXTURE.getTarget().getColorTextureView();
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        VertexFormat format = pipeline.getVertexFormatBinding(0);
        if (format == null) {
            return;
        }
        StagedVertexBuffer.Draw draw = SCREEN_BUFFER.appendDraw(format, pipeline.getPrimitiveTopology(), null);
        VertexConsumer buffer = SCREEN_BUFFER.getVertexBuilder(draw);
        matrices.pushPose();
        computeScreenPose(matrices, f);
        Matrix4f pose = matrices.last().pose();
        TextureTarget target = TEXTURE.getTarget();
        float aspect = target.height > 0 ? (float) target.width / target.height : 1.0F;
        SpatialRenderUtil.addScreenQuad(buffer, pose, aspect, 255, quadAlpha(f));
        matrices.popPose();
        SCREEN_BUFFER.upload();
        StagedVertexBuffer.ExecuteInfo info = SCREEN_BUFFER.getExecuteInfo(draw);
        if (info != null) {
            drawScreenMesh(info, texture, f);
        }
        SCREEN_BUFFER.endFrame();
    }

    private static int quadAlpha(SpatialInventoryFeature f) {
        float alpha = fadeAlpha(f) * f.screenOpacity.getValue().floatValue() / 100.0F;
        return Math.round(Mth.clamp(alpha, 0.0F, 0.999F) * 255.0F);
    }

    private static float fadeAlpha(SpatialInventoryFeature f) {
        long fadeMs = f.fadeMs.getValue().longValue();
        if (fadeMs <= 0L) {
            return 1.0F;
        }
        return Math.min(1.0F, (float) animationElapsedMs() / fadeMs);
    }

    private static void drawScreenMesh(StagedVertexBuffer.ExecuteInfo info, GpuTextureView texture, SpatialInventoryFeature f) {
        GpuTextureView output = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
        if (output == null) {
            return;
        }
        float alpha = fadeAlpha(f) * f.screenOpacity.getValue().floatValue() / 100.0F;
        COLOR_MODULATOR.set(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 0.999F));
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(IDENTITY_MATRIX, COLOR_MODULATOR, ZERO_VECTOR, IDENTITY_MATRIX);
        RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "ShizaVisuals Spatial GUI", output, Optional.empty(), null, OptionalDouble.empty());
        try {
            renderPass.setPipeline(RenderPipelines.GUI_TEXTURED);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", texture, RenderSystem.getSamplerCache()
                    .getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, true));
            renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
            renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
            renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        } finally {
            renderPass.close();
        }
    }

    // ===================== Математика =====================

    public static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    public static double lerp(double start, double end, float t) {
        return start + (end - start) * t;
    }

    public static float rotLerp(float start, float end, float t) {
        float diff = ((end - start) % 360.0F + 540.0F) % 360.0F - 180.0F;
        return start + diff * t;
    }

    public static Vec3 lerpEntityPosition(net.minecraft.world.entity.Entity entity, float partialTicks) {
        return new Vec3(
                lerp(entity.xOld, entity.getX(), partialTicks),
                lerp(entity.yOld, entity.getY(), partialTicks),
                lerp(entity.zOld, entity.getZ(), partialTicks));
    }

    public static float easeInOutSine(float t) {
        return (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);
    }

    public static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float t1 = t - 1.0F;
        return 1.0F + c3 * t1 * t1 * t1 + c1 * t1 * t1;
    }
}
