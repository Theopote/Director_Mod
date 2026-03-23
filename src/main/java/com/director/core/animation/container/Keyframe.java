package com.director.core.animation.container;

import com.google.gson.annotations.SerializedName;

/**
 * 🎚️ Keyframe<T> - 时间 + 值 + 插值信息。
 *
 * @param <T> 关键帧值类型
 */
public class Keyframe<T> {

    public enum Easing {
        LINEAR,
        EASE_IN,
        EASE_OUT
    }

    @SerializedName("time")
    private double timeSec;

    @SerializedName("value")
    private T value;

    @SerializedName("ease")
    private Easing easing = Easing.LINEAR;

    public Keyframe(double timeSec, T value) {
        this.timeSec = timeSec;
        this.value = value;
    }

    public double time() {
        return timeSec;
    }

    public void setTime(double timeSec) {
        this.timeSec = Math.max(0.0, timeSec);
    }

    public T value() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public Easing easing() {
        return easing;
    }

    public void setEasing(Easing easing) {
        this.easing = easing != null ? easing : Easing.LINEAR;
    }
}

