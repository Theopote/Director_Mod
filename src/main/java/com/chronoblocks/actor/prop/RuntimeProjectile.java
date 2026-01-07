package com.chronoblocks.actor.prop;

import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RuntimeProjectile v1.0
 *
 * <p>运行时投掷物：一次投掷的实例
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 临时舞台对象（不属于 Actor）</li>
 *   <li>✅ 纯客户端视觉对象</li>
 *   <li>✅ 抛物线运动（时间函数）</li>
 *   <li>❌ 不继承 Entity</li>
 *   <li>❌ 不影响世界</li>
 * </ul>
 *
 * <p>核心特点：
 * <ul>
 *   <li>✅ 时间函数：time → position</li>
 *   <li>✅ 抛物线插值（sin 函数）</li>
 *   <li>✅ 完成后自动清理</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class RuntimeProjectile {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeProjectile.class);

    /** 道具实例 */
    public final RuntimeProp prop;

    /** 起始位置 */
    public final Vec3d startPos;

    /** 目标位置 */
    public final Vec3d targetPos;

    /** 抛物线高度 */
    public final float arcHeight;

    /** 飞行时长（秒） */
    public final float duration;

    /** 已飞行时间 */
    private float time = 0f;

    /** 是否已完成 */
    private boolean finished = false;

    /**
     * 构造函数
     *
     * @param prop 道具实例
     * @param startPos 起始位置
     * @param targetPos 目标位置
     * @param arcHeight 抛物线高度
     * @param duration 飞行时长（秒）
     */
    public RuntimeProjectile(
            RuntimeProp prop,
            Vec3d startPos,
            Vec3d targetPos,
            float arcHeight,
            float duration
    ) {
        this.prop = prop;
        this.startPos = startPos;
        this.targetPos = targetPos;
        this.arcHeight = Math.max(0f, arcHeight);
        this.duration = Math.max(0.01f, duration);
        LOGGER.debug("[RuntimeProjectile] 创建投掷物: {} -> ({}, {}, {})",
                prop.getDescriptor().getId(), targetPos.x, targetPos.y, targetPos.z);
    }

    // =========================================================
    // Update
    // =========================================================

    /**
     * 更新投掷物（每帧调用）
     *
     * @param deltaTime 时间增量（秒）
     */
    public void update(float deltaTime) {
        if (finished) {
            return;
        }

        time += deltaTime;
        float t = Math.min(time / duration, 1.0f);

        // 抛物线插值
        Vec3d pos = interpolateArc(startPos, targetPos, t, arcHeight);

        // 更新道具位置
        prop.setWorldTransform(pos);

        if (t >= 1.0f) {
            finished = true;
            prop.dispose(); // 视觉消失
            LOGGER.debug("[RuntimeProjectile] 投掷物完成: {}", prop.getDescriptor().getId());
        }
    }

    /**
     * 检查是否已完成
     *
     * @return 如果已完成则返回 true
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * 获取当前进度 [0-1]
     *
     * @return 进度值
     */
    public float getProgress() {
        return Math.min(time / duration, 1.0f);
    }

    // =========================================================
    // Parabolic Interpolation
    // =========================================================

    /**
     * 抛物线插值
     * <p>使用 sin 函数创建平滑的抛物线轨迹
     *
     * @param a 起始位置
     * @param b 目标位置
     * @param t 进度 [0-1]
     * @param height 抛物线高度
     * @return 插值后的位置
     */
    private Vec3d interpolateArc(
            Vec3d a,
            Vec3d b,
            float t,
            float height
    ) {
        // 线性插值（水平方向）
        Vec3d linear = a.lerp(b, t);

        // 抛物线高度（sin 函数，在 t=0.5 时达到最高点）
        double yOffset = Math.sin(t * Math.PI) * height;

        return linear.add(0, yOffset, 0);
    }
}

