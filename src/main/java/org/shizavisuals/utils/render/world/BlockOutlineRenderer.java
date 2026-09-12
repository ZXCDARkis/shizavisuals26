package org.shizavisuals.utils.render.world;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.shizavisuals.feature.impl.visual.BlockOutlineFeature;
import org.shizavisuals.utils.render.post.PostFx;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.post.PostPipelines;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class BlockOutlineRenderer {
    private static final float BOX_EPSILON = 0.0025F;
    private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();
    private static final int STYLE_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();

    private final GpuBuffer transformUniforms = uniformBuffer(
            "ShizaVisuals Block Outline Transform UBO",
            TRANSFORM_SIZE
    );
    private final GpuBuffer styleUniforms = uniformBuffer(
            "ShizaVisuals Block Outline Style UBO",
            STYLE_SIZE
    );

    private BlockPos selectedPos;
    private BlockState selectedState;
    private long lastFrameNanos;
    private float transition;
    private GpuBuffer cachedVertexBuffer;
    private int cachedVertexCount;

    public void render(BlockOutlineFeature feature, CameraRenderState cameraState) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.gameRenderer == null
                || cameraState == null
                || !cameraState.initialized
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            resetSelection();
            return;
        }

        BlockPos blockPos = hit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(blockPos);
        if (state.isAir()) {
            resetSelection();
            return;
        }
        boolean selectionChanged = !blockPos.equals(this.selectedPos);
        if (selectionChanged) {
            this.selectedPos = blockPos.immutable();
            this.transition = 0.0F;
            this.lastFrameNanos = System.nanoTime();
        }
        advanceTransition(feature.animationSpeed.getValue().floatValue());

        if (selectionChanged || state != this.selectedState || this.cachedVertexBuffer == null) {
            this.selectedState = state;
            rebuildMesh(minecraft, blockPos, state);
        }
        if (this.cachedVertexBuffer == null || this.cachedVertexCount == 0) {
            return;
        }

        var target = minecraft.gameRenderer.mainRenderTarget();
        if (target == null
                || target.getColorTextureView() == null
                || target.getDepthTextureView() == null) {
            return;
        }

        float eased = 1.0F - (float) Math.pow(1.0F - this.transition, 3.0D);
        float scale = 0.92F + eased * 0.08F;
        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null) {
            return;
        }
        Matrix4f modelViewProjection = projection
                .mul(cameraState.viewRotationMatrix)
                .translate(
                        (float) (blockPos.getX() - cameraState.pos.x() + 0.5D),
                        (float) (blockPos.getY() - cameraState.pos.y() + 0.5D),
                        (float) (blockPos.getZ() - cameraState.pos.z() + 0.5D)
                )
                .scale(scale)
                .translate(-0.5F, -0.5F, -0.5F);
        writeUniforms(feature, modelViewProjection, target.width, target.height, eased);

        RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "ShizaVisuals Block Outline",
                target.getColorTextureView(),
                Optional.empty(),
                target.getDepthTextureView(),
                OptionalDouble.empty()
        );
        try {
            pass.setPipeline(pipeline(feature));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("BlockOutlineTransform", this.transformUniforms);
            pass.setUniform("BlockOutlineStyle", this.styleUniforms);
            pass.setVertexBuffer(0, this.cachedVertexBuffer.slice());
            pass.draw(this.cachedVertexCount, 1, 0, 0);
        } finally {
            pass.close();
        }
    }

    private void rebuildMesh(Minecraft minecraft, BlockPos blockPos, BlockState state) {
        releaseMesh();
        List<AABB> boxes = state.getShape(minecraft.level, blockPos).toAabbs();
        if (boxes.isEmpty()) {
            boxes = List.of(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        }
        MeshData mesh = buildMesh(boxes);
        if (mesh == null) {
            return;
        }
        try (mesh) {
            this.cachedVertexCount = mesh.drawState().vertexCount();
            this.cachedVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "ShizaVisuals Block Outline Vertices",
                    GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer()
            );
        }
    }

    private void releaseMesh() {
        if (this.cachedVertexBuffer != null) {
            this.cachedVertexBuffer.close();
            this.cachedVertexBuffer = null;
        }
        this.cachedVertexCount = 0;
    }

    public void resetSelection() {
        this.selectedPos = null;
        this.selectedState = null;
        this.transition = 0.0F;
        this.lastFrameNanos = 0L;
        releaseMesh();
    }

    public void release() {
        resetSelection();
        this.transformUniforms.close();
        this.styleUniforms.close();
    }

    private void advanceTransition(float speed) {
        long now = System.nanoTime();
        if (this.lastFrameNanos == 0L) {
            this.lastFrameNanos = now;
            return;
        }
        float seconds = Math.min(0.1F, (now - this.lastFrameNanos) / 1.0E9F);
        this.lastFrameNanos = now;
        this.transition = Math.min(1.0F, this.transition + seconds * speed * 0.12F);
    }

    private void writeUniforms(BlockOutlineFeature feature,
                               Matrix4f modelViewProjection,
                               int width,
                               int height,
                               float alpha) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer transform = Std140Builder.onStack(stack, TRANSFORM_SIZE)
                    .putMat4f(modelViewProjection)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
                    this.transformUniforms.slice(),
                    transform
            );

            int tint = feature.resolvedTint();
            ByteBuffer style = Std140Builder.onStack(stack, STYLE_SIZE)
                    .putVec4(
                            ColorUtil.red(tint) / 255.0F,
                            ColorUtil.green(tint) / 255.0F,
                            ColorUtil.blue(tint) / 255.0F,
                            alpha * 0.82F
                    )
                    .putVec4(
                            width,
                            height,
                            PostFx.shaderTime() * feature.shaderSpeed.getValue().floatValue(),
                            feature.shaderIntensity.getValue().floatValue()
                    )
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
                    this.styleUniforms.slice(),
                    style
            );
        }
    }

    private RenderPipeline pipeline(BlockOutlineFeature feature) {
        boolean through = feature.ignoreDepth.getValue();
        return switch (feature.variant.getValue()) {
            case BlockOutlineFeature.VARIANT_CAUSTICS -> through
                    ? PostPipelines.BLOCK_OUTLINE_CAUSTICS_THROUGH
                    : PostPipelines.BLOCK_OUTLINE_CAUSTICS;
            case BlockOutlineFeature.VARIANT_PRISMATIC -> through
                    ? PostPipelines.BLOCK_OUTLINE_PRISMATIC_THROUGH
                    : PostPipelines.BLOCK_OUTLINE_PRISMATIC;
            case BlockOutlineFeature.VARIANT_GLOSSY -> through
                    ? PostPipelines.BLOCK_OUTLINE_GLOSSY_THROUGH
                    : PostPipelines.BLOCK_OUTLINE_GLOSSY;
            case BlockOutlineFeature.VARIANT_DEEP_SPACE -> through
                    ? PostPipelines.BLOCK_OUTLINE_DEEP_SPACE_THROUGH
                    : PostPipelines.BLOCK_OUTLINE_DEEP_SPACE;
            case BlockOutlineFeature.VARIANT_NEBULA -> through
                    ? PostPipelines.BLOCK_OUTLINE_NEBULA_THROUGH
                    : PostPipelines.BLOCK_OUTLINE_NEBULA;
            default -> through
                    ? PostPipelines.BLOCK_OUTLINE_CLASSIC_THROUGH
                    : PostPipelines.BLOCK_OUTLINE_CLASSIC;
        };
    }

    private MeshData buildMesh(List<AABB> boxes) {
        int vertexCount = boxes.size() * 36;
        if (vertexCount == 0) {
            return null;
        }
        BufferBuilder builder = new BufferBuilder(
                new ByteBufferBuilder(Math.max(
                        256,
                        vertexCount * DefaultVertexFormat.POSITION.getVertexSize()
                )),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION
        );
        for (AABB box : boxes) {
            float minX = (float) box.minX - BOX_EPSILON;
            float minY = (float) box.minY - BOX_EPSILON;
            float minZ = (float) box.minZ - BOX_EPSILON;
            float maxX = (float) box.maxX + BOX_EPSILON;
            float maxY = (float) box.maxY + BOX_EPSILON;
            float maxZ = (float) box.maxZ + BOX_EPSILON;
            face(builder, minX, minY, minZ, maxX, maxY, minZ);
            face(builder, maxX, minY, maxZ, minX, maxY, maxZ);
            face(builder, minX, minY, maxZ, minX, maxY, minZ);
            face(builder, maxX, minY, minZ, maxX, maxY, maxZ);
            face(builder, minX, maxY, minZ, maxX, maxY, maxZ);
            face(builder, minX, minY, maxZ, maxX, minY, minZ);
        }
        return builder.buildOrThrow();
    }

    private void face(BufferBuilder builder,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2) {
        boolean constantX = x1 == x2;
        boolean constantY = y1 == y2;
        if (constantX) {
            vertex(builder, x1, y1, z1);
            vertex(builder, x1, y1, z2);
            vertex(builder, x1, y2, z2);
            vertex(builder, x1, y2, z2);
            vertex(builder, x1, y2, z1);
            vertex(builder, x1, y1, z1);
        } else if (constantY) {
            vertex(builder, x1, y1, z1);
            vertex(builder, x2, y1, z1);
            vertex(builder, x2, y1, z2);
            vertex(builder, x2, y1, z2);
            vertex(builder, x1, y1, z2);
            vertex(builder, x1, y1, z1);
        } else {
            vertex(builder, x1, y1, z1);
            vertex(builder, x2, y1, z1);
            vertex(builder, x2, y2, z1);
            vertex(builder, x2, y2, z1);
            vertex(builder, x1, y2, z1);
            vertex(builder, x1, y1, z1);
        }
    }

    private void vertex(BufferBuilder builder, float x, float y, float z) {
        builder.addVertex(x, y, z);
    }

    private static GpuBuffer uniformBuffer(String label, int size) {
        return RenderSystem.getDevice().createBuffer(
                () -> label,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
        );
    }

}
