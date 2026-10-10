package com.director.core.animation;

import java.io.Serial;
import java.io.Serializable;

/** Clip-local settings; switching scope preserves the per-block settings. */
public final class WholeContainerAnimationConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public enum Effect { SCALE, FLY, ROTATE, TRANSFORM, FADE, INSTANT }
    public enum Pivot { CENTER, BOTTOM_CENTER, CUSTOM }
    public enum Motion { NONE, ELASTIC, BOUNCE }

    private Motion motion;
    private Float motionStrength;
    private Integer motionCycles;
    private Float bounceHeight;

    private boolean dustEnabled;
    private Float dustIntensity;
    private Float dustSpread;

    private Effect effect = Effect.SCALE;
    private Pivot pivot = Pivot.BOTTOM_CENTER;
    private float scale = 0.0f;
    private float offsetX = 0.0f;
    private float offsetY = 10.0f;
    private float offsetZ = 0.0f;
    private float rotationX = 0.0f;
    private float rotationY = 180.0f;
    private float rotationZ = 0.0f;
    // Null means legacy defaults: all three transform channels on, opacity off.
    private Boolean translationEnabled;
    private Boolean rotationEnabled;
    private Boolean scaleEnabled;
    private Boolean opacityEnabled;
    private float opacity = 0.0f;

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
    public boolean isTranslationEnabled() { return translationEnabled == null || translationEnabled; }
    public void setTranslationEnabled(boolean value) { translationEnabled = value; }
    public boolean isRotationEnabled() { return rotationEnabled == null || rotationEnabled; }
    public void setRotationEnabled(boolean value) { rotationEnabled = value; }
    public boolean isScaleEnabled() { return scaleEnabled == null || scaleEnabled; }
    public void setScaleEnabled(boolean value) { scaleEnabled = value; }
    public boolean isOpacityEnabled() { return Boolean.TRUE.equals(opacityEnabled); }
    public void setOpacityEnabled(boolean value) { opacityEnabled = value; }
    public float getOpacity() { return finite(opacity, 0, 0, 1); }
    public void setOpacity(float value) { opacity = finite(value, 0, 0, 1); }
    public boolean usesTranslation() { return getEffect() == Effect.FLY || getEffect() == Effect.TRANSFORM && isTranslationEnabled(); }
    public boolean usesRotation() { return getEffect() == Effect.ROTATE || getEffect() == Effect.TRANSFORM && isRotationEnabled(); }
    public boolean usesScale() { return getEffect() == Effect.SCALE || getEffect() == Effect.TRANSFORM && isScaleEnabled(); }
    public boolean usesOpacity() { return getEffect() == Effect.FADE || getEffect() == Effect.TRANSFORM && isOpacityEnabled(); }

    public boolean isDustEnabled() { return dustEnabled; }
    public void setDustEnabled(boolean value) { dustEnabled = value; }
    public float getDustIntensity() { return finite(dustIntensity != null ? dustIntensity : 1, 1, 0, 5); }
    public void setDustIntensity(float value) { dustIntensity = finite(value, 1, 0, 5); }
    public float getDustSpread() { return finite(dustSpread != null ? dustSpread : 0.6f, 0.6f, 0, 5); }
    public void setDustSpread(float value) { dustSpread = finite(value, 0.6f, 0, 5); }

    public Motion getMotion() { return motion != null ? motion : Motion.NONE; }
    public void setMotion(Motion value) { motion = value != null ? value : Motion.NONE; }
    public float getMotionStrength() { return finite(motionStrength != null ? motionStrength : 1, 1, 0, 1); }
    public void setMotionStrength(float value) { motionStrength = finite(value, 1, 0, 1); }
    public int getMotionCycles() { return motionCycles != null ? Math.clamp(motionCycles, 1, 8) : 3; }
    public void setMotionCycles(int value) { motionCycles = Math.clamp(value, 1, 8); }
    public float getBounceHeight() { return finite(bounceHeight != null ? bounceHeight : 1, 1, 0, 100); }
    public void setBounceHeight(float value) { bounceHeight = finite(value, 1, 0, 100); }
    public boolean usesGeometry() { return usesTranslation() || usesRotation() || usesScale(); }

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
        copy.translationEnabled = translationEnabled;
        copy.rotationEnabled = rotationEnabled;
        copy.scaleEnabled = scaleEnabled;
        copy.opacityEnabled = opacityEnabled;
        copy.setOpacity(getOpacity());
        copy.setMotion(getMotion()); copy.setMotionStrength(getMotionStrength());
        copy.setMotionCycles(getMotionCycles()); copy.setBounceHeight(getBounceHeight());
        copy.setDustEnabled(isDustEnabled()); copy.setDustIntensity(getDustIntensity()); copy.setDustSpread(getDustSpread());
        return copy;
    }
}
