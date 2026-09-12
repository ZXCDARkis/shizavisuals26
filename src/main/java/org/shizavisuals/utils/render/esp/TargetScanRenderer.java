package org.shizavisuals.utils.render.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Vector3f;

import java.lang.reflect.Method;

/** Renders a single animated scan band directly on the visible target skin. Ported from the Delta reference. */
public final class TargetScanRenderer {
    private static final float ATTRIBUTE_SCALE = 1000.0f;
    private static final float MODEL_INFLATE = 1.008f;
    private static final float ARMOR_INFLATE = 1.006f;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static volatile boolean injectedMeshLookupComplete;
    private static volatile Method injectedMeshGetter;

    private TargetScanRenderer() {
    }

    public static <S extends LivingEntityRenderState> void submit(
            EntityModel<? super S> model,
            S state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Identifier texture,
            int themeColor,
            int secondaryColor,
            float saturation,
            float animation,
            float speed,
            float glow) {
        submitSurface(
                model,
                state,
                poseStack,
                collector,
                texture,
                themeColor,
                secondaryColor,
                saturation,
                animation,
                speed,
                glow,
                null);
    }

    public static <S extends HumanoidRenderState> void submitArmor(
            HumanoidModel<S> model,
            S state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Identifier armorTexture,
            EquipmentSlot armorSlot,
            int themeColor,
            int secondaryColor,
            float saturation,
            float animation,
            float speed,
            float glow) {
        submitSurface(
                model,
                state,
                poseStack,
                collector,
                armorTexture,
                themeColor,
                secondaryColor,
                saturation,
                animation,
                speed,
                glow,
                armorSlot);
    }

    private static <S extends LivingEntityRenderState> void submitSurface(
            EntityModel<? super S> model,
            S state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Identifier texture,
            int themeColor,
            int secondaryColor,
            float saturation,
            float animation,
            float speed,
            float glow,
            EquipmentSlot armorSlot) {
        if (model == null || state == null || poseStack == null || collector == null
                || texture == null || animation <= 0.001f) {
            return;
        }

        int color = saturate(themeColor, saturation);
        int red = (color >>> 16) & 255;
        int green = (color >>> 8) & 255;
        int blue = color & 255;
        int secondColor = saturate(secondaryColor, saturation);
        int secondRed = (secondColor >>> 16) & 255;
        int secondGreen = (secondColor >>> 8) & 255;
        int secondBlue = secondColor & 255;
        float appear = Mth.clamp(animation, 0.0f, 1.0f);
        float glowStrength = Mth.clamp(glow, 0.0f, 2.0f);
        ScanMotion motion = motion(speed);

        collector.order(20).submitCustomGeometry(
                poseStack,
                TargetScanRenderTypes.scan(texture),
                (pose, buffer) -> {
                    PoseStack modelPose = new PoseStack();
                    modelPose.last().set(pose);
                    boolean armorSurface = armorSlot != null;
                    float inflate = armorSurface ? ARMOR_INFLATE : MODEL_INFLATE;
                    modelPose.scale(inflate, inflate, inflate);

                    model.setupAnim(state);
                    BaseLayerVisibility visibility = BaseLayerVisibility.hide(model);
                    try {
                        BoundsConsumer bounds = new BoundsConsumer();
                        renderSurfaceModel(model, modelPose, bounds, armorSlot);
                        if (armorSurface) {
                            bounds.useHumanoidReference(modelPose.last());
                        }
                        if (!bounds.valid()) {
                            return;
                        }

                        VertexConsumer scan = new ScanVertexConsumer(
                                buffer,
                                bounds,
                                red,
                                green,
                                blue,
                                secondRed,
                                secondGreen,
                                secondBlue,
                                motion.phase(),
                                motion.ascending(),
                                appear,
                                glowStrength);
                        renderSurfaceModel(model, modelPose, scan, armorSlot);
                    } finally {
                        visibility.restore();
                    }
                });
    }

    private static ScanMotion motion(float speed) {
        double seconds = (System.nanoTime() % 120_000_000_000L) / 1_000_000_000.0;
        double duration = 2.8 / Math.max(0.05, speed);
        float progress = (float) ((seconds % duration) / duration);
        float phase = 0.5f - (0.5f * Mth.cos(progress * TWO_PI));
        return new ScanMotion(phase, progress < 0.5f);
    }

