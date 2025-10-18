package com.chronoblocks.core.animation;

/**
 * 曲线插值器 v4.5 - 实现各种插值算法
 * 
 * <p>提供高性能的插值计算，支持线性、缓动、贝塞尔、弹簧等多种曲线类型。
 * 
 * <h3>支持的插值类型</h3>
 * <ul>
 *   <li>线性插值 - O(1)</li>
 *   <li>缓动曲线 - O(1)</li>
 *   <li>贝塞尔曲线 - O(1) 近似</li>
 *   <li>弹簧效果 - O(1)</li>
 *   <li>弹跳效果 - O(1)</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 基础插值
 * float t = 0.5f; // 时间进度 [0, 1]
 * float value = CurveInterpolator.interpolate(InterpolationType.EASE_IN_OUT, t);
 * 
 * // 值插值
 * float result = CurveInterpolator.lerp(start, end, t, InterpolationType.EASE_OUT);
 * 
 * // 自定义贝塞尔曲线
 * float[] controlPoints = {0.42f, 0.0f, 0.58f, 1.0f};
 * float value = CurveInterpolator.bezier(t, controlPoints);
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class CurveInterpolator {
    
    // ==================== 常量 ====================
    
    /** 贝塞尔曲线采样精度 */
    private static final int BEZIER_SAMPLES = 11;
    
    /** 弹簧阻尼系数 */
    private static final float SPRING_DAMPING = 6.0f;
    
    /** 弹簧频率 */
    private static final float SPRING_FREQUENCY = 6.0f;
    
    /** 弹跳次数 */
    private static final int BOUNCE_COUNT = 4;
    
    /** PI 常量 */
    private static final float PI = (float) Math.PI;
    
    // ==================== 主插值方法 ====================
    
    /**
     * 根据插值类型计算插值值
     * 
     * @param type 插值类型
     * @param t 时间进度 [0.0, 1.0]
     * @return 插值结果 [0.0, 1.0]（部分类型可能超出范围）
     */
    public static float interpolate(InterpolationType type, float t) {
        return interpolate(type, t, null);
    }
    
    /**
     * 根据插值类型和自定义参数计算插值值
     * 
     * @param type 插值类型
     * @param t 时间进度 [0.0, 1.0]
     * @param params 自定义参数（如贝塞尔控制点）
     * @return 插值结果
     */
    public static float interpolate(InterpolationType type, float t, float[] params) {
        // 边界处理
        if (t <= 0.0f) return 0.0f;
        if (t >= 1.0f) return 1.0f;
        
        return switch (type) {
            case LINEAR -> t;
            case EASE_IN -> easeIn(t);
            case EASE_OUT -> easeOut(t);
            case EASE_IN_OUT -> easeInOut(t);
            case BEZIER -> bezier(t, params != null ? params : type.getDefaultBezierControlPoints());
            case STEP -> step(t);
            case SPRING -> spring(t, params);
            case BOUNCE -> bounce(t);
        };
    }
    
    /**
     * 在两个值之间进行插值
     * 
     * @param start 起始值
     * @param end 结束值
     * @param t 时间进度 [0.0, 1.0]
     * @param type 插值类型
     * @return 插值结果
     */
    public static float lerp(float start, float end, float t, InterpolationType type) {
        float interpolatedT = interpolate(type, t);
        return start + (end - start) * interpolatedT;
    }
    
    /**
     * 在两个值之间进行插值（带自定义参数）
     * 
     * @param start 起始值
     * @param end 结束值
     * @param t 时间进度 [0.0, 1.0]
     * @param type 插值类型
     * @param params 自定义参数
     * @return 插值结果
     */
    public static float lerp(float start, float end, float t, InterpolationType type, float[] params) {
        float interpolatedT = interpolate(type, t, params);
        return start + (end - start) * interpolatedT;
    }
    
    // ==================== 基础缓动函数 ====================
    
    /**
     * 缓入 - 二次方缓入
     * f(t) = t²
     */
    private static float easeIn(float t) {
        return t * t;
    }
    
    /**
     * 缓出 - 二次方缓出
     * f(t) = 1 - (1-t)²
     */
    private static float easeOut(float t) {
        float invT = 1.0f - t;
        return 1.0f - invT * invT;
    }
    
    /**
     * 缓入缓出 - 平滑阶梯函数
     * f(t) = 3t² - 2t³
     */
    private static float easeInOut(float t) {
        return t * t * (3.0f - 2.0f * t);
    }
    
    /**
     * 阶梯 - 在 0.5 处跳变
     * f(t) = step(t, 0.5)
     */
    private static float step(float t) {
        return t < 0.5f ? 0.0f : 1.0f;
    }
    
    // ==================== 高级缓动函数 ====================
    
    /**
     * 三次贝塞尔曲线插值
     * 
     * <p>使用 4 个控制点：P0(0,0), P1(x1,y1), P2(x2,y2), P3(1,1)
     * <p>参数格式：[x1, y1, x2, y2]
     * 
     * @param t 时间进度 [0.0, 1.0]
     * @param controlPoints 控制点数组 [x1, y1, x2, y2]
     * @return 插值结果
     */
    private static float bezier(float t, float[] controlPoints) {
        if (controlPoints == null || controlPoints.length < 4) {
            // 降级为线性插值
            return t;
        }
        
        float x1 = controlPoints[0];
        float y1 = controlPoints[1];
        float x2 = controlPoints[2];
        float y2 = controlPoints[3];
        
        // 使用牛顿迭代法求解 t 对应的 x
        // 这里使用简化的近似算法以提高性能
        
        // 对于标准的缓动曲线，可以直接计算
        // 三次贝塞尔曲线: B(t) = (1-t)³P0 + 3(1-t)²tP1 + 3(1-t)t²P2 + t³P3
        
        // 简化版：直接使用 t 作为参数（假设 x 单调递增）
        float u = t;
        float v = 1.0f - u;
        
        // 计算 y 值
        // P0 = (0, 0), P3 = (1, 1)
        // y = 3v²u*y1 + 3v*u²*y2 + u³
        float y = 3.0f * v * v * u * y1 +
                  3.0f * v * u * u * y2 +
                  u * u * u;
        
        return clamp(y, 0.0f, 1.0f);
    }
    
    /**
     * 弹簧效果 - 阻尼振荡
     * 
     * <p>f(t) = 1 - e^(-damping*t) * cos(frequency * 2π * t)
     * 
     * @param t 时间进度 [0.0, 1.0]
     * @param params [damping, frequency] 或 null（使用默认值）
     * @return 插值结果（可能超出 [0,1] 范围）
     */
    private static float spring(float t, float[] params) {
        float damping = SPRING_DAMPING;
        float frequency = SPRING_FREQUENCY;
        
        if (params != null && params.length >= 2) {
            damping = params[0];
            frequency = params[1];
        }
        
        // 阻尼振荡公式
        float envelope = (float) Math.exp(-damping * t);
        float oscillation = (float) Math.cos(frequency * 2.0f * PI * t);
        
        return 1.0f - envelope * oscillation;
    }
    
    /**
     * 弹跳效果 - 多次回弹
     * 
     * <p>模拟球落地的弹跳效果
     * 
     * @param t 时间进度 [0.0, 1.0]
     * @return 插值结果 [0.0, 1.0]
     */
    private static float bounce(float t) {
        // 反向时间（从1到0），因为弹跳是从下往上
        t = 1.0f - t;
        
        // 分段弹跳函数
        if (t < 1.0f / 2.75f) {
            return 1.0f - 7.5625f * t * t;
        } else if (t < 2.0f / 2.75f) {
            t -= 1.5f / 2.75f;
            return 1.0f - (7.5625f * t * t + 0.75f);
        } else if (t < 2.5f / 2.75f) {
            t -= 2.25f / 2.75f;
            return 1.0f - (7.5625f * t * t + 0.9375f);
        } else {
            t -= 2.625f / 2.75f;
            return 1.0f - (7.5625f * t * t + 0.984375f);
        }
    }
    
    // ==================== 高级插值方法 ====================
    
    /**
     * 三次厄米特插值（Cubic Hermite）
     * 
     * <p>使用切线信息进行平滑插值
     * 
     * @param p0 起始值
     * @param p1 结束值
     * @param m0 起始切线
     * @param m1 结束切线
     * @param t 时间进度 [0.0, 1.0]
     * @return 插值结果
     */
    public static float cubicHermite(float p0, float p1, float m0, float m1, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        
        float h00 = 2.0f * t3 - 3.0f * t2 + 1.0f;
        float h10 = t3 - 2.0f * t2 + t;
        float h01 = -2.0f * t3 + 3.0f * t2;
        float h11 = t3 - t2;
        
        return h00 * p0 + h10 * m0 + h01 * p1 + h11 * m1;
    }
    
    /**
     * Catmull-Rom 样条插值
     * 
     * <p>通过4个点计算平滑曲线，自动计算切线
     * 
     * @param p0 前一个点
     * @param p1 当前起始点
     * @param p2 当前结束点
     * @param p3 后一个点
     * @param t 时间进度 [0.0, 1.0]
     * @return 插值结果
     */
    public static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        // 自动计算切线（使用相邻点的差值）
        float m1 = (p2 - p0) * 0.5f;
        float m2 = (p3 - p1) * 0.5f;
        
        return cubicHermite(p1, p2, m1, m2, t);
    }
    
    /**
     * 余弦插值 - 更平滑的过渡
     * 
     * @param start 起始值
     * @param end 结束值
     * @param t 时间进度 [0.0, 1.0]
     * @return 插值结果
     */
    public static float cosineInterpolate(float start, float end, float t) {
        float mu2 = (1.0f - (float) Math.cos(t * PI)) / 2.0f;
        return start * (1.0f - mu2) + end * mu2;
    }
    
    // ==================== 数组插值方法 ====================
    
    /**
     * 对浮点数组进行插值
     * 
     * @param start 起始数组
     * @param end 结束数组
     * @param t 时间进度 [0.0, 1.0]
     * @param type 插值类型
     * @return 插值结果数组
     */
    public static float[] interpolateArray(float[] start, float[] end, float t, InterpolationType type) {
        if (start == null || end == null) return null;
        
        int length = Math.min(start.length, end.length);
        float[] result = new float[length];
        
        for (int i = 0; i < length; i++) {
            result[i] = lerp(start[i], end[i], t, type);
        }
        
        return result;
    }
    
    /**
     * 对浮点数组进行插值（带自定义参数）
     * 
     * @param start 起始数组
     * @param end 结束数组
     * @param t 时间进度 [0.0, 1.0]
     * @param type 插值类型
     * @param params 自定义参数
     * @return 插值结果数组
     */
    public static float[] interpolateArray(float[] start, float[] end, float t, InterpolationType type, float[] params) {
        if (start == null || end == null) return null;
        
        int length = Math.min(start.length, end.length);
        float[] result = new float[length];
        
        for (int i = 0; i < length; i++) {
            result[i] = lerp(start[i], end[i], t, type, params);
        }
        
        return result;
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 限制值在指定范围内
     * 
     * @param value 输入值
     * @param min 最小值
     * @param max 最大值
     * @return 限制后的值
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * 计算两个值的平滑过渡（使用 smoothstep）
     * 
     * @param edge0 下边界
     * @param edge1 上边界
     * @param x 输入值
     * @return 平滑过渡值 [0.0, 1.0]
     */
    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
    
    /**
     * 计算两个值的更平滑过渡（使用 smootherstep）
     * 
     * @param edge0 下边界
     * @param edge1 上边界
     * @param x 输入值
     * @return 更平滑的过渡值 [0.0, 1.0]
     */
    public static float smootherstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }
    
    /**
     * 验证贝塞尔控制点的有效性
     * 
     * @param controlPoints 控制点数组
     * @return true 如果控制点有效
     */
    public static boolean validateBezierControlPoints(float[] controlPoints) {
        if (controlPoints == null || controlPoints.length != 4) {
            return false;
        }
        
        // 检查 x 值是否在 [0, 1] 范围内
        float x1 = controlPoints[0];
        float x2 = controlPoints[2];
        
        return x1 >= 0.0f && x1 <= 1.0f && x2 >= 0.0f && x2 <= 1.0f;
    }
    
    /**
     * 创建预设的贝塞尔曲线控制点
     * 
     * @param preset 预设名称（ease-in, ease-out, ease-in-out等）
     * @return 控制点数组 [x1, y1, x2, y2]
     */
    public static float[] getBezierPreset(String preset) {
        return switch (preset.toLowerCase()) {
            case "ease-in" -> new float[]{0.42f, 0.0f, 1.0f, 1.0f};
            case "ease-out" -> new float[]{0.0f, 0.0f, 0.58f, 1.0f};
            case "ease-in-out" -> new float[]{0.42f, 0.0f, 0.58f, 1.0f};
            case "ease" -> new float[]{0.25f, 0.1f, 0.25f, 1.0f};
            case "linear" -> new float[]{0.0f, 0.0f, 1.0f, 1.0f};
            case "ease-in-quad" -> new float[]{0.55f, 0.085f, 0.68f, 0.53f};
            case "ease-out-quad" -> new float[]{0.25f, 0.46f, 0.45f, 0.94f};
            case "ease-in-cubic" -> new float[]{0.55f, 0.055f, 0.675f, 0.19f};
            case "ease-out-cubic" -> new float[]{0.215f, 0.61f, 0.355f, 1.0f};
            default -> new float[]{0.25f, 0.1f, 0.25f, 1.0f};
        };
    }
}

