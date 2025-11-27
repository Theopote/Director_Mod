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

        float flowScore = computeFlowScore(rel, flow, params);
        float fillScore = computeFillScore(rel, fill);

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

