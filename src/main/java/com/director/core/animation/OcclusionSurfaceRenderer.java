package com.director.core.animation;

import com.director.core.container.visibility.BlockVisualStateManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockRenderType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import java.util.*;

/** Settled ghost blocks use a textured shell; independently moving blocks retain their animation path. */
final class OcclusionSurfaceRenderer {
    private record Vertex(float x, float y, float z, int color, float u, float v, int light,
                          float nx, float ny, float nz) {}
    private record Quad(Sprite sprite, Direction direction, List<Vertex> vertices) {}
    private record Slice(Direction direction, int plane) {}
    // Layout includes winding and UV rotation/mirroring, so unlike model faces cannot be merged.
    private record Material(Sprite sprite, int color, int light, float alpha, boolean depthWrite,
                            List<Integer> layout) {}

    static boolean accepts(BlockVisualStateManager.RenderEntry entry) {
        if (entry == null || entry.pos() == null || entry.blockState() == null
                || entry.blockState().getRenderType() != BlockRenderType.MODEL) return false;
        var visual = entry.visualState();
        var snapshot = entry.overrideSnapshot();
        if (visual == null || visual.isHidden() || !visual.isGhostMode()
                || !visual.shouldSuppressWorldRendering() || visual.getAlphaMultiplier() <= 0.01f
                || visual.getAlphaMultiplier() >= 0.999f || snapshot == null
                || !snapshot.isVisible() || snapshot.getAlpha() < 0.999f) return false;
        return snapshot.getTransform() == null
                || snapshot.getTransform().peek().getPositionMatrix().equals(new Matrix4f(), 1.0e-6f);
    }

    private record NativeFace(Quad quad, BlockPos pos, float alpha) {}
    private record MergedFace(Slice slice, GreedySurfaceMesher.Rectangle<Material> rect) {}
    record Prepared(Set<BlockPos> handled, List<NativeFace> nativeFaces, List<MergedFace> mergedFaces) {
        void draw(MatrixStack matrices, VertexConsumerProvider consumers) {
            for (var face : nativeFaces) replay(face.quad(), face.pos(), matrices, consumers, face.alpha(), false);
            for (var face : mergedFaces) emit(face.slice(), face.rect(), matrices, consumers);
        }
    }