    private static void renderSurfaceModel(
            EntityModel<?> model,
            PoseStack poseStack,
            VertexConsumer buffer,
            EquipmentSlot armorSlot) {
        if (model instanceof HumanoidModel<?> humanoid) {
            if (armorSlot != null) {
                renderArmorSlot(humanoid, armorSlot, poseStack, buffer);
                return;
            }
            renderPart(humanoid.head, poseStack, buffer);
            renderPart(humanoid.body, poseStack, buffer);
            renderPart(humanoid.rightArm, poseStack, buffer);
            renderPart(humanoid.leftArm, poseStack, buffer);
            renderPart(humanoid.rightLeg, poseStack, buffer);
            renderPart(humanoid.leftLeg, poseStack, buffer);
            return;
        }
        model.renderToBuffer(poseStack, buffer, FULL_BRIGHT, 0, -1);
    }

    private static void renderArmorSlot(
            HumanoidModel<?> model,
            EquipmentSlot slot,
            PoseStack poseStack,
            VertexConsumer buffer) {
        switch (slot) {
            case HEAD -> renderPart(model.head, poseStack, buffer);
            case CHEST -> {
                renderPart(model.body, poseStack, buffer);
                renderPart(model.rightArm, poseStack, buffer);
                renderPart(model.leftArm, poseStack, buffer);
            }
            case LEGS -> {
                renderPart(model.body, poseStack, buffer);
                renderPart(model.rightLeg, poseStack, buffer);
                renderPart(model.leftLeg, poseStack, buffer);
            }
            case FEET -> {
                renderPart(model.rightLeg, poseStack, buffer);
                renderPart(model.leftLeg, poseStack, buffer);
            }
            default -> {
            }
        }
    }

    private static void renderPart(ModelPart part, PoseStack poseStack, VertexConsumer buffer) {
        part.render(poseStack, buffer, FULL_BRIGHT, 0, -1);
    }

