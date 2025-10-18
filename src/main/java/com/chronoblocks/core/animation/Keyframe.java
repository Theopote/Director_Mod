package com.chronoblocks.core.animation;

import java.util.Arrays;

/**
 * Keyframe data for ChronoBlocks animations v4.5
 * Contains time (seconds) and transform/visual properties with curve interpolation support.
 * 
 * @version 4.5 - 添加插值类型和贝塞尔控制点支持
 */
public class Keyframe {
    private float time; // seconds
    private final float[] position = new float[] {0f, 0f, 0f};
    private final float[] rotation = new float[] {0f, 0f, 0f}; // degrees
    private final float[] scale = new float[] {1f, 1f, 1f};
    private float opacity = 1.0f; // 0..1
    
    // ==================== v4.5 新增：曲线插值支持 ====================
    
    /** 插值类型（到下一个关键帧的插值方式） */
    private InterpolationType interpolationType = InterpolationType.LINEAR;
    
    /** 贝塞尔曲线控制点 [x1, y1, x2, y2]（仅当 interpolationType == BEZIER 时使用） */
    private float[] bezierControlPoints = null;
    
    /** 自定义参数（用于 Spring, Bounce 等特殊插值类型） */
    private float[] customParams = null;

    public Keyframe() {}

    public Keyframe(float time) {
        this.time = time;
    }

    public Keyframe(float time, float[] position, float[] rotation, float[] scale, float opacity) {
        this.time = time;
        if (position != null && position.length >= 3) System.arraycopy(position, 0, this.position, 0, 3);
        if (rotation != null && rotation.length >= 3) System.arraycopy(rotation, 0, this.rotation, 0, 3);
        if (scale != null && scale.length >= 3) System.arraycopy(scale, 0, this.scale, 0, 3);
        this.opacity = opacity;
    }

    public Keyframe(Keyframe other) {
        this(other.time, other.position, other.rotation, other.scale, other.opacity);
    }

    public float getTime() { return time; }
    public void setTime(float time) { this.time = time; }

    public float[] getPosition() { return position; }
    public void setPosition(float x, float y, float z) { position[0] = x; position[1] = y; position[2] = z; }

    public float[] getRotation() { return rotation; }
    public void setRotation(float x, float y, float z) { rotation[0] = x; rotation[1] = y; rotation[2] = z; }

    public float[] getScale() { return scale; }
    public void setScale(float x, float y, float z) { scale[0] = x; scale[1] = y; scale[2] = z; }

    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { this.opacity = opacity; }
    
    // ==================== v4.5 新增：插值类型 Getter/Setter ====================
    
    /**
     * 获取插值类型
     * @return 到下一个关键帧的插值类型
     */
    public InterpolationType getInterpolationType() {
        return interpolationType;
    }
    
    /**
     * 设置插值类型
     * @param type 插值类型
     */
    public void setInterpolationType(InterpolationType type) {
        this.interpolationType = type != null ? type : InterpolationType.LINEAR;
    }
    
    /**
     * 获取贝塞尔控制点
     * @return 控制点数组 [x1, y1, x2, y2] 或 null
     */
    public float[] getBezierControlPoints() {
        return bezierControlPoints;
    }
    
    /**
     * 设置贝塞尔控制点
     * @param controlPoints 控制点数组 [x1, y1, x2, y2]
     */
    public void setBezierControlPoints(float[] controlPoints) {
        if (controlPoints != null && controlPoints.length == 4) {
            this.bezierControlPoints = Arrays.copyOf(controlPoints, 4);
        } else {
            this.bezierControlPoints = null;
        }
    }
    
    /**
     * 设置贝塞尔控制点（便捷方法）
     * @param x1 第一个控制点 x
     * @param y1 第一个控制点 y
     * @param x2 第二个控制点 x
     * @param y2 第二个控制点 y
     */
    public void setBezierControlPoints(float x1, float y1, float x2, float y2) {
        this.bezierControlPoints = new float[]{x1, y1, x2, y2};
    }
    
    /**
     * 获取自定义参数
     * @return 自定义参数数组或 null
     */
    public float[] getCustomParams() {
        return customParams;
    }
    
    /**
     * 设置自定义参数
     * @param params 自定义参数数组
     */
    public void setCustomParams(float[] params) {
        this.customParams = params != null ? Arrays.copyOf(params, params.length) : null;
    }

    @Override
    public String toString() {
        return "Keyframe{" +
                "time=" + time +
                ", position=" + Arrays.toString(position) +
                ", rotation=" + Arrays.toString(rotation) +
                ", scale=" + Arrays.toString(scale) +
                ", opacity=" + opacity +
                ", interpolationType=" + interpolationType +
                (bezierControlPoints != null ? ", bezier=" + Arrays.toString(bezierControlPoints) : "") +
                '}';
    }

    /**
     * 插值两个关键帧 v4.5 - 使用曲线插值
     * <p>使用关键帧 a 的插值类型进行插值
     * 
     * @param a 起始关键帧
     * @param b 结束关键帧
     * @param t 时间进度 [0.0, 1.0]
     * @param out 输出关键帧
     */
    public static void lerp(Keyframe a, Keyframe b, float t, Keyframe out) {
        if (a == null || b == null || out == null) return;
        t = clamp01(t);
        
        // 使用起始关键帧的插值类型和参数
        InterpolationType interpType = a.getInterpolationType();
        float[] params = null;
        
        // 准备插值参数
        if (interpType == InterpolationType.BEZIER) {
            params = a.getBezierControlPoints();
        } else if (interpType.requiresCustomParameters()) {
            params = a.getCustomParams();
        }
        
        // 计算插值后的 t 值
        float interpolatedT = CurveInterpolator.interpolate(interpType, t, params);
        
        // 对各个属性进行插值
        for (int i = 0; i < 3; i++) {
            out.position[i] = lerp(a.position[i], b.position[i], interpolatedT);
            out.rotation[i] = lerp(a.rotation[i], b.rotation[i], interpolatedT);
            out.scale[i] = lerp(a.scale[i], b.scale[i], interpolatedT);
        }
        out.opacity = lerp(a.opacity, b.opacity, interpolatedT);
        
        // 复制插值类型（保持属性一致性）
        out.setInterpolationType(interpType);
    }
    
    /**
     * 线性插值两个关键帧（忽略插值类型）
     * <p>强制使用线性插值，向后兼容旧代码
     * 
     * @param a 起始关键帧
     * @param b 结束关键帧
     * @param t 时间进度 [0.0, 1.0]
     * @param out 输出关键帧
     */
    public static void lerpLinear(Keyframe a, Keyframe b, float t, Keyframe out) {
        if (a == null || b == null || out == null) return;
        t = clamp01(t);
        for (int i = 0; i < 3; i++) {
            out.position[i] = lerp(a.position[i], b.position[i], t);
            out.rotation[i] = lerp(a.rotation[i], b.rotation[i], t);
            out.scale[i] = lerp(a.scale[i], b.scale[i], t);
        }
        out.opacity = lerp(a.opacity, b.opacity, t);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
