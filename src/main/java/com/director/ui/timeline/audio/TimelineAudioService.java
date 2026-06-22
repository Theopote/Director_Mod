package com.director.ui.timeline.audio;

import com.director.core.DirectorServices;
import com.director.timeline.container.ContainerClipAudioSync;
import com.director.timeline.audio.TimelineAudioLoadHook;
import com.director.timeline.audio.TimelineClipSoundCollector;
import com.director.core.animation.AnimationSoundCollector;
import com.director.core.animation.sequence.BlockSequenceEntry;
import com.director.core.animation.sequence.BlockSequenceGenerator;
import com.director.core.container.Container;
import com.director.core.container.ContainerType;
import com.director.core.container.ContainerManager;
import com.director.core.animation.ClipAnimationConfig;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.audio.AudioClipKeyframe;
import com.director.timeline.model.audio.AudioEvent;
import com.director.timeline.model.audio.AudioTrack;
import com.director.timeline.model.audio.AudioTimelineClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 * @author Director Team
 * @version 1.0
 */
public class TimelineAudioService implements ContainerClipAudioSync, TimelineClipSoundCollector, TimelineAudioLoadHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineAudioService.class);
    
    private static TimelineAudioService instance;
    private TimelineModel timelineModel;
    private boolean suppressAutoCreate = false;

    /**
     * 自动生成音频事件与其所属 Clip 的链接关系（内存态）。
     *
     * <p>需求：容器片段移动时，自动生成的音频默认应跟随片段整体平移；用户可对单个事件“取消链接”。</p>
     *
     * <p>注意：当前时间线持久化未保存 AudioEvent，因此该链接关系也只在本次运行期间有效。</p>
     */
    private final Map<TimelineClip, List<UUID>> linkedEventIdsByClip = new IdentityHashMap<>();
    private final Map<UUID, TimelineClip> linkedClipByEventId = new HashMap<>();
    private final Map<TimelineClip, TimelineClip> linkedAudioClipsByClip = new IdentityHashMap<>();
    private final Map<TimelineClip, TimelineClip> linkedContainerClipsByAudioClip = new IdentityHashMap<>();
    /**
     * 自动音效生成签名：用于避免属性面板每帧刷新时重复重建事件。
     * key = 容器片段（CONTAINER），value = 与动画/序列相关的 hash。
     */
    private final Map<TimelineClip, Integer> autoSoundSignatureByClip = new IdentityHashMap<>();

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
        rebuildAudioLinksFromTimeline();
    }

    public void setSuppressAutoCreate(boolean suppress) {
        this.suppressAutoCreate = suppress;
    }

    public boolean isSuppressAutoCreate() {
        return suppressAutoCreate;
    }

    private void rebuildAudioLinksFromTimeline() {
        linkedEventIdsByClip.clear();
        linkedClipByEventId.clear();
        linkedAudioClipsByClip.clear();
        linkedContainerClipsByAudioClip.clear();
        clipPreviewStates.clear();
        autoSoundSignatureByClip.clear();

        if (timelineModel == null) {
            return;
        }

        AudioTrack audioTrack = findAudioTrack();
        if (audioTrack == null) {
            // å¦‚æžœè¿˜æ²¡æœ‰éŸ³é¢‘è½¨é“ï¼Œä¸è‡ªåŠ¨åˆ›å»ºï¼Œä¿æŒç”¨æˆ·æ‰‹åŠ¨åˆ†é…è¯­ä¹‰
            return;
        }

        // ✅ 关键修复：不要在 setTimelineModel() 时无条件清空已加载的音频事件。
        // 旧逻辑假设“AudioEvent 不落盘”，但现在我们会从持久化中恢复 AudioEvent。
        // - 如果 events 已存在：只重建链接关系（用于跟随/颜色/属性面板）
        // - 如果 events 为空：才从容器片段自动重建（保底）
        boolean hasPersistedEvents = audioTrack.hasEvents();

        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            TrackType type = track.getType();
            if (type != TrackType.CONTAINER) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                // 1) 建立“容器片段 -> 音频片段”的绑定关系（不生成事件也要建立）
                if (clip != null) {
                    TimelineClip audioClip = null;
                    // 只在允许创建时才补齐音频片段；否则仅尝试查找现有绑定
                    if (!suppressAutoCreate) {
                        try {
                            boolean isDemolition = isDemolitionContainer(clip.getBoundContainerId());
                            audioClip = ensureAudioClipForContainerClip(clip, audioTrack, !hasPersistedEvents, isDemolition);
                        } catch (RuntimeException e) {
                            LOGGER.warn("[TimelineAudio] 无法为容器片段 '{}' 建立音频片段绑定: {}",
                                    clip.getLabel(), e.getMessage());
                            LOGGER.debug("[TimelineAudio] 音频片段绑定失败详情", e);
                        }
                    }
                    if (audioClip != null) {
                        linkedAudioClipsByClip.put(clip, audioClip);
                        linkedContainerClipsByAudioClip.put(audioClip, clip);
                    }
                }

                // 2) 如果没有任何事件，则从容器片段重建（保底：重启后也能看到音频）
                if (!hasPersistedEvents) {
                    collectAndAddSoundEvents(clip, true);
                }
            }
        }

        // 3) 如果事件是从持久化恢复的：按“音频片段时间范围”把事件重新链接到容器片段
        // 这样容器片段移动/缩放时，音频点能正确跟随；清除轨道的点也能正确染色。
        if (hasPersistedEvents) {
            // 先收集所有 audioClip -> containerClip 的已知映射
            List<TimelineClip> audioClips = audioTrack.getClips();
            if (audioClips != null && !audioClips.isEmpty()) {
                for (TimelineClip ac : audioClips) {
                    TimelineClip cc = linkedContainerClipsByAudioClip.get(ac);
                    if (cc == null) {
                        // 兜底：按 boundContainerId 匹配
                        UUID bid = ac != null ? ac.getBoundContainerId() : null;
                        if (bid != null) {
                            for (TimelineTrack t : timelineModel.getAllTracks()) {
                                if (t == null) continue;
                                TrackType tt = t.getType();
                                if (tt != TrackType.CONTAINER) continue;
                                for (TimelineClip c : t.getClips()) {
                                    if (c != null && bid.equals(c.getBoundContainerId())) {
                                        cc = c;
                                        linkedAudioClipsByClip.put(c, ac);
                                        linkedContainerClipsByAudioClip.put(ac, c);
                                        break;
                                    }
                                }
                                if (cc != null) break;
                            }
                        }
                    }
                    if (cc == null) continue;

                    float a0 = ac.getStartTime();
                    float a1 = ac.getEndTime();
                    for (AudioEvent ev : audioTrack.getEvents()) {
                        if (ev == null) continue;
                        float t = ev.getTime();
                        if (t >= a0 - 0.0005f && t <= a1 + 0.0005f) {
                            linkEventToClip(cc, ev);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 为容器片段收集并添加声音事件到音频轨道
     * 
     * <p>当片段添加到时间线时调用此方法，自动收集该片段的声音事件。
     * 
     * @param clip 时间线片段
     */
    public void collectAndAddSoundEvents(TimelineClip clip) {
        collectAndAddSoundEvents(clip, true);
    }

    private void collectAndAddSoundEvents(TimelineClip clip, boolean allowAutoCreate) {
        if (clip == null || timelineModel == null) {
            return;
        }

        if (suppressAutoCreate) {
            return;
        }

        if (!isContainerClip(clip)) {
            return;
        }

        UUID containerId = clip.getBoundContainerId();
        if (containerId == null) {
            return;
        }

        Container container = DirectorServices.get().containers().getContainer(containerId);
        if (container == null) {
            return;
        }

        List<UUID> existing = linkedEventIdsByClip.get(clip);
        if (existing != null && !existing.isEmpty()) {
            return;
        }

        boolean allowCreate = allowAutoCreate && !suppressAutoCreate;

        AudioTrack audioTrack = getOrCreateAudioTrack(allowCreate);
        if (audioTrack == null) {
            return;
        }

        boolean isEraseTrack = isDemolitionContainer(clip.getBoundContainerId());
        
        TimelineClip audioClip = ensureAudioClipForContainerClip(clip, audioTrack, allowCreate, isEraseTrack);
        if (audioClip == null) {
            return;
        }

        ClipAnimationConfig config = clip.getAnimationConfig();
        if (config == null) {
            config = ClipAnimationConfig.defaultConfig();
        }
        
        // 确保擦除轨道的片段使用 EXIT 阶段
        if (isEraseTrack && config.getPhase() != ClipAnimationConfig.AnimationPhase.EXIT) {
            config.setPhase(ClipAnimationConfig.AnimationPhase.EXIT);
        }

        float clipDuration = Math.max(0.05f, clip.getLength());
        float animDuration = Math.max(0.05f, Math.min(config.getDuration(), clipDuration));
        float totalDelay = Math.max(0.0f, config.getDelay());
        com.director.core.animation.sequence.SequenceParameters sequenceParameters = config.getSequenceParameters();
        if (sequenceParameters != null) {
            totalDelay += Math.max(0.0f, sequenceParameters.getGlobalDelay());
        }

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

        AnimationSoundCollector collector = AnimationSoundCollector.getInstance();
        List<AudioEvent> events = collector.collectFromClip(clip, container, sequenceEntries);

        for (AudioEvent event : events) {
            audioTrack.addEvent(event);
            linkEventToClip(clip, event);
        }
        audioTrack.sortEvents();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineAudioService] ?????????{} ?????????????????? {} ???????????????",
                    clip.getLabel(), events.size());
        }
    }

    public void onClipTimeRangeChanged(TimelineClip clip, float oldStartTime, float oldEndTime, float newStartTime, float newEndTime) {
        if (clip == null) {
            return;
        }

        boolean isContainer = isContainerClip(clip);
        boolean isAudio = !isContainer && isAudioClip(clip);
        if (!isContainer && !isAudio) {
            return;
        }

        float oldLen = oldEndTime - oldStartTime;
        float newLen = newEndTime - newStartTime;
        if (oldLen < 0.0005f || newLen < 0.0005f) {
            return;
        }

        TimelineClip containerClip = isContainer ? clip : resolveContainerClipForClip(clip);
        if (containerClip == null) {
            containerClip = clip;
        }

        List<UUID> ids = linkedEventIdsByClip.getOrDefault(containerClip, Collections.emptyList());
        AudioTrack audioTrack = findAudioTrack();
        if (audioTrack != null && !ids.isEmpty()) {
            for (UUID id : ids) {
                AudioEvent ev = audioTrack.getEventById(id);
                if (ev == null) {
                    continue;
                }
                float t = ev.getTime();
                float u = (t - oldStartTime) / oldLen;
                u = Math.max(0f, Math.min(1f, u));
                ev.setTime(newStartTime + u * newLen);
            }

            audioTrack.sortEvents();
        }

        if (isAudio) {
            updateAudioClipKeyframes(clip, oldStartTime, oldEndTime, newStartTime, newEndTime);
        }

        if (isContainer) {
            updateLinkedAudioClipTimeRange(clip, oldStartTime, oldEndTime, newStartTime, newEndTime);
        }
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

        TimelineClip containerClip = resolveContainerClipForClip(clip);
        if (containerClip == null && isAudioClip(clip)) {
            containerClip = clip;
        }
        if (containerClip == null) {
            return;
        }

        List<UUID> ids = linkedEventIdsByClip.getOrDefault(containerClip, Collections.emptyList());
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

        linkedClipByEventId.remove(id);

        linkedEventIdsByClip.entrySet().removeIf(e -> {
            e.getValue().removeIf(id::equals);
            return e.getValue().isEmpty();
        });
    }

    public boolean isEventLinked(AudioEvent event) {
        return getLinkedClip(event).isPresent();
    }

    public Optional<TimelineClip> getLinkedClip(AudioEvent event) {
        if (event == null || event.getId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(linkedClipByEventId.get(event.getId()));
    }

    private void linkEventToClip(TimelineClip clip, AudioEvent event) {
        if (clip == null || event == null || event.getId() == null) {
            return;
        }
        linkedEventIdsByClip.computeIfAbsent(clip, c -> new ArrayList<>()).add(event.getId());
        linkedClipByEventId.put(event.getId(), clip);
    }

    public void moveLinkedEventsToTrack(TimelineClip audioClip, TimelineTrack targetTrack) {
        if (audioClip == null || targetTrack == null) {
            return;
        }
        TimelineClip containerClip = resolveContainerClipForClip(audioClip);
        if (containerClip == null && isAudioClip(audioClip)) {
            containerClip = audioClip;
        }
        if (containerClip == null) {
            return;
        }

        List<UUID> ids = linkedEventIdsByClip.getOrDefault(containerClip, Collections.emptyList());
        if (ids.isEmpty()) {
            return;
        }

        AudioTrack targetAudioTrack = resolveAudioTrack(targetTrack);
        if (targetAudioTrack == null) {
            return;
        }

        List<AudioTrack> audioTracks = getAllAudioTracks();
        if (audioTracks.isEmpty()) {
            return;
        }

        Map<UUID, AudioEvent> eventsToMove = new HashMap<>();
        for (AudioTrack track : audioTracks) {
            for (UUID id : ids) {
                AudioEvent ev = track.getEventById(id);
                if (ev == null) {
                    continue;
                }
                if (track != targetAudioTrack) {
                    track.removeEvent(ev);
                }
                eventsToMove.put(id, ev);
            }
        }

        if (!eventsToMove.isEmpty()) {
            for (AudioEvent ev : eventsToMove.values()) {
                targetAudioTrack.addEvent(ev);
            }
            targetAudioTrack.sortEvents();
        }
    }

    private AudioTrack resolveAudioTrack(TimelineTrack track) {
        if (track instanceof AudioTrack audioTrack) {
            return audioTrack;
        }
        if (track != null && track.getType() == TrackType.AUDIO && timelineModel != null) {
            return convertToAudioTrack(track);
        }
        return null;
    }

    private List<AudioTrack> getAllAudioTracks() {
        if (timelineModel == null) {
            return Collections.emptyList();
        }
        List<AudioTrack> tracks = new ArrayList<>();
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null || track.getType() != TrackType.AUDIO) {
                continue;
            }
            AudioTrack audioTrack = resolveAudioTrack(track);
            if (audioTrack != null && !tracks.contains(audioTrack)) {
                tracks.add(audioTrack);
            }
        }
        return tracks;
    }

    private TimelineClip resolveContainerClipForClip(TimelineClip clip) {
        if (clip == null) {
            return null;
        }
        if (isContainerClip(clip)) {
            return clip;
        }
        if (isAudioClip(clip)) {
            TimelineClip containerClip = linkedContainerClipsByAudioClip.get(clip);
            if (containerClip != null) {
                return containerClip;
            }
            return getContainerClipForAudioClip(clip).orElse(null);
        }
        return null;
    }

    public boolean isAudioClipBound(TimelineClip clip) {
        if (!isAudioClip(clip)) {
            return false;
        }
        return resolveContainerClipForClip(clip) != null;
    }

    public void onAudioClipBindingChanged(TimelineClip audioClip, UUID oldContainerId, UUID newContainerId) {
        if (!isAudioClip(audioClip)) {
            return;
        }

        TimelineClip oldContainerClip = linkedContainerClipsByAudioClip.get(audioClip);
        if (oldContainerClip == null && oldContainerId != null) {
            oldContainerClip = findContainerClipForAudioClip(audioClip, oldContainerId);
        }

        if (newContainerId == null) {
            if (oldContainerClip != null) {
                linkedAudioClipsByClip.remove(oldContainerClip);
                linkedContainerClipsByAudioClip.remove(audioClip);
                transferLinkedEvents(oldContainerClip, audioClip);
            }
            return;
        }

        TimelineClip newContainerClip = findContainerClipForAudioClip(audioClip, newContainerId);
        if (newContainerClip == null) {
            if (oldContainerClip != null) {
                linkedAudioClipsByClip.remove(oldContainerClip);
                linkedContainerClipsByAudioClip.remove(audioClip);
                transferLinkedEvents(oldContainerClip, audioClip);
            }
            return;
        }

        if (oldContainerClip != null && oldContainerClip != newContainerClip) {
            linkedAudioClipsByClip.remove(oldContainerClip);
            transferLinkedEvents(oldContainerClip, newContainerClip);
        }

        linkAudioClip(newContainerClip, audioClip);
        syncAudioClipRange(audioClip, newContainerClip);
        transferLinkedEvents(audioClip, newContainerClip);
    }

    private void transferLinkedEvents(TimelineClip fromClip, TimelineClip toClip) {
        if (fromClip == null || toClip == null || fromClip == toClip) {
            return;
        }
        List<UUID> ids = linkedEventIdsByClip.remove(fromClip);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        linkedEventIdsByClip.computeIfAbsent(toClip, c -> new ArrayList<>()).addAll(ids);
        for (UUID id : ids) {
            linkedClipByEventId.put(id, toClip);
        }
    }

    private TimelineClip findContainerClipForAudioClip(TimelineClip audioClip, UUID containerId) {
        if (audioClip == null || containerId == null || timelineModel == null) {
            return null;
        }

        TimelineClip best = null;
        float bestScore = Float.MAX_VALUE;
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null || track.getType() != TrackType.CONTAINER) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (clip == null || !containerId.equals(clip.getBoundContainerId())) {
                    continue;
                }
                float score = Math.abs(clip.getStartTime() - audioClip.getStartTime())
                        + Math.abs(clip.getEndTime() - audioClip.getEndTime());
                if (score < bestScore) {
                    bestScore = score;
                    best = clip;
                }
            }
        }
        return best;
    }

    
    private boolean isContainerClip(TimelineClip clip) {
        if (clip == null) {
            return false;
        }
        TimelineTrack track = clip.getParentTrack();
        if (track == null && timelineModel != null) {
            track = timelineModel.findTrackForClip(clip);
        }
        if (track == null) {
            return false;
        }
        TrackType type = track.getType();
        return type == TrackType.CONTAINER;
    }

    private boolean isAudioClip(TimelineClip clip) {
        if (clip == null) {
            return false;
        }
        TimelineTrack track = clip.getParentTrack();
        if (track == null && timelineModel != null) {
            track = timelineModel.findTrackForClip(clip);
        }
        return track != null && track.getType() == TrackType.AUDIO;
    }

    private AudioTrack getOrCreateAudioTrack(boolean allowCreate) {
        AudioTrack audioTrack = findAudioTrack();
        if (audioTrack != null) {
            return audioTrack;
        }
        if (!allowCreate || timelineModel == null) {
            return null;
        }
        AudioTrack created = new AudioTrack("Audio");
        timelineModel.addTrack(created);
        return created;
    }

    private TimelineClip ensureAudioClipForContainerClip(TimelineClip containerClip, AudioTrack audioTrack, boolean allowCreate, boolean isEraseTrack) {
        if (containerClip == null || audioTrack == null) {
            return null;
        }

        TimelineClip linked = linkedAudioClipsByClip.get(containerClip);
        if (linked != null) {
            linked = upgradeToAudioTimelineClipIfNeeded(audioTrack, linked);
            syncAudioClipRange(linked, containerClip);
            // 更新标签以反映擦除状态
            if (isEraseTrack) {
                linked.setLabel(formatAudioClipLabel(containerClip.getLabel(), true));
            }
            return linked;
        }

        TimelineClip existing = findAudioClipForContainerClip(audioTrack, containerClip);
        if (existing != null) {
            existing = upgradeToAudioTimelineClipIfNeeded(audioTrack, existing);
            linkAudioClip(containerClip, existing);
            syncAudioClipRange(existing, containerClip);
            // 更新标签以反映擦除状态
            if (isEraseTrack) {
                existing.setLabel(formatAudioClipLabel(containerClip.getLabel(), true));
            }
            return existing;
        }

        if (!allowCreate) {
            return null;
        }

        int clipColor = TrackType.AUDIO.getClipColor();
        TimelineClip audioClip = new AudioTimelineClip(
                formatAudioClipLabel(containerClip.getLabel(), isEraseTrack),
                containerClip.getStartTime(),
                containerClip.getEndTime(),
                clipColor
        );
        audioClip.bindToContainer(containerClip.getBoundContainerId(), containerClip.getBoundContainerName());
        audioTrack.addClip(audioClip);
        linkAudioClip(containerClip, audioClip);
        return audioClip;
    }

    /**
     * 生成音频片段的显示名称（避免与容器片段混淆）。
     * 
     * @param containerLabel 容器片段标签
     * @param isEraseTrack 是否为擦除轨道
     */
    private String formatAudioClipLabel(String containerLabel, boolean isEraseTrack) {
        String base = (containerLabel == null || containerLabel.isBlank()) 
                ? (isEraseTrack ? "清除音频" : "Audio") 
                : containerLabel.trim();
        String prefix = TrackType.AUDIO.getIcon() + " ";
        if (base.startsWith(prefix)) {
            return base;
        }
        // 如果是擦除轨道，添加"清除"标识
        if (isEraseTrack && !base.contains("清除") && !base.contains("擦除")) {
            base = "清除: " + base;
        }
        return prefix + base;
    }
    
    /**
     * 生成音频片段的显示名称（避免与容器片段混淆）。
     * 兼容旧版本调用（默认非擦除轨道）。
     */
    private String formatAudioClipLabel(String containerLabel) {
        return formatAudioClipLabel(containerLabel, false);
    }

    /**
     * 当容器重命名时，同步更新其关联的音频片段标题（保留音频前缀）。
     */
    public void updateLinkedAudioClipLabel(UUID containerId, String containerName) {
        if (containerId == null || containerName == null || containerName.isBlank()) {
            return;
        }
        if (timelineModel == null) {
            return;
        }
        AudioTrack audioTrack = findAudioTrack();
        if (audioTrack == null) {
            return;
        }
        for (TimelineClip clip : audioTrack.getClips()) {
            if (clip == null) {
                continue;
            }
            if (containerId.equals(clip.getBoundContainerId())) {
                clip.setLabel(formatAudioClipLabel(containerName));
                clip.setBoundContainerName(containerName);
            }
        }
    }

    private TimelineClip findAudioClipForContainerClip(AudioTrack audioTrack, TimelineClip containerClip) {
        if (audioTrack == null || containerClip == null) {
            return null;
        }
        UUID containerId = containerClip.getBoundContainerId();
        if (containerId == null) {
            return null;
        }

        int matchCount = 0;
        float bestScore = Float.MAX_VALUE;
        TimelineClip best = null;
        for (TimelineClip clip : audioTrack.getClips()) {
            if (clip == null) {
                continue;
            }
            if (!containerId.equals(clip.getBoundContainerId())) {
                continue;
            }
            matchCount++;
            float score = Math.abs(clip.getStartTime() - containerClip.getStartTime())
                    + Math.abs(clip.getEndTime() - containerClip.getEndTime());
            if (score < bestScore) {
                bestScore = score;
                best = clip;
            }
        }

        if (matchCount == 1) {
            return best;
        }
        if (best != null && bestScore <= 0.01f) {
            return best;
        }

        return null;
    }

    /**
     * 兼容旧数据：如果音频轨道上历史上创建的是普通 TimelineClip，这里将其升级为 AudioTimelineClip。
     */
    private TimelineClip upgradeToAudioTimelineClipIfNeeded(AudioTrack audioTrack, TimelineClip clip) {
        if (audioTrack == null || clip == null) {
            return clip;
        }
        if (clip instanceof AudioTimelineClip) {
            return clip;
        }
        try {
            AudioTimelineClip upgraded = new AudioTimelineClip(clip);
            // 保留“音频前缀”显示
            upgraded.setLabel(formatAudioClipLabel(clip.getLabel()));
            // 维护绑定信息（拷贝构造已复制，但这里再确保一次）
            upgraded.bindToContainer(clip.getBoundContainerId(), clip.getBoundContainerName());

            TimelineTrack parent = clip.getParentTrack();
            if (parent != null) {
                parent.removeClip(clip);
                parent.addClip(upgraded);
            } else {
                // 回退：仅在 audioTrack 中替换
                audioTrack.removeClip(clip);
                audioTrack.addClip(upgraded);
            }
            return upgraded;
        } catch (RuntimeException e) {
            LOGGER.warn("[TimelineAudio] 升级 AudioTimelineClip 失败，保留原片段: {}", clip.getLabel(), e);
            return clip;
        }
    }

    private boolean isDemolitionContainer(UUID containerId) {
        if (containerId == null) {
            return false;
        }
        Container container = DirectorServices.get().containers().getContainer(containerId);
        return container != null && container.getType() == ContainerType.DEMOLITION;
    }

    private void linkAudioClip(TimelineClip containerClip, TimelineClip audioClip) {
        if (containerClip == null || audioClip == null) {
            return;
        }
        linkedAudioClipsByClip.put(containerClip, audioClip);
        linkedContainerClipsByAudioClip.put(audioClip, containerClip);
    }

    private void syncAudioClipRange(TimelineClip audioClip, TimelineClip containerClip) {
        if (audioClip == null || containerClip == null) {
            return;
        }
        float newStart = containerClip.getStartTime();
        float newEnd = containerClip.getEndTime();
        float oldStart = audioClip.getStartTime();
        float oldEnd = audioClip.getEndTime();
        if (Math.abs(oldStart - newStart) < 0.0005f && Math.abs(oldEnd - newEnd) < 0.0005f) {
            return;
        }
        audioClip.setStartTime(newStart);
        audioClip.setEndTime(newEnd);
        float actualStart = audioClip.getStartTime();
        float actualEnd = audioClip.getEndTime();
        updateAudioClipKeyframes(audioClip, oldStart, oldEnd, actualStart, actualEnd);
    }

    private void updateLinkedAudioClipTimeRange(TimelineClip containerClip,
                                                float oldStartTime,
                                                float oldEndTime,
                                                float newStartTime,
                                                float newEndTime) {
        TimelineClip audioClip = linkedAudioClipsByClip.get(containerClip);
        if (audioClip == null) {
            return;
        }
        audioClip.setStartTime(newStartTime);
        audioClip.setEndTime(newEndTime);
        float actualStart = audioClip.getStartTime();
        float actualEnd = audioClip.getEndTime();
        updateAudioClipKeyframes(audioClip, oldStartTime, oldEndTime, actualStart, actualEnd);
    }

    private void updateAudioClipKeyframes(TimelineClip audioClip,
                                          float oldStartTime,
                                          float oldEndTime,
                                          float newStartTime,
                                          float newEndTime) {
        if (audioClip == null) {
            return;
        }
        float oldLen = oldEndTime - oldStartTime;
        float newLen = newEndTime - newStartTime;
        if (oldLen < 0.0005f || newLen < 0.0005f) {
            return;
        }

        List<com.director.timeline.model.clip.ClipKeyframe> keyframes =
                new ArrayList<>(audioClip.getClipKeyframes());
        for (com.director.timeline.model.clip.ClipKeyframe kf : keyframes) {
            if (kf == null) {
                continue;
            }
            float t = kf.getTime();
            float u = (t - oldStartTime) / oldLen;
            u = Math.max(0f, Math.min(1f, u));
            float newTime = newStartTime + u * newLen;
            audioClip.moveKeyframe(kf, newTime);
        }
    }

    public float getVolumeMultiplierForEvent(AudioEvent event) {
        if (event == null || event.getId() == null) {
            return 1.0f;
        }
        TimelineClip containerClip = linkedClipByEventId.get(event.getId());
        if (containerClip == null) {
            return 1.0f;
        }
        TimelineClip audioClip = linkedAudioClipsByClip.get(containerClip);
        if (audioClip == null) {
            return 1.0f;
        }
        return computeVolumeForClipAtTime(audioClip, event.getTime());
    }

    public float getVolumeAtTimeForClip(TimelineClip clip, float time) {
        if (!isAudioClip(clip)) {
            return 1.0f;
        }
        return computeVolumeForClipAtTime(clip, time);
    }

    private float computeVolumeForClipAtTime(TimelineClip clip, float time) {
        List<com.director.timeline.model.clip.ClipKeyframe> keyframes = clip.getClipKeyframes();
        if (keyframes == null || keyframes.isEmpty()) {
            return 1.0f;
        }

        AudioClipKeyframe prev = null;
        AudioClipKeyframe next = null;
        for (com.director.timeline.model.clip.ClipKeyframe kf : keyframes) {
            if (!(kf instanceof AudioClipKeyframe audioKf)) {
                continue;
            }
            if (audioKf.getTime() <= time) {
                prev = audioKf;
            }
            if (audioKf.getTime() >= time) {
                next = audioKf;
                break;
            }
        }

        if (prev == null && next == null) {
            return 1.0f;
        }
        if (prev == null) {
            return next.getVolume();
        }
        if (next == null || next == prev) {
            return prev.getVolume();
        }

        float t = (time - prev.getTime()) / (next.getTime() - prev.getTime());
        t = Math.max(0f, Math.min(1f, t));
        TimelineInterpolation interp = prev.getInterpolation();
        if (interp == TimelineInterpolation.STEP) {
            return prev.getVolume();
        }
        if (interp == TimelineInterpolation.SMOOTH) {
            t = t * t * (3f - 2f * t);
        }
        float v = prev.getVolume() + (next.getVolume() - prev.getVolume()) * t;
        return Math.max(0f, Math.min(1.0f, v));
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
            // 对于旧的 TimelineTrack，使用迁移方法读取事件（旧存储位于 TimelineTrack.audioEvents）
            List<AudioEvent> oldEvents = track.getLegacyAudioEventsForMigration();
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
    
    public void onClipRemoved(TimelineClip clip) {
        if (clip == null) {
            return;
        }

        clipPreviewStates.remove(clip);
        autoSoundSignatureByClip.remove(clip);

        if (isAudioClip(clip)) {
            TimelineClip containerClip = linkedContainerClipsByAudioClip.remove(clip);
            if (containerClip != null) {
                linkedAudioClipsByClip.remove(containerClip);
                removeSoundEventsForClip(containerClip);
            } else {
                removeSoundEventsForClip(clip);
            }
            return;
        }

        if (!isContainerClip(clip)) {
            return;
        }

        TimelineClip audioClip = linkedAudioClipsByClip.remove(clip);
        if (audioClip != null) {
            linkedContainerClipsByAudioClip.remove(audioClip);
            TimelineTrack audioTrack = audioClip.getParentTrack();
            if (audioTrack != null) {
                audioTrack.removeClip(audioClip);
            }
        }

        removeSoundEventsForClip(clip);
    }

    /**
     * 当属性面板修改了动画/序列设置后，刷新该容器片段自动生成的音效事件。
     *
     * <p>目标：音效时机始终与“方块出现在目标位置/到位”的时刻一致，避免配置修改后音效仍停留在旧时间。</p>
     */
    public void refreshAutoSoundEventsIfNeeded(TimelineClip clip) {
        if (clip == null || timelineModel == null) {
            return;
        }
        if (suppressAutoCreate) {
            return;
        }
        if (!isContainerClip(clip)) {
            return;
        }
        UUID containerId = clip.getBoundContainerId();
        if (containerId == null) {
            return;
        }

        int sig = computeAutoSoundSignature(clip);
        Integer last = autoSoundSignatureByClip.get(clip);
        if (last != null && last == sig) {
            return;
        }

        // 只重建“自动生成并仍然链接”的事件；若用户对单个事件取消链接，它会保留在轨道上（不被此处删除）。
        removeSoundEventsForClip(clip);
        collectAndAddSoundEvents(clip, true);
        autoSoundSignatureByClip.put(clip, sig);
    }

    /**
     * 获取某个音频片段绑定的“容器片段”。
     *
     * <p>用于：在音频片段属性面板中修改音效时机/偏移，并刷新自动生成的音效事件。</p>
     */
    public Optional<TimelineClip> getContainerClipForAudioClip(TimelineClip audioClip) {
        if (audioClip == null) {
            return Optional.empty();
        }
        TimelineClip container = linkedContainerClipsByAudioClip.get(audioClip);
        if (container != null) {
            return Optional.of(container);
        }
        // 回退：如果是同一个 boundContainerId，也尝试从已链接的容器片段中找一个
        UUID id = audioClip.getBoundContainerId();
        if (id != null) {
            for (TimelineClip c : linkedAudioClipsByClip.keySet()) {
                if (c != null && id.equals(c.getBoundContainerId())) {
                    return Optional.of(c);
                }
            }
        }
        return Optional.empty();
    }

    private int hashConfigParam(ClipAnimationConfig cfg, String key) {
        if (cfg == null || cfg.getParams() == null) {
            return 0;
        }
        Object value = cfg.getParams().get(key);
        return value != null ? value.toString().hashCode() : 0;
    }

    private int computeAutoSoundSignature(TimelineClip clip) {
        ClipAnimationConfig cfg = clip != null ? clip.getAnimationConfig() : null;
        if (cfg == null) {
            return 0;
        }
        com.director.core.animation.sequence.SequenceParameters sp = cfg.getSequenceParameters();

        int h = 17;
        h = 31 * h + Float.floatToIntBits(clip.getStartTime());
        h = 31 * h + Float.floatToIntBits(clip.getEndTime());
        h = 31 * h + (cfg.getPhase() != null ? cfg.getPhase().ordinal() : 0);
        h = 31 * h + (cfg.getMode() != null ? cfg.getMode().ordinal() : 0);
        h = 31 * h + Float.floatToIntBits(cfg.getDuration());
        h = 31 * h + Float.floatToIntBits(cfg.getDelay());
        h = 31 * h + (cfg.getEasing() != null ? cfg.getEasing().hashCode() : 0);
        h = 31 * h + (cfg.getSequenceMode() != null ? cfg.getSequenceMode().ordinal() : 0);
        h = 31 * h + hashConfigParam(cfg, "enterSoundTiming");
        h = 31 * h + hashConfigParam(cfg, "exitSoundTiming");
        h = 31 * h + hashConfigParam(cfg, "enterSoundOffsetSeconds");
        h = 31 * h + hashConfigParam(cfg, "exitSoundOffsetSeconds");
        if (sp != null) {
            h = 31 * h + (sp.getFlowType() != null ? sp.getFlowType().ordinal() : 0);
            h = 31 * h + (sp.getFillType() != null ? sp.getFillType().ordinal() : 0);
            h = 31 * h + (sp.getOriginType() != null ? sp.getOriginType().ordinal() : 0);
            h = 31 * h + Float.floatToIntBits(sp.getPlaneYaw());
            h = 31 * h + Float.floatToIntBits(sp.getPlanePitch());
            h = 31 * h + Float.floatToIntBits(sp.getLayerStep());
            h = 31 * h + sp.getCustomX();
            h = 31 * h + sp.getCustomY();
            h = 31 * h + sp.getCustomZ();
            h = 31 * h + Float.floatToIntBits(sp.getPriorityWeight());
            h = 31 * h + (sp.isReverse() ? 1 : 0);
            h = 31 * h + (sp.isAlternating() ? 1 : 0);
            h = 31 * h + (sp.getFloodSeedMode() != null ? sp.getFloodSeedMode().ordinal() : 0);
            h = 31 * h + (sp.getConcurrencyMode() != null ? sp.getConcurrencyMode().ordinal() : 0);
            h = 31 * h + sp.getBatchSize();
            h = 31 * h + Float.floatToIntBits(sp.getBatchInterval());
            h = 31 * h + (sp.getTimeMappingStrategy() != null ? sp.getTimeMappingStrategy().ordinal() : 0);
            h = 31 * h + Float.floatToIntBits(sp.getPerBlockDuration());
            h = 31 * h + Float.floatToIntBits(sp.getGlobalDelay());
            Map<String, Object> extraParams = sp.getExtraParams();
            if (extraParams != null) {
                h = 31 * h + extraParams.hashCode();
            }
        }
        return h;
    }

    public void removeSoundEventsForClip(TimelineClip clip) {
        if (clip == null || timelineModel == null) {
            return;
        }

        List<AudioTrack> audioTracks = getAllAudioTracks();
        if (audioTracks.isEmpty()) {
            return;
        }

        // 优先：按“链接关系”精确移除（避免仅靠时间范围误删/漏删）
        List<UUID> linked = linkedEventIdsByClip.remove(clip);
        int removed = 0;
        if (linked != null && !linked.isEmpty()) {
            for (UUID id : linked) {
                linkedClipByEventId.remove(id);
                for (AudioTrack audioTrack : audioTracks) {
                    AudioEvent ev = audioTrack.getEventById(id);
                    if (ev != null) {
                        audioTrack.removeEvent(ev);
                        removed++;
                    }
                }
            }
        } else {
            // 向后兼容：如果没有建立链接关系（旧数据），回退到按时间范围移除
            for (AudioTrack audioTrack : audioTracks) {
                List<AudioEvent> eventsToRemove = audioTrack.getEventsInRange(clip.getStartTime(), clip.getEndTime());
                for (AudioEvent event : eventsToRemove) {
                    if (event != null && event.getId() != null) {
                        linkedClipByEventId.remove(event.getId());
                    }
                    audioTrack.removeEvent(event);
                    removed++;
                }
            }
        }

        if (LOGGER.isDebugEnabled() && removed > 0) {
            LOGGER.debug("[TimelineAudioService] 移除了片段 {} 的 {} 个声音事件", clip.getLabel(), removed);
        }
    }
}
