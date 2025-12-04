package com.chronoblocks.ui.timeline.persistence;

import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.animation.sequence.SequenceMode;
import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.core.animation.sequence.evaluator.FillType;
import com.chronoblocks.core.animation.sequence.evaluator.FlowType;
import com.chronoblocks.core.animation.sequence.evaluator.OriginType;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TimelineViewportState;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.util.WorldIdentifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * TimelineModel 持久化管理器
 * 负责保存和加载时间线轨道数据
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineModelPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineModelPersistence.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // 配置文件路径
    private static final Path CONFIG_DIR = Paths.get("config", "chronoblocks");
    private static final Set<String> RESERVED_SEQUENCE_KEYS = Set.of(
        "flowType",
        "fillType",
        "originType",
        "planeYaw",
        "planePitch",
        "customX",
        "customY",
        "customZ",
        "priorityWeight",
        "reverse",
        "alternating",
        "extraParams"
    );
    
    /**
     * 获取当前世界的时间线文件路径
     */
    private static Path getTimelineFile() {
        String worldId = WorldIdentifier.getCurrentWorldId();
        return CONFIG_DIR.resolve("timelines").resolve(worldId + "_timeline_model.json");
    }
    
    /**
     * 时间线数据传输对象
     */
    public static class TimelineDTO {
        public List<TrackDTO> tracks = new ArrayList<>();
        public float pixelsPerSecond = 80.0f;
        public float scrollX = 0f;
        public boolean snapEnabled = true;
        public float snapInterval = 0.25f;
        public long timestamp;
        public String worldFingerprint;
    }
    
    /**
     * 轨道数据传输对象
     */
    public static class TrackDTO {
        public String name;
        public String typeName;  // TrackType 的名称
        public List<ClipDTO> clips = new ArrayList<>();
        public boolean locked = false;
        public boolean visible = true;
        public int zOrder = 0;
        public boolean muted = false;
        public boolean solo = false;
        public String boundContainerName;
        public String boundContainerId;
    }
    
    /**
     * 片段数据传输对象
     */
    public static class ClipDTO {
        public String label;
        public float startTime;
        public float endTime;
        public int color;
        public List<Float> keyframes = new ArrayList<>();
        public boolean muted = false;
        public float opacity = 1.0f;
        public String animationMode;
        public float animationDuration = 2.0f;
        public float animationDelay = 0.0f;
        public String animationEasing = "Linear";
        public String animationOrdering = "Sequential";
        public Map<String, Object> animationParams = new HashMap<>();
        // 序列策略配置
        public String sequenceMode; // SequenceMode.name() (只支持 EVALUATOR_BASED)
        // 新排序系统参数（通过 sequenceParams 存储）
        public Map<String, Object> sequenceParams = new HashMap<>();
        public String boundContainerId;
        public String boundContainerName;
        public boolean autoFadePreviousClips = true;
        public float autoFadeOpacity = 0.35f;
    }
    
    /**
     * 将 TimelineModel 转换为 DTO
     */
    public static TimelineDTO toDTO(TimelineModel model, TimelineViewportState viewportState) {
        TimelineDTO dto = new TimelineDTO();
        
        // 保存视图状态
        if (viewportState != null) {
            dto.pixelsPerSecond = viewportState.getPixelsPerSecond();
            dto.scrollX = viewportState.getScrollX();
        } else {
            dto.pixelsPerSecond = model.getPixelsPerSecond();
            dto.scrollX = model.getScrollX();
        }
        dto.snapEnabled = model.isSnappingEnabled();
        dto.snapInterval = model.getSnapSettings().getGridInterval();
        dto.timestamp = System.currentTimeMillis();
        dto.worldFingerprint = getWorldFingerprint();
        
        // 保存所有轨道
        for (TimelineTrack track : model.getTracks()) {
            TrackDTO trackDTO = new TrackDTO();
            trackDTO.name = track.getName();
            trackDTO.typeName = track.getType() != null ? track.getType().name() : null;
            trackDTO.locked = track.isLocked();
            trackDTO.visible = track.isVisible();
            trackDTO.zOrder = track.getZOrder();
            trackDTO.muted = track.isMuted();
            trackDTO.solo = track.isSolo();
            trackDTO.boundContainerName = track.getBoundContainerName();
            trackDTO.boundContainerId = track.getBoundContainerId();
            
            // 保存轨道的所有片段
            for (TimelineClip clip : track.getClips()) {
                ClipDTO clipDTO = new ClipDTO();
                clipDTO.label = clip.getLabel();
                clipDTO.startTime = clip.getStartTime();
                clipDTO.endTime = clip.getEndTime();
                clipDTO.color = clip.getColor();
                clipDTO.muted = clip.isMuted();
                clipDTO.opacity = clip.getOpacity();
                clipDTO.keyframes.addAll(clip.getKeyframes());
                ClipAnimationConfig config = clip.getAnimationConfig();
                clipDTO.animationMode = config.getMode().name();
                clipDTO.animationDuration = config.getDuration();
                clipDTO.animationDelay = config.getDelay();
                clipDTO.animationEasing = config.getEasing();
                clipDTO.animationOrdering = config.getOrdering();
                clipDTO.animationParams.putAll(config.getParams());
                // 序列策略配置
                clipDTO.sequenceMode = config.getSequenceMode().name();
                SequenceParameters seqParams = config.getSequenceParameters();
                if (seqParams != null) {
                    clipDTO.sequenceParams.clear();
                    clipDTO.sequenceParams.put("flowType", seqParams.getFlowType().name());
                    clipDTO.sequenceParams.put("fillType", seqParams.getFillType().name());
                    clipDTO.sequenceParams.put("originType", seqParams.getOriginType().name());
                    clipDTO.sequenceParams.put("planeYaw", seqParams.getPlaneYaw());
                    clipDTO.sequenceParams.put("planePitch", seqParams.getPlanePitch());
                    clipDTO.sequenceParams.put("customX", seqParams.getCustomX());
                    clipDTO.sequenceParams.put("customY", seqParams.getCustomY());
                    clipDTO.sequenceParams.put("customZ", seqParams.getCustomZ());
                    clipDTO.sequenceParams.put("priorityWeight", seqParams.getPriorityWeight());
                    clipDTO.sequenceParams.put("reverse", seqParams.isReverse());
                    clipDTO.sequenceParams.put("alternating", seqParams.isAlternating());
                    if (!seqParams.getExtraParams().isEmpty()) {
                        clipDTO.sequenceParams.put("extraParams", new HashMap<>(seqParams.getExtraParams()));
                    }
                }
                if (clip.getBoundContainerId() != null) {
                    clipDTO.boundContainerId = clip.getBoundContainerId().toString();
                }
                clipDTO.boundContainerName = clip.getBoundContainerName();
                clipDTO.autoFadePreviousClips = clip.isAutoFadePreviousClips();
                clipDTO.autoFadeOpacity = clip.getAutoFadeOpacity();
                
                trackDTO.clips.add(clipDTO);
            }
            
            dto.tracks.add(trackDTO);
        }
        
        return dto;
    }
    
    /**
     * 从 DTO 恢复 TimelineModel
     */
    public static void fromDTO(TimelineDTO dto, TimelineModel model, TimelineViewportState viewportState) {
        if (dto == null) {
            LOGGER.warn("[TimelineModelPersistence] DTO 为空，跳过恢复");
            return;
        }
        
        // 恢复视图状态
        if (viewportState != null) {
            viewportState.setPixelsPerSecond(dto.pixelsPerSecond);
            viewportState.setScrollX(dto.scrollX);
        } else {
            model.setZoom(dto.pixelsPerSecond);
            model.setScrollX(dto.scrollX);
        }
        model.setSnappingEnabled(dto.snapEnabled);
        model.getSnapSettings().setGridInterval(dto.snapInterval);
        
        // 清空现有轨道
        model.getTracks().clear();
        
        // 恢复所有轨道
        for (TrackDTO trackDTO : dto.tracks) {
            // 创建轨道
            TrackType type = null;
            if (trackDTO.typeName != null) {
                try {
                    type = TrackType.valueOf(trackDTO.typeName);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[TimelineModelPersistence] 未知的轨道类型: {}", trackDTO.typeName);
                }
            }
            
            TimelineTrack track = new TimelineTrack(trackDTO.name, type);
            track.setLocked(trackDTO.locked);
            track.setVisible(trackDTO.visible);
            track.setZOrder(trackDTO.zOrder);
            track.setMuted(trackDTO.muted);
            track.setSolo(trackDTO.solo);
            track.setBoundContainerName(trackDTO.boundContainerName);
            track.setBoundContainerId(trackDTO.boundContainerId);
            
            // 恢复所有片段
            for (ClipDTO clipDTO : trackDTO.clips) {
                TimelineClip clip = new TimelineClip(
                    clipDTO.label,
                    clipDTO.startTime,
                    clipDTO.endTime,
                    clipDTO.color
                );
                clip.setMuted(clipDTO.muted);
                clip.setOpacity(clipDTO.opacity);
                clip.setAutoFadePreviousClips(clipDTO.autoFadePreviousClips);
                clip.setAutoFadeOpacity(clipDTO.autoFadeOpacity);
                ClipAnimationConfig config = clip.getAnimationConfig();
                if (clipDTO.animationMode != null) {
                    try {
                        config.setMode(AnimationMode.valueOf(clipDTO.animationMode));
                    } catch (IllegalArgumentException ignored) {
                        LOGGER.warn("[TimelineModelPersistence] 未知的动画模式: {}", clipDTO.animationMode);
                    }
                }
                config.setDuration(clipDTO.animationDuration > 0.0f ? clipDTO.animationDuration : config.getDuration());
                config.setDelay(Math.max(0.0f, clipDTO.animationDelay));
                if (clipDTO.animationEasing != null) {
                    config.setEasing(clipDTO.animationEasing);
                }
                if (clipDTO.animationOrdering != null) {
                    config.setOrdering(clipDTO.animationOrdering);
                }
                if (clipDTO.animationParams != null && !clipDTO.animationParams.isEmpty()) {
                    config.getParams().clear();
                    config.getParams().putAll(clipDTO.animationParams);
                }
                // 序列策略配置
                if (clipDTO.sequenceMode != null) {
                    try {
                        SequenceMode loadedMode = SequenceMode.valueOf(clipDTO.sequenceMode);
                        // 只接受 EVALUATOR_BASED，其他模式自动使用默认值
                        if (loadedMode == SequenceMode.EVALUATOR_BASED) {
                            config.setSequenceMode(loadedMode);
                        } else {
                            LOGGER.warn("[TimelineModelPersistence] 检测到废弃的序列模式: {}，已自动迁移到 EVALUATOR_BASED", clipDTO.sequenceMode);
                            config.setSequenceMode(SequenceMode.EVALUATOR_BASED);
                        }
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[TimelineModelPersistence] 未知的序列模式: {}，使用默认值", clipDTO.sequenceMode);
                        config.setSequenceMode(SequenceMode.EVALUATOR_BASED);
                    }
                } else {
                    // 如果没有序列模式，使用新系统作为默认值
                    config.setSequenceMode(SequenceMode.EVALUATOR_BASED);
                }
                SequenceParameters seqParams = config.getSequenceParameters();
                if (seqParams != null && clipDTO.sequenceParams != null && !clipDTO.sequenceParams.isEmpty()) {
                    Map<String, Object> raw = clipDTO.sequenceParams;
                    seqParams.setFlowType(parseEnum(raw.get("flowType"), FlowType.class, seqParams.getFlowType()));
                    seqParams.setFillType(parseEnum(raw.get("fillType"), FillType.class, seqParams.getFillType()));
                    seqParams.setOriginType(parseEnum(raw.get("originType"), OriginType.class, seqParams.getOriginType()));
                    seqParams.setPlaneYaw(asFloat(raw.get("planeYaw"), seqParams.getPlaneYaw()));
                    seqParams.setPlanePitch(asFloat(raw.get("planePitch"), seqParams.getPlanePitch()));
                    int customX = asInt(raw.get("customX"), seqParams.getCustomX());
                    int customY = asInt(raw.get("customY"), seqParams.getCustomY());
                    int customZ = asInt(raw.get("customZ"), seqParams.getCustomZ());
                    seqParams.setCustomXYZ(customX, customY, customZ);
                    seqParams.setPriorityWeight(asFloat(raw.get("priorityWeight"), seqParams.getPriorityWeight()));
                    seqParams.setReverse(asBoolean(raw.get("reverse"), seqParams.isReverse()));
                    seqParams.setAlternating(asBoolean(raw.get("alternating"), seqParams.isAlternating()));

                    Map<String, Object> extras = new HashMap<>();
                    Object extraObj = raw.get("extraParams");
                    if (extraObj instanceof Map<?, ?> extraMap) {
                        extras.putAll(toStringObjectMap(extraMap));
                    }
                    for (Map.Entry<String, Object> entry : raw.entrySet()) {
                        if (!RESERVED_SEQUENCE_KEYS.contains(entry.getKey())) {
                            extras.put(entry.getKey(), entry.getValue());
                        }
                    }
                    seqParams.getExtraParams().clear();
                    seqParams.getExtraParams().putAll(extras);
                }

                if (clipDTO.boundContainerId != null && !clipDTO.boundContainerId.isBlank()) {
                    try {
                        clip.setBoundContainerId(UUID.fromString(clipDTO.boundContainerId));
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[TimelineModelPersistence] 无法解析容器 ID: {}", clipDTO.boundContainerId);
                    }
                }
                clip.setBoundContainerName(clipDTO.boundContainerName);
                
                // 恢复关键帧
                for (Float keyframe : clipDTO.keyframes) {
                    clip.addKeyframe(keyframe);
                }
                
                track.addClip(clip);
            }
            
            model.addTrack(track);
        }
        
        LOGGER.info("[TimelineModelPersistence] 成功恢复 {} 条轨道", dto.tracks.size());
    }
    
    /**
     * 保存 TimelineModel 到文件（按世界）
     */
    public static void saveToFile(TimelineModel model, TimelineViewportState viewportState) {
        try {
            Path timelineFile = getTimelineFile();
            
            // 确保配置目录存在
            if (!Files.exists(timelineFile.getParent())) {
                Files.createDirectories(timelineFile.getParent());
            }
            
            // 转换为 DTO 并保存
            TimelineDTO dto = toDTO(model, viewportState);
            String json = GSON.toJson(dto);
            Files.writeString(timelineFile, json);
        } catch (IOException e) {
            LOGGER.error("[TimelineModelPersistence] 保存时间线数据失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 从文件加载 TimelineModel（按世界）
     */
    public static void loadFromFile(TimelineModel model, TimelineViewportState viewportState) {
        try {
            Path timelineFile = getTimelineFile();
            
            if (Files.exists(timelineFile)) {
                String json = Files.readString(timelineFile);
                TimelineDTO dto = GSON.fromJson(json, TimelineDTO.class);
                
                if (!isFingerprintMatch(dto.worldFingerprint)) {
                    LOGGER.warn("[TimelineModelPersistence] 世界指纹不匹配，忽略旧时间线并删除文件: {}", timelineFile);
                    Files.deleteIfExists(timelineFile);
                    return;
                }
                
                fromDTO(dto, model, viewportState);
                LOGGER.info("[TimelineModelPersistence] 世界 {} 的时间线数据已加载: {}", 
                    WorldIdentifier.getCurrentWorldId(), timelineFile);
            } else {
                LOGGER.info("[TimelineModelPersistence] 世界 {} 的时间线数据文件不存在，使用默认数据", 
                    WorldIdentifier.getCurrentWorldId());
            }
        } catch (IOException e) {
            LOGGER.error("[TimelineModelPersistence] 加载时间线数据失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("[TimelineModelPersistence] 解析时间线数据失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 检查是否存在保存的数据（当前世界）
     */
    public static boolean hasSavedData() {
        return Files.exists(getTimelineFile());
    }

    private static String getWorldFingerprint() {
        try {
            java.nio.file.Path path = com.chronoblocks.util.WorldIdentifier.getWorldSavePath();
            if (path != null && Files.exists(path)) {
                long modified = Files.getLastModifiedTime(path).toMillis();
                return path.toAbsolutePath().toString() + "#" + modified;
            }
        } catch (Exception e) {
            LOGGER.warn("[TimelineModelPersistence] 获取世界指纹失败: {}", e.getMessage());
        }
        // 退化为 worldId，避免 null
        return WorldIdentifier.getCurrentWorldId();
    }

    private static boolean isFingerprintMatch(String saved) {
        String current = getWorldFingerprint();
        return current != null && current.equals(saved);
    }

    /**
     * 删除当前世界的时间线存档文件，用于“新建动画”或存档重置。
     */
    public static void resetForCurrentWorld() {
        try {
            Path file = getTimelineFile();
            if (Files.exists(file)) {
                Files.delete(file);
                LOGGER.info("[TimelineModelPersistence] 已删除世界 {} 的时间线存档: {}", 
                    WorldIdentifier.getCurrentWorldId(), file);
            }
        } catch (IOException e) {
            LOGGER.warn("[TimelineModelPersistence] 删除时间线存档失败: {}", e.getMessage(), e);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Object value, Class<E> type, E defaultValue) {
        if (value instanceof String s) {
            try {
                return Enum.valueOf(type, s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return defaultValue;
    }

    private static float asFloat(Object value, float defaultValue) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String s) {
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
