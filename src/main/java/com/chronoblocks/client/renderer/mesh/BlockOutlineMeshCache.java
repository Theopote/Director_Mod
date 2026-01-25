package com.chronoblocks.client.renderer.mesh;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * BlockOutlineMeshCache (通用网格生成器)
 * <p>
 * 用于所有方块集合的"外层填充 mesh"+"外轮廓线 mesh"。
 * 用途：
 *  - SelectionRenderer（选区预览）
 *  - ContainerHighlightRenderer（容器预览）
 *  - ToolPreviewManager（工具形状预览）
 * <p>
 * 基本设计：
 *  - 输入：Set<BlockPos>（方块集合）
 *  - 输出：Mesh { quads: List<Quad>, lines: List<Line> }
 *  - Quad：渲染填充面（面贪婪合并）
 *  - Line：渲染线框（线贪婪合并）
 * <p>
 * 特点：
 *  - 统一缓存（基于集合哈希）
 *  - 只生成外露的面和线
 *  - 顶点坐标完全遵循 Minecraft BlockPosition
 */
public final class BlockOutlineMeshCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockOutlineMeshCache.class);

    /* -------------------------------------------------------------------
     * 缓存机制
     * ------------------------------------------------------------------- */
    private Mesh cachedMesh;
    private int cachedHash;

    public void clear() {
        cachedMesh = null;
        cachedHash = 0;
    }

    /* -------------------------------------------------------------------
     * Mesh 数据结构
     * ------------------------------------------------------------------- */
    public record Quad(BlockPos p1, BlockPos p2, BlockPos p3, BlockPos p4, Direction dir) {}
    public record Line(double x1, double y1, double z1,
                       double x2, double y2, double z2) {}
    public record Mesh(List<Quad> quads, List<Line> lines) {}

    /* -------------------------------------------------------------------
     * 主入口：生成或获取 Mesh
     * ------------------------------------------------------------------- */
    public Mesh getOrCreateMesh(Collection<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return new Mesh(Collections.emptyList(), Collections.emptyList());
        }

        // ✅ 命中时零分配：先用“稳定哈希”快速判断是否命中缓存；只有 miss 才构建 solid set 并生成网格。
        // 重要：blocks 在我们体系中通常是 Set<BlockPos>（无重复），因此可直接用 asLong 的 xor/sum 计算稳定哈希。
        int hash = computeStableHash(blocks);
        if (cachedMesh != null && cachedHash == hash) {
            return cachedMesh;
        }

        Set<Long> solid = toSolidSet(blocks);
        Mesh mesh = generateMeshInternal(solid);
        cachedMesh = mesh;
        cachedHash = hash;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[BlockOutlineMeshCache] Generated mesh: {} quads, {} lines",
                    mesh.quads().size(), mesh.lines().size());
        }

        return mesh;
    }

    /* -------------------------------------------------------------------
     * 内部：将 Collection 转成 HashSet<Long> 并计算稳定哈希
     * ------------------------------------------------------------------- */
    private int computeStableHash(Collection<BlockPos> blocks) {
        // 对于 Set<BlockPos>，blocks.size() 稳定且无重复；xor+sum 对迭代顺序不敏感。
        int xor = 0;
        long sum = 0L;
        for (BlockPos p : blocks) {
            long l = p.asLong();
            xor ^= Long.hashCode(l);
            sum += l;
        }
        int hash = 1;
        hash = 31 * hash + blocks.size();
        hash = 31 * hash + xor;
        hash = 31 * hash + Long.hashCode(sum);
        return hash;
    }

    private Set<Long> toSolidSet(Collection<BlockPos> blocks) {
        Set<Long> solid = new HashSet<>(blocks.size() * 2);
        for (BlockPos p : blocks) {
            solid.add(p.asLong());
        }
        return solid;
    }

    /* -------------------------------------------------------------------
     * 核心：生成 Quad + Line 网格
     * ------------------------------------------------------------------- */
    private Mesh generateMeshInternal(Set<Long> solid) {
        if (solid.isEmpty()) return new Mesh(List.of(), List.of());

        // 计算边界（提高扫描效率）
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long l : solid) {
            BlockPos p = BlockPos.fromLong(l);
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }

        List<Quad> quads = greedyQuadMerge(solid, minX, minY, minZ, maxX, maxY, maxZ);
        List<Line> lines = greedyLineMerge(quads);

        return new Mesh(quads, lines);
    }

    /* -------------------------------------------------------------------
     * 1) 贪婪合并 Quad（面）
     * ------------------------------------------------------------------- */
    private List<Quad> greedyQuadMerge(Set<Long> solid,
                                       int minX, int minY, int minZ,
                                       int maxX, int maxY, int maxZ) {
        List<Quad> result = new ArrayList<>();
        int[] coord = new int[3];

        for (Direction dir : Direction.values()) {
            int axis = dir.getAxis().ordinal();
            boolean pos = (dir.getDirection() == Direction.AxisDirection.POSITIVE);
            int u = (axis + 1) % 3;
            int v = (axis + 2) % 3;

            int axisMin = (axis == 0 ? minX : axis == 1 ? minY : minZ);
            int axisMax = (axis == 0 ? maxX : axis == 1 ? maxY : maxZ);
            int uMin    = (u == 0 ? minX : u == 1 ? minY : minZ);
            int uMax    = (u == 0 ? maxX : u == 1 ? maxY : maxZ);
            int vMin    = (v == 0 ? minX : v == 1 ? minY : minZ);
            int vMax    = (v == 0 ? maxX : v == 1 ? maxY : maxZ);
            int uSize = uMax - uMin + 1;
            int vSize = vMax - vMin + 1;

            // face 在 axis 上的切片：axisMin..axisMax+1
            for (int face = axisMin; face <= axisMax + 1; face++) {
                boolean[][] mask = new boolean[uSize][vSize];

                for (int uu = uMin; uu <= uMax; uu++) {
                    for (int vv = vMin; vv <= vMax; vv++) {
                        int solidCoord = pos ? face - 1 : face;
                        int emptyCoord = pos ? face : face - 1;

                        boolean solidExists = false, emptyExists = false;

                        if (solidCoord >= axisMin && solidCoord <= axisMax) {
                            coord[axis] = solidCoord;
                            coord[u] = uu;
                            coord[v] = vv;
                            solidExists = solid.contains(BlockPos.asLong(coord[0], coord[1], coord[2]));
                        }

                        if (emptyCoord >= axisMin && emptyCoord <= axisMax) {
                            coord[axis] = emptyCoord;
                            coord[u] = uu;
                            coord[v] = vv;
                            emptyExists = solid.contains(BlockPos.asLong(coord[0], coord[1], coord[2]));
                        }

                        if (solidExists != emptyExists) {
                            mask[uu - uMin][vv - vMin] = true;
                        }
                    }
                }

                // 贪婪合并 mask → Quads
                for (int iu = 0; iu < uSize; iu++) {
                    for (int iv = 0; iv < vSize; ) {
                        if (!mask[iu][iv]) { iv++; continue; }

                        int w = 1;
                        while (iv + w < vSize && mask[iu][iv + w]) w++;

                        int h = 1;
                        outer:
                        while (iu + h < uSize) {
                            for (int k = 0; k < w; k++) {
                                if (!mask[iu + h][iv + k]) break outer;
                            }
                            h++;
                        }

                        int u0 = uMin + iu, u1 = uMin + iu + h;
                        int v0 = vMin + iv, v1 = vMin + iv + w;

                        int[] p1c = new int[3];
                        int[] p2c = new int[3];
                        int[] p3c = new int[3];
                        int[] p4c = new int[3];

                        p1c[axis] = face; p2c[axis] = face; p3c[axis] = face; p4c[axis] = face;
                        p1c[u] = u0; p1c[v] = v0;
                        p2c[u] = u1; p2c[v] = v0;
                        p3c[u] = u1; p3c[v] = v1;
                        p4c[u] = u0; p4c[v] = v1;

                        result.add(new Quad(
                                new BlockPos(p1c[0], p1c[1], p1c[2]),
                                new BlockPos(p2c[0], p2c[1], p2c[2]),
                                new BlockPos(p3c[0], p3c[1], p3c[2]),
                                new BlockPos(p4c[0], p4c[1], p4c[2]),
                                dir));

                        for (int du = 0; du < h; du++)
                            for (int dv = 0; dv < w; dv++)
                                mask[iu + du][iv + dv] = false;
                        iv += w;
                    }
                }
            }
        }

        return result;
    }

    /* -------------------------------------------------------------------
     * 2) Quad → 合并后的轮廓线
     * ------------------------------------------------------------------- */
    private List<Line> greedyLineMerge(List<Quad> quads) {
        if (quads.isEmpty()) return List.of();

        Map<EdgeKey, List<IntInterval>> groups = new HashMap<>();

        for (Quad q : quads) {
            BlockPos[] vs = { q.p1(), q.p2(), q.p3(), q.p4() };
            addQuadEdge(vs[0], vs[1], groups);
            addQuadEdge(vs[1], vs[2], groups);
            addQuadEdge(vs[2], vs[3], groups);
            addQuadEdge(vs[3], vs[0], groups);
        }

        List<Line> out = new ArrayList<>();

        for (var e : groups.entrySet()) {
            EdgeKey key = e.getKey();
            List<IntInterval> list = e.getValue();
            if (list.isEmpty()) continue;

            list.sort(Comparator.comparingInt(a -> a.start));

            int cs = list.getFirst().start;
            int ce = list.getFirst().end;
            for (int i = 1; i < list.size(); i++) {
                IntInterval iv = list.get(i);
                if (iv.start <= ce) {
                    ce = Math.max(ce, iv.end);
                } else {
                    addMergedLine(out, key, cs, ce);
                    cs = iv.start;
                    ce = iv.end;
                }
            }
            addMergedLine(out, key, cs, ce);
        }

        return out;
    }

    /* -------------------------------------------------------------------
     * 线框辅助逻辑（合并为长边）
     * ------------------------------------------------------------------- */
    private void addQuadEdge(BlockPos a, BlockPos b,
                             Map<EdgeKey, List<IntInterval>> map) {
        int dx = Integer.compare(b.getX(), a.getX());
        int dy = Integer.compare(b.getY(), a.getY());
        int dz = Integer.compare(b.getZ(), a.getZ());

        Direction.Axis axis;
        int start, end, c1, c2;

        if (dx != 0 && dy == 0 && dz == 0) {
            axis = Direction.Axis.X;
            start = Math.min(a.getX(), b.getX());
            end   = Math.max(a.getX(), b.getX());
            c1 = a.getY();
            c2 = a.getZ();
        } else if (dy != 0 && dx == 0 && dz == 0) {
            axis = Direction.Axis.Y;
            start = Math.min(a.getY(), b.getY());
            end   = Math.max(a.getY(), b.getY());
            c1 = a.getX();
            c2 = a.getZ();
        } else if (dz != 0 && dx == 0 && dy == 0) {
            axis = Direction.Axis.Z;
            start = Math.min(a.getZ(), b.getZ());
            end   = Math.max(a.getZ(), b.getZ());
            c1 = a.getX();
            c2 = a.getY();
        } else {
            return;
        }

        if (start == end) return;

        EdgeKey key = new EdgeKey(axis, c1, c2);
        map.computeIfAbsent(key, k -> new ArrayList<>())
           .add(new IntInterval(start, end));
    }

    private void addMergedLine(List<Line> out, EdgeKey key, int s, int e) {
        if (s == e) return;

        double x1, y1, z1;
        double x2, y2, z2;

        switch (key.axis) {
            case X -> {
                x1 = s; x2 = e;
                y1 = y2 = key.c1;
                z1 = z2 = key.c2;
            }
            case Y -> {
                y1 = s; y2 = e;
                x1 = x2 = key.c1;
                z1 = z2 = key.c2;
            }
            case Z -> {
                z1 = s; z2 = e;
                x1 = x2 = key.c1;
                y1 = y2 = key.c2;
            }
            default -> { return; }
        }

        out.add(new Line(x1, y1, z1, x2, y2, z2));
    }

    private record EdgeKey(Direction.Axis axis, int c1, int c2) {}

    private record IntInterval(int start, int end) {
    }
}

