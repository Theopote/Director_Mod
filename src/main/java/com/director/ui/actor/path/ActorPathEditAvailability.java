package com.director.ui.actor.path;

import com.director.actor.path.ActorPathDefinition;
import com.director.timeline.model.TimelineModel;

/** Shared explanation for library mutation controls; browsing remains available. */
final class ActorPathEditAvailability {
    private ActorPathEditAvailability() {}

    static String reason(TimelineModel model, ActorPathDefinition path) {
        if (model == null) return "请先打开时间线工程";
        if (model.isPlaying()) return "请先暂停播放再修改路径";
        if (model.isScrubbing()) return "请先结束拖动播放头再修改路径";
        if (!Float.isFinite(model.getCurrentTime()) || model.getCurrentTime() < 0) return "当前时间无效，请重置播放头";
        if (path != null && path.isLocked()) return "路径已锁定，请先解锁再重命名或删除";
        return "";
    }
}
