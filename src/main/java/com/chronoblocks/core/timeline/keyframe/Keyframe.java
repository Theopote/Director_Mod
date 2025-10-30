package com.chronoblocks.core.timeline.keyframe;

import org.joml.Vector3f;

/**
 * 关键帧类
 * 表示时间轴上的关键点
 */
public final class Keyframe {
    public enum Type { POSITION, ROTATION, SCALE, OPACITY }

    public final float time;
    public final Type type;
    public final Vector3f vector;
    public final float scalar;

    private Keyframe(float time, Type type, Vector3f vector, float scalar) {
        this.time = time; 
        this.type = type; 
        this.vector = vector; 
        this.scalar = scalar;
    }

    /**
     * 创建位置关键帧
     */
    public static Keyframe ofPosition(float time, Vector3f position) {
        return new Keyframe(time, Type.POSITION, new Vector3f(position), 0);
    }

    /**
     * 创建旋转关键帧
     */
    public static Keyframe ofRotation(float time, Vector3f rotation) {
        return new Keyframe(time, Type.ROTATION, new Vector3f(rotation), 0);
    }

    /**
     * 创建缩放关键帧
     */
    public static Keyframe ofScale(float time, Vector3f scale) {
        return new Keyframe(time, Type.SCALE, new Vector3f(scale), 0);
    }

    /**
     * 创建透明度关键帧
     */
    public static Keyframe ofOpacity(float time, float opacity) {
        return new Keyframe(time, Type.OPACITY, null, opacity);
    }

    /**
     * 获取时间
     */
    public float getTime() {
        return time;
    }

    /**
     * 获取类型
     */
    public Type getType() {
        return type;
    }

    /**
     * 获取向量值
     */
    public Vector3f getVector() {
        return vector;
    }

    /**
     * 获取标量值
     */
    public float getScalar() {
        return scalar;
    }

    @Override
    public String toString() {
        return String.format("Keyframe{time=%.2f, type=%s, vector=%s, scalar=%.2f}", 
                time, type, vector, scalar);
    }
}
