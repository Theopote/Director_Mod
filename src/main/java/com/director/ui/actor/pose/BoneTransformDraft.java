package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.BoneTransform;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/** Numeric editor state. A changed target or sampled pose invalidates uncommitted edits. */
final class BoneTransformDraft {
    final float[] translation = new float[3];
    final float[] rotation = new float[3];
    final float[] scale = new float[3];
    private Object context;
    private BoneTransform source;
    private int changedChannels;
    private boolean dirty;
    private boolean rotationChanged;

    boolean synchronize(Object nextContext, BoneTransform sampled) {
        if (Objects.equals(context, nextContext) && source != null
                && source.toMatrix().equals(sampled.toMatrix())) return false;
        boolean discarded = dirty;
        context = nextContext;
        source = sampled;
        reset();
        return discarded;
    }

    void markChanged(boolean rotationEdited) {
        dirty = true;
        rotationChanged |= rotationEdited;
    }

    void markChannelsChanged(int channels) {
        changedChannels |= channels;
        markChanged((channels & com.director.actor.rig.skeleton.RigPose.ROTATION) != 0);
    }

    int changedChannels() { return changedChannels; }

    boolean isDirty() { return dirty; }

    boolean isValid() {
        return new Vector3f(translation).isFinite() && new Vector3f(rotation).isFinite()
                && new Vector3f(scale).isFinite() && scale[0] > 0 && scale[1] > 0 && scale[2] > 0;
    }

    BoneTransform value() {
        if (!isValid()) throw new IllegalArgumentException("请输入有限数值，且缩放必须大于零。");
        Quaternionf q = rotationChanged ? new Quaternionf().rotationZYX(
                (float)Math.toRadians(rotation[2]), (float)Math.toRadians(rotation[1]),
                (float)Math.toRadians(rotation[0])) : source.rotation();
        return new BoneTransform(new Vector3f(translation), q, new Vector3f(scale));
    }

    void reset() {
        copy(source.translation(), translation);
        copy(source.rotation().getEulerAnglesZYX(new Vector3f()).mul((float)(180 / Math.PI)), rotation);
        copy(source.scale(), scale);
        changedChannels = 0;
        dirty = false;
        rotationChanged = false;
    }

    void clear() {
        context = null;
        source = null;
        changedChannels = 0;
        dirty = false;
        rotationChanged = false;
    }

    private static void copy(Vector3f vector, float[] target) {
        target[0] = vector.x;
        target[1] = vector.y;
        target[2] = vector.z;
    }
}
