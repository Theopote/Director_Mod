package com.director.core.animation;

import com.director.core.animation.easing.EasingFunction;
import com.director.core.container.Container;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RotationAxis;

/** Pure timeline sampling: no world edits, effects, or frame-dependent state. */
public final class WholeContainerAnimation {
    private WholeContainerAnimation() {}

    public record Frame(boolean worldVisible, boolean renderVisible, boolean animating,
                        float progress, float scale, float x, float y, float z,
                        float rotationX, float rotationY, float rotationZ) {
        public Frame(boolean worldVisible, boolean renderVisible, boolean animating,
                     float progress, float scale, float x, float y, float z) {
            this(worldVisible, renderVisible, animating, progress, scale, x, y, z, 0, 0, 0);
        }
        /** Conjugate the shared pivot into block-local space, preserving all relative positions. */
        public MatrixStack localTransform(BlockPos pos, Vec3d pivot) {
            if (!animating) return null;
            MatrixStack matrix = new MatrixStack();
            double px = pivot.x - pos.getX();
            double py = pivot.y - pos.getY();
            double pz = pivot.z - pos.getZ();
            matrix.translate(x + px, y + py, z + pz);
            // Match the existing block engine: yaw, pitch, then roll. Angles preserve full turns.
            if (rotationY != 0) matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationY));
            if (rotationX != 0) matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotationX));
            if (rotationZ != 0) matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationZ));
            // Avoid a singular normal matrix at the exact beginning/end of a zero-scale effect.
            float safeScale = Math.max(0.0001f, scale);
            matrix.scale(safeScale, safeScale, safeScale);
            matrix.translate(-px, -py, -pz);
            return matrix;
        }
    }

    public record Window(float start, float end) {}

    /** Rendering, world handoff and audio use the same effective clip-local window. */
    public static Window window(ClipAnimationConfig config, float clipStart, float clipEnd) {
        return window(clipStart, clipEnd, config.getDelay(), config.getDuration());
    }

    private static Window window(float clipStart, float clipEnd, float delay, float duration) {
        float start = Math.min(clipEnd, clipStart + Math.max(0, delay));
        return new Window(start, Math.min(clipEnd, start + duration));
    }

    public static Frame sample(ClipAnimationConfig config, float clipStart, float clipEnd,
                               float time, boolean exit) {
        Window window = window(config, clipStart, clipEnd);
        float start = window.start(), end = window.end();
        if (time < start) return stationary(exit, 0);
        if (time >= end || end <= start) return stationary(!exit, 1);
        float raw = (time - start) / (end - start);
        float eased = EasingFunction.fromString(config.getEasing()).apply(raw);
        float amount = exit ? eased : 1 - eased;
        WholeContainerAnimationConfig settings = config.getWholeContainer();
        float scale = 1;
        float x = 0, y = 0, z = 0;
        float rotationX = 0, rotationY = 0, rotationZ = 0;
        boolean combined = settings.getEffect() == WholeContainerAnimationConfig.Effect.TRANSFORM;
        if (combined || settings.getEffect() == WholeContainerAnimationConfig.Effect.SCALE) {
            scale = Math.max(0, 1 + (settings.getScale() - 1) * amount);
        }
        if (combined || settings.getEffect() == WholeContainerAnimationConfig.Effect.FLY) {
            x = settings.getOffsetX() * amount;
            y = settings.getOffsetY() * amount;
            z = settings.getOffsetZ() * amount;
        }
        if (combined || settings.getEffect() == WholeContainerAnimationConfig.Effect.ROTATE) {
            rotationX = settings.getRotationX() * amount;
            rotationY = settings.getRotationY() * amount;
            rotationZ = settings.getRotationZ() * amount;
        }
        return new Frame(false, true, true, raw, scale, x, y, z, rotationX, rotationY, rotationZ);
    }

    /** Keep the preview at the same normalized phase when the duration is edited.
     * Times outside the old animation window belong to the rest of the timeline.
     */
    public static float retimePreviewTime(float clipStart, float clipEnd, float delay,
                                         float oldDuration, float newDuration, float time) {
        Window oldWindow = window(clipStart, clipEnd, delay, oldDuration);
        Window newWindow = window(clipStart, clipEnd, delay, newDuration);
        float start = oldWindow.start(), oldEnd = oldWindow.end(), newEnd = newWindow.end();
        if (oldEnd <= start || time < start || time > oldEnd) return time;
        float progress = (time - start) / (oldEnd - start);
        return start + progress * (newEnd - start);
    }

    private static Frame stationary(boolean visible, float progress) {
        return new Frame(visible, visible, false, progress, 1, 0, 0, 0);
    }

    public static Vec3d pivot(Container.BoundingBox bounds, WholeContainerAnimationConfig.Pivot kind) {
        if (bounds == null) return Vec3d.ZERO;
        BlockPos min = bounds.getMin(), max = bounds.getMax();
        return new Vec3d((min.getX() + (double) max.getX() + 1) / 2,
                kind == WholeContainerAnimationConfig.Pivot.BOTTOM_CENTER ? min.getY()
                        : (min.getY() + (double) max.getY() + 1) / 2,
                (min.getZ() + (double) max.getZ() + 1) / 2);
    }

    public static Vec3d pivot(Container.BoundingBox bounds, WholeContainerAnimationConfig settings) {
        Vec3d base = pivot(bounds, settings.getPivot());
        return settings.getPivot() == WholeContainerAnimationConfig.Pivot.CUSTOM
                ? base.add(settings.getPivotX(), settings.getPivotY(), settings.getPivotZ()) : base;
    }
}
