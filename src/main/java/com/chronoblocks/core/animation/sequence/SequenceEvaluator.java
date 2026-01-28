package com.chronoblocks.core.animation.sequence;

import com.chronoblocks.core.animation.sequence.evaluator.FillType;
import com.chronoblocks.core.animation.sequence.evaluator.FlowType;
import com.chronoblocks.core.animation.sequence.evaluator.OriginType;
import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 基于 Flow + Fill + Weight 模型的评分器。
 */
public final class SequenceEvaluator {

    private static final float RASTER_ROW_SCALE = 1_000_000.0f;

    public record Evaluation(float flowScore, float fillScore, float finalScore, int layerIndex) {
    }

    /**
     * 计算单个方块的排序评分。
     */
    public Evaluation evaluate(BlockPos pos, Container.BoundingBox bounds, SequenceParameters params) {
        if (pos == null || params == null) {
            return new Evaluation(0f, 0f, 0f, 0);
        }

        FlowType flow = params.getFlowType();
        FillType fill = params.getFillType();
        OriginType originType = params.getOriginType();

        Vec3d origin = resolveOrigin(bounds, originType, params);
        Vec3d point = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        Vec3d rel = point.subtract(origin);

        // Flow score 先算连续值，再按 layerStep 做量化（解决 PLANAR 自定义角度“层太碎”）
        float flowScoreRaw = computeFlowScore(rel, flow, params);
        float layerStep = params.getLayerStep();
        float flowScore = flowScoreRaw / layerStep;
        float fillScore;
        if (fill == FillType.RASTER) {
            fillScore = computeRasterFillScore(pos, bounds, params, rel);
        } else {
            fillScore = computeFillScore(rel, fill);
        }

        int layerIndex = (int) Math.floor(flowScore);
        if (params.isAlternating() && (layerIndex & 1) != 0) {
            fillScore = -fillScore;
        }

        float finalScore = flowScore * Math.max(0f, params.getPriorityWeight()) + fillScore;
        if (params.isReverse()) {
            finalScore = -finalScore;
        }

        return new Evaluation(flowScore, fillScore, finalScore, layerIndex);
    }

    private Vec3d resolveOrigin(Container.BoundingBox bounds, OriginType origin, SequenceParameters params) {
        if (bounds == null) {
            return Vec3d.ZERO;
        }
        BlockPos min = bounds.getMin();
        BlockPos max = bounds.getMax();
        double midX = (min.getX() + max.getX()) / 2.0;
        double midY = (min.getY() + max.getY()) / 2.0;
        double midZ = (min.getZ() + max.getZ()) / 2.0;

        return switch (origin) {
            case CENTER -> new Vec3d(midX, midY, midZ);
            case BOTTOM_CENTER -> new Vec3d(midX, min.getY(), midZ);
            case TOP_CENTER -> new Vec3d(midX, max.getY(), midZ);
            case CUSTOM -> new Vec3d(
                min.getX() + params.getCustomX(),
                min.getY() + params.getCustomY(),
                min.getZ() + params.getCustomZ()
            );
            case MIN_CORNER -> new Vec3d(min.getX(), min.getY(), min.getZ());
        };
    }

    private float computeFlowScore(Vec3d rel, FlowType type, SequenceParameters params) {
        if (rel == null || type == null) {
            return 0f;
        }
        switch (type) {
            case PLANAR -> {
                Vec3d normal = getNormalFromAngles(params.getPlaneYaw(), params.getPlanePitch());
                return (float) rel.dotProduct(normal);
            }
            case SPHERICAL -> {
                return (float) rel.length();
            }
            case CYLINDRICAL -> {
                return (float) Math.sqrt(rel.x * rel.x + rel.z * rel.z);
            }
            case RANDOM -> {
                return hash(rel);
            }
            default -> {
                return 0f;
            }
        }
    }

    private float computeFillScore(Vec3d rel, FillType type) {
        if (rel == null || type == null) {
            return 0f;
        }
        switch (type) {
            case LINEAR_SCAN -> {
                return (float) (rel.x + rel.z);
            }
            case RASTER -> {
                // Raster 依赖 bounds/params/pos 的稳定坐标，因此这里不实现；由 evaluate(...) 走专用分支。
                return 0f;
            }
            case RADIAL -> {
                return (float) (Math.abs(rel.x) + Math.abs(rel.z));
            }
            case SPIRAL -> {
                return (float) Math.atan2(rel.z, rel.x);
            }
            case NOISE -> {
                return hash(rel) * 10.0f;
            }
            default -> {
                return 0f;
            }
        }
    }

