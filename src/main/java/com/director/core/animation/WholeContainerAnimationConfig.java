package com.director.core.animation;

import java.io.Serial;
import java.io.Serializable;

/** Clip-local settings; switching scope preserves the per-block settings. */
public final class WholeContainerAnimationConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public enum Effect { SCALE, FLY, ROTATE, TRANSFORM }
    public enum Pivot { CENTER, BOTTOM_CENTER, CUSTOM }

    private Effect effect = Effect.SCALE;
    private Pivot pivot = Pivot.BOTTOM_CENTER;
    private float scale = 0.0f;
    private float offsetX = 0.0f;
    private float offsetY = 10.0f;
    private float offsetZ = 0.0f;
    private float rotationX = 0.0f;
    private float rotationY = 180.0f;
    private float rotationZ = 0.0f;
    // Relative to the bounds center, so moving the container preserves the authored pivot.
    private float pivotX = 0.0f;
    private float pivotY = 0.0f;
    private float pivotZ = 0.0f;

    public Effect getEffect() { return effect != null ? effect : Effect.SCALE; }
    public void setEffect(Effect value) { effect = value != null ? value : Effect.SCALE; }
    public Pivot getPivot() { return pivot != null ? pivot : Pivot.BOTTOM_CENTER; }
    public void setPivot(Pivot value) { pivot = value != null ? value : Pivot.BOTTOM_CENTER; }
    public float getScale() { return finite(scale, 0.0f, 0.0f, 10.0f); }
    public void setScale(float value) { scale = finite(value, 0.0f, 0.0f, 10.0f); }
    public float getOffsetX() { return finite(offsetX, 0.0f, -10000.0f, 10000.0f); }
    public float getOffsetY() { return finite(offsetY, 10.0f, -10000.0f, 10000.0f); }
    public float getOffsetZ() { return finite(offsetZ, 0.0f, -10000.0f, 10000.0f); }
    public void setOffset(float x, float y, float z) {
        offsetX = finite(x, 0.0f, -10000.0f, 10000.0f);
        offsetY = finite(y, 10.0f, -10000.0f, 10000.0f);
        offsetZ = finite(z, 0.0f, -10000.0f, 10000.0f);
    }
    public float getRotationX() { return finite(rotationX, 0, -3600, 3600); }
    public float getRotationY() { return finite(rotationY, 180, -3600, 3600); }
    public float getRotationZ() { return finite(rotationZ, 0, -3600, 3600); }
    public void setRotation(float x, float y, float z) {
        rotationX = finite(x, 0, -3600, 3600);
        rotationY = finite(y, 180, -3600, 3600);
        rotationZ = finite(z, 0, -3600, 3600);
    }
    public float getPivotX() { return finite(pivotX, 0, -10000, 10000); }
    public float getPivotY() { return finite(pivotY, 0, -10000, 10000); }
    public float getPivotZ() { return finite(pivotZ, 0, -10000, 10000); }
    public void setCustomPivot(float x, float y, float z) {
        pivotX = finite(x, 0, -10000, 10000);
        pivotY = finite(y, 0, -10000, 10000);
        pivotZ = finite(z, 0, -10000, 10000);
    }
    private static float finite(float value, float fallback, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    public WholeContainerAnimationConfig copy() {
        WholeContainerAnimationConfig copy = new WholeContainerAnimationConfig();
        copy.setEffect(getEffect());
        copy.setPivot(getPivot());
        copy.setScale(getScale());
        copy.setOffset(getOffsetX(), getOffsetY(), getOffsetZ());
        copy.setRotation(getRotationX(), getRotationY(), getRotationZ());
        copy.setCustomPivot(getPivotX(), getPivotY(), getPivotZ());
        return copy;
    }
}
