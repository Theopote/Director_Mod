package com.chronoblocks.ui.timeline.audio;

import com.chronoblocks.core.animation.AnimationSoundCollector;
import com.chronoblocks.core.animation.sequence.BlockSequenceEntry;
import com.chronoblocks.core.animation.sequence.BlockSequenceGenerator;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineInterpolation;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.audio.AudioClipKeyframe;
import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import com.chronoblocks.ui.timeline.model.audio.AudioTrack;
import com.chronoblocks.ui.timeline.model.audio.AudioTimelineClip;
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
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineAudioService {
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

        if (timelineModel == null) {
            return;
        }

        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track instanceof AudioTrack at) {
                at.clearEvents();
            }
        }

        AudioTrack audioTrack = findAudioTrack();
        if (audioTrack == null) {
            // å¦‚æžœè¿˜æ²¡æœ‰éŸ³é¢‘è½¨é“ï¼Œä¸è‡ªåŠ¨åˆ›å»ºï¼Œä¿æŒç”¨æˆ·æ‰‹åŠ¨åˆ†é…è¯­ä¹‰
            return;
        }

        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            TrackType type = track.getType();
            if (type != TrackType.CONTAINER && type != TrackType.ERASE) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                collectAndAddSoundEvents(clip, false);
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

        Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerId);
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

        TimelineClip audioClip = ensureAudioClipForContainerClip(clip, audioTrack, allowCreate);
        if (audioClip == null) {
            return;
        }

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

        if (!isContainerClip(clip)) {
            return;
        }

        float oldLen = oldEndTime - oldStartTime;
        float newLen = newEndTime - newStartTime;
        if (oldLen < 0.0005f || newLen < 0.0005f) {
            return;
        }

        List<UUID> ids = linkedEventIdsByClip.getOrDefault(clip, Collections.emptyList());
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

        updateLinkedAudioClipTimeRange(clip, oldStartTime, oldEndTime, newStartTime, newEndTime);
    }

    // =========================
    // Preview API
    // =========================

    public void beginClipTimeRangePreview(TimelineClip clip, float oldStartTime, float oldEndTime) {
        if (clip == null) {
            return;
        }
        if (!isContainerClip(clip)) {
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
        if (!isContainerClip(clip)) {
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
        return type == TrackType.CONTAINER || type == TrackType.ERASE;
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

    private TimelineClip ensureAudioClipForContainerClip(TimelineClip containerClip, AudioTrack audioTrack, boolean allowCreate) {
        if (containerClip == null || audioTrack == null) {
            return null;
        }

        TimelineClip linked = linkedAudioClipsByClip.get(containerClip);
        if (linked != null) {
            linked = upgradeToAudioTimelineClipIfNeeded(audioTrack, linked);
            syncAudioClipRange(linked, containerClip);
            return linked;
        }

        TimelineClip existing = findAudioClipForContainerClip(audioTrack, containerClip);
        if (existing != null) {
            existing = upgradeToAudioTimelineClipIfNeeded(audioTrack, existing);
            linkAudioClip(containerClip, existing);
            syncAudioClipRange(existing, containerClip);
            return existing;
        }

        if (!allowCreate) {
            return null;
        }

        int clipColor = TrackType.AUDIO.getClipColor();
        TimelineClip audioClip = new AudioTimelineClip(
                formatAudioClipLabel(containerClip.getLabel()),
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
     */
    private String formatAudioClipLabel(String containerLabel) {
        String base = (containerLabel == null || containerLabel.isBlank()) ? "Audio" : containerLabel.trim();
        String prefix = TrackType.AUDIO.getIcon() + " ";
        if (base.startsWith(prefix)) {
            return base;
        }
        return prefix + base;
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
        } catch (Exception ignored) {
            return clip;
        }
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

        List<com.chronoblocks.ui.timeline.model.clip.ClipKeyframe> keyframes =
                new ArrayList<>(audioClip.getClipKeyframes());
        for (com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf : keyframes) {
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
        List<com.chronoblocks.ui.timeline.model.clip.ClipKeyframe> keyframes = clip.getClipKeyframes();
        if (keyframes == null || keyframes.isEmpty()) {
            return 1.0f;
        }

        AudioClipKeyframe prev = null;
        AudioClipKeyframe next = null;
        for (com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf : keyframes) {
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
            // 对于旧的 TimelineTrack，使用迁移方法读取事件（避免调用已弃用的 getAudioEvents）
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

        if (isAudioClip(clip)) {
            TimelineClip containerClip = linkedContainerClipsByAudioClip.remove(clip);
            if (containerClip != null) {
                linkedAudioClipsByClip.remove(containerClip);
                removeSoundEventsForClip(containerClip);
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
                linkedClipByEventId.remove(id);
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
                if (event != null && event.getId() != null) {
                    linkedClipByEventId.remove(event.getId());
                }
                audioTrack.removeEvent(event);
                removed++;
            }
        }

        if (LOGGER.isDebugEnabled() && removed > 0) {
            LOGGER.debug("[TimelineAudioService] 移除了片段 {} 的 {} 个声音事件", clip.getLabel(), removed);
        }
    }
}
