package com.director.core.animation;

/**
 * 动画系统常量定义
 *
 * <p>集中管理动画相关的魔法数字，便于维护和调整。
 */
public final class AnimationConstants {

    private AnimationConstants() {
        // 工具类，禁止实例化
    }

    // ==================== 交接窗口相关常量 ====================

    /**
     * 交接窗口：ENTER 阶段动画结束后继续渲染覆盖层的时长（秒）
     *
     * <p>用于掩盖区块重建延迟导致的黑影/空隙。
     * 该值应该略大于区块重建的平均延迟（通常为 100-200ms）。
     */
    public static final float HANDOFF_POST_WINDOW = 0.3f;

    /**
     * 交接窗口：ENTER 阶段动画结束前开始准备覆盖层的时长（秒）
     *
     * <p>提前开始渲染覆盖层，确保在动画结束瞬间无缝切换。
     */
    public static final float HANDOFF_PRE_WINDOW = 0.2f;

    /**
     * EXIT 阶段交接窗口的方块缩放因子（微放大以覆盖缝隙）
     *
     * <p>在 EXIT 动画边界处，方块需要微放大（1.002 = 0.2% 放大）
     * 以覆盖底下真实方块与覆盖层之间的细微缝隙，避免 Z-fighting。
     */
    public static final float HANDOFF_EXIT_SCALE = 1.002f;

    /**
     * ENTER 阶段交接窗口的方块缩放因子（微缩小以避免重叠）
     *
     * <p>在 ENTER 动画末段，方块需要微缩小（0.99 = 1% 缩小）
     * 以避免与刚出现的真实方块重叠导致瞬时发黑。
     */
    public static final float HANDOFF_ENTER_SCALE = 0.99f;

    // ==================== 性能相关常量 ====================

    /**
     * 区块重渲染的最大方块数量阈值
     *
     * <p>单帧重渲染方块数量超过此阈值时，将跳过或分批处理，
     * 避免一次性重建大量区块导致游戏卡顿。
     */
    public static final int MAX_RERENDER_BLOCKS = 10000;

    /**
     * 缓存清理周期（帧数）
     *
     * <p>每隔此帧数清理一次过期的缓存条目，防止内存泄漏。
     */
    public static final int CACHE_CLEANUP_INTERVAL = 60;

    /**
     * 性能日志输出间隔（毫秒）
     *
     * <p>性能统计日志的最小输出间隔，避免日志刷屏。
     */
    public static final long PERF_LOG_INTERVAL_MS = 5000L;

    // ==================== 浮点比较容差 ====================

    /**
     * 时间比较的最小容差（秒）
     *
     * <p>用于浮点时间的相等判断，避免浮点精度误差。
     */
    public static final float TIME_EPSILON = 1e-4f;

    /**
     * 归一化时间比较的容差
     *
     * <p>用于 [0..1] 范围内归一化时间的比较。
     */
    public static final float NORMALIZED_TIME_EPSILON = 1e-4f;

    /**
     * 可见方块缓存的时间容差（秒）
     *
     * <p>在静止预览模式下，时间变化小于此值时可以复用缓存结果。
     * 该值略大于 TIME_EPSILON，以提高缓存命中率。
     */
    public static final float VISIBLE_BLOCKS_CACHE_TIME_EPSILON = 1e-3f;
}
