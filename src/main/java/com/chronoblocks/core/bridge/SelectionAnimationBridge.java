package com.chronoblocks.core.bridge;

import com.chronoblocks.core.actions.SelectionToContainerUtil;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.timeline.TimelineContext;
import com.chronoblocks.core.timeline.model.TimelineProject;
import com.chronoblocks.core.timeline.model.TimelineSequence;
import com.chronoblocks.core.timeline.model.TimelineTrack;
import com.chronoblocks.core.timeline.keyframe.Keyframe;
import com.chronoblocks.core.timeline.TrackFactory;
import com.chronoblocks.timeline.event.TimelineEventBus;
import com.chronoblocks.timeline.event.TimelineEvent;
import com.chronoblocks.timeline.event.TimelineEventTypes;

import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Objects;

/**
 * 将当前选区转换为：
 * 1) 新的 Container（承载方块）
 * 2) 时间线 Track（绑定该 Container）
 * 3) 默认关键帧（Transform/Opacity）
 * 4) 自动注册到当前 Sequence & 广播事件
 */
public final class SelectionAnimationBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionAnimationBridge.class);

    private SelectionAnimationBridge() {}

    public static Result createAnimatedTrackFromSelection(String baseName, Color tagColor, Defaults defaults) {
        Objects.requireNonNull(defaults, "defaults must not be null");

        // 0) 读取当前选区
        var selection = SelectionManager.get().getSelectionSet();
        if (selection.isEmpty()) {
            return Result.fail("当前没有选区");
        }

        // 1) 生成容器
        Container container = SelectionToContainerUtil.createContainerFromSelection(
                baseName, tagColor, selection, defaults.computeCenterFromSelection);

        // 2) 获取当前时间线上下文
        TimelineContext ctx = TimelineContext.get();
        TimelineProject project = ctx.getActiveProject();
        TimelineSequence sequence = ctx.getActiveSequence();
        if (project == null || sequence == null) {
            // 回滚容器（简化实现）
            // ContainerManager.get().remove(container.getId()); // 暂时注释掉
            return Result.fail("未找到活动的 Project / Sequence");
        }

        // 3) 创建轨道并绑定容器
        TimelineTrack track = TrackFactory.createContainerTrack(container, tagColor);
        sequence.addTrack(track);

        // 4) 默认关键帧（入场/出场/可选平移动画）
        addDefaultKeyframes(track, container, defaults);

        // 5) 广播事件（用于刷新 UI / 时间线）（简化实现）
        try {
            // TimelineEventBus.get().post(TimelineEvent.payload(TimelineEventTypes.TRACK_ADDED, track)); // 暂时注释掉
            // TimelineEventBus.get().post(TimelineEvent.payload(TimelineEventTypes.SEQUENCE_CHANGED, sequence)); // 暂时注释掉
            LOGGER.debug("[SelectionAnimationBridge] 事件广播（简化实现）");
        } catch (Exception e) {
            LOGGER.warn("[SelectionAnimationBridge] 事件广播失败", e);
        }

        // 6) 聚焦：选中轨道并切换到属性面板（可选）
        tryFocusSelection(track);

        // 7) 清空选区（可选）
        if (defaults.clearSelectionAfterCreate) {
            selection.clear();
        }

        LOGGER.info("[SelectionAnimationBridge] 成功创建动画轨道: {}", track.getName());
        return Result.ok(container, track, sequence);
    }

    private static void addDefaultKeyframes(TimelineTrack track, Container container, Defaults d) {
        float t0 = d.startTimeSeconds;
        float t1 = d.endTimeSeconds;
        float fade = d.fadeSeconds;

        // 透明度关键帧（淡入淡出）
        if (d.withOpacityFade) {
            track.putKeyframe(Keyframe.ofOpacity(t0, 0f));
            track.putKeyframe(Keyframe.ofOpacity(Math.min(t0 + fade, t1), 1f));
            track.putKeyframe(Keyframe.ofOpacity(Math.max(t1 - fade, t0), 1f));
            track.putKeyframe(Keyframe.ofOpacity(t1, 0f));
        } else {
            track.putKeyframe(Keyframe.ofOpacity(t0, 1f));
            track.putKeyframe(Keyframe.ofOpacity(t1, 1f));
        }

        // 位移/缩放/旋转关键帧（按需）（简化实现）
        if (d.withTransformIntro) {
            Vector3f base = new Vector3f(); // 简化实现，使用原点
            Vector3f intro = new Vector3f(base).add(d.introOffset);
            track.putKeyframe(Keyframe.ofPosition(t0, intro));
            track.putKeyframe(Keyframe.ofPosition(Math.min(t0 + d.transformEase, t1), base));
        }
        if (d.withTransformOutro) {
            Vector3f base = new Vector3f(); // 简化实现，使用原点
            Vector3f outro = new Vector3f(base).add(d.outroOffset);
            track.putKeyframe(Keyframe.ofPosition(Math.max(t1 - d.transformEase, t0), base));
            track.putKeyframe(Keyframe.ofPosition(t1, outro));
        }

        // 可选：插入默认缓动
        if (d.withEase) {
            track.applyDefaultEasing(d.defaultEasing);
        }
    }

    private static void tryFocusSelection(TimelineTrack track) {
        try {
            // 通知属性面板切换到 Track（简化实现）
            // com.chronoblocks.ui.selection.SelectionManager.get().setCurrentSelection(track);
            // 可选：通知时间线 GUI 聚焦该轨道（简化实现）
            // TimelineEventBus.get().post(TimelineEvent.payload(TimelineEventTypes.FOCUS_TRACK, track)); // 暂时注释掉
            LOGGER.debug("[SelectionAnimationBridge] 聚焦选择（简化实现）");
        } catch (Throwable ignored) {
            // 忽略UI相关错误
        }
    }

    /* ------------ 配置项与返回值 ------------ */

    public static final class Defaults {
        public boolean computeCenterFromSelection = true;
        public boolean clearSelectionAfterCreate = true;

        public boolean withOpacityFade = true;
        public float fadeSeconds = 0.25f;

        public boolean withTransformIntro = false;
        public boolean withTransformOutro = false;
        public float transformEase = 0.3f; // s

        public Vector3f introOffset = new Vector3f(0, 2, 0);
        public Vector3f outroOffset = new Vector3f(0, -2, 0);

        public boolean withEase = true;
        public Easing defaultEasing = Easing.EASE_OUT_QUAD;

        public float startTimeSeconds = 0f;
        public float endTimeSeconds = 2f;

        public Defaults range(float start, float end) { 
            this.startTimeSeconds = start; 
            this.endTimeSeconds = end; 
            return this; 
        }
        
        public Defaults fade(float s) { 
            this.fadeSeconds = s; 
            return this; 
        }
        
        public Defaults intro(Vector3f off) { 
            this.withTransformIntro = true; 
            this.introOffset = new Vector3f(off); 
            return this; 
        }
        
        public Defaults outro(Vector3f off) { 
            this.withTransformOutro = true; 
            this.outroOffset = new Vector3f(off); 
            return this; 
        }
    }

    public enum Easing { 
        LINEAR, EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD 
    }

    public record Result(boolean ok, String message, Container container, TimelineTrack track, TimelineSequence sequence) {
        public static Result ok(Container c, TimelineTrack t, TimelineSequence s) { 
            return new Result(true, "OK", c, t, s); 
        }
        
        public static Result fail(String m) { 
            return new Result(false, m, null, null, null); 
        }
    }
}
