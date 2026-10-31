package com.espmod.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Tekent gekleurde quads zonder diepte-test (dus door muren heen).
 * Lijnen en outlines worden gemaakt van dunne, gekruiste "linten" (2 quads per lijn).
 * Gebaseerd op https://docs.fabricmc.net/1.21.11/develop/rendering/world
 */
public final class Shapes {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("espmod", "pipeline/esp_through_walls"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static MappableRingBuffer vertexBuffer;
    private static BufferBuilder buffer;
    private static Matrix4fc pose;
    private static double camX, camY, camZ;

    private Shapes() {}

    /** Roep aan bij het opstarten zodat de pipeline op tijd geregistreerd wordt. */
    public static void init() {
    }

    // ---------------------------------------------------------------- extraction

    public static void begin(Matrix4fc poseMatrix, Vec3 camera) {
        pose = poseMatrix;
        camX = camera.x;
        camY = camera.y;
        camZ = camera.z;
        buffer = null;
    }

    private static void v(double x, double y, double z, int argb) {
        if (buffer == null) {
            buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
        }
        buffer.addVertex(pose, (float) (x - camX), (float) (y - camY), (float) (z - camZ))
                .setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }

    /** Vlak met 4 hoekpunten in wereldcoordinaten. */
    public static void quad(double x1, double y1, double z1,
                            double x2, double y2, double z2,
                            double x3, double y3, double z3,
                            double x4, double y4, double z4, int argb) {
        v(x1, y1, z1, argb);
        v(x2, y2, z2, argb);
        v(x3, y3, z3, argb);
        v(x4, y4, z4, argb);
    }

    /** Lijn van a naar b (wereldcoordinaten); halfWidth = halve dikte. */
    public static void line(Vec3 a, Vec3 b, int argb, float halfWidth) {
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return;
        dx /= len;
        dy /= len;
        dz /= len;

        double ux = 0, uy = 1, uz = 0;
        if (Math.abs(dy) > 0.99) {
            ux = 1;
            uy = 0;
        }
        // p1 = d x up
        double p1x = dy * uz - dz * uy, p1y = dz * ux - dx * uz, p1z = dx * uy - dy * ux;
        double l1 = Math.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
        p1x = p1x / l1 * halfWidth;
        p1y = p1y / l1 * halfWidth;
        p1z = p1z / l1 * halfWidth;
        // p2 = d x p1
        double p2x = dy * p1z - dz * p1y, p2y = dz * p1x - dx * p1z, p2z = dx * p1y - dy * p1x;
        double l2 = Math.sqrt(p2x * p2x + p2y * p2y + p2z * p2z);
        p2x = p2x / l2 * halfWidth;
        p2y = p2y / l2 * halfWidth;
        p2z = p2z / l2 * halfWidth;

        quad(a.x - p1x, a.y - p1y, a.z - p1z, b.x - p1x, b.y - p1y, b.z - p1z,
                b.x + p1x, b.y + p1y, b.z + p1z, a.x + p1x, a.y + p1y, a.z + p1z, argb);
        quad(a.x - p2x, a.y - p2y, a.z - p2z, b.x - p2x, b.y - p2y, b.z - p2z,
                b.x + p2x, b.y + p2y, b.z + p2z, a.x + p2x, a.y + p2y, a.z + p2z, argb);
    }

    /** Outline (12 randen) van een box. */
    public static void box(AABB bb, int argb, float halfWidth) {
        Vec3 c000 = new Vec3(bb.minX, bb.minY, bb.minZ);
        Vec3 c100 = new Vec3(bb.maxX, bb.minY, bb.minZ);
        Vec3 c010 = new Vec3(bb.minX, bb.maxY, bb.minZ);
        Vec3 c110 = new Vec3(bb.maxX, bb.maxY, bb.minZ);
        Vec3 c001 = new Vec3(bb.minX, bb.minY, bb.maxZ);
        Vec3 c101 = new Vec3(bb.maxX, bb.minY, bb.maxZ);
        Vec3 c011 = new Vec3(bb.minX, bb.maxY, bb.maxZ);
        Vec3 c111 = new Vec3(bb.maxX, bb.maxY, bb.maxZ);
        // onder
        line(c000, c100, argb, halfWidth);
        line(c100, c101, argb, halfWidth);
        line(c101, c001, argb, halfWidth);
        line(c001, c000, argb, halfWidth);
        // boven
        line(c010, c110, argb, halfWidth);
        line(c110, c111, argb, halfWidth);
        line(c111, c011, argb, halfWidth);
        line(c011, c010, argb, halfWidth);
        // pilaren
        line(c000, c010, argb, halfWidth);
        line(c100, c110, argb, halfWidth);
        line(c101, c111, argb, halfWidth);
        line(c001, c011, argb, halfWidth);
    }

    // ---------------------------------------------------------------- drawing

    /** Bouwt de buffer en tekent alles wat sinds begin() is toegevoegd. */
    public static void finish(Minecraft client) {
        if (buffer == null) return;

        MeshData builtBuffer = buffer.buildOrThrow();
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        VertexFormat format = drawParameters.format();

        GpuBuffer vertices = upload(drawParameters, format, builtBuffer);
        draw(client, builtBuffer, drawParameters, vertices, format);

        vertexBuffer.rotate();
        buffer = null;
    }

    private static GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer) {
        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            vertexBuffer = new MappableRingBuffer(() -> "espmod vertex buffer",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }
        return vertexBuffer.currentBuffer();
    }

    private static void draw(Minecraft client, MeshData builtBuffer, MeshData.DrawState drawParameters,
                             GpuBuffer vertices, VertexFormat format) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (PIPELINE.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = PIPELINE.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
            indexType = builtBuffer.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer =
                    RenderSystem.getSequentialBuffer(PIPELINE.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "espmod render pass",
                        client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                        client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
    }

    public static void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}