    private float computeRasterFillScore(BlockPos pos, Container.BoundingBox bounds, SequenceParameters params, Vec3d rel) {
        if (pos == null || bounds == null || params == null) {
            return 0f;
        }

        // 行内扫描方向：U 或 V（U/V 是“层平面上的两条基向量”）
        String scanAxis = params.getRasterScanAxis();
        float rowStep = params.getRasterRowStep();
        boolean zigzag = params.isRasterZigZag();

        // 仅对 PLANAR Flow 做“与层切片一致”的行方向；其他 flow 暂用 rel 的 XZ 投影退化
        Vec3d normal = null;
        try {
            normal = getNormalFromAngles(params.getPlaneYaw(), params.getPlanePitch());
        } catch (Exception ignored) {
        }
        if (normal == null || normal.lengthSquared() < 1.0e-6) {
            normal = new Vec3d(0, 1, 0);
        }

        // 1) 优先处理轴对齐的切片：这样“XY 层 → 行方向 X/Y”这类语义最直观且坐标稳定（相对 min）。
        Vec3d n = normal.normalize();
        double ax = Math.abs(n.x), ay = Math.abs(n.y), az = Math.abs(n.z);
        BlockPos min = bounds.getMin();

        // XY 平面（法线≈Z）
        if (az > 0.999) {
            int x = pos.getX() - min.getX();
            int y = pos.getY() - min.getY();
            int scan = scanAxis.equals("U") ? x : y;       // U=X, V=Y
            int rowCoord = scanAxis.equals("U") ? y : x;   // 换行方向是另一个轴
            int row = (int) Math.floor(rowCoord / Math.max(0.1f, rowStep));
            float col = scan;
            if (zigzag && (row & 1) != 0) {
                col = -col;
            }
            return row * RASTER_ROW_SCALE + col;
        }

        // XZ 平面（法线≈Y）
        if (ay > 0.999) {
            int x = pos.getX() - min.getX();
            int z = pos.getZ() - min.getZ();
            int scan = scanAxis.equals("U") ? x : z;       // U=X, V=Z
            int rowCoord = scanAxis.equals("U") ? z : x;
            int row = (int) Math.floor(rowCoord / Math.max(0.1f, rowStep));
            float col = scan;
            if (zigzag && (row & 1) != 0) {
                col = -col;
            }
            return row * RASTER_ROW_SCALE + col;
        }

        // YZ 平面（法线≈X）
        if (ax > 0.999) {
            int y = pos.getY() - min.getY();
            int z = pos.getZ() - min.getZ();
            int scan = scanAxis.equals("U") ? y : z;       // U=Y, V=Z
            int rowCoord = scanAxis.equals("U") ? z : y;
            int row = (int) Math.floor(rowCoord / Math.max(0.1f, rowStep));
            float col = scan;
            if (zigzag && (row & 1) != 0) {
                col = -col;
            }
            return row * RASTER_ROW_SCALE + col;
        }

        // 2) 自定义角度：构造层平面基向量 U/V，使用投影坐标做栅格扫描（不如轴对齐稳定，但符合“与切片一致”）。
        Vec3d up = Math.abs(n.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        Vec3d u = up.crossProduct(n);
        if (u.lengthSquared() < 1.0e-6) {
            u = new Vec3d(1, 0, 0);
        } else {
            u = u.normalize();
        }
        Vec3d v = n.crossProduct(u);
        if (v.lengthSquared() < 1.0e-6) {
            v = new Vec3d(0, 0, 1);
        } else {
            v = v.normalize();
        }

        double uCoord = rel.dotProduct(u);
        double vCoord = rel.dotProduct(v);

        double scanCoord = scanAxis.equals("U") ? uCoord : vCoord;
        double rowCoord = scanAxis.equals("U") ? vCoord : uCoord;

        int row = (int) Math.floor(rowCoord / Math.max(0.1f, rowStep));
        float col = (float) scanCoord;
        if (zigzag && (row & 1) != 0) {
            col = -col;
        }
        return row * RASTER_ROW_SCALE + col;
    }

    private Vec3d getNormalFromAngles(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);

        double y = Math.sin(pitch);
        double horiz = Math.cos(pitch);
        double x = horiz * Math.sin(yaw);
        double z = horiz * Math.cos(yaw);
        Vec3d normal = new Vec3d(x, y, z);
        return normal.lengthSquared() == 0 ? new Vec3d(0, 1, 0) : normal.normalize();
    }

    private float hash(Vec3d rel) {
        long xBits = Double.doubleToLongBits(rel.x);
        long yBits = Double.doubleToLongBits(rel.y);
        long zBits = Double.doubleToLongBits(rel.z);
        long hash = xBits;
        hash = hash * 31 + yBits;
        hash = hash * 31 + zBits;
        hash ^= (hash << 13);
        hash ^= (hash >>> 17);
        hash ^= (hash << 5);
        return (float) ((hash & 0x7FFFFFFFL) % 10_000) / 10_000.0f;
    }
}

