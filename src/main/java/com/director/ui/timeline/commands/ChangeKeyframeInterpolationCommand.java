package com.director.ui.timeline.commands;

import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 修改关键帧插值类型命令（支持撤销）。
 * 
 * <p>优化：继承 BaseTimelineCommand 以确保模型更新和一致性。
 * 
 * <p>注意：当前使用静态 Map 存储插值信息。未来应该移到 TimelineClip 中作为正式属性。
 * 当前实现使用 ConcurrentHashMap 确保线程安全，但需要注意内存管理。
 * 
 * <p>内存管理：当 clip 被删除时，对应的插值映射不会自动清理。
 * 建议在删除 clip 时调用 {@link #clearInterpolationsForClip(TimelineClip)} 进行清理。
 */
public class ChangeKeyframeInterpolationCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeKeyframeInterpolationCommand.class);
    private static final float KEYFRAME_TOLERANCE = 0.0001f;

    @SuppressWarnings("unused")
    private final TimelineTrack track;  // 保留用于未来扩展（如轨道级别的插值设置）
    private final TimelineClip clip;
    private final float keyframeTime;
    private final TimelineInterpolation newInterpolation;
    private TimelineInterpolation oldInterpolation;
    private boolean executed = false;

    // 临时存储：关键帧插值映射（clip -> time -> interpolation）
    // TODO: 未来应该移到 TimelineClip 中作为正式属性
    // 使用 ConcurrentHashMap 确保线程安全
    private static final Map<TimelineClip, Map<Float, TimelineInterpolation>> keyframeInterpolations = new ConcurrentHashMap<>();

    public ChangeKeyframeInterpolationCommand(
            TimelineModel model,
            TimelineTrack track,
            TimelineClip clip,
            float keyframeTime,
            TimelineInterpolation interpolation) {
        super(model, "keyframe_interpolation_changed");
        this.track = track;
        this.clip = clip;
        this.keyframeTime = keyframeTime;
        this.newInterpolation = interpolation;
    }

    @Override
    protected void doExecute() {
        if (clip == null) {
            LOGGER.warn("[Timeline] 设置关键帧插值失败：clip 为 null");
            return;
        }

        // 检查关键帧是否存在
        if (!clip.hasKeyframeAt(keyframeTime, KEYFRAME_TOLERANCE)) {
            LOGGER.warn("[Timeline] 设置关键帧插值失败：关键帧不存在于时间 {:.2f}", keyframeTime);
            return;
        }

        // 获取或创建插值映射
        Map<Float, TimelineInterpolation> clipInterpolations = keyframeInterpolations.computeIfAbsent(
            clip, k -> new ConcurrentHashMap<>());

        // 保存旧值
        oldInterpolation = clipInterpolations.getOrDefault(keyframeTime, TimelineInterpolation.LINEAR);

        // 如果新插值与旧插值相同，跳过执行
        if (newInterpolation == oldInterpolation) {
            LOGGER.debug("[Timeline] 关键帧 {:.2f}s 插值类型未改变，跳过: {}", keyframeTime, newInterpolation.getDisplayName());
            return;
        }

        // 设置新值
        clipInterpolations.put(keyframeTime, newInterpolation);
        executed = true;

        LOGGER.debug("[Timeline] 设置关键帧 {:.2f}s 插值类型为 {} (原: {})", 
                keyframeTime, newInterpolation.getDisplayName(), oldInterpolation.getDisplayName());
    }

    @Override
    protected void doUndo() {
        if (clip == null) {
            LOGGER.warn("[Timeline] 恢复关键帧插值失败：clip 为 null");
            return;
        }

        if (!executed || oldInterpolation == null) {
            return;
        }

        Map<Float, TimelineInterpolation> clipInterpolations = keyframeInterpolations.get(clip);
        if (clipInterpolations != null) {
            clipInterpolations.put(keyframeTime, oldInterpolation);
            LOGGER.debug("[Timeline] 恢复关键帧 {:.2f}s 插值类型为 {}", keyframeTime, oldInterpolation.getDisplayName());
        }
    }

    @Override
    public String getDescription() {
        return String.format("设置插值类型为 %s", newInterpolation.getDisplayName());
    }

    @Override
    public boolean canUndo() {
        return executed;
    }

    /**
     * 获取关键帧的插值类型（辅助方法）
     * 
     * @param clip 片段
     * @param time 关键帧时间
     * @return 插值类型，如果未设置则返回 LINEAR
     */
    public static TimelineInterpolation getKeyframeInterpolation(TimelineClip clip, float time) {
        if (clip == null) {
            return TimelineInterpolation.LINEAR;
        }
        Map<Float, TimelineInterpolation> clipInterpolations = keyframeInterpolations.get(clip);
        if (clipInterpolations == null) {
            return TimelineInterpolation.LINEAR; // 默认线性
        }
        return clipInterpolations.getOrDefault(time, TimelineInterpolation.LINEAR);
    }

    /**
     * 清理指定 clip 的所有插值信息（用于内存管理）
     * 
     * <p>当 clip 被删除时，应该调用此方法清理对应的插值映射，避免内存泄漏。
     * 
     * @param clip 要清理的片段
     */
    public static void clearInterpolationsForClip(TimelineClip clip) {
        if (clip != null) {
            keyframeInterpolations.remove(clip);
            LOGGER.debug("[Timeline] 清理片段插值信息: {}", clip.getLabel());
        }
    }

    /**
     * 直接设置关键帧的插值类型（辅助方法，用于内部操作）
     * 
     * <p>此方法用于在不创建命令的情况下设置插值，例如在移动关键帧时迁移插值信息。
     * 
     * @param clip 片段
     * @param time 关键帧时间
     * @param interpolation 插值类型
     */
    public static void setKeyframeInterpolationDirectly(TimelineClip clip, float time, TimelineInterpolation interpolation) {
        if (clip == null || interpolation == null) {
            return;
        }
        Map<Float, TimelineInterpolation> clipInterpolations = keyframeInterpolations.computeIfAbsent(
            clip, k -> new ConcurrentHashMap<>());
        clipInterpolations.put(time, interpolation);
    }

    /**
     * 移除指定关键帧的插值信息（辅助方法）
     * 
     * @param clip 片段
     * @param time 关键帧时间
     */
    public static void removeKeyframeInterpolation(TimelineClip clip, float time) {
        if (clip == null) {
            return;
        }
        Map<Float, TimelineInterpolation> clipInterpolations = keyframeInterpolations.get(clip);
        if (clipInterpolations != null) {
            clipInterpolations.remove(time);
        }
    }
}

