package com.director.ui.timeline.interaction;

import com.director.actor.action.ActorActionType;

/**
 * ActionBrushState v1.0
 *
 * <p>Action Brush 全局轻量状态（当前绘制动作）。
 *
 * <p>设计目标：
 * <ul>
 *   <li>导演在时间线上"拖一段"，就等于在说："这段时间他在 RUN / DANCE / CARRY"</li>
 *   <li>而不是：新建 Clip → 设开始 → 设结束 → 设动作</li>
 * </ul>
 *
 * <p>这个 Brush 是"当前绘制动作"，不是 Clip。
 * <p>导演选择动作（Brush），然后在 Timeline 中拖拽一段，系统自动创建 ActionClip。
 *
 * <p>使用方式：
 * <pre>
 * // 设置当前绘制动作
 * ActionBrushState.set(ActorActionType.RUN);
 *
 * // 获取当前绘制动作
 * ActorActionType current = ActionBrushState.get();
 * </pre>
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActionBrushState {

    /** 当前绘制动作（默认 RUN） */
    private static ActorActionType current = ActorActionType.RUN;

    /**
     * 获取当前绘制动作
     *
     * @return 当前动作类型
     */
    public static ActorActionType get() {
        return current;
    }

    /**
     * 设置当前绘制动作
     *
     * @param type 动作类型（不能为 null）
     */
    public static void set(ActorActionType type) {
        if (type == null) {
            throw new IllegalArgumentException("ActionType cannot be null");
        }
        current = type;
    }

    /**
     * 重置为默认动作（RUN）
     */
    public static void reset() {
        current = ActorActionType.RUN;
    }

    private ActionBrushState() {
        // 工具类，不允许实例化
    }
}
