package org.shizavisuals.utils.render.post;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;

public final class FullscreenQuad {
    private static GpuBuffer buffer;
    private static int vertexCount;

    private FullscreenQuad() {
    }

    public static GpuBuffer buffer() {
        ensure();
        return buffer;
    }

    public static int vertexCount() {
        ensure();
        return vertexCount;
    }

    private static void ensure() {
        if (buffer != null) {
            return;
        }
        BufferBuilder builder = new BufferBuilder(
                ByteBufferBuilder.exactlySized(6 * DefaultVertexFormat.POSITION_TEX.getVertexSize()),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION_TEX
        );
        builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
        builder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
        builder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
        builder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
        builder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);
        builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
        try (MeshData mesh = builder.buildOrThrow()) {
            vertexCount = mesh.drawState().vertexCount();
            buffer = RenderSystem.getDevice().createBuffer(
                    () -> "ShizaVisuals fullscreen quad",
                    GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer()
            );
        }
    }
}
