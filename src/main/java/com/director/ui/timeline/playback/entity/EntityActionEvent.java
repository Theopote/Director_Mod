package com.director.ui.timeline.playback.entity;

import com.director.ui.timeline.model.entity.EntityActionClip;

import java.util.Map;

/**
 * EntityActionEvent v1.0
 *
 * <p>实体行为事件，表示本帧触发的 Action。
 *
 * <p>用于在采样时收集 Action Clip 的触发事件，
 * 然后由 TimelineActorController 处理这些事件。
 *
 * @author Director Team
 * @version 1.0
 */
public record EntityActionEvent(
        EntityActionClip.ActionType type,
        Map<String, Object> params
) {
}

