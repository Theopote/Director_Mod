package com.chronoblocks.ui.adapters.animation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 🎞 PropertyAnimationInterpolator - 属性动画插值器
 * --------------------------------------------
 * 为 ChronoBlocks 提供平滑的属性过渡动画支持。
 *
 * <h3>✨ 核心功能：</h3>
 * <ul>
 *     <li>🎯 类型安全的插值：自动识别数值/向量/颜色类型</li>
 *     <li>💫 丰富的缓动函数：LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, SMOOTHSTEP, SPRING</li>
 *     <li>🔄 可扩展的插值器：支持注册自定义类型插值器</li>
 *     <li>🎬 关键帧过渡：支持多关键帧的平滑过渡</li>
 *     <li>🧵 线程安全：支持并发插值计算</li>
 *     <li>🔌 系统集成：与 PropertyChangeScheduler 完美协同</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * // 1. 简单插值（线性）
 * Float result = PropertyAnimationInterpolator.get().interpolate(
 *     0.0f, 1.0f, 0.5f, Easing.LINEAR);
 * // 结果: 0.5f
 *
 * // 2. 缓动插值（平滑）
 * Float smooth = PropertyAnimationInterpolator.get().interpolate(
 *     0.0f, 100.0f, 0.5f, Easing.EASE_IN_OUT);
 * // 结果: ~50.0f (平滑过渡)
 *
 * // 3. 关键帧过渡
 * List<Keyframe<Float>> keyframes = Arrays.asList(
 *     new Keyframe<>(0.0f, 0.0f, Easing.EASE_IN),
 *     new Keyframe<>(0.5f, 5.0f, Easing.EASE_OUT),
 *     new Keyframe<>(1.0f, 10.0f, Easing.EASE_IN_OUT)
 * );
 * Float value = PropertyAnimationInterpolator.get().interpolateKeyframes(keyframes, 0.75f);
 *
 * // 4. 自定义类型插值
 * PropertyAnimationInterpolator.get().register(MyType.class, (a, b, t) -> {
 *     return new MyType(a.getValue() + (b.getValue() - a.getValue()) * t);
 * });
 * }</pre>
 *
 * <h3>🎯 应用场景：</h3>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>场景</th>
 *     <th>说明</th>
 *     <th>推荐缓动</th>
 *   </tr>
 *   <tr>
 *     <td>相机系统</td>
 *     <td>FOV、焦距、视角的平滑插值</td>
 *     <td>EASE_IN_OUT</td>
 *   </tr>
 *   <tr>
 *     <td>时间线轨道</td>
 *     <td>关键帧过渡动画</td>
 *     <td>多种混合</td>
 *   </tr>
 *   <tr>
 *     <td>后期特效</td>
 *     <td>Bloom、曝光等平滑变化</td>
 *     <td>SMOOTHSTEP</td>
 *   </tr>
 *   <tr>
 *     <td>容器动画</td>
 *     <td>平滑移动、缩放、旋转</td>
 *     <td>SPRING</td>
 *   </tr>
 *   <tr>
 *     <td>物理环境</td>
 *     <td>重力、风场平滑调整</td>
 *     <td>LINEAR</td>
 *   </tr>
 * </table>
 *
 * @author ChronoBlocks Team
 * @version 4.7
 * @since 4.7
 */
