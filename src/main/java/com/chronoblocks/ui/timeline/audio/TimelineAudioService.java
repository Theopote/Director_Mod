package com.chronoblocks.ui.timeline.audio;

import com.chronoblocks.core.animation.AnimationSoundCollector;
import com.chronoblocks.core.animation.sequence.BlockSequenceEntry;
import com.chronoblocks.core.animation.sequence.BlockSequenceGenerator;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import com.chronoblocks.ui.timeline.model.audio.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 时间线音频服务
 * 
 * <p>负责自动收集容器片段的声音事件，并将它们添加到音频轨道中。
 * 
 * <p>核心功能：
 * <ul>
 *   <li>当容器片段添加到时间线时，自动收集声音事件</li>
 *   <li>将声音事件添加到音频轨道（仅当音频轨道已存在）</li>
 *   <li>当片段移动或调整时，自动更新声音事件的时间</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineAudioService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineAudioService.class);
    
    private static TimelineAudioService instance;
    private TimelineModel timelineModel;

    /**
     * 自动生成音频事件与其所属 Clip 的链接关系（内存态）。
     *
     * <p>需求：容器片段移动时，自动生成的音频默认应跟随片段整体平移；用户可对单个事件“取消链接”。</p>
     *
     * <p>注意：当前时间线持久化未保存 AudioEvent，因此该链接关系也只在本次运行期间有效。</p>
     */
    private final Map<TimelineClip, List<UUID>> linkedEventIdsByClip = new IdentityHashMap<>();

    // =========================
    // 实时预览（拖动/缩放时）
    // =========================

    private static final class ClipAudioPreviewState {
        final float oldStart;
        final float oldEnd;
        final Map<UUID, Float> originalTimesByEventId;
        final Map<UUID, Float> previewTimesByEventId = new java.util.HashMap<>();

        ClipAudioPreviewState(float oldStart, float oldEnd, Map<UUID, Float> originalTimesByEventId) {
            this.oldStart = oldStart;
            this.oldEnd = oldEnd;
            this.originalTimesByEventId = originalTimesByEventId;
        }
    }

    /**
     * 当前正在预览的 clip -> 预览状态。
     * 只用于 UI 拖动时的实时显示，不落盘、不改变事件真实时间。
     */
    private final Map<TimelineClip, ClipAudioPreviewState> clipPreviewStates = new IdentityHashMap<>();
    
    private TimelineAudioService() {
    }
    
    /**
     * 获取单例实例
     */
    public static TimelineAudioService getInstance() {
        if (instance == null) {
            instance = new TimelineAudioService();
        }
        return instance;
    }
    
    /**
     * 设置时间线模型
     */
    public void setTimelineModel(TimelineModel model) {
        this.timelineModel = model;
    }
    
    /**
     * 为容器片段收集并添加声音事件到音频轨道
     * 
     * <p>当片段添加到时间线时调用此方法，自动收集该片段的声音事件。
     * 
     * @param clip 时间线片段
     */
    public void collectAndAddSoundEvents(TimelineClip clip) {
        if (clip == null || timelineModel == null) {
            return;
        }
        
        UUID containerId = clip.getBoundContainerId();
        if (containerId == null) {
            return;
        }
        
        Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerId);
        if (container == null) {
            return;
        }
        
        // 仅当音频轨道已存在时才注入事件：
        // - 没有音频轨道：播放应静音（不自动创建音频轨道）
        // - 音频轨道上没有该片段对应事件：播放也应静音
        AudioTrack audioTrack = findAudioTrack();
        if (audioTrack == null) {
            return;
        }
        
        // 生成方块序列
        ClipAnimationConfig config = clip.getAnimationConfig();
        if (config == null) {
            config = ClipAnimationConfig.defaultConfig();
        }

        float clipDuration = Math.max(0.05f, clip.getLength());
        float animDuration = Math.max(0.05f, Math.min(config.getDuration(), clipDuration));
        float totalDelay = Math.max(0.0f, config.getDelay());
        try {
            if (config.getSequenceParameters() != null) {
                totalDelay += Math.max(0.0f, config.getSequenceParameters().getGlobalDelay());
            }
        } catch (Exception ignored) {
        }
        
        // 将 Set<BlockPos> 转换为 List<BlockPos>
        List<BlockSequenceEntry> sequenceEntries = BlockSequenceGenerator.generate(
                new ArrayList<>(container.getBlocks()),
                container,
                config.getSequenceParameters(),
                clipDuration,
                animDuration,
                totalDelay
        );
        
        if (sequenceEntries == null || sequenceEntries.isEmpty()) {
            return;
        }
        
        // 收集声音事件
        AnimationSoundCollector collector = AnimationSoundCollector.getInstance();
        List<AudioEvent> events = collector.collectFromClip(clip, container, sequenceEntries);
        
        // 添加到音频轨道
        for (AudioEvent event : events) {
            audioTrack.addEvent(event);
            linkEventToClip(clip, event);
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineAudioService] 为片段 {} 收集并添加了 {} 个声音事件",
                    clip.getLabel(), events.size());
        }
    }

    /**
     * 当片段时间范围变化（移动/拉伸/压缩）时调用：将仍保持链接的事件按片段范围做仿射映射。
     *
     * <p>映射规则：保持每个事件在片段内的归一化位置 u 不变，事件新时间 = newStart + u * newLength。</p>
     */
    public void onClipTimeRangeChanged(TimelineClip clip, float oldStartTime, float oldEndTime, float newStartTime, float newEndTime) {
        if (clip == null) {
            return;
        }

        float oldLen = oldEndTime - oldStartTime;
        float newLen = newEndTime - newStartTime;
        if (oldLen < 0.0005f || newLen < 0.0005f) {
            return;
        }

        List<UUID> ids = linkedEventIdsByClip.getOrDefault(clip, Collections.emptyList());
        if (ids.isEmpty()) {
            return;
        }

        AudioTrack audioTrack = findAudioTrack();
        if (audioTrack == null) {
            return;
        }

        for (UUID id : ids) {
            AudioEvent ev = audioTrack.getEventById(id);
            if (ev == null) {
                continue;
            }
            float t = ev.getTime();
            float u = (t - oldStartTime) / oldLen; // 归一化位置
            // 允许少量超出范围（例如极端情况下事件在 clip 外），但仍尽量保持相对位置
            u = Math.max(0f, Math.min(1f, u));
            ev.setTime(newStartTime + u * newLen);
        }

        audioTrack.sortEvents();
    }

    // =========================
    // Preview API
    // =========================

    public void beginClipTimeRangePreview(TimelineClip clip, float oldStartTime, float oldEndTime) {
        if (clip == null) {
            return;
        }
        if (clipPreviewStates.containsKey(clip)) {
            return;
        }

        List<UUID> ids = linkedEventIdsByClip.getOrDefault(clip, Collections.emptyList());
        if (ids.isEmpty()) {
            return;
        }

        AudioTrack audioTrack = findAudioTrack();
        if (audioTrack == null) {
            return;
        }

        Map<UUID, Float> original = new java.util.HashMap<>();
        for (UUID id : ids) {
            AudioEvent ev = audioTrack.getEventById(id);
            if (ev != null) {
                original.put(id, ev.getTime());
            }
        }

        if (original.isEmpty()) {
            return;
        }

        clipPreviewStates.put(clip, new ClipAudioPreviewState(oldStartTime, oldEndTime, original));
        updateClipTimeRangePreview(clip, clip.getStartTime(), clip.getEndTime());
    }

    public void updateClipTimeRangePreview(TimelineClip clip, float newStartTime, float newEndTime) {
        if (clip == null) {
            return;
        }
        ClipAudioPreviewState state = clipPreviewStates.get(clip);
        if (state == null) {
            return;
        }

        float oldLen = state.oldEnd - state.oldStart;
        float newLen = newEndTime - newStartTime;
        if (oldLen < 0.0005f || newLen < 0.0005f) {
            state.previewTimesByEventId.clear();
            return;
        }

        state.previewTimesByEventId.clear();
        for (Map.Entry<UUID, Float> e : state.originalTimesByEventId.entrySet()) {
            float t0 = e.getValue();
            float u = (t0 - state.oldStart) / oldLen;
            u = Math.max(0f, Math.min(1f, u));
            state.previewTimesByEventId.put(e.getKey(), newStartTime + u * newLen);
        }
    }

    public void endClipTimeRangePreview(TimelineClip clip) {
        if (clip == null) {
            return;
        }
        clipPreviewStates.remove(clip);
    }

    public void endAllClipTimeRangePreviews() {
        clipPreviewStates.clear();
    }

    /**
     * 获取某个音频事件的“预览时间”，用于拖动/缩放过程中实时渲染。
     * @return 预览时间；若当前未处于预览，则返回 -1。
     */
    public float getPreviewTimeForEvent(AudioEvent event) {
        if (event == null || event.getId() == null) {
            return -1f;
        }
        UUID id = event.getId();
        for (ClipAudioPreviewState state : clipPreviewStates.values()) {
            Float t = state.previewTimesByEventId.get(id);
            if (t != null) {
                return t;
            }
        }
        return -1f;
    }

    /**
     * 取消某个事件与片段的链接：之后片段移动不再影响该事件。
     */
    public void unlinkEvent(AudioEvent event) {
        if (event == null) {
            return;
        }
        UUID id = event.getId();
        if (id == null) {
            return;
        }

        // 从所有 clip 的映射中移除该事件
        for (Map.Entry<TimelineClip, List<UUID>> e : linkedEventIdsByClip.entrySet()) {
            e.getValue().removeIf(id::equals);
        }
    }

    public boolean isEventLinked(AudioEvent event) {
        return getLinkedClip(event).isPresent();
    }

    public Optional<TimelineClip> getLinkedClip(AudioEvent event) {
        if (event == null || event.getId() == null) {
            return Optional.empty();
        }
        UUID id = event.getId();
        for (Map.Entry<TimelineClip, List<UUID>> e : linkedEventIdsByClip.entrySet()) {
            if (e.getValue().contains(id)) {
                return Optional.ofNullable(e.getKey());
            }
        }
        return Optional.empty();
    }

    private void linkEventToClip(TimelineClip clip, AudioEvent event) {
        if (clip == null || event == null || event.getId() == null) {
            return;
        }
        linkedEventIdsByClip.computeIfAbsent(clip, c -> new ArrayList<>()).add(event.getId());
    }

    private AudioTrack findAudioTrack() {
        if (timelineModel == null) {
            return null;
        }
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track != null && track.getType() == TrackType.AUDIO) {
                if (track instanceof AudioTrack at) {
                    return at;
                }
                return convertToAudioTrack(track);
            }
        }
        return null;
    }
    
    /**
     * 将旧的 TimelineTrack 转换为 AudioTrack
     * 
     * @param track 旧的轨道
     * @return 新的 AudioTrack
     */
    private AudioTrack convertToAudioTrack(TimelineTrack track) {
        AudioTrack audioTrack = new AudioTrack(track.getName());
        
        // 复制属性
        audioTrack.setLocked(track.isLocked());
        audioTrack.setVisible(track.isVisible());
        audioTrack.setMuted(track.isMuted());
        audioTrack.setSolo(track.isSolo());
        audioTrack.setCollapsed(track.isCollapsed());
        audioTrack.setZOrder(track.getZOrder());
        
        // 迁移事件：先转换为 AudioTrack，然后使用新方法
        // 如果 track 不是 AudioTrack，先转换为 AudioTrack 以访问新方法
        if (track instanceof AudioTrack sourceTrack) {
            for (AudioEvent event : sourceTrack.getEvents()) {
                audioTrack.addEvent(event);
            }
        } else {
            // 对于旧的 TimelineTrack，使用已弃用方法进行迁移（这是最后一次使用）
            @SuppressWarnings("deprecation")
            List<AudioEvent> oldEvents = track.getAudioEvents();
            for (AudioEvent event : oldEvents) {
                audioTrack.addEvent(event);
            }
        }
        
        // 替换轨道
        int index = timelineModel.getAllTracks().indexOf(track);
        if (index >= 0) {
            timelineModel.removeTrack(track);
            timelineModel.getAllTracks().add(index, audioTrack);
        }
        
        return audioTrack;
    }
    
    /**
     * 移除片段关联的声音事件
     * 
     * <p>当片段从时间线移除时调用此方法，清理该片段的声音事件。
     * 
     * @param clip 时间线片段
     */
    public void removeSoundEventsForClip(TimelineClip clip) {
        if (clip == null || timelineModel == null) {
            return;
        }

        AudioTrack audioTrack = findAudioTrack();
        if (audioTrack == null) {
            return;
        }

        // 优先：按“链接关系”精确移除（避免仅靠时间范围误删/漏删）
        List<UUID> linked = linkedEventIdsByClip.remove(clip);
        int removed = 0;
        if (linked != null && !linked.isEmpty()) {
            for (UUID id : linked) {
                AudioEvent ev = audioTrack.getEventById(id);
                if (ev != null) {
                    audioTrack.removeEvent(ev);
                    removed++;
                }
            }
        } else {
            // 向后兼容：如果没有建立链接关系（旧数据），回退到按时间范围移除
            List<AudioEvent> eventsToRemove = audioTrack.getEventsInRange(clip.getStartTime(), clip.getEndTime());
            for (AudioEvent event : eventsToRemove) {
                audioTrack.removeEvent(event);
                removed++;
            }
        }

        if (LOGGER.isDebugEnabled() && removed > 0) {
            LOGGER.debug("[TimelineAudioService] 移除了片段 {} 的 {} 个声音事件", clip.getLabel(), removed);
        }
    }
}
