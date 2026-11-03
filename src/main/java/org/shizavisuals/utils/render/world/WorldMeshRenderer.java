package org.shizavisuals.utils.render.world;

import org.shizavisuals.utils.render.Render3DUtil;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class WorldMeshRenderer {
    private static final float LINE_HALF_WIDTH = 0.012F;

    private static final RenderPipeline THROUGH_WALLS = buildPipeline("line", CompareOp.ALWAYS_PASS);
    private static final RenderPipeline DEPTH_TESTED = buildPipeline("line_depth", CompareOp.GREATER_THAN_OR_EQUAL);

    private static GpuBuffer vertexBuffer;

    public static List<RenderPipeline> pipelines() {
        return List.of(THROUGH_WALLS, DEPTH_TESTED);
    }

    private WorldMeshRenderer() {
    }

    private static RenderPipeline buildPipeline(String name, CompareOp compareOp) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("shizavisuals:pipeline/world/" + name))
                .withVertexShader(Identifier.parse("shizavisuals:core/shizavisuals_line"))
                .withFragmentShader(Identifier.parse("shizavisuals:core/shizavisuals_line"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                .withDepthStencilState(new DepthStencilState(compareOp, false))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    public static void render(WorldMesh mesh) {
        render(mesh, true);
    }

    public static void render(WorldMesh mesh, boolean throughWalls) {
        if (mesh == null || mesh.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) {
            return;
        }

        var target = mc.gameRenderer.mainRenderTarget();
        GpuTextureView colorView = target != null ? target.getColorTextureView() : null;
        if (colorView == null) {
            return;
        }

        BuiltMesh built = buildMesh(mc, mesh);
        if (built == null) {
            return;
        }

        var device = RenderSystem.getDevice();
        try {
            ByteBuffer vertexData = built.meshData().vertexBuffer();
            int byteSize = vertexData.remaining();
            ensureVertexCapacity(byteSize);
            device.createCommandEncoder().writeToBuffer(vertexBuffer.slice(0, byteSize), vertexData);

            GpuTextureView depthView = target.getDepthTextureView();
            RenderPass pass = depthView != null
                    ? device.createCommandEncoder().createRenderPass(
                    () -> "ShizaVisuals World Mesh Pass", colorView, Optional.empty(), depthView, OptionalDouble.empty())
                    : device.createCommandEncoder().createRenderPass(
                    () -> "ShizaVisuals World Mesh Pass", colorView, Optional.empty());
            try {
                pass.setPipeline(throughWalls ? THROUGH_WALLS : DEPTH_TESTED);
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setVertexBuffer(0, vertexBuffer.slice(0, byteSize));
                pass.draw(built.vertexCount(), 1, 0, 0);
            } finally {
                pass.close();
            }
        } finally {
            built.meshData().close();
        }
    }

    private static void ensureVertexCapacity(int byteSize) {
        if (vertexBuffer != null && vertexBuffer.size() >= byteSize) {
            return;
        }
        if (vertexBuffer != null) {
            vertexBuffer.close();
        }

        int capacity = Math.max(byteSize + byteSize / 2, 16 * 1024);
        vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "ShizaVisuals World Mesh Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                capacity
        );
    }

    private static BuiltMesh buildMesh(Minecraft mc, WorldMesh mesh) {
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Matrix4f pose = Render3DUtil.cameraViewPose(camera);

        int estimatedVertices = mesh.lines().size() * 6
                + mesh.rings().stream().mapToInt(Ring::segments).sum() * 6
                + mesh.planeRects().size() * 6
                + mesh.filledTriangles().size() * 3;
        if (estimatedVertices <= 0) {
            return null;
        }

        int bytes = estimatedVertices * DefaultVertexFormat.POSITION_COLOR.getVertexSize();
        BufferBuilder builder = new BufferBuilder(
                new ByteBufferBuilder(Math.max(bytes, 256)),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR
        );
        int vertexCount = 0;

        for (Line line : mesh.lines()) {
            addThickLine(
                    builder,
                    Render3DUtil.toViewSpace(line.start(), cameraPos, pose),
                    Render3DUtil.toViewSpace(line.end(), cameraPos, pose),
                    line.startColor(),
                    line.endColor()
            );
            vertexCount += 6;
        }

        for (Ring ring : mesh.rings()) {
            addRing(builder, ring, cameraPos, pose);
            vertexCount += ring.segments() * 6;
        }

        for (PlaneRect rect : mesh.planeRects()) {
            addPlaneRect(builder, rect, cameraPos, pose);
            vertexCount += 6;
        }

        for (FilledTriangle tri : mesh.filledTriangles()) {
            addFilledTriangle(builder, tri, cameraPos, pose);
            vertexCount += 3;
        }

        if (vertexCount == 0) {
            return null;
        }
        MeshData meshData = builder.buildOrThrow();
        return new BuiltMesh(meshData, vertexCount);
    }

    private static void addThickLine(BufferBuilder builder, Vector4f start, Vector4f end, int startColor, int endColor) {
        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float normalX;
        float normalY;
        if (length > 0.05F) {
            normalX = -dy / length * LINE_HALF_WIDTH;
            normalY = dx / length * LINE_HALF_WIDTH;
        } else {
            normalX = LINE_HALF_WIDTH;
            normalY = 0.0F;
        }

        float sx1 = start.x + normalX;
        float sy1 = start.y + normalY;
        float sx2 = start.x - normalX;
        float sy2 = start.y - normalY;
        float ex1 = end.x + normalX;
        float ey1 = end.y + normalY;
        float ex2 = end.x - normalX;
        float ey2 = end.y - normalY;

        builder.addVertex(sx1, sy1, start.z).setColor(startColor);
        builder.addVertex(sx2, sy2, start.z).setColor(startColor);
        builder.addVertex(ex2, ey2, end.z).setColor(endColor);
        builder.addVertex(sx1, sy1, start.z).setColor(startColor);
        builder.addVertex(ex2, ey2, end.z).setColor(endColor);
        builder.addVertex(ex1, ey1, end.z).setColor(endColor);
    }

    private static void addRing(BufferBuilder builder, Ring ring, Vec3 cameraPos, Matrix4f pose) {
        double innerRadius = Math.max(0.0D, ring.radius() - ring.halfWidth());
        double outerRadius = ring.radius() + ring.halfWidth();

        for (int i = 0; i < ring.segments(); i++) {
            double angle1 = i * (Math.PI * 2.0D) / ring.segments();
            double angle2 = (i + 1) * (Math.PI * 2.0D) / ring.segments();

            Vec3 outer1 = ring.center().add(ring.u().scale(Math.cos(angle1) * outerRadius)).add(ring.v().scale(Math.sin(angle1) * outerRadius));
            Vec3 inner1 = ring.center().add(ring.u().scale(Math.cos(angle1) * innerRadius)).add(ring.v().scale(Math.sin(angle1) * innerRadius));
            Vec3 outer2 = ring.center().add(ring.u().scale(Math.cos(angle2) * outerRadius)).add(ring.v().scale(Math.sin(angle2) * outerRadius));
            Vec3 inner2 = ring.center().add(ring.u().scale(Math.cos(angle2) * innerRadius)).add(ring.v().scale(Math.sin(angle2) * innerRadius));

            Vector4f outerView1 = Render3DUtil.toViewSpace(outer1, cameraPos, pose);
            Vector4f innerView1 = Render3DUtil.toViewSpace(inner1, cameraPos, pose);
            Vector4f outerView2 = Render3DUtil.toViewSpace(outer2, cameraPos, pose);
            Vector4f innerView2 = Render3DUtil.toViewSpace(inner2, cameraPos, pose);

            builder.addVertex(outerView1.x, outerView1.y, outerView1.z).setColor(ring.color());
            builder.addVertex(innerView1.x, innerView1.y, innerView1.z).setColor(ring.color());
            builder.addVertex(innerView2.x, innerView2.y, innerView2.z).setColor(ring.color());
            builder.addVertex(outerView1.x, outerView1.y, outerView1.z).setColor(ring.color());
            builder.addVertex(innerView2.x, innerView2.y, innerView2.z).setColor(ring.color());
            builder.addVertex(outerView2.x, outerView2.y, outerView2.z).setColor(ring.color());
        }
    }

    private static void addPlaneRect(BufferBuilder builder, PlaneRect rect, Vec3 cameraPos, Matrix4f pose) {
        Vec3 p1 = rect.center().add(rect.axis().scale(rect.halfLength())).add(rect.normal().scale(rect.halfWidth()));
        Vec3 p2 = rect.center().add(rect.axis().scale(rect.halfLength())).add(rect.normal().scale(-rect.halfWidth()));
        Vec3 p3 = rect.center().add(rect.axis().scale(-rect.halfLength())).add(rect.normal().scale(-rect.halfWidth()));
        Vec3 p4 = rect.center().add(rect.axis().scale(-rect.halfLength())).add(rect.normal().scale(rect.halfWidth()));

        Vector4f v1 = Render3DUtil.toViewSpace(p1, cameraPos, pose);
        Vector4f v2 = Render3DUtil.toViewSpace(p2, cameraPos, pose);
        Vector4f v3 = Render3DUtil.toViewSpace(p3, cameraPos, pose);
        Vector4f v4 = Render3DUtil.toViewSpace(p4, cameraPos, pose);

        builder.addVertex(v1.x, v1.y, v1.z).setColor(rect.color());
        builder.addVertex(v2.x, v2.y, v2.z).setColor(rect.color());
        builder.addVertex(v3.x, v3.y, v3.z).setColor(rect.color());
        builder.addVertex(v1.x, v1.y, v1.z).setColor(rect.color());
        builder.addVertex(v3.x, v3.y, v3.z).setColor(rect.color());
        builder.addVertex(v4.x, v4.y, v4.z).setColor(rect.color());
    }

    private static void addFilledTriangle(BufferBuilder builder, FilledTriangle tri, Vec3 cameraPos, Matrix4f pose) {
        Vector4f v1 = Render3DUtil.toViewSpace(tri.p1(), cameraPos, pose);
        Vector4f v2 = Render3DUtil.toViewSpace(tri.p2(), cameraPos, pose);
        Vector4f v3 = Render3DUtil.toViewSpace(tri.p3(), cameraPos, pose);

        builder.addVertex(v1.x, v1.y, v1.z).setColor(tri.color());
        builder.addVertex(v2.x, v2.y, v2.z).setColor(tri.color());
        builder.addVertex(v3.x, v3.y, v3.z).setColor(tri.color());
    }

    public record WorldMesh(List<Line> lines, List<Ring> rings, List<PlaneRect> planeRects, List<FilledTriangle> filledTriangles) {
        public boolean isEmpty() {
            return lines.isEmpty() && rings.isEmpty() && planeRects.isEmpty() && filledTriangles.isEmpty();
        }

        public WorldMesh(List<Line> lines, List<Ring> rings, List<PlaneRect> planeRects) {
            this(lines, rings, planeRects, List.of());
        }
    }

    public record Line(Vec3 start, Vec3 end, int startColor, int endColor) {
        public Line(Vec3 start, Vec3 end, int color) {
            this(start, end, color, color);
        }
    }

    public record Ring(Vec3 center, Vec3 u, Vec3 v, double radius, double halfWidth, int color, int segments) {
    }

    public record PlaneRect(Vec3 center, Vec3 axis, Vec3 normal, double halfLength, double halfWidth, int color) {
    }

    public record FilledTriangle(Vec3 p1, Vec3 p2, Vec3 p3, int color) {
    }

    private record BuiltMesh(MeshData meshData, int vertexCount) {
    }
}
