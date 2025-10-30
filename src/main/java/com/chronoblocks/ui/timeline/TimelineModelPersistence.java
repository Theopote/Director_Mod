package com.chronoblocks.ui.timeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
    private static final Path TIMELINE_FILE = CONFIG_DIR.resolve("timeline_model.json");
    
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
    }
    
    /**
     * 将 TimelineModel 转换为 DTO
     */
    public static TimelineDTO toDTO(TimelineModel model) {
        TimelineDTO dto = new TimelineDTO();
        
        // 保存视图状态
        dto.pixelsPerSecond = model.getPixelsPerSecond();
        dto.scrollX = model.getScrollX();
        dto.snapEnabled = model.isSnapEnabled();
        dto.snapInterval = model.getSnapInterval();
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
                
                trackDTO.clips.add(clipDTO);
            }
            
            dto.tracks.add(trackDTO);
        }
        
        return dto;
    }
    
    /**
     * 从 DTO 恢复 TimelineModel
     */
    public static void fromDTO(TimelineDTO dto, TimelineModel model) {
        if (dto == null) {
            LOGGER.warn("[TimelineModelPersistence] DTO 为空，跳过恢复");
            return;
        }
        
        // 恢复视图状态
        model.setZoom(dto.pixelsPerSecond);
        model.setScrollX(dto.scrollX);
        model.setSnapEnabled(dto.snapEnabled);
        model.setSnapInterval(dto.snapInterval);
        
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
     * 保存 TimelineModel 到文件
     */
    public static void saveToFile(TimelineModel model) {
        try {
            // 确保配置目录存在
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            
            // 转换为 DTO 并保存
            TimelineDTO dto = toDTO(model);
            String json = GSON.toJson(dto);
            Files.writeString(TIMELINE_FILE, json);
            
            LOGGER.info("[TimelineModelPersistence] 时间线数据已保存: {}", TIMELINE_FILE);
        } catch (IOException e) {
            LOGGER.error("[TimelineModelPersistence] 保存时间线数据失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 从文件加载 TimelineModel
     */
    public static void loadFromFile(TimelineModel model) {
        try {
            if (Files.exists(TIMELINE_FILE)) {
                String json = Files.readString(TIMELINE_FILE);
                TimelineDTO dto = GSON.fromJson(json, TimelineDTO.class);
                fromDTO(dto, model);
                LOGGER.info("[TimelineModelPersistence] 时间线数据已加载: {}", TIMELINE_FILE);
            } else {
                LOGGER.info("[TimelineModelPersistence] 时间线数据文件不存在，使用默认数据");
            }
        } catch (IOException e) {
            LOGGER.error("[TimelineModelPersistence] 加载时间线数据失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("[TimelineModelPersistence] 解析时间线数据失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 检查是否存在保存的数据
     */
    public static boolean hasSavedData() {
        return Files.exists(TIMELINE_FILE);
    }
    
    /**
     * 删除保存的数据
     */
    public static void deleteSavedData() {
        try {
            if (Files.exists(TIMELINE_FILE)) {
                Files.delete(TIMELINE_FILE);
                LOGGER.info("[TimelineModelPersistence] 时间线数据已删除");
            }
        } catch (IOException e) {
            LOGGER.error("[TimelineModelPersistence] 删除时间线数据失败: {}", e.getMessage(), e);
        }
    }
}

