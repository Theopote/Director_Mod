package com.director.client.renderer;

import com.director.client.gizmo.Axis;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Utility primitives shared by interaction overlays and gizmo renderers.
 * Keeps {@link SelectionRenderer} focused on core selection visuals.
 */
public final class OverlayRenderHelper {

    private static final int[][] CUBE_EDGES = {
        {0, 1}, {2, 3}, {4, 5}, {6, 7},
        {0, 2}, {1, 3}, {4, 6}, {5, 7},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private static final MutableVec3d[] CORNERS = new MutableVec3d[8];
    private static final double[] RING_POINT = new double[3];

    static {
        for (int i = 0; i < CORNERS.length; i++) {
            CORNERS[i] = new MutableVec3d();
        }
    }

    private OverlayRenderHelper() {}

    /** 面中心锚点（框选面手柄）的视觉大小：未激活 / 激活（悬停或拖拽）。 */
    private static final float HANDLE_SIZE_IDLE = 0.2f;
    private static final float HANDLE_SIZE_ACTIVE = 0.28f;
    /** 拾取半宽：与视觉手柄对齐并略放大，供 {@link com.director.core.selection.tools.FaceHandleManager} 使用。 */
    public static final float HANDLE_PICK_HALF_SIZE = HANDLE_SIZE_IDLE / 2.0f + 0.01f;

    private static final int[][] CUBE_FACES = {
        {0, 2, 3, 1}, // -Z
        {4, 5, 7, 6}, // +Z
        {0, 1, 5, 4}, // -Y
        {2, 6, 7, 3}, // +Y
        {0, 4, 6, 2}, // -X
        {1, 3, 7, 5}  // +X
    };

    public static void drawHandle(VertexConsumer consumer, Matrix4f matrix, Vec3d pos, float[] color, boolean active) {
        if (consumer == null || pos == null || color == null || color.length < 4) {
            return;
        }

        float size = active ? HANDLE_SIZE_ACTIVE : HANDLE_SIZE_IDLE;
        drawWireCube(consumer, matrix, pos, size, color[0], color[1], color[2],
                active ? Math.min(1.0f, color[3] + 0.2f) : color[3]);
    }

    /**
     * 半透明实心填充（需 EntityTranslucent 顶点格式）。
     */
    public static void drawSolidHandle(VertexConsumer consumer, Matrix4f matrix, Vec3d pos,
                                       float[] color, boolean active) {
        if (consumer == null || pos == null || color == null || color.length < 4) {
            return;
        }

        float size = active ? HANDLE_SIZE_ACTIVE : HANDLE_SIZE_IDLE;
        double halfSize = size / 2.0;
        populateCorners(
            pos.x - halfSize, pos.y - halfSize, pos.z - halfSize,
            pos.x + halfSize, pos.y + halfSize, pos.z + halfSize
        );

        float alpha = Math.min(1.0f, Math.max(0.0f, color[3]));
        for (int[] face : CUBE_FACES) {
            MutableVec3d a = CORNERS[face[0]];
            MutableVec3d b = CORNERS[face[1]];
            MutableVec3d c = CORNERS[face[2]];
            MutableVec3d d = CORNERS[face[3]];
            emitSolidQuad(consumer, matrix, a, b, c, d, color[0], color[1], color[2], alpha);
        }
    }

    private static void drawWireCube(VertexConsumer consumer, Matrix4f matrix, Vec3d pos, float size,
                                     float r, float g, float b, float a) {
        double halfSize = size / 2.0;
        populateCorners(
            pos.x - halfSize, pos.y - halfSize, pos.z - halfSize,
            pos.x + halfSize, pos.y + halfSize, pos.z + halfSize
        );

        for (int[] edge : CUBE_EDGES) {
            MutableVec3d p0 = CORNERS[edge[0]];
            MutableVec3d p1 = CORNERS[edge[1]];
            consumer.vertex(matrix, (float) p0.x, (float) p0.y, (float) p0.z)
                    .color(r, g, b, a)
                    .normal(0, 1, 0);
            consumer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z)
                    .color(r, g, b, a)
                    .normal(0, 1, 0);
        }
    }

    private static void emitSolidQuad(VertexConsumer consumer, Matrix4f matrix,
                                      MutableVec3d p1, MutableVec3d p2, MutableVec3d p3, MutableVec3d p4,
                                      float r, float g, float b, float a) {
        double ex = p2.x - p1.x;
        double ey = p2.y - p1.y;
        double ez = p2.z - p1.z;
        double fx = p3.x - p1.x;
        double fy = p3.y - p1.y;
        double fz = p3.z - p1.z;
        double nx = ey * fz - ez * fy;
        double ny = ez * fx - ex * fz;
        double nz = ex * fy - ey * fx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            nx = 0;
            ny = 1;
            nz = 0;
        }
        float nxf = (float) nx;
        float nyf = (float) ny;
        float nzf = (float) nz;

