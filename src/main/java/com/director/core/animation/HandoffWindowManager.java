package com.director.core.animation;

import com.director.core.animation.ClipAnimationConfig.AnimationPhase;
import com.director.core.container.visibility.BlockVisualStateManager;
import net.minecraft.client.util.math.MatrixStack;

/**
 * 交接窗口管理器
 *
 * <p>负责处理动画边界处的"交接窗口"逻辑，解决区块重建延迟导致的视觉问题。
 *
 * <h3>问题背景</h3>
 * 当方块动画结束时，需要从"覆盖层渲染"切换到"真实世界方块渲染"。
 * 但区块重建有延迟（100-200ms），在这段时间内：
 * <ul>
 *   <li>覆盖层已经消失</li>
 *   <li>真实方块尚未显示</li>
 *   <li>结果：出现黑影、空隙、Z-fighting 等视觉瑕疵</li>
 * </ul>
 *
 * <h3>解决方案</h3>
 * 引入"交接窗口"（Handoff Window），在动画边界附近：
 * <ul>
 *   <li>ENTER 阶段：动画结束前后一小段时间，强制渲染覆盖层遮盖底下的瑕疵</li>
 *   <li>EXIT 阶段：动画开始前后一小段时间，使用微放大的覆盖层覆盖边界缝隙</li>
 *   <li>覆盖层使用微调的缩放和透明度，确保视觉平滑过渡</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class HandoffWindowManager {

    private HandoffWindowManager() {
        // 工具类，禁止实例化
    }

    /**
     * 判断方块是否处于 ENTER 阶段的交接窗口内
     *
     * @param currentTime    当前时间线时间
     * @param blockStartTime 方块动画开始时间
     * @param blockEndTime   方块动画结束时间
     * @return true 如果在交接窗口内
     */
    public static boolean isInEnterHandoffWindow(float currentTime, float blockStartTime, float blockEndTime) {
        // 交接窗口：[blockEndTime - PRE_WINDOW, blockEndTime + POST_WINDOW]
        float windowStart = blockEndTime - AnimationConstants.HANDOFF_PRE_WINDOW;
        float windowEnd = blockEndTime + AnimationConstants.HANDOFF_POST_WINDOW;
        return currentTime >= windowStart && currentTime < windowEnd;
    }

    /**
     * 判断方块是否处于 EXIT 阶段的交接窗口内
     *
     * @param currentTime    当前时间线时间
     * @param blockStartTime 方块动画开始时间
     * @param blockEndTime   方块动画结束时间
     * @return true 如果在交接窗口内
     */
    public static boolean isInExitHandoffWindow(float currentTime, float blockStartTime, float blockEndTime) {
        // 交接窗口：[blockStartTime - POST_WINDOW, blockStartTime + PRE_WINDOW]
        float windowStart = blockStartTime - AnimationConstants.HANDOFF_POST_WINDOW;
        float windowEnd = blockStartTime + AnimationConstants.HANDOFF_PRE_WINDOW;
        return currentTime >= windowStart && currentTime < windowEnd;
    }

    /**
     * 判断方块是否处于交接窗口内（根据阶段自动选择）
     *
     * @param currentTime    当前时间线时间
     * @param blockStartTime 方块动画开始时间
     * @param blockEndTime   方块动画结束时间
     * @param phase          动画阶段
     * @return true 如果在交接窗口内
     */
    public static boolean isInHandoffWindow(float currentTime, float blockStartTime, float blockEndTime,
                                           AnimationPhase phase) {
        if (phase == AnimationPhase.EXIT) {
            return isInExitHandoffWindow(currentTime, blockStartTime, blockEndTime);
        } else {
            return isInEnterHandoffWindow(currentTime, blockStartTime, blockEndTime);
        }
    }

    /**
     * 应用交接窗口修正到 snapshot
     *
     * <p>在交接窗口内，强制修正 snapshot 的状态：
     * <ul>
     *   <li>Alpha 设为 1.0（完全不透明）</li>
     *   <li>应用微调的缩放（ENTER 微缩小，EXIT 微放大）</li>
     *   <li>标记为完全可见</li>
     * </ul>
     *
     * @param snapshot 要修正的 snapshot
     * @param phase    动画阶段
     */
    public static void applyHandoffCorrection(BlockAnimationSnapshot snapshot, AnimationPhase phase) {
        if (snapshot == null) {
            return;
        }

        // 1. 强制完全显示（Alpha 1.0），遮挡内部瑕疵
        snapshot.setAlpha(1.0f);
        snapshot.markFullyVisible();

        // 2. 应用微调缩放
        // ENTER：微缩小（0.99）避免与真实方块重叠导致发黑
        // EXIT：微放大（1.002）覆盖边界缝隙
        float scale = (phase == AnimationPhase.EXIT)
            ? AnimationConstants.HANDOFF_EXIT_SCALE
            : AnimationConstants.HANDOFF_ENTER_SCALE;

        // 必须从中心缩放：translate(0.5) -> scale -> translate(-0.5)
        MatrixStack ms = new MatrixStack();
        ms.translate(0.5, 0.5, 0.5);
        ms.scale(scale, scale, scale);
        ms.translate(-0.5, -0.5, -0.5);
        snapshot.setTransform(ms);
    }

    /**
     * 创建交接窗口的视觉状态
     *
     * <p>交接窗口期间，方块需要特殊的视觉状态：
     * <ul>
     *   <li>GHOST_WITH_DEPTH：允许覆盖层渲染，但不抑制世界渲染</li>
     *   <li>Alpha 1.0：完全不透明</li>
     *   <li>DepthWrite true：确保覆盖层能遮挡其他透明物体</li>
     *   <li>SuppressWorldRendering false：允许区块重建正常进行</li>
     * </ul>
     *
     * @return 交接窗口专用的视觉状态
     */
    public static BlockVisualStateManager.BlockVisualState createHandoffVisualState() {
        // GHOST_WITH_DEPTH + alpha=1.0 + depthWrite=true 视觉效果等同实体方块
        // 但 suppressWorldRendering=false 允许区块重建继续进行
        return BlockVisualStateManager.BlockVisualState.ghost(1.0f, true, false);
    }
}
