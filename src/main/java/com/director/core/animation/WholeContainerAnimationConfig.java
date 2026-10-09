package com.director.core.animation;

import java.io.Serializable;

/** Clip-local settings; switching scope preserves the per-block settings. */
public final class WholeContainerAnimationConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Effect { SCALE, FLY }
    public enum Pivot { CENTER, BOTTOM_CENTER }

    private Effect effect = Effect.SCALE;
    private Pivot pivot = Pivot.BOTTOM_CENTER;
    private float scale = 0.0f;
    private float offsetX = 0.0f;
    private float offsetY = 10.0f;
    private float offsetZ = 0.0f;

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
    private static float finite(float value, float fallback, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    public WholeContainerAnimationConfig copy() {
        WholeContainerAnimationConfig copy = new WholeContainerAnimationConfig();
        copy.setEffect(getEffect());
        copy.setPivot(getPivot());
        copy.setScale(getScale());
        copy.setOffset(getOffsetX(), getOffsetY(), getOffsetZ());
        return copy;
    }
}
