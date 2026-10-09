package com.director.core.animation;

import com.director.core.animation.easing.EasingFunction;
import com.director.core.container.Container;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Pure timeline sampling: no world edits, effects, or frame-dependent state. */
public final class WholeContainerAnimation {
    private WholeContainerAnimation() {}

    public record Frame(boolean worldVisible, boolean renderVisible, boolean animating,
                        float progress, float scale, float x, float y, float z) {
        /** Conjugate the shared pivot into block-local space, preserving all relative positions. */
        public MatrixStack localTransform(BlockPos pos, Vec3d pivot) {
            if (!animating) return null;
            MatrixStack matrix = new MatrixStack();
            double px = pivot.x - pos.getX();
            double py = pivot.y - pos.getY();
            double pz = pivot.z - pos.getZ();
            matrix.translate(x + px, y + py, z + pz);
            // Avoid a singular normal matrix at the exact beginning/end of a zero-scale effect.
            float safeScale = Math.max(0.0001f, scale);
            matrix.scale(safeScale, safeScale, safeScale);
            matrix.translate(-px, -py, -pz);
            return matrix;
        }
    }

    public static Frame sample(ClipAnimationConfig config, float clipStart, float clipEnd,
                               float time, boolean exit) {
        float start = Math.min(clipEnd, clipStart + Math.max(0, config.getDelay()));
        float end = Math.min(clipEnd, start + config.getDuration());
        if (time < start) return stationary(exit, 0);
        if (time >= end || end <= start) return stationary(!exit, 1);
        float raw = (time - start) / (end - start);
        float eased = EasingFunction.fromString(config.getEasing()).apply(raw);
        float amount = exit ? eased : 1 - eased;
        WholeContainerAnimationConfig settings = config.getWholeContainer();
        float scale = 1;
        float x = 0, y = 0, z = 0;
        if (settings.getEffect() == WholeContainerAnimationConfig.Effect.SCALE) {
            scale = Math.max(0, 1 + (settings.getScale() - 1) * amount);
        } else {
            x = settings.getOffsetX() * amount;
            y = settings.getOffsetY() * amount;
            z = settings.getOffsetZ() * amount;
        }
        return new Frame(false, true, true, raw, scale, x, y, z);
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
}
