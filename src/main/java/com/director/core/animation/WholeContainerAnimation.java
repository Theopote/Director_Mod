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
                        float rotationX, float rotationY, float rotationZ, float alpha) {
        public Frame(boolean worldVisible, boolean renderVisible, boolean animating,
                     float progress, float scale, float x, float y, float z,
                     float rotationX, float rotationY, float rotationZ) {
            this(worldVisible, renderVisible, animating, progress, scale, x, y, z,
                    rotationX, rotationY, rotationZ, 1);
        }
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
        float duration = config.getWholeContainer().getEffect() == WholeContainerAnimationConfig.Effect.INSTANT
                ? 0 : config.getDuration();
        return window(clipStart, clipEnd, config.getDelay(), duration);
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
        float geometryAmount = amount;
        // The modifier uses normalized time, so seeking and duration edits preserve its shape.
        // Exit reverses the modifier around the real-world pose; opacity keeps its own easing.
        float phase = exit ? 1 - raw : raw;
        if (settings.usesGeometry() && settings.getMotion() == WholeContainerAnimationConfig.Motion.ELASTIC) {
            double spring = 1 - Math.pow(1 - phase, 3)
                    * Math.cos(2 * Math.PI * settings.getMotionCycles() * phase);
            geometryAmount -= settings.getMotionStrength() * (float) (spring - phase);
        }
        float scale = 1;
        float x = 0, y = 0, z = 0;
        float rotationX = 0, rotationY = 0, rotationZ = 0;
        if (settings.usesScale()) {
            scale = Math.max(0, 1 + (settings.getScale() - 1) * geometryAmount);
        }
        if (settings.usesTranslation()) {
            x = settings.getOffsetX() * geometryAmount;
            y = settings.getOffsetY() * geometryAmount;
            z = settings.getOffsetZ() * geometryAmount;
            if (settings.getFlightPath() == WholeContainerAnimationConfig.FlightPath.ARC) {
                // Quadratic Bezier: preserve both endpoints, with authored world-Y lift at the midpoint.
                // Clamp only the arc lift during overshoot; the original motion can still rebound.
                float path = Math.clamp(geometryAmount, 0, 1);
                y += 4 * settings.getArcHeight() * path * (1 - path);
            }
        }
        if (settings.usesRotation()) {
            rotationX = settings.getRotationX() * geometryAmount;
            rotationY = settings.getRotationY() * geometryAmount;
            rotationZ = settings.getRotationZ() * geometryAmount;
        }
        if (settings.usesGeometry() && settings.getMotion() == WholeContainerAnimationConfig.Motion.BOUNCE) {
            // Nonnegative, decaying world-Y hops, with exact zero displacement at both endpoints.
            y += settings.getBounceHeight() * settings.getMotionStrength()
                    * (float) (Math.abs(Math.sin(Math.PI * settings.getMotionCycles() * phase))
                    * Math.pow(1 - phase, 2));
        }
        float alpha = settings.usesOpacity()
                ? Math.clamp(1 + (settings.getOpacity() - 1) * amount, 0, 1) : 1;
        return new Frame(false, true, true, raw, scale, x, y, z, rotationX, rotationY, rotationZ, alpha);
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
        return new Frame(visible, visible, false, progress, 1, 0, 0, 0, 0, 0, 0, visible ? 1 : 0);
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
