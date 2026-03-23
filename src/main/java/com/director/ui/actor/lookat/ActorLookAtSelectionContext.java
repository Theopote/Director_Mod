package com.director.ui.actor.lookat;

import com.director.ui.timeline.model.entity.EntityLookAtClip;

/**
 * ActorLookAtSelectionContext v1.0
 *
 * <p>LookAt 选择上下文（只读）
 * <p>用于在 Drag Controller 和 Gizmo Controller 之间共享当前活跃的 LookAtClip
 *
 * <p>设计原则：
 * <ul>
 *   <li>只读上下文，不修改数据</li>
 *   <li>由 Gizmo Controller 更新</li>
 *   <li>由 Selection Hub 清除</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActorLookAtSelectionContext {

    /** 当前活跃的 LookAtClip */
    private static EntityLookAtClip activeClip = null;

    private ActorLookAtSelectionContext() {
        // 静态工具类
    }

    /**
     * 设置当前活跃的 LookAtClip
     *
     * @param clip LookAtClip，如果为 null 则清除
     */
    public static void setActiveLookAtClip(EntityLookAtClip clip) {
        activeClip = clip;
    }

    /**
     * 获取当前活跃的 LookAtClip（只读）
     *
     * @return LookAtClip，如果未设置则返回 null
     */
    public static EntityLookAtClip getActiveLookAtClip() {
        return activeClip;
    }
}

