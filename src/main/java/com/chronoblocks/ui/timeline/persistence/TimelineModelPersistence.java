package com.chronoblocks.ui.timeline.persistence;

import com.chronoblocks.animation.timeline.ClipAnimationConfig;
import com.chronoblocks.core.container.AnimationMode;
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
        public String boundContainerId;
        public String boundContainerName;
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
                if (clip.getBoundContainerId() != null) {
                    clipDTO.boundContainerId = clip.getBoundContainerId().toString();
                }
                clipDTO.boundContainerName = clip.getBoundContainerName();
                
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
            
            LOGGER.info("[TimelineModelPersistence] 世界 {} 的时间线数据已保存: {}", 
                WorldIdentifier.getCurrentWorldId(), timelineFile);
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
    
    /**
     * 删除保存的数据（当前世界）
     */
    public static void deleteSavedData() {
        try {
            Path timelineFile = getTimelineFile();
            if (Files.exists(timelineFile)) {
                Files.delete(timelineFile);
                LOGGER.info("[TimelineModelPersistence] 世界 {} 的时间线数据已删除", 
                    WorldIdentifier.getCurrentWorldId());
            }
        } catch (IOException e) {
            LOGGER.error("[TimelineModelPersistence] 删除时间线数据失败: {}", e.getMessage(), e);
        }
    }
}