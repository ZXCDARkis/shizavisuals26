package org.shizavisuals.utils.render.world;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryStack;
import org.shizavisuals.feature.impl.visual.PetsFeature;
import org.shizavisuals.utils.render.esp.EspSpriteRenderer;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Renders the client-only pet: a camera-facing cutout or the game's own pig
 * model, both drawn into the main target while the world depth is intact so
 * normal block occlusion applies. The media pipeline is the shared
 * POSITION_TEX_COLOR model pass (depth test + write) with quads and the
 * sequential index buffer.
 */
public final class PetsRenderer {
    private static final Identifier MASMOL = Identifier.parse("shizavisuals:textures/pet/masmol.png");
    private static final Identifier PIG = Identifier.parse("minecraft:textures/entity/pig/pig_temperate.png");
    /** Past this the pet stops walking and simply appears next to you. */
    private static final double TELEPORT_DISTANCE = 12.0d;
    private static final double GRAVITY = 0.08d;
    private static final double JUMP_POWER = 0.42d;
    /** Half the width of the box the pet walks with. */
    private static final double RADIUS = 0.25d;
    private static final double PERSONAL_SPACE = 1.4d;
    private static final int STUCK_TICKS = 40;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();

    private Vec3 pos;
    private Vec3 prevPos;
    private double motionY;
    private boolean onGround;
    private float yaw;
    private float prevYaw;
    /** Runs on while the pet walks; every animation value is read off it. */
    private float walk;
    private float prevWalk;
    private float squash;
    private float prevSquash;
    private int stuck;
    /** How far the pet moved on the last tick: the pace of legs and bounce. */
    private float pace;
    private float prevPace;
    /** Eased walking speed, so the pet does not start and stop dead. */
    private double moveSpeed;
    /** Where the head points and where it pointed a tick ago. */
    private float headYaw;
    private float prevHeadYaw;
    private float headPitch;
    private float prevHeadPitch;

    private PigModel pigModel;
    private final PigRenderState pigState = new PigRenderState();

    public void reset() {
        this.pos = null;
        this.prevPos = null;
    }

    // ===== Simulation (one call per game tick) =====