    static Prepared prepare(ClientWorld world, Collection<BlockVisualStateManager.RenderEntry> entries) {
        var groups = new IdentityHashMap<Object, List<BlockVisualStateManager.RenderEntry>>();
        for (var entry : entries) if (accepts(entry))
            groups.computeIfAbsent(entry.clip(), key -> new ArrayList<>()).add(entry);
        var handled = new HashSet<BlockPos>();
        var nativeFaces = new ArrayList<NativeFace>();
        var mergedFaces = new ArrayList<MergedFace>();
        var manager = MinecraftClient.getInstance().getBlockRenderManager();
        for (var group : groups.values()) {
            var states = new HashMap<BlockPos, BlockState>();
            for (var entry : group) states.put(entry.pos(), entry.blockState());
            var view = new WholeContainerRenderView(world, states, group.getFirst().pos(), new Matrix4f());
            var slices = new HashMap<Slice, Map<GreedySurfaceMesher.Cell, Material>>();
            for (var entry : group) {
                var parts = new ArrayList<BlockModelPart>();
                manager.getModel(entry.blockState()).addParts(Random.create(entry.blockState().getRenderingSeed(entry.pos())), parts);
                var collector = new Collector();
                manager.renderBlock(entry.blockState(), entry.pos(), view, new MatrixStack(), collector, true, parts);
                collector.finish();
                // Fabric custom meshes may write vertices without BakedQuad metadata: leave them to the native path.
                if (collector.unsupported) continue;
                handled.add(entry.pos());
                float alpha = entry.visualState().getAlphaMultiplier();
                // A transparent occluder must not prevent the later animation from drawing.
                boolean depth = false;
                var faceCounts = new EnumMap<Direction, Integer>(Direction.class);
                for (var quad : collector.quads) faceCounts.merge(quad.direction(), 1, Integer::sum);
                for (var quad : collector.quads) {
                    var material = material(quad, alpha, depth);
                    if (material == null || !entry.blockState().isOpaqueFullCube()
                            || faceCounts.get(quad.direction()) != 1) {
                        nativeFaces.add(new NativeFace(quad, entry.pos(), alpha));
                        continue;
                    }
                    int axis = quad.direction().getAxis().ordinal();
                    int u = (axis + 1) % 3, v = (axis + 2) % 3;
                    int plane = coordinate(entry.pos(), axis)
                            + (quad.direction().getDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
                    var mask = slices.computeIfAbsent(new Slice(quad.direction(), plane), key -> new HashMap<>());
                    var cell = new GreedySurfaceMesher.Cell(coordinate(entry.pos(), u), coordinate(entry.pos(), v));
                    mask.put(cell, material);
                }
            }
            for (var slice : slices.entrySet())
                for (var rect : GreedySurfaceMesher.merge(slice.getValue()))
                    mergedFaces.add(new MergedFace(slice.getKey(), rect));
        }
        return new Prepared(handled, nativeFaces, mergedFaces);
    }

    private static Material material(Quad quad, float alpha, boolean depth) {
        var sprite = quad.sprite();
        int width = sprite.getContents().getWidth(), height = sprite.getContents().getHeight();
        if (Integer.bitCount(width) != 1 || Integer.bitCount(height) != 1
                || sprite.getX() > 32767 || sprite.getY() > 32767 || quad.vertices().size() != 4) return null;
        int axis = quad.direction().getAxis().ordinal(), u = (axis + 1) % 3, v = (axis + 2) % 3;
        int plane = quad.direction().getDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        var first = quad.vertices().getFirst();
        var layout = new ArrayList<Integer>();
        var corners = new HashSet<Integer>();
        var uvCorners = new HashSet<Integer>();
        for (var vertex : quad.vertices()) {
            int a = unit(coordinate(vertex, u)), b = unit(coordinate(vertex, v));
            int tu = unit(sprite.getFrameFromU(vertex.u())), tv = unit(sprite.getFrameFromV(vertex.v()));
            if (a < 0 || b < 0 || tu < 0 || tv < 0 || Math.abs(coordinate(vertex, axis) - plane) > 0.0001f
                    || vertex.color() != first.color() || vertex.light() != first.light()) return null;
            corners.add(a * 2 + b); uvCorners.add(tu * 2 + tv);
            layout.add(a); layout.add(b); layout.add(tu); layout.add(tv);
        }
        if (corners.size() != 4 || uvCorners.size() != 4) return null;
        // Reject unusual UV mappings that are not an affine rotation/reflection of one complete tile.
        for (int channel = 2; channel <= 3; channel++) {
            int[] values = new int[4];
            for (int i = 0; i < 4; i++) values[layout.get(i*4)*2 + layout.get(i*4+1)] = layout.get(i*4+channel);
            if (values[3] != values[2] + values[1] - values[0]) return null;
        }
        return new Material(sprite, first.color(), first.light(), alpha, depth, List.copyOf(layout));
    }
    private static int unit(float value) {
        if (Math.abs(value) < 0.005f) return 0;
        if (Math.abs(value - 1) < 0.005f) return 1;
        return -1;
    }
    private static int coordinate(BlockPos pos, int axis) { return axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ(); }
    private static float coordinate(Vertex vertex, int axis) { return axis == 0 ? vertex.x() : axis == 1 ? vertex.y() : vertex.z(); }

    private static void emit(Slice slice, GreedySurfaceMesher.Rectangle<Material> rect,
                             MatrixStack matrices, VertexConsumerProvider consumers) {
        var material = rect.material();
        var buffer = consumers.getBuffer(OcclusionSurfaceRenderLayers.tiled(material.depthWrite()));
        int axis = slice.direction().getAxis().ordinal(), u = (axis + 1) % 3, v = (axis + 2) % 3;
        int[][] values = new int[2][4];
        for (int i = 0; i < 4; i++) {
            int offset = i * 4, corner = material.layout().get(offset) * 2 + material.layout().get(offset+1);
            values[0][corner] = material.layout().get(offset+2);
            values[1][corner] = material.layout().get(offset+3);
        }
        for (int i = 0; i < 4; i++) {
            float a = material.layout().get(i*4) * rect.width(), b = material.layout().get(i*4+1) * rect.height();
            float[] p = new float[3]; p[axis] = slice.plane(); p[u] = rect.u() + a; p[v] = rect.v() + b;
            float tu = values[0][0] + (values[0][2]-values[0][0])*a + (values[0][1]-values[0][0])*b;
            float tv = values[1][0] + (values[1][2]-values[1][0])*a + (values[1][1]-values[1][0])*b;
            buffer.vertex(matrices.peek().getPositionMatrix(), p[0], p[1], p[2])
                    .color(withAlpha(material.color(), material.alpha())).texture(tu, tv)
                    .overlay(material.sprite().getX(), material.sprite().getY()).light(material.light())
                    .normal((Integer.numberOfTrailingZeros(material.sprite().getContents().getWidth()) + 0.25f)/127f,
                            (Integer.numberOfTrailingZeros(material.sprite().getContents().getHeight()) + 0.25f)/127f, 0);
        }
    }
    private static int withAlpha(int color, float alpha) {
        return (color & 0x00ffffff) | (Math.round((color >>> 24) * alpha) << 24);
    }
    private static void replay(Quad quad, BlockPos pos, MatrixStack matrices, VertexConsumerProvider consumers,
                               float alpha, boolean depth) {
        var buffer = consumers.getBuffer(OcclusionSurfaceRenderLayers.nativeLayer(depth));
        for (var vertex : quad.vertices()) buffer.vertex(matrices.peek().getPositionMatrix(),
                        pos.getX()+vertex.x(), pos.getY()+vertex.y(), pos.getZ()+vertex.z())
                .color(withAlpha(vertex.color(), alpha)).texture(vertex.u(), vertex.v())
                .light(vertex.light()).normal(vertex.nx(), vertex.ny(), vertex.nz());
    }

    private static final class Collector implements VertexConsumer {
        final List<Quad> quads = new ArrayList<>();
        final List<Vertex> vertices = new ArrayList<>();
        Sprite sprite; Direction direction; boolean unsupported;
        @Override public void quad(MatrixStack.Entry matrix, BakedQuad quad, float[] brightness,
                float r, float g, float b, float alpha, int[] lights, int overlay, boolean colorize) {
            finish(); sprite = quad.sprite(); direction = quad.face();
            VertexConsumer.super.quad(matrix, quad, brightness, r, g, b, alpha, lights, overlay, colorize);
            finish(); sprite = null;
        }
        void finish() {
            if (!vertices.isEmpty()) {
                if (sprite == null || vertices.size() != 4) unsupported = true;
                else quads.add(new Quad(sprite, direction, List.copyOf(vertices)));
                vertices.clear();
            }
        }
        @Override public void vertex(float x, float y, float z, int color, float u, float v,
                int overlay, int light, float nx, float ny, float nz) {
            vertices.add(new Vertex(x,y,z,color,u,v,light,nx,ny,nz));
        }
        @Override public VertexConsumer vertex(float x, float y, float z) { unsupported = true; return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer texture(float u, float v) { return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
    }
}