    /**
     * Skin Layers 3D injects its voxel mesh into the vanilla outer ModelPart.
     * Reflection keeps the compatibility optional and avoids a hard dependency.
     */
    private static boolean hasInjectedSkinMesh(ModelPart part) {
        if (!injectedMeshLookupComplete) {
            synchronized (TargetScanRenderer.class) {
                if (!injectedMeshLookupComplete) {
                    try {
                        injectedMeshGetter = ModelPart.class.getMethod("getInjectedMesh");
                    } catch (NoSuchMethodException ignored) {
                        injectedMeshGetter = null;
                    }
                    injectedMeshLookupComplete = true;
                }
            }
        }

        Method getter = injectedMeshGetter;
        if (getter == null) {
            return false;
        }
        try {
            return getter.invoke(part) != null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static int saturate(int color, float factor) {
        float red = ((color >>> 16) & 255) / 255.0f;
        float green = ((color >>> 8) & 255) / 255.0f;
        float blue = (color & 255) / 255.0f;
        float luminance = (red * 0.2126f) + (green * 0.7152f) + (blue * 0.0722f);
        float amount = Mth.clamp(factor, 0.0f, 2.0f);
        int outRed = Math.round(Mth.clamp(luminance + ((red - luminance) * amount), 0.0f, 1.0f) * 255.0f);
        int outGreen = Math.round(Mth.clamp(luminance + ((green - luminance) * amount), 0.0f, 1.0f) * 255.0f);
        int outBlue = Math.round(Mth.clamp(luminance + ((blue - luminance) * amount), 0.0f, 1.0f) * 255.0f);
        return (color & 0xFF000000) | (outRed << 16) | (outGreen << 8) | outBlue;
    }

    private record ScanMotion(float phase, boolean ascending) {
    }

    private static final class BaseLayerVisibility {
        private final HumanoidModel<?> humanoid;
        private final boolean hat;
        private final PlayerModel player;
        private final boolean jacket;
        private final boolean leftSleeve;
        private final boolean rightSleeve;
        private final boolean leftPants;
        private final boolean rightPants;

        private BaseLayerVisibility(EntityModel<?> model) {
            this.humanoid = model instanceof HumanoidModel<?> value ? value : null;
            this.hat = this.humanoid != null && this.humanoid.hat.visible;
            this.player = model instanceof PlayerModel value ? value : null;
            this.jacket = this.player != null && this.player.jacket.visible;
            this.leftSleeve = this.player != null && this.player.leftSleeve.visible;
            this.rightSleeve = this.player != null && this.player.rightSleeve.visible;
            this.leftPants = this.player != null && this.player.leftPants.visible;
            this.rightPants = this.player != null && this.player.rightPants.visible;
        }

        static BaseLayerVisibility hide(EntityModel<?> model) {
            BaseLayerVisibility visibility = new BaseLayerVisibility(model);
            if (visibility.humanoid != null) {
                visibility.humanoid.hat.visible = visibility.hat
                        && hasInjectedSkinMesh(visibility.humanoid.hat);
            }
            if (visibility.player != null) {
                visibility.player.jacket.visible = visibility.jacket
                        && hasInjectedSkinMesh(visibility.player.jacket);
                visibility.player.leftSleeve.visible = visibility.leftSleeve
                        && hasInjectedSkinMesh(visibility.player.leftSleeve);
                visibility.player.rightSleeve.visible = visibility.rightSleeve
                        && hasInjectedSkinMesh(visibility.player.rightSleeve);
                visibility.player.leftPants.visible = visibility.leftPants
                        && hasInjectedSkinMesh(visibility.player.leftPants);
                visibility.player.rightPants.visible = visibility.rightPants
                        && hasInjectedSkinMesh(visibility.player.rightPants);
            }
            return visibility;
        }

        void restore() {
            if (this.humanoid != null) {
                this.humanoid.hat.visible = this.hat;
            }
            if (this.player != null) {
                this.player.jacket.visible = this.jacket;
                this.player.leftSleeve.visible = this.leftSleeve;
                this.player.rightSleeve.visible = this.rightSleeve;
                this.player.leftPants.visible = this.leftPants;
                this.player.rightPants.visible = this.rightPants;
            }
        }
    }

    private static final class BoundsConsumer implements VertexConsumer {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;

        boolean valid() {
            return Float.isFinite(this.minX) && Float.isFinite(this.minY)
                    && this.maxX - this.minX > 0.0001f
                    && this.maxY - this.minY > 0.0001f;
        }

        void useHumanoidReference(PoseStack.Pose pose) {
            Vector3f top = pose.pose().transformPosition(0.0f, -0.58f, 0.0f, new Vector3f());
            Vector3f bottom = pose.pose().transformPosition(0.0f, 1.58f, 0.0f, new Vector3f());
            float referenceMinY = Math.min(top.y, bottom.y);
            float referenceMaxY = Math.max(top.y, bottom.y);
            if (referenceMaxY - referenceMinY > 0.05f) {
                this.minY = referenceMinY;
                this.maxY = referenceMaxY;
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    private static final class ScanVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final BoundsConsumer bounds;
        private final int red;
        private final int green;
        private final int blue;
        private final int phase;
        private final int motion;
        private final int glow;
        private float x;
        private float y;

        private ScanVertexConsumer(
                VertexConsumer delegate,
                BoundsConsumer bounds,
                int red,
                int green,
                int blue,
                int secondRed,
                int secondGreen,
                int secondBlue,
                float phase,
                boolean ascending,
                float appear,
                float glow) {
            this.delegate = delegate;
            this.bounds = bounds;
            this.red = red;
            this.green = green;
            this.blue = blue;
            int phaseByte = Math.round(Mth.clamp(phase, 0.0f, 1.0f) * 255.0f);
            int appearByte = Math.round(Mth.clamp(appear, 0.0f, 1.0f) * 127.0f);
            int motionByte = (ascending ? 0 : 128) | appearByte;
            int glowByte = Math.round(Mth.clamp(glow, 0.0f, 2.0f) * 127.5f);
            this.phase = ((secondRed & 255) << 8) | phaseByte;
            this.motion = ((secondGreen & 255) << 8) | motionByte;
            this.glow = ((secondBlue & 255) << 8) | glowByte;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this.setScanColor();
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this.setScanColor();
        }

        private VertexConsumer setScanColor() {
            float height = normalize(this.y, this.bounds.minY, this.bounds.maxY);
            this.delegate.setColor(this.red, this.green, this.blue, Math.round(height * 255.0f));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(this.phase, this.motion);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            float horizontal = normalize(this.x, this.bounds.minX, this.bounds.maxX);
            this.delegate.setUv2(this.glow, Math.round(horizontal * ATTRIBUTE_SCALE));
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            this.delegate.setLineWidth(width);
            return this;
        }

        private static float normalize(float value, float minimum, float maximum) {
            return Mth.clamp((value - minimum) / Math.max(0.0001f, maximum - minimum), 0.0f, 1.0f);
        }
    }
}
