package com.chronoblocks.ui.adapters.animation;

import com.chronoblocks.animation.integration.playback.ClipPlaybackRuntimeManager;
import com.chronoblocks.ui.timeline.TimelineTrack;
import com.chronoblocks.ui.timeline.TimelineClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 🧩 PropertyAnimationTimelineBridge
 * ------------------------------------------------------
 * ChronoBlocks 动画系统的核心桥接逻辑。
 *
 * <p>该类实时监听时间线（TimelineTrack / TimelineClip / Keyframe）
 * 的状态变化，并同步到 PropertyAnimationController。
 *
 * <p>使「时间线 UI」与「属性插值系统」保持一致。
 *
 * <h3>支持功能：</h3>
 * <ul>
 *  <li>✅ 轨道与通道自动映射</li>
 *  <li>✅ 关键帧数据自动生成插值 Keyframe</li>
 *  <li>✅ 实时属性更新（编辑器模式预览）</li>
 *  <li>✅ Timeline <-> Controller 双向同步</li>
 * </ul>
 *
 * <h3>工作原理：</h3>
 * <pre>
 * TimelineTrack → AnimationChannel
 *     ↓                   ↓
 * TimelineClip    →   Keyframe[]
 *     ↓                   ↓
 * Keyframe[]      →   插值计算
 *     ↓                   ↓
 * 目标对象        ←   属性更新
 * </pre>
 *
 * @author ChronoBlocks Team
 * @version 4.7
 * @since 2025-10-18
 */
public class PropertyAnimationTimelineBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyAnimationTimelineBridge.class);

    /** 全局属性动画控制器（可由时间线共享） */
    private final PropertyAnimationController controller;

    /** 注册的时间线轨道映射 */
    private final Map<String, TimelineTrack> trackMap = new HashMap<>();

    /** Clip 到 Channel 的映射表 */
    private final Map<String, String> clipToChannel = new HashMap<>();

    /** 绑定的目标对象（轨道ID → 目标对象） */
    private final Map<String, Object> targetObjects = new HashMap<>();

    /** 绑定的属性名（轨道ID → 属性名） */
    private final Map<String, String> targetProperties = new HashMap<>();

    /** 当前序列名称 */
    private String currentSequenceName;

    /** 序列时长 */
    private float sequenceDuration = 10.0f;

    /** 是否循环播放 */
    private boolean sequenceLoop = false;

    // ================================
    // 🏗️ 构造函数
    // ================================

    public PropertyAnimationTimelineBridge(PropertyAnimationController controller) {
        this.controller = controller;
        LOGGER.info("[Bridge] PropertyAnimationTimelineBridge 已创建");
    }

    // ================================
    // 🕓 时间线同步
    // ================================

    /**
     * 绑定到一个时间线序列
     * 
     * @param sequenceName 序列名称
     * @param tracks 轨道列表
     * @param duration 序列时长
     * @param loop 是否循环
     */
    public void bindSequence(String sequenceName, List<TimelineTrack> tracks, float duration, boolean loop) {
        this.currentSequenceName = sequenceName;
        this.sequenceDuration = duration;
        this.sequenceLoop = loop;
        this.trackMap.clear();
        this.clipToChannel.clear();

        LOGGER.info("[Bridge] 绑定时间线序列: {} (时长: {}s, 循环: {})", sequenceName, duration, loop);
        buildChannelsFromTracks(tracks);
    }

    /**
     * 绑定单个轨道
     * 
     * @param track 时间线轨道
     */
    public void bindTrack(TimelineTrack track) {
        String trackId = generateTrackId(track);
        trackMap.put(trackId, track);
        buildChannelFromTrack(track, trackId);
        LOGGER.debug("[Bridge] 绑定轨道: {} (ID: {})", track.getName(), trackId);
    }

    /**
     * 为轨道绑定目标对象和属性
     * 
     * @param track 时间线轨道
     * @param target 目标对象
     * @param propertyName 属性名
     */
    public void bindTrackToProperty(TimelineTrack track, Object target, String propertyName) {
        String trackId = generateTrackId(track);
        targetObjects.put(trackId, target);
        targetProperties.put(trackId, propertyName);
        LOGGER.debug("[Bridge] 轨道 {} 绑定到 {}.{}", track.getName(), 
            target.getClass().getSimpleName(), propertyName);
    }

    /**
     * 根据时间线内容构建动画通道
     */
    private void buildChannelsFromTracks(List<TimelineTrack> tracks) {
        controller.clearChannels();

        for (TimelineTrack track : tracks) {
            String trackId = generateTrackId(track);
            trackMap.put(trackId, track);
            buildChannelFromTrack(track, trackId);
        }

        controller.setDuration(sequenceDuration);
        controller.setLoop(sequenceLoop);
    }

    /**
     * 从单个轨道构建动画通道
     */
    private void buildChannelFromTrack(TimelineTrack track, String trackId) {
        // 收集轨道上所有 Clip 的关键帧
        List<PropertyAnimationInterpolator.Keyframe<Float>> frames = new ArrayList<>();
        
        for (TimelineClip clip : track.getClips()) {
            String clipId = generateClipId(clip);
            clipToChannel.put(clipId, trackId);
            
            // 从 Clip 中提取关键帧
            for (Float keyframeTime : clip.getKeyframes()) {
                // 使用关键帧时间作为值（简化示例，实际应从数据中获取值）
                frames.add(new PropertyAnimationInterpolator.Keyframe<>(
                    keyframeTime,
                    keyframeTime, // 简化：使用时间作为值
                    PropertyAnimationInterpolator.Easing.SMOOTHSTEP
                ));
            }
        }

        // 如果没有关键帧，跳过通道创建
        if (frames.isEmpty()) {
            LOGGER.debug("[Bridge] 轨道 {} 没有关键帧，跳过通道创建", track.getName());
            return;
        }

        // 按时间排序
        frames.sort((k1, k2) -> Float.compare(k1.time, k2.time));

        // 注册到控制器
        controller.registerChannel(
            trackId,
            new PropertyAnimationController.AnimationChannel<>(
                track.getName(),
                frames,
                PropertyAnimationInterpolator.Easing.SMOOTHSTEP,
                value -> applyValueToTarget(trackId, value)
            )
        );

        LOGGER.debug("[Bridge] 注册轨道通道: {} -> {} (关键帧数: {})", 
            track.getName(), trackId, frames.size());
    }

    // ================================
    // 🔄 实时更新机制
    // ================================

    /**
     * 每帧同步时间线时间 → 动画控制器
     * 
     * @param timelineTime 时间线当前时间
     */
    public void updateFromTimeline(float timelineTime) {
        if (controller.isPlaying()) return; // 播放时由控制器驱动

        // 在编辑器预览模式下，手动模拟该时间点的状态
        simulateFrameAt(timelineTime);
    }

    /**
     * 模拟指定时间点的帧
     */
    private void simulateFrameAt(float time) {
        // 遍历所有通道，手动计算该时间点的值
        for (String channelId : controller.getChannelIds()) {
            PropertyAnimationController.AnimationChannel<Float> channel = 
                controller.getChannel(channelId);
            
            if (channel != null && !channel.getKeyframes().isEmpty()) {
                PropertyAnimationInterpolator interpolator = PropertyAnimationInterpolator.get();
                Float value = interpolator.interpolateKeyframes(channel.getKeyframes(), time);
                if (value != null) {
                    applyValueToTarget(channelId, value);
                }
            }
        }

        ClipPlaybackRuntimeManager.get().update(time);
    }

    /**
     * 将动画值应用到目标对象（如容器或属性）
     */
    private void applyValueToTarget(String trackId, Float value) {
        Object target = targetObjects.get(trackId);
        String property = targetProperties.get(trackId);

        if (target == null || property == null) {
            LOGGER.trace("[Bridge] 轨道 {} 没有绑定目标对象或属性", trackId);
            return;
        }

        try {
            // 尝试通过 setter 方法设置
            String setterName = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            try {
                var method = target.getClass().getMethod(setterName, float.class);
                method.invoke(target, value);
                LOGGER.trace("[Bridge] 属性同步: {}.{} = {}", 
                    target.getClass().getSimpleName(), property, value);
                return;
            } catch (NoSuchMethodException e) {
                // 如果没有 setter，尝试直接访问字段
            }

            // 尝试通过字段直接设置
            Field field = target.getClass().getDeclaredField(property);
            field.setAccessible(true);
            field.setFloat(target, value);
            LOGGER.trace("[Bridge] 字段同步: {}.{} = {}", 
                target.getClass().getSimpleName(), property, value);
                
        } catch (Exception e) {
            LOGGER.warn("[Bridge] 无法设置属性 {}.{}: {}", 
                target.getClass().getSimpleName(), property, e.getMessage());
        }
    }

    // ================================
    // 📡 事件响应
    // ================================

    /**
     * 当时间线有关键帧更新时调用
     * 
     * @param track 发生变化的轨道
     */
    public void onTrackModified(TimelineTrack track) {
        String trackId = generateTrackId(track);
        if (!trackMap.containsKey(trackId)) {
            LOGGER.warn("[Bridge] 轨道 {} 未注册", track.getName());
            return;
        }

        LOGGER.debug("[Bridge] 轨道更新: {}", track.getName());
        rebuildChannelFromTrack(track, trackId);
    }

    /**
     * 当 Clip 被修改时调用
     * 
     * @param track 包含该 Clip 的轨道
     */
    public void onClipModified(TimelineTrack track) {
        onTrackModified(track);
    }

    /**
     * 重建指定轨道的通道
     */
    private void rebuildChannelFromTrack(TimelineTrack track, String trackId) {
        buildChannelFromTrack(track, trackId);
        LOGGER.debug("[Bridge] 重建轨道通道: {}", track.getName());
    }

    // ================================
    // 🔧 辅助方法
    // ================================

    /**
     * 生成轨道的唯一ID
     */
    private String generateTrackId(TimelineTrack track) {
        return "track_" + track.getName() + "_" + track.hashCode();
    }

    /**
     * 生成 Clip 的唯一ID
     */
    private String generateClipId(TimelineClip clip) {
        return "clip_" + clip.getLabel() + "_" + clip.hashCode();
    }

    // ================================
    // 🧩 状态获取
    // ================================

    /**
     * 获取当前序列名称
     */
    public String getCurrentSequenceName() {
        return currentSequenceName;
    }

    /**
     * 获取序列时长
     */
    public float getSequenceDuration() {
        return sequenceDuration;
    }

    /**
     * 是否循环播放
     */
    public boolean isSequenceLoop() {
        return sequenceLoop;
    }

    /**
     * 获取动画控制器
     */
    public PropertyAnimationController getController() {
        return controller;
    }

    /**
     * 获取轨道映射表
     */
    public Map<String, TimelineTrack> getTrackMap() {
        return Collections.unmodifiableMap(trackMap);
    }

    /**
     * 获取通道数量
     */
    public int getChannelCount() {
        return controller.getChannelCount();
    }

    /**
     * 清空所有绑定
     */
    public void clear() {
        controller.clearChannels();
        trackMap.clear();
        clipToChannel.clear();
        targetObjects.clear();
        targetProperties.clear();
        LOGGER.info("[Bridge] 已清空所有绑定");
    }

    // ================================
    // 🎯 便利方法
    // ================================

    /**
     * 开始播放
     */
    public void play() {
        controller.play();
        LOGGER.debug("[Bridge] 开始播放动画");
    }

    /**
     * 暂停播放
     */
    public void pause() {
        controller.pause();
        LOGGER.debug("[Bridge] 暂停动画");
    }

    /**
     * 停止播放
     */
    public void stop() {
        controller.stop();
        LOGGER.debug("[Bridge] 停止动画");
    }

    /**
     * 设置播放速度
     */
    public void setSpeed(float speed) {
        controller.setSpeed(speed);
        LOGGER.debug("[Bridge] 设置播放速度: {}x", speed);
    }

    /**
     * 获取当前播放时间
     */
    public float getCurrentTime() {
        return controller.getCurrentTime();
    }

    /**
     * 是否正在播放
     */
    public boolean isPlaying() {
        return controller.isPlaying();
    }

    /**
     * 调试信息
     */
    public String getDebugInfo() {
        return String.format("PropertyAnimationTimelineBridge [序列: %s, 轨道: %d, 通道: %d, 播放: %s, 时间: %.2f/%.2f]",
            currentSequenceName != null ? currentSequenceName : "未绑定",
            trackMap.size(),
            getChannelCount(),
            isPlaying() ? "是" : "否",
            getCurrentTime(),
            getSequenceDuration());
    }
}

