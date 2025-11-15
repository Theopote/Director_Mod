package com.chronoblocks.client.renderer;

import com.chronoblocks.core.selection.SelectionState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🚀 v2.0: 贪婪合并算法与缓存的核心实现
 * 这是一个独立的内部类，专门负责将 Set<BlockPos> 转换为优化的渲染网格。
 */
final class SelectionMeshCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionMeshCache.class);

    // 缓存
    private Mesh cachedMesh;
    private int cachedStateHash;

    /**
     * 获取或创建选区的渲染网格。
     * 如果缓存命中，则直接返回；否则，生成新网格并缓存。
     */
    public Mesh getOrCreateMesh(SelectionState state) {
        // 使用 Objects.hashCode 来处理 state 可能为 null 的情况，更健壮
        int stateHash = Objects.hashCode(state);
        if (cachedMesh != null && cachedStateHash == stateHash) {
            return cachedMesh;
        }

        LOGGER.debug("Cache miss for selection state hash {}. Generating new mesh...", stateHash);
        long startTime = System.nanoTime();

        Mesh newMesh = generateMesh(state.getSelectedBlocks());

        long endTime = System.nanoTime();
        LOGGER.info("Generated selection mesh in {} ms ({} quads, {} lines)",
                    String.format("%.3f", (endTime - startTime) / 1_000_000.0),
                    newMesh.fillQuads().size(),
                    newMesh.outlineLines().size());

        this.cachedMesh = newMesh;
        this.cachedStateHash = stateHash;
        return newMesh;
    }

    public void clear() {
        this.cachedMesh = null;
        this.cachedStateHash = 0;
    }

    /**
     * 网格数据结构
     */
    public record Quad(BlockPos p1, BlockPos p2, BlockPos p3, BlockPos p4, Direction direction) {}
    public record Line(double x1, double y1, double z1, double x2, double y2, double z2) {}
    public record Mesh(List<Quad> fillQuads, List<Line> outlineLines) {}

    /**
     * 🚀 v2.0: 贪婪合并算法的完整实现
     */
    private Mesh generateMesh(Set<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return new Mesh(Collections.emptyList(), Collections.emptyList());
        }

        List<Quad> quads = new ArrayList<>();

        // ⭐ 修正：手动计算精确的 min/max 坐标（不使用 Box.union，避免 maxX 被错误地 +1）
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // 遍历所有6个方向 (UP, DOWN, EAST, WEST, SOUTH, NORTH)
        for (Direction dir : Direction.values()) {
            // u, v 是垂直于 dir 的两个轴
            Direction u = (dir.getAxis() == Direction.Axis.Y) ? Direction.EAST : Direction.UP;
            Direction v = (dir.getAxis() == Direction.Axis.Y) ? Direction.SOUTH : (dir.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST);

            // x, u, v 是三个维度的坐标
            int[] x = new int[3];

            // ⭐ 修正：sliceWidth 和 sliceHeight 的计算（基于正确的 min/max）
            int uMax, vMax;
            vMax = switch (dir.getAxis()) {
                case X -> {
                    uMax = maxY - minY;
                    yield maxZ - minZ;
                }
                case Y -> {
                    uMax = maxX - minX;
                    yield maxZ - minZ;
                }
                default -> {
                    uMax = maxY - minY;
                    yield maxX - minX;
                }
            };

            boolean[][] mask = new boolean[uMax + 1][vMax + 1];

            // 沿主轴方向扫描，逐一切片
            for (x[dir.getAxis().ordinal()] = (dir.getAxis() == Direction.Axis.X ? minX : (dir.getAxis() == Direction.Axis.Y ? minY : minZ));
                 x[dir.getAxis().ordinal()] <= (dir.getAxis() == Direction.Axis.X ? maxX : (dir.getAxis() == Direction.Axis.Y ? maxY : maxZ));
                 x[dir.getAxis().ordinal()]++) {

                // 1. 生成2D掩码 (Mask Generation)
                for (x[u.getAxis().ordinal()] = (u.getAxis() == Direction.Axis.X ? minX : (u.getAxis() == Direction.Axis.Y ? minY : minZ));
                     x[u.getAxis().ordinal()] <= (u.getAxis() == Direction.Axis.X ? maxX : (u.getAxis() == Direction.Axis.Y ? maxY : maxZ));
                     x[u.getAxis().ordinal()]++) {

                    for (x[v.getAxis().ordinal()] = (v.getAxis() == Direction.Axis.X ? minX : (v.getAxis() == Direction.Axis.Y ? minY : minZ));
                         x[v.getAxis().ordinal()] <= (v.getAxis() == Direction.Axis.X ? maxX : (v.getAxis() == Direction.Axis.Y ? maxY : maxZ));
                         x[v.getAxis().ordinal()]++) {

                        BlockPos currentPos = new BlockPos(x[0], x[1], x[2]);
                        BlockPos neighborPos = currentPos.offset(dir);

                        boolean currentSolid = blocks.contains(currentPos);
                        boolean neighborSolid = blocks.contains(neighborPos);

                        // ⭐ 修正：如果当前和邻居状态相同，跳过（避免不必要的处理）
                        if (currentSolid == neighborSolid) continue;

                        int uCoord = x[u.getAxis().ordinal()] - (u.getAxis() == Direction.Axis.X ? minX : (u.getAxis() == Direction.Axis.Y ? minY : minZ));
                        int vCoord = x[v.getAxis().ordinal()] - (v.getAxis() == Direction.Axis.X ? minX : (v.getAxis() == Direction.Axis.Y ? minY : minZ));

                        // ⭐ 修正：掩码生成逻辑（只要 currentSolid != neighborSolid，就标记为可见面）
                        // 如果是正向的面，当前方块实心而邻居为空；如果是负向的面，当前为空而邻居实心
                        mask[uCoord][vCoord] = true;
                    }
                }

                // 2. 在2D掩码上运行贪婪合并 (Greedy Meshing on Mask)
                for (int i = 0; i <= uMax; i++) {
                    for (int j = 0; j <= vMax; ) {
                        if (mask[i][j]) {
                            // 计算宽度 w
                            int w = 1;
                            while (j + w <= vMax && mask[i][j + w]) {
                                w++;
                            }

                            // 计算高度 h
                            int h = 1;
                            boolean done = false;
                            while (i + h <= uMax) {
                                for (int k = 0; k < w; k++) {
                                    if (!mask[i + h][j + k]) {
                                        done = true;
                                        break;
                                    }
                                }
                                if (done) break;
                                h++;
                            }

                            // 3. 创建合并后的 Quad 的四个顶点
                            int normalCoord = x[dir.getAxis().ordinal()];
                            int uCoord = (u.getAxis() == Direction.Axis.X ? minX : (u.getAxis() == Direction.Axis.Y ? minY : minZ)) + i;
                            int vCoord = (v.getAxis() == Direction.Axis.X ? minX : (v.getAxis() == Direction.Axis.Y ? minY : minZ)) + j;

                            // 根据方向构建顶点坐标
                            int[] p1_coords = new int[3];
                            int[] p2_coords = new int[3];
                            int[] p3_coords = new int[3];
                            int[] p4_coords = new int[3];

                            // 设置法向轴坐标（根据方向调整）
                            // 判断方向是正向还是负向：通过检查 offset 值
                            boolean isPositive = (dir.getOffsetX() > 0 || dir.getOffsetY() > 0 || dir.getOffsetZ() > 0);
                            if (isPositive) {
                                p1_coords[dir.getAxis().ordinal()] = normalCoord + 1;
                                p2_coords[dir.getAxis().ordinal()] = normalCoord + 1;
                                p3_coords[dir.getAxis().ordinal()] = normalCoord + 1;
                                p4_coords[dir.getAxis().ordinal()] = normalCoord + 1;
                            } else {
                                p1_coords[dir.getAxis().ordinal()] = normalCoord;
                                p2_coords[dir.getAxis().ordinal()] = normalCoord;
                                p3_coords[dir.getAxis().ordinal()] = normalCoord;
                                p4_coords[dir.getAxis().ordinal()] = normalCoord;
                            }

                            // 设置 u 和 v 轴坐标
                            p1_coords[u.getAxis().ordinal()] = uCoord;
                            p1_coords[v.getAxis().ordinal()] = vCoord;

                            p2_coords[u.getAxis().ordinal()] = uCoord + h;
                            p2_coords[v.getAxis().ordinal()] = vCoord;

                            p3_coords[u.getAxis().ordinal()] = uCoord + h;
                            p3_coords[v.getAxis().ordinal()] = vCoord + w;

                            p4_coords[u.getAxis().ordinal()] = uCoord;
                            p4_coords[v.getAxis().ordinal()] = vCoord + w;

                            BlockPos p1 = new BlockPos(p1_coords[0], p1_coords[1], p1_coords[2]);
                            BlockPos p2 = new BlockPos(p2_coords[0], p2_coords[1], p2_coords[2]);
                            BlockPos p3 = new BlockPos(p3_coords[0], p3_coords[1], p3_coords[2]);
                            BlockPos p4 = new BlockPos(p4_coords[0], p4_coords[1], p4_coords[2]);

                            // 根据方向调整顶点顺序以匹配渲染（确保正确的法线方向）
                            // 复用之前计算的 isPositive 变量
                            if (isPositive) {
                                quads.add(new Quad(p1, p4, p3, p2, dir));
                            } else {
                                quads.add(new Quad(p1, p2, p3, p4, dir));
                            }

                            // 4. 标记已处理（直接修改 mask，避免重复处理）
                            for (int l = 0; l < h; l++) {
                                for (int m = 0; m < w; m++) {
                                    mask[i + l][j + m] = false;
                                }
                            }
                            j += w;
                        } else {
                            j++;
                        }
                    }
                }
            }
        }

        // --- 线框生成 (Outline Generation) ---
        // 这是一个正确的、只生成外露边的算法，但没有合并成连续长线。
        // 相比逐方块渲染所有边，性能已有巨大提升。
        List<Line> lines = generateOutlineLines(blocks);

        return new Mesh(quads, lines);
    }

    /**
     * 生成边框线（优化实现：只生成外露边）
     */
    private List<Line> generateOutlineLines(Set<BlockPos> blocks) {
        List<Line> lines = new ArrayList<>();
        Set<Long> processedEdges = new HashSet<>(); // 防止重复边

        for (BlockPos pos : blocks) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();

            // 检查12条边
            checkAndAddEdge(blocks, processedEdges, lines, x, y, z, x + 1, y, z);
            checkAndAddEdge(blocks, processedEdges, lines, x, y, z, x, y + 1, z);
            checkAndAddEdge(blocks, processedEdges, lines, x, y, z, x, y, z + 1);
            checkAndAddEdge(blocks, processedEdges, lines, x + 1, y, z, x + 1, y + 1, z);
            checkAndAddEdge(blocks, processedEdges, lines, x + 1, y, z, x + 1, y, z + 1);
            checkAndAddEdge(blocks, processedEdges, lines, x, y + 1, z, x + 1, y + 1, z);
            checkAndAddEdge(blocks, processedEdges, lines, x, y + 1, z, x, y + 1, z + 1);
            checkAndAddEdge(blocks, processedEdges, lines, x, y, z + 1, x + 1, y, z + 1);
            checkAndAddEdge(blocks, processedEdges, lines, x, y, z + 1, x, y + 1, z + 1);
            checkAndAddEdge(blocks, processedEdges, lines, x + 1, y + 1, z, x + 1, y + 1, z + 1);
            checkAndAddEdge(blocks, processedEdges, lines, x + 1, y, z + 1, x + 1, y + 1, z + 1);
            checkAndAddEdge(blocks, processedEdges, lines, x, y + 1, z + 1, x + 1, y + 1, z + 1);
        }

        return lines;
    }

    /**
     * 检查并添加一条边（如果它是可见的）
     */
    private void checkAndAddEdge(Set<BlockPos> blocks, Set<Long> processed, List<Line> lines, 
                                  int x1, int y1, int z1, int x2, int y2, int z2) {
        // 使用边的两个端点创建唯一哈希
        long edgeHash = BlockPos.asLong(x1, y1, z1) ^ (BlockPos.asLong(x2, y2, z2) * 31L);
        if (processed.contains(edgeHash)) return;

        BlockPos p1 = new BlockPos(x1, y1, z1);
        BlockPos p2 = new BlockPos(x2, y2, z2);

        // 计算边的方向向量
        Direction edgeDir = getDirection(p2, p1);

        if (edgeDir == null) return;

        // 找到垂直于边的两个方向
        Direction.Axis edgeAxis = edgeDir.getAxis();
        Direction perp1 = null;
        Direction perp2 = null;

        switch (edgeAxis) {
            case X -> {
                perp1 = Direction.UP;
                perp2 = Direction.SOUTH;
            }
            case Y -> {
                perp1 = Direction.EAST;
                perp2 = Direction.SOUTH;
            }
            case Z -> {
                perp1 = Direction.EAST;
                perp2 = Direction.UP;
            }
        }

        if (perp1 == null) return;

        // 检查边的四个相邻面
        BlockPos[] neighbors = {
            p1.offset(perp1), p1.offset(perp1.getOpposite()),
            p1.offset(perp2), p1.offset(perp2.getOpposite())
        };

        int adjacentFaces = 0;
        for (BlockPos neighbor : neighbors) {
            if (!blocks.contains(neighbor)) {
                adjacentFaces++;
            }
        }

        // 如果一条边暴露在外的面多于0个，就认为是可见的
        if (adjacentFaces > 0) {
            lines.add(new Line(x1, y1, z1, x2, y2, z2));
            processed.add(edgeHash);
        }
    }

    private static @Nullable Direction getDirection(BlockPos p2, BlockPos p1) {
        int dx = p2.getX() - p1.getX();
        int dy = p2.getY() - p1.getY();
        int dz = p2.getZ() - p1.getZ();

        // 确定边的方向
        Direction edgeDir = null;
        if (dx != 0 && dy == 0 && dz == 0) {
            edgeDir = dx > 0 ? Direction.EAST : Direction.WEST;
        } else if (dx == 0 && dy != 0 && dz == 0) {
            edgeDir = dy > 0 ? Direction.UP : Direction.DOWN;
        } else if (dx == 0 && dy == 0 && dz != 0) {
            edgeDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return edgeDir;
    }
}