public class PropertyAnimationInterpolator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyAnimationInterpolator.class);

    // ==================== 单例实例 ====================

    private static final PropertyAnimationInterpolator INSTANCE = new PropertyAnimationInterpolator();

    /**
     * 获取单例实例
     *
     * @return 插值器实例
     */
    public static PropertyAnimationInterpolator get() {
        return INSTANCE;
    }

    // ==================== 插值器注册表 ====================

    /** 类型插值器映射表（线程安全） */
    private final Map<Class<?>, Interpolator<?>> typeInterpolators = new ConcurrentHashMap<>();

    /** 是否启用调试日志 */
    private volatile boolean debug = false;

    /**
     * 私有构造函数
     */
    private PropertyAnimationInterpolator() {
        registerDefaults();
        this.debug = Boolean.getBoolean("chronoblocks.debug.interpolator");
    }

    // ==================== 核心插值接口 ====================

    /**
     * 执行插值计算
     *
     * @param <T>    值类型
     * @param from   起始值
     * @param to     目标值
     * @param t      插值因子 [0, 1]
     * @param easing 缓动函数
     * @return 插值结果
     */
    @SuppressWarnings("unchecked")
    public <T> T interpolate(T from, T to, float t, Easing easing) {
        if (from == null || to == null) {
            if (debug) {
                LOGGER.trace("[PropertyAnimationInterpolator] Null value, returning target");
            }
            return to;
        }

        if (easing == null) {
            easing = Easing.LINEAR;
        }

        // 查找插值器
        Interpolator<T> interpolator = (Interpolator<T>) typeInterpolators.get(from.getClass());

        // 如果没有找到，尝试数值类型
        if (interpolator == null && from instanceof Number && to instanceof Number) {
            interpolator = (Interpolator<T>) NUMERIC_INTERPOLATOR;
        }

        // 仍然没有找到，警告并返回目标值
        if (interpolator == null) {
            if (debug) {
                LOGGER.warn("[PropertyAnimationInterpolator] No interpolator for type {}", from.getClass().getSimpleName());
            }
            return to;
        }

        // 应用缓动
        float easedT = easing.apply(Math.max(0, Math.min(1, t)));

        // 执行插值
        try {
            T result = interpolator.interpolate(from, to, easedT);

            if (debug) {
                LOGGER.trace("[PropertyAnimationInterpolator] {} -> {} (t={}, eased={}, easing={}) = {}",
                        from, to, t, easedT, easing, result);
            }

            return result;

        } catch (Exception e) {
            LOGGER.error("[PropertyAnimationInterpolator] Interpolation failed for type {}", from.getClass().getSimpleName(), e);
            return to;
        }
    }

    /**
     * 执行插值计算（使用默认线性缓动）
     *
     * @param <T>  值类型
     * @param from 起始值
     * @param to   目标值
     * @param t    插值因子 [0, 1]
     * @return 插值结果
     */
    public <T> T interpolate(T from, T to, float t) {
        return interpolate(from, to, t, Easing.LINEAR);
    }

    // ==================== 插值器注册 ====================

    /**
     * 注册自定义类型的插值器
     *
     * @param <T>          类型
     * @param type         类型Class
     * @param interpolator 插值器实现
     */
    public <T> void register(Class<T> type, Interpolator<T> interpolator) {
        if (type == null || interpolator == null) {
            throw new IllegalArgumentException("Type and interpolator cannot be null");
        }

        typeInterpolators.put(type, interpolator);
        LOGGER.debug("[PropertyAnimationInterpolator] 注册插值器: {}", type.getSimpleName());
    }

    /**
     * 取消注册插值器
     *
     * @param type 类型Class
     */
    public void unregister(Class<?> type) {
        if (typeInterpolators.remove(type) != null) {
            LOGGER.debug("[PropertyAnimationInterpolator] 取消注册插值器: {}", type.getSimpleName());
        }
    }

    /**
     * 检查是否已注册插值器
     *
     * @param type 类型Class
     * @return true 如果已注册
     */
    public boolean hasInterpolator(Class<?> type) {
        return typeInterpolators.containsKey(type);
    }

    /**
     * 注册默认插值器
     */
    private void registerDefaults() {
        // 基础数值类型
        register(Float.class, (a, b, t) -> a + (b - a) * t);
        register(Double.class, (a, b, t) -> a + (b - a) * t);
        register(Integer.class, (a, b, t) -> (int) Math.round(a + (b - a) * t));
        register(Long.class, (a, b, t) -> (long) Math.round(a + (b - a) * t));
        register(Short.class, (a, b, t) -> (short) Math.round(a + (b - a) * t));
        register(Byte.class, (a, b, t) -> (byte) Math.round(a + (b - a) * t));

        // 尝试注册Vector3f（JOML库）
        tryRegisterVector3f();

        // 颜色插值（RGB整数）
        registerColorInterpolator();

        LOGGER.info("[PropertyAnimationInterpolator] 默认插值器已注册");
    }

    /**
     * 尝试注册Vector3f插值器
     */
    private void tryRegisterVector3f() {
        try {
            @SuppressWarnings("unchecked")
            Class<Object> vec3Class = (Class<Object>) Class.forName("org.joml.Vector3f");

            Interpolator<Object> vec3Interpolator = (a, b, t) -> {
                try {
                    // 获取分量
                    float ax = (float) vec3Class.getMethod("x").invoke(a);
                    float ay = (float) vec3Class.getMethod("y").invoke(a);
                    float az = (float) vec3Class.getMethod("z").invoke(a);

                    float bx = (float) vec3Class.getMethod("x").invoke(b);
                    float by = (float) vec3Class.getMethod("y").invoke(b);
                    float bz = (float) vec3Class.getMethod("z").invoke(b);

                    // 插值
                    float rx = ax + (bx - ax) * t;
                    float ry = ay + (by - ay) * t;
                    float rz = az + (bz - az) * t;

                    // 创建新向量
                    return vec3Class.getConstructor(float.class, float.class, float.class)
                            .newInstance(rx, ry, rz);

                } catch (Exception e) {
                    LOGGER.error("[PropertyAnimationInterpolator] Vector3f 插值失败", e);
                    return b;
                }
            };

            register(vec3Class, vec3Interpolator);
            LOGGER.debug("[PropertyAnimationInterpolator] Vector3f 插值器已注册");

        } catch (ClassNotFoundException e) {
            // JOML库不可用，跳过
            if (debug) {
                LOGGER.trace("[PropertyAnimationInterpolator] Vector3f 不可用，跳过注册");
            }
        }
    }

    /**
     * 注册颜色插值器
     */
    private void registerColorInterpolator() {
        // RGB整数颜色插值（格式：0xRRGGBB）
        Interpolator<Integer> colorInterpolator = (a, b, t) -> {
            int r1 = (a >> 16) & 0xFF;
            int g1 = (a >> 8) & 0xFF;
            int b1 = a & 0xFF;

            int r2 = (b >> 16) & 0xFF;
            int g2 = (b >> 8) & 0xFF;
            int b2 = b & 0xFF;

            int r = (int) Math.round(r1 + (r2 - r1) * t);
            int g = (int) Math.round(g1 + (g2 - g1) * t);
            int bb = (int) Math.round(b1 + (b2 - b1) * t);

            // 钳制到[0, 255]
            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            bb = Math.max(0, Math.min(255, bb));

            return (r << 16) | (g << 8) | bb;
        };

        // 注意：Integer已被注册为数值插值，这里覆盖为颜色插值
        // 如果需要区分，可以创建Color类
        // register(Integer.class, colorInterpolator);
    }

    // ==================== 关键帧插值 ====================

    /**
     * 根据关键帧列表插值
     *
     * @param <T>       值类型
     * @param keyframes 关键帧列表（必须按时间排序）
     * @param t         当前时间
     * @return 插值结果
     */
    public <T> T interpolateKeyframes(List<Keyframe<T>> keyframes, float t) {
        if (keyframes == null || keyframes.isEmpty()) {
            return null;
        }

        // 只有一个关键帧
        if (keyframes.size() == 1) {
            return keyframes.get(0).value;
        }

        // 在第一个关键帧之前
        if (t <= keyframes.get(0).time) {
            return keyframes.get(0).value;
        }

        // 在最后一个关键帧之后
        if (t >= keyframes.get(keyframes.size() - 1).time) {
            return keyframes.get(keyframes.size() - 1).value;
        }

        // 查找t所在的区间
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe<T> current = keyframes.get(i);
            Keyframe<T> next = keyframes.get(i + 1);

            if (t >= current.time && t <= next.time) {
                // 计算局部插值因子
                float duration = next.time - current.time;
                float localT = duration > 0 ? (t - current.time) / duration : 1.0f;

                // 使用下一个关键帧的缓动函数
                Easing easing = next.easing != null ? next.easing : Easing.LINEAR;

                // 执行插值
                return interpolate(current.value, next.value, localT, easing);
            }
        }

        // 理论上不会到达这里
        return keyframes.get(keyframes.size() - 1).value;
    }

    /**
     * 根据关键帧数组插值
     *
     * @param <T>       值类型
     * @param keyframes 关键帧数组
     * @param t         当前时间
     * @return 插值结果
     */
    @SafeVarargs
    public final <T> T interpolateKeyframes(float t, Keyframe<T>... keyframes) {
        return interpolateKeyframes(Arrays.asList(keyframes), t);
    }

    // ==================== 配置接口 ====================

    /**
     * 设置调试模式
     *
     * @param enabled true 启用，false 禁用
     */
    public void setDebug(boolean enabled) {
        this.debug = enabled;
        LOGGER.info("[PropertyAnimationInterpolator] 调试模式: {}", enabled ? "开启" : "关闭");
    }

    /**
     * 获取调试模式状态
     *
     * @return true 如果启用调试
     */
    public boolean isDebugMode() {
        return debug;
    }

    /**
     * 获取已注册的插值器数量
     *
     * @return 插值器数量
     */
    public int getInterpolatorCount() {
        return typeInterpolators.size();
    }

    // ==================== 插值器接口 ====================

    /**
     * 插值器接口
     *
     * @param <T> 值类型
     */
    @FunctionalInterface
    public interface Interpolator<T> {
        /**
         * 执行插值
         *
         * @param from 起始值
         * @param to   目标值
         * @param t    插值因子 [0, 1]
         * @return 插值结果
         */
        T interpolate(T from, T to, float t);
    }

    /**
     * 数值插值器（通用）
     */
    private static final Interpolator<Number> NUMERIC_INTERPOLATOR = (a, b, t) -> {
        double result = a.doubleValue() + (b.doubleValue() - a.doubleValue()) * t;

        // 保持原始类型
        if (a instanceof Float || b instanceof Float) {
            return (float) result;
        } else if (a instanceof Integer || b instanceof Integer) {
            return (int) Math.round(result);
        } else if (a instanceof Long || b instanceof Long) {
            return Math.round(result);
        } else {
            return result;
        }
    };

    // ==================== 缓动函数枚举 ====================

    /**
     * 缓动函数枚举
     * <p>定义了多种常用的缓动曲线。
     */
    public enum Easing {
        /** 线性：匀速运动 */
        LINEAR(t -> t),

        /** 缓入：从慢到快 */
        EASE_IN(t -> t * t),

        /** 缓出：从快到慢 */
        EASE_OUT(t -> 1 - (1 - t) * (1 - t)),

        /** 缓入缓出：两端慢，中间快 */
        EASE_IN_OUT(t -> t < 0.5f ?
                2 * t * t :
                (float) (1 - Math.pow(-2 * t + 2, 2) / 2)),

        /** 平滑步：S曲线 */
        SMOOTHSTEP(t -> t * t * (3 - 2 * t)),

        /** 三次缓入：更强的缓入效果 */
        EASE_IN_CUBIC(t -> t * t * t),

        /** 三次缓出：更强的缓出效果 */
        EASE_OUT_CUBIC(t -> (float) (1 - Math.pow(1 - t, 3))),

        /** 三次缓入缓出 */
        EASE_IN_OUT_CUBIC(t -> t < 0.5f ?
                4 * t * t * t :
                (float) (1 - Math.pow(-2 * t + 2, 3) / 2)),

        /** 弹性：带回弹效果 */
        SPRING(t -> {
            double f = Math.sin(t * Math.PI * (0.2 + 2.5 * t * t * t)) *
                      Math.pow(1 - t, 2.2) + t;
            return (float) (f * (1.0 + 1.2 * (1 - t)));
        }),

        /** 弹跳：物理弹跳效果 */
        BOUNCE(t -> {
            if (t < 1 / 2.75) {
                return 7.5625f * t * t;
            } else if (t < 2 / 2.75) {
                t -= 1.5f / 2.75f;
                return 7.5625f * t * t + 0.75f;
            } else if (t < 2.5 / 2.75) {
                t -= 2.25f / 2.75f;
                return 7.5625f * t * t + 0.9375f;
            } else {
                t -= 2.625f / 2.75f;
                return 7.5625f * t * t + 0.984375f;
            }
        }),

        /** 指数缓入：急速加快 */
        EASE_IN_EXPO(t -> t == 0 ? 0 : (float) Math.pow(2, 10 * (t - 1))),

        /** 指数缓出：急速减慢 */
        EASE_OUT_EXPO(t -> t == 1 ? 1 : (float) (1 - Math.pow(2, -10 * t)));

        private final Function<Float, Float> function;

        Easing(Function<Float, Float> function) {
            this.function = function;
        }

        /**
         * 应用缓动函数
         *
         * @param t 输入值 [0, 1]
         * @return 输出值 [0, 1]
         */
        public float apply(float t) {
            return clamp(function.apply(t));
        }

        /**
         * 钳制到[0, 1]
         */
        private static float clamp(float value) {
            return Math.max(0, Math.min(1, value));
        }
    }

    // ==================== 关键帧类 ====================

    /**
     * 关键帧
     *
     * @param <T> 值类型
     */
    public static class Keyframe<T> {
        /** 时间点 */
        public final float time;

        /** 值 */
        public final T value;

        /** 缓动函数 */
        public final Easing easing;

        /**
         * 创建关键帧
         *
         * @param time   时间点
         * @param value  值
         * @param easing 缓动函数
         */
        public Keyframe(float time, T value, Easing easing) {
            this.time = time;
            this.value = value;
            this.easing = easing != null ? easing : Easing.LINEAR;
        }

        /**
         * 创建关键帧（使用线性缓动）
         *
         * @param time  时间点
         * @param value 值
         */
        public Keyframe(float time, T value) {
            this(time, value, Easing.LINEAR);
        }

        @Override
        public String toString() {
            return String.format("Keyframe{time=%.2f, value=%s, easing=%s}",
                    time, value, easing);
        }
    }
}