        consumer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z)
                .color(r, g, b, a)
                .texture(0.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nxf, nyf, nzf);
        consumer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z)
                .color(r, g, b, a)
                .texture(1.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nxf, nyf, nzf);
        consumer.vertex(matrix, (float) p3.x, (float) p3.y, (float) p3.z)
                .color(r, g, b, a)
                .texture(1.0f, 1.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nxf, nyf, nzf);
        consumer.vertex(matrix, (float) p4.x, (float) p4.y, (float) p4.z)
                .color(r, g, b, a)
                .texture(0.0f, 1.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nxf, nyf, nzf);
    }

    public static void drawRing(VertexConsumer consumer, Matrix4f matrix, Vec3d center, Axis axis, double radius, float[] color) {
        if (consumer == null || matrix == null || center == null || axis == null || color == null || color.length < 4) {
            return;
        }

        double prevX = 0.0;
        double prevY = 0.0;
        double prevZ = 0.0;
        double firstX = 0.0;
        double firstY = 0.0;
        double firstZ = 0.0;
        boolean hasFirst = false;

        final int segments = 64;
        for (int i = 0; i <= segments; i++) {
            double angle = (i / (double) segments) * Math.PI * 2.0;
            computeRingPoint(center, axis, radius, angle);

            if (hasFirst) {
                consumer.vertex(matrix, (float) prevX, (float) prevY, (float) prevZ)
                        .color(color[0], color[1], color[2], color[3])
                        .normal(0, 1, 0);
                consumer.vertex(matrix, (float) RING_POINT[0], (float) RING_POINT[1], (float) RING_POINT[2])
                        .color(color[0], color[1], color[2], color[3])
                        .normal(0, 1, 0);
            } else {
                firstX = RING_POINT[0];
                firstY = RING_POINT[1];
                firstZ = RING_POINT[2];
                hasFirst = true;
            }

            prevX = RING_POINT[0];
            prevY = RING_POINT[1];
            prevZ = RING_POINT[2];
        }

        consumer.vertex(matrix, (float) prevX, (float) prevY, (float) prevZ)
                .color(color[0], color[1], color[2], color[3])
                .normal(0, 1, 0);
        consumer.vertex(matrix, (float) firstX, (float) firstY, (float) firstZ)
                .color(color[0], color[1], color[2], color[3])
                .normal(0, 1, 0);
    }

    private static void populateCorners(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        CORNERS[0].set(minX, minY, minZ);
        CORNERS[1].set(maxX, minY, minZ);
        CORNERS[2].set(minX, maxY, minZ);
        CORNERS[3].set(maxX, maxY, minZ);
        CORNERS[4].set(minX, minY, maxZ);
        CORNERS[5].set(maxX, minY, maxZ);
        CORNERS[6].set(minX, maxY, maxZ);
        CORNERS[7].set(maxX, maxY, maxZ);
    }

    private static void computeRingPoint(Vec3d center, Axis axis, double radius, double angle) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius;

        switch (axis) {
            case X -> {
                RING_POINT[0] = center.x;
                RING_POINT[1] = center.y + cos;
                RING_POINT[2] = center.z + sin;
            }
            case Y -> {
                RING_POINT[0] = center.x + cos;
                RING_POINT[1] = center.y;
                RING_POINT[2] = center.z + sin;
            }
            case Z -> {
                RING_POINT[0] = center.x + cos;
                RING_POINT[1] = center.y + sin;
                RING_POINT[2] = center.z;
            }
        }
    }

    public static Vec3d faceCenter(Box box, int faceIndex) {
        double centerX = (box.minX + box.maxX) / 2.0;
        double centerY = (box.minY + box.maxY) / 2.0;
        double centerZ = (box.minZ + box.maxZ) / 2.0;

        return switch (faceIndex) {
            case 0 -> new Vec3d(box.maxX, centerY, centerZ);
            case 1 -> new Vec3d(box.minX, centerY, centerZ);
            case 2 -> new Vec3d(centerX, box.maxY, centerZ);
            case 3 -> new Vec3d(centerX, box.minY, centerZ);
            case 4 -> new Vec3d(centerX, centerY, box.maxZ);
            case 5 -> new Vec3d(centerX, centerY, box.minZ);
            default -> new Vec3d(centerX, centerY, centerZ);
        };
    }

    public static Vec3d faceNormal(int faceIndex) {
        return switch (faceIndex) {
            case 1 -> new Vec3d(-1, 0, 0);
            case 2 -> new Vec3d(0, 1, 0);
            case 3 -> new Vec3d(0, -1, 0);
            case 4 -> new Vec3d(0, 0, 1);
            case 5 -> new Vec3d(0, 0, -1);
            default -> new Vec3d(1, 0, 0);
        };
    }

    private static final class MutableVec3d {
        double x;
        double y;
        double z;

        void set(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}