    public void tick(PetsFeature feature) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            this.pos = null;
            return;
        }
        if (this.pos == null) {
            spawn(mc, feature);
            return;
        }
        this.prevPos = this.pos;
        this.prevYaw = this.yaw;
        this.prevSquash = this.squash;

        Vec3 target = heel(mc, feature);
        double dx = target.x - this.pos.x;
        double dz = target.z - this.pos.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        // Too far, or stuck behind something it cannot walk around: just appear next to you.
        if (feature.teleport.getValue()
                && (mc.player.position().distanceTo(this.pos) > TELEPORT_DISTANCE || this.stuck > STUCK_TICKS)) {
            spawn(mc, feature);
            return;
        }

        double moved = 0.0d;
        boolean wants = flat > 0.65d && !crowding(mc);
        // Speed eases up and down over a few ticks instead of stepping between idle and full pace.
        double wantedSpeed = wants
                ? 0.115d * feature.speed.getValue().floatValue() * Mth.clamp((float) (flat / 2.0d), 0.5f, 2.2f)
                : 0.0d;
        this.moveSpeed += (wantedSpeed - this.moveSpeed) * 0.25d;
        if (wants) {
            moved = walkTowards(dx / flat, dz / flat, Math.min(this.moveSpeed, flat), feature);
            float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
            this.yaw += Mth.wrapDegrees(wanted - this.yaw) * 0.35f;
            this.stuck = moved < 0.01d ? this.stuck + 1 : 0;
        } else {
            this.stuck = 0;
            // Idle: turns to look at the player.
            Vec3 toPlayer = mc.player.position().subtract(this.pos);
            if (toPlayer.horizontalDistanceSqr() > 0.04d) {
                float wanted = (float) (Mth.atan2(toPlayer.z, toPlayer.x) * Mth.RAD_TO_DEG) - 90.0f;
                this.yaw += Mth.wrapDegrees(wanted - this.yaw) * 0.2f;
            }
        }

        // Player jumped with the pet right behind: it hops along.
        if (feature.jump.getValue() && this.onGround && !mc.player.onGround()
                && mc.player.getDeltaMovement().y > 0.2d && flat < 3.5d) {
            this.motionY = JUMP_POWER;
            this.onGround = false;
        }

        fall(mc, feature);
        // The game's own walk animation state: pace eases towards the real speed.
        this.prevPace = this.pace;
        this.prevWalk = this.walk;
        this.pace += (Math.min((float) moved * 4.0f, 1.0f) - this.pace) * 0.4f;
        this.walk += this.pace;
        this.squash *= 0.75f;
        turnHead(mc, moved);
    }

    /**
     * The head turns the way a mob's does: towards the player while standing
     * around, straight ahead once walking, never faster than ten degrees a tick.
     */
    private void turnHead(Minecraft mc, double moved) {
        this.prevHeadYaw = this.headYaw;
        this.prevHeadPitch = this.headPitch;
        float wantedYaw = 0.0f;
        float wantedPitch = 0.0f;
        if (moved < 0.02d) {
            Vec3 toPlayer = mc.player.getEyePosition().subtract(this.pos.add(0.0d, 0.7d, 0.0d));
            if (toPlayer.horizontalDistanceSqr() > 0.04d) {
                wantedYaw = Mth.clamp(Mth.wrapDegrees(
                        (float) (Mth.atan2(toPlayer.z, toPlayer.x) * Mth.RAD_TO_DEG) - 90.0f - this.yaw), -50.0f, 50.0f);
                wantedPitch = Mth.clamp(
                        (float) -(Mth.atan2(toPlayer.y, toPlayer.horizontalDistance()) * Mth.RAD_TO_DEG), -35.0f, 35.0f);
            }
        }
        this.headYaw += Mth.clamp(Mth.wrapDegrees(wantedYaw - this.headYaw), -10.0f, 10.0f);
        this.headPitch += Mth.clamp(wantedPitch - this.headPitch, -10.0f, 10.0f);
    }

    /** Right on top of the player is no place for a pet: stops inside this ring. */
    private boolean crowding(Minecraft mc) {
        return mc.player.position().distanceTo(new Vec3(this.pos.x, mc.player.getY(), this.pos.z)) < PERSONAL_SPACE;
    }

    /**
     * Walks up to {@code distance} in small steps, stopping at whatever it runs
     * into. A wall it could stand on top of makes it jump: the hop carries it
     * forward and it lands on the block, which is how it gets up stairs.
     */
    private double walkTowards(double dirX, double dirZ, double distance, PetsFeature feature) {
        double left = Double.isFinite(distance) ? distance : 0.0d;
        double moved = 0.0d;
        // Hard cap on sub-steps: a stray number must never spin this loop forever.
        for (int guard = 0; left > 1.0E-4d && guard < 64; guard++) {
            double step = Math.min(0.08d, left);
            left -= step;
            double nx = this.pos.x + dirX * step;
            double nz = this.pos.z + dirZ * step;
            if (free(nx, this.pos.y, nz)) {
                this.pos = new Vec3(nx, this.pos.y, nz);
                moved += step;
                continue;
            }
            if (this.onGround && free(nx, this.pos.y + 1.0d, nz)) {
                double climb = floorAt(nx, this.pos.y + 1.2d, nz);
                if (!Double.isNaN(climb) && climb - this.pos.y <= 1.05d && free(nx, climb, nz)) {
                    if (climb - this.pos.y <= 0.55d) {
                        this.pos = new Vec3(nx, climb, nz);
                        moved += step;
                        continue;
                    }
                    if (feature.jump.getValue()) {
                        this.motionY = JUMP_POWER;
                        this.onGround = false;
                    }
                }
            }
            break;
        }
        return moved;
    }

    /** Gravity and the ground under the pet: lands on whatever block is below. */
    private void fall(Minecraft mc, PetsFeature feature) {
        this.motionY = (this.motionY - GRAVITY) * 0.98d;
        double next = this.pos.y + this.motionY;
        double floor = floorAt(this.pos.x, this.pos.y + 0.5d, this.pos.z);
        if (this.motionY <= 0.0d && !Double.isNaN(floor) && next <= floor) {
            if (!this.onGround) {
                this.squash = (float) Math.min(0.45d, -this.motionY * 1.2d);
            }
            this.pos = new Vec3(this.pos.x, floor, this.pos.z);
            this.motionY = 0.0d;
            this.onGround = true;
            return;
        }
        this.onGround = false;
        this.pos = new Vec3(this.pos.x, next, this.pos.z);
        if (this.pos.y < mc.level.getMinY() - 8) {
            spawn(mc, feature);
        }
    }

    /** A spot behind the player, on the side they came from. */
    private Vec3 heel(Minecraft mc, PetsFeature feature) {
        Vec3 back = Vec3.directionFromRotation(0.0f, mc.player.getYRot())
                .scale(feature.distance.getValue().floatValue());
        return mc.player.position().subtract(back);
    }

    private void spawn(Minecraft mc, PetsFeature feature) {
        Vec3 target = heel(mc, feature);
        double floor = floorAt(target.x, mc.player.getY() + 1.0d, target.z);
        double y = Double.isNaN(floor) ? mc.player.getY() : floor;
        this.pos = new Vec3(target.x, y, target.z);
        this.prevPos = this.pos;
        this.motionY = 0.0d;
        this.onGround = true;
        this.yaw = mc.player.getYRot();
        this.prevYaw = this.yaw;
        this.squash = 0.0f;
        this.moveSpeed = 0.0d;
        this.headYaw = 0.0f;
        this.prevHeadYaw = 0.0f;
        this.headPitch = 0.0f;
        this.prevHeadPitch = 0.0f;
        this.stuck = 0;
        this.prevSquash = 0.0f;
    }

    /** Top of the first block below {@code from} under the pet's body corners, or NaN. */
    private double floorAt(double x, double from, double z) {
        Minecraft mc = Minecraft.getInstance();
        double best = Double.NaN;
        for (int corner = 0; corner < 4; corner++) {
            double cx = x + ((corner & 1) == 0 ? -RADIUS : RADIUS);
            double cz = z + ((corner & 2) == 0 ? -RADIUS : RADIUS);
            Vec3 start = new Vec3(cx, from, cz);
            Vec3 end = new Vec3(cx, from - 6.0d, cz);
            HitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS && (Double.isNaN(best) || hit.getLocation().y > best)) {
                best = hit.getLocation().y;
            }
        }
        return best;
    }

    /** No block in the box the pet takes up when standing with its feet at {@code y}. */
    private boolean free(double x, double y, double z) {
        for (int corner = 0; corner < 4; corner++) {
            double cx = x + ((corner & 1) == 0 ? -RADIUS : RADIUS);
            double cz = z + ((corner & 2) == 0 ? -RADIUS : RADIUS);
            if (solid(cx, y + 0.15d, cz) || solid(cx, y + 0.75d, cz)) {
                return false;
            }
        }
        return !solid(x, y + 0.45d, z);
    }

    private boolean solid(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos blockPos = BlockPos.containing(x, y, z);
        BlockState state = mc.level.getBlockState(blockPos);
        return !state.getCollisionShape(mc.level, blockPos).isEmpty();
    }

    // ===== Rendering (world pass, depth intact) =====

    public void render(PetsFeature feature, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || this.pos == null) {
            return;
        }
        // Contained so the pet can never break the world pass.
        try {
            Camera camera = mc.gameRenderer.mainCamera();
            RenderTarget target = mc.gameRenderer.mainRenderTarget();
            if (camera == null || target == null || target.getColorTextureView() == null) {
                return;
            }
            doRender(mc, feature, camera, target, tickDelta);
        } catch (Throwable ignored) {
        }
    }

    private void doRender(Minecraft mc, PetsFeature feature, Camera camera, RenderTarget target, float tickDelta) {
        Vec3 cam = camera.position();
        Vec3 render = this.prevPos == null ? this.pos : this.prevPos.lerp(this.pos, tickDelta);
        Vec3 delta = render.subtract(cam);
        float walkNow = Mth.lerp(tickDelta, this.prevWalk, this.walk);
        float squashNow = Mth.lerp(tickDelta, this.prevSquash, this.squash);
        float yawNow = this.prevYaw + Mth.wrapDegrees(this.yaw - this.prevYaw) * tickDelta;

        if (feature.mode.is("Masmol")) {
            drawFlat(feature, mc, target, camera, delta, walkNow, squashNow);
        } else {
            drawPig(mc, target, camera, delta, walkNow, squashNow, yawNow, tickDelta, feature);
        }
    }

    /** The cutout: upright, turned to the camera, bobbing and leaning with every step. */
    private void drawFlat(PetsFeature feature, Minecraft mc, RenderTarget target, Camera camera, Vec3 delta, float walk, float squash) {
        float size = feature.size.getValue().floatValue();
        float height = 1.6f * size;
        float width = height * (512.0f / 306.0f);
        float bob = 0.0f;
        float lean = 0.0f;
        if (feature.isAnimated()) {
            bob = Mth.sin(walk * 0.9f) * 0.06f * size;
            lean = Mth.sin(walk * 0.45f) * 5.0f;
            if (!this.onGround) {
                // Stretched out in the air, squashed the moment it lands.
                height *= 1.08f;
                width *= 0.94f;
                lean += 6.0f;
            }
            height *= 1.0f - squash;
            width *= 1.0f + squash * 0.6f;
        }

        double yawRad = Math.toRadians(camera.yRot());
        // Right along the camera, up leaning with the step, so the cutout rocks as it walks.
        double leanRad = Math.toRadians(lean);
        Vec3 flatRight = new Vec3(Math.cos(yawRad), 0.0d, Math.sin(yawRad));
        Vec3 up = new Vec3(-flatRight.x * Math.sin(leanRad), Math.cos(leanRad), -flatRight.z * Math.sin(leanRad));
        Vec3 right = flatRight.scale(Math.cos(leanRad) * width / 2.0f)
                .add(0.0d, Math.sin(leanRad) * width / 2.0f, 0.0d);
        Vec3 top = up.scale(height);
        Vec3 bottom = delta.add(0.0d, bob, 0.0d);

        Matrix4f pose = new Matrix4f().rotation(camera.rotation().invert(new Quaternionf()));
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 4)) {
            BufferBuilder buffer = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            vertex(buffer, pose, bottom.subtract(right), 0.0f, 1.0f);
            vertex(buffer, pose, bottom.add(right), 1.0f, 1.0f);
            vertex(buffer, pose, bottom.add(right).add(top), 1.0f, 0.0f);
            vertex(buffer, pose, bottom.subtract(right).add(top), 0.0f, 0.0f);
            drawQuads(mc, target, "ShizaVisuals Pet Flat", buffer, MASMOL);
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, Vec3 point, float u, float v) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setUv(u, v).setColor(-1);
    }

    /** The game's own pig, posed the way the game poses it, with the walk cycle driving its legs. */
    private void drawPig(Minecraft mc, RenderTarget target, Camera camera, Vec3 delta,
                         float walk, float squash, float yaw, float tickDelta, PetsFeature feature) {
        PigModel model = pigModel(mc);
        boolean animated = feature.isAnimated();
        // The same numbers the game feeds its own mobs: distance walked drives the legs, pace their swing.
        this.pigState.walkAnimationPos = animated ? walk : 0.0f;
        this.pigState.walkAnimationSpeed = animated ? Mth.lerp(tickDelta, this.prevPace, this.pace) : 0.0f;
        this.pigState.ageInTicks = mc.player.tickCount + tickDelta;
        this.pigState.isBaby = false;
        this.pigState.yRot = this.prevHeadYaw + Mth.wrapDegrees(this.headYaw - this.prevHeadYaw) * tickDelta;
        this.pigState.xRot = Mth.lerp(tickDelta, this.prevHeadPitch, this.headPitch);
        model.setupAnim(this.pigState);

        float size = feature.size.getValue().floatValue();
        PoseStack stack = new PoseStack();
        stack.mulPose(camera.rotation().invert(new Quaternionf()));
        stack.translate((float) delta.x, (float) delta.y, (float) delta.z);
        stack.scale(size, size, size);
        // Nothing on top of the game's own animation: legs and head posed as a pig's always are.
        stack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(180.0f - yaw)));
        stack.scale(-1.0f, -1.0f, 1.0f);
        stack.translate(0.0f, -1.501f, 0.0f);
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 256)) {
            BufferBuilder buffer = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            model.root().render(stack, new PlainVertexConsumer(buffer), 0xF000F0, OverlayTexture.NO_OVERLAY, -1);
            drawQuads(mc, target, "ShizaVisuals Pet Pig", buffer, PIG);
        }
    }

    private PigModel pigModel(Minecraft mc) {
        if (this.pigModel == null) {
            this.pigModel = new PigModel(mc.getEntityModels().bakeLayer(ModelLayers.PIG));
        }
        return this.pigModel;
    }

    /**
     * Uploads the QUADS mesh and draws it against the world depth through the
     * shared textured model pipeline.
     */
    private void drawQuads(Minecraft mc, RenderTarget target, String name, BufferBuilder buffer, Identifier textureId) {
        AbstractTexture texture = mc.getTextureManager().getTexture(textureId);
        if (texture == null || texture.getTextureView() == null) {
            return;
        }

        GpuBuffer vertices = null;
        GpuBuffer uniforms = null;
        try (MeshData mesh = buffer.buildOrThrow()) {
            int vertexCount = mesh.drawState().vertexCount();
            int indexCount = vertexCount / 4 * 6;
            if (indexCount <= 0) {
                return;
            }
            var device = RenderSystem.getDevice();
            vertices = device.createBuffer(
                    () -> name,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    mesh.vertexBuffer()
            );
            uniforms = uploadBrightness(1.0f);

            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuTextureView depthView = target.getDepthTextureView();
            try (RenderPass pass = depthView != null
                    ? device.createCommandEncoder().createRenderPass(
                    () -> name, target.getColorTextureView(), Optional.empty(), depthView, OptionalDouble.empty())
                    : device.createCommandEncoder().createRenderPass(
                    () -> name, target.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(EspSpriteRenderer.modelPipeline());
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setUniform("EspSpriteUniforms", uniforms);
                pass.bindTexture("EspSpriteSampler", texture.getTextureView(), sampler);
                pass.setVertexBuffer(0, vertices.slice());
                GpuBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(indexCount);
                pass.setIndexBuffer(indices, RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).type());
                pass.drawIndexed(indexCount, 1, 0, 0, 0);
            }
        } catch (Throwable ignored) {
        } finally {
            if (uniforms != null) {
                uniforms.close();
            }
            if (vertices != null) {
                vertices.close();
            }
        }
    }

    private GpuBuffer uploadBrightness(float brightness) {
        var device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(
                () -> "ShizaVisuals Pets UBO",
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

    public void release() {
        reset();
    }

    /**
     * Takes the vertices of a game model and keeps only position, texture and
     * colour. Model parts write with the one call that carries everything at
     * once; BufferBuilder answers it at the offsets of the entity format, so a
     * plain textured format handed in directly garbles the mesh. This wrapper
     * splits that call into the single setters and drops the rest.
     */
    private static final class PlainVertexConsumer implements VertexConsumer {
        private final BufferBuilder buffer;

        PlainVertexConsumer(BufferBuilder buffer) {
            this.buffer = buffer;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.buffer.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.buffer.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            this.buffer.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.buffer.setUv(u, v);
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
}
