package com.chronoblocks.ui.timeline.persistence;

import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.animation.sequence.SequenceMode;
import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.core.animation.sequence.evaluator.FillType;
import com.chronoblocks.core.animation.sequence.evaluator.FlowType;
import com.chronoblocks.core.animation.sequence.evaluator.OriginType;
import com.chronoblocks.ui.timeline.audio.TimelineAudioService;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TimelineInterpolation;
import com.chronoblocks.ui.timeline.model.TimelineViewportState;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.audio.AudioClipKeyframe;
import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import com.chronoblocks.ui.timeline.model.audio.AudioTrack;
import com.chronoblocks.ui.timeline.model.audio.AudioTimelineClip;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import com.chronoblocks.ui.timeline.model.clip.ClipKeyframe;
import com.chronoblocks.ui.timeline.model.season.SeasonKeyframe;
import com.chronoblocks.ui.timeline.model.time.TimeKeyframe;
import com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe;
import com.chronoblocks.ui.timeline.model.VisibilityKeyframe;
import com.chronoblocks.util.WorldIdentifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
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
        Path worldDir = getWorldChronoBlocksDir();
        if (worldDir != null) {
            // ✅ 最佳实践：把工程文件写进世界存档目录，天然“仅绑定当前存档”
            return worldDir.resolve("timeline_model.json");
        }
        // 兜底：写入 config（用于无法获取世界目录的场景）
        String worldId = WorldIdentifier.getCurrentWorldId();
        return CONFIG_DIR.resolve("timelines").resolve(worldId + "_timeline_model.json");
    }

    /**
     * 旧版（config/chronoblocks/timelines）路径：用于迁移。
     */
    private static Path getLegacyTimelineFile() {
        String worldId = WorldIdentifier.getCurrentWorldId();
        return CONFIG_DIR.resolve("timelines").resolve(worldId + "_timeline_model.json");
    }

    /**
     * 获取当前世界的 ChronoBlocks 工作目录（saves/<world>/chronoblocks），不存在则返回 null。
     */
    private static Path getWorldChronoBlocksDir() {
        try {
            Path savePath = WorldIdentifier.getWorldSavePath();
            if (savePath != null) {
                return savePath.resolve("chronoblocks");
            }
        } catch (Exception ignored) {
        }
        return null;
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
        public List<AutomationKeyframeDTO> automationKeyframes = new ArrayList<>();
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
        /** 旧版（向后兼容）：仅保存关键帧时间点 */
        public List<Float> keyframes = new ArrayList<>();
        /** 新版：ClipKeyframe 对象（支持插值/选择/属性面板/UndoRedo） */
        public List<ClipKeyframeDTO> clipKeyframes = new ArrayList<>();
        /** 可见性关键帧（淡入淡出/隐藏显示等） */
        public List<VisibilityKeyframeDTO> visibilityKeyframes = new ArrayList<>();
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
     * 三维向量 DTO（避免直接序列化 JOML Vector3f）
     */
    public static final class Vec3DTO {
        public float x;
        public float y;
        public float z;

        public Vec3DTO() {}

        public Vec3DTO(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public static Vec3DTO from(org.joml.Vector3f v) {
            if (v == null) return null;
            return new Vec3DTO(v.x, v.y, v.z);
        }
    }

    /**
     * ClipKeyframe DTO（新版片段关键帧）
     */
    public static final class ClipKeyframeDTO {
        public float time;
        public String interpolation; // TimelineInterpolation.name()
        public Float audioVolume;
    }

    /**
     * VisibilityKeyframe DTO
     */
    public static final class VisibilityKeyframeDTO {
        public float time; // 相对 clip 开始时间（秒）
        public float alpha;
        public String mode; // VisibilityMode.name()
    }

    /**
     * 轨道级自动化关键帧 DTO（相机/时间/天气/季节等）
     */
    public static final class AutomationKeyframeDTO {
        public String type; // CameraKeyframe / TimeKeyframe / WeatherKeyframe / SeasonKeyframe
        public String name;
        public float time;

        // ===== AudioEvent =====
        public String audioLabel;
        public String audioSoundId;
        public float audioVolume = 1.0f;
        public float audioPitch = 1.0f;
        public boolean audioEnabled = true;

        // ===== CameraKeyframe =====
        public Vec3DTO position;
        public Vec3DTO rotation;
        public float fov;
        public float nearPlane;
        public float farPlane;
        public String cameraKeyType;
        public String cameraSegmentType;
        public String cameraRotationInterpolation;
        public Vec3DTO bezierInHandle;
        public Vec3DTO bezierOutHandle;
        public String cameraHandleMode;
        public boolean dofEnabled;
        public float focusDistance;
        public float aperture;

        // ===== TimeKeyframe =====
        public long worldTimeTicks;
        public boolean useNaturalTime;
        public float sunElevation;
        public float sunAzimuth;
        public String timePathInterpolation;
        public String timeAngleInterpolation;

        // ===== WeatherKeyframe =====
        public String weatherType;
        public String weatherInterpolation;
        public float rainStrength;
        public String rainType;
        public float rainParticleDensity;
        public float puddleLevel;
        public float thunderStrength;
        public float lightningFrequency;
        public float windStrength;
        public float stormDarkness;
        public float cloudDensity;
        public float cloudSpeed;
        public float fogDensity;
        public Vec3DTO fogColor;
        public float tempBias;
        public float humidityBias;
        public String specialWeatherType;
        public float specialWeatherIntensity;

        // ===== SeasonKeyframe =====
        public String seasonType;
        public float snowlineHeight;
        public float snowAmount;
        public int snowThickness;
        public float freezeStrength;
        public Vec3DTO grassTint;
        public Vec3DTO foliageTint;
        public Vec3DTO skyTint;
        public Vec3DTO fogTint;
        public Vec3DTO lightTint;
        public String seasonInterpolation;
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

            // 保存轨道级自动化关键帧（相机/时间/天气/季节等）
            for (var selectable : track.getAutomationKeyframes()) {
                if (selectable == null) continue;
                AutomationKeyframeDTO kdto = new AutomationKeyframeDTO();
                kdto.type = selectable.getType();
                kdto.name = selectable.getName();
                kdto.time = selectable.getStartTime();

                switch (selectable) {
                    case CameraKeyframe ck -> {
                        kdto.position = Vec3DTO.from(ck.getPosition());
                        kdto.rotation = Vec3DTO.from(ck.getRotation());
                        kdto.fov = ck.getFov();
                        kdto.nearPlane = ck.getNearPlane();
                        kdto.farPlane = ck.getFarPlane();
                        kdto.cameraKeyType = ck.getKeyType() != null ? ck.getKeyType().name() : null;
                        kdto.cameraSegmentType = ck.getSegmentType() != null ? ck.getSegmentType().name() : null;
                        kdto.cameraRotationInterpolation = ck.getRotationInterpolation() != null ? ck.getRotationInterpolation().name() : null;
                        kdto.bezierInHandle = Vec3DTO.from(ck.getBezierInHandle());
                        kdto.bezierOutHandle = Vec3DTO.from(ck.getBezierOutHandle());
                        kdto.cameraHandleMode = ck.getHandleMode() != null ? ck.getHandleMode().name() : null;
                        kdto.dofEnabled = ck.isDepthOfFieldEnabled();
                        kdto.focusDistance = ck.getFocusDistance();
                        kdto.aperture = ck.getAperture();
                    }
                    case TimeKeyframe tk -> {
                        kdto.worldTimeTicks = tk.getWorldTimeTicks();
                        kdto.useNaturalTime = tk.isUseNaturalTime();
                        kdto.sunElevation = tk.getSunElevation();
                        kdto.sunAzimuth = tk.getSunAzimuth();
                        kdto.timePathInterpolation = tk.getPathInterpolation() != null ? tk.getPathInterpolation().name() : null;
                        kdto.timeAngleInterpolation = tk.getAngleInterpolation() != null ? tk.getAngleInterpolation().name() : null;
                    }
                    case WeatherKeyframe wk -> {
                        kdto.weatherType = wk.getWeatherType() != null ? wk.getWeatherType().name() : null;
                        kdto.weatherInterpolation = wk.getInterpolation() != null ? wk.getInterpolation().name() : null;
                        kdto.rainStrength = wk.getRainStrength();
                        kdto.rainType = wk.getRainType() != null ? wk.getRainType().name() : null;
                        kdto.rainParticleDensity = wk.getRainParticleDensity();
                        kdto.puddleLevel = wk.getPuddleLevel();
                        kdto.thunderStrength = wk.getThunderStrength();
                        kdto.lightningFrequency = wk.getLightningFrequency();
                        kdto.windStrength = wk.getWindStrength();
                        kdto.stormDarkness = wk.getStormDarkness();
                        kdto.cloudDensity = wk.getCloudDensity();
                        kdto.cloudSpeed = wk.getCloudSpeed();
                        kdto.fogDensity = wk.getFogDensity();
                        kdto.fogColor = Vec3DTO.from(wk.getFogColor());
                        kdto.tempBias = wk.getTempBias();
                        kdto.humidityBias = wk.getHumidityBias();
                        kdto.specialWeatherType = wk.getSpecialWeatherType() != null ? wk.getSpecialWeatherType().name() : null;
                        kdto.specialWeatherIntensity = wk.getSpecialWeatherIntensity();
                    }
                    case SeasonKeyframe sk -> {
                        kdto.seasonType = sk.getSeasonType() != null ? sk.getSeasonType().name() : null;
                        kdto.snowlineHeight = sk.getSnowlineHeight();
                        kdto.snowAmount = sk.getSnowAmount();
                        kdto.snowThickness = sk.getSnowThickness();
                        kdto.freezeStrength = sk.getFreezeStrength();
                        kdto.grassTint = Vec3DTO.from(sk.getGrassTint());
                        kdto.foliageTint = Vec3DTO.from(sk.getFoliageTint());
                        kdto.skyTint = Vec3DTO.from(sk.getSkyTint());
                        kdto.fogTint = Vec3DTO.from(sk.getFogTint());
                        kdto.lightTint = Vec3DTO.from(sk.getLightTint());
                        kdto.seasonInterpolation = sk.getInterpolation() != null ? sk.getInterpolation().name() : null;
                    }
                    case AudioEvent ae -> {
                        kdto.audioLabel = ae.getLabel();
                        kdto.audioSoundId = ae.getSoundId();
                        kdto.audioVolume = ae.getVolume();
                        kdto.audioPitch = ae.getPitch();
                        kdto.audioEnabled = ae.isEnabled();
                    }
                    default -> {
                    }
                }

                trackDTO.automationKeyframes.add(kdto);
            }
            
            // 保存轨道的所有片段
            for (TimelineClip clip : track.getClips()) {
                ClipDTO clipDTO = new ClipDTO();
                clipDTO.label = clip.getLabel();
                clipDTO.startTime = clip.getStartTime();
                clipDTO.endTime = clip.getEndTime();
                clipDTO.color = clip.getColor();
                clipDTO.muted = clip.isMuted();
                clipDTO.opacity = clip.getOpacity();
                // 旧版：float 时间点关键帧（向后兼容）
                clipDTO.keyframes.addAll(clip.getKeyframes());

                // 新版：ClipKeyframe 对象关键帧
                for (ClipKeyframe ckf : clip.getClipKeyframes()) {
                    if (ckf == null) continue;
                    ClipKeyframeDTO ckfdto = new ClipKeyframeDTO();
                    ckfdto.time = ckf.getTime();
                    ckfdto.interpolation = ckf.getInterpolation() != null ? ckf.getInterpolation().name() : TimelineInterpolation.LINEAR.name();
                    if (ckf instanceof AudioClipKeyframe audioKeyframe) {
                        ckfdto.audioVolume = audioKeyframe.getVolume();
                    }
                    clipDTO.clipKeyframes.add(ckfdto);
                }

                // 可见性关键帧
                for (VisibilityKeyframe vkf : clip.getVisibilityKeyframes()) {
                    if (vkf == null) continue;
                    VisibilityKeyframeDTO vdto = new VisibilityKeyframeDTO();
                    vdto.time = vkf.getTime();
                    vdto.alpha = vkf.getAlpha();
                    vdto.mode = vkf.getMode() != null ? vkf.getMode().name() : VisibilityKeyframe.VisibilityMode.NORMAL.name();
                    clipDTO.visibilityKeyframes.add(vdto);
                }

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

        TimelineAudioService audioService = TimelineAudioService.getInstance();
        boolean prevSuppress = audioService.isSuppressAutoCreate();
        boolean loaded = false;
        audioService.setSuppressAutoCreate(true);
        try {
        
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
            TrackType type = resolveTrackType(trackDTO);
            
            // 根据轨道类型创建正确的轨道实例
            TimelineTrack track = getTimelineTrack(trackDTO, type);

            // 恢复轨道级自动化关键帧
            if (trackDTO.automationKeyframes != null) {
                for (AutomationKeyframeDTO kdto : trackDTO.automationKeyframes) {
                    if (kdto == null || kdto.type == null) continue;
                    try {
                        switch (kdto.type) {
                            case "CameraKeyframe" -> {
                                CameraKeyframe ck = new CameraKeyframe(kdto.name != null ? kdto.name : "Camera Keyframe", kdto.time);
                                if (kdto.position != null) ck.setPosition(new org.joml.Vector3f(kdto.position.x, kdto.position.y, kdto.position.z));
                                if (kdto.rotation != null) ck.setRotation(new org.joml.Vector3f(kdto.rotation.x, kdto.rotation.y, kdto.rotation.z));
                                ck.setFov(kdto.fov);
                                ck.setNearPlane(kdto.nearPlane);
                                ck.setFarPlane(kdto.farPlane);
                                if (kdto.cameraKeyType != null) {
                                    try { ck.setKeyType(CameraKeyframe.KeyType.valueOf(kdto.cameraKeyType)); } catch (Exception ignored) {}
                                }
                                if (kdto.cameraSegmentType != null) {
                                    try { ck.setSegmentType(CameraKeyframe.SegmentPathType.valueOf(kdto.cameraSegmentType)); } catch (Exception ignored) {}
                                }
                                if (kdto.cameraRotationInterpolation != null) {
                                    try { ck.setRotationInterpolation(CameraKeyframe.RotationInterpolation.valueOf(kdto.cameraRotationInterpolation)); } catch (Exception ignored) {}
                                }
                                if (kdto.bezierInHandle != null) ck.setBezierInHandle(new org.joml.Vector3f(kdto.bezierInHandle.x, kdto.bezierInHandle.y, kdto.bezierInHandle.z));
                                if (kdto.bezierOutHandle != null) ck.setBezierOutHandle(new org.joml.Vector3f(kdto.bezierOutHandle.x, kdto.bezierOutHandle.y, kdto.bezierOutHandle.z));
                                if (kdto.cameraHandleMode != null) {
                                    try { ck.setHandleMode(CameraKeyframe.HandleMode.valueOf(kdto.cameraHandleMode)); } catch (Exception ignored) {}
                                }
                                ck.setDepthOfFieldEnabled(kdto.dofEnabled);
                                ck.setFocusDistance(kdto.focusDistance);
                                ck.setAperture(kdto.aperture);
                                track.addAutomationKeyframe(ck);
                            }
                            case "TimeKeyframe" -> {
                                TimeKeyframe tk = new TimeKeyframe(kdto.name != null ? kdto.name : "Time Keyframe", kdto.time);
                                tk.setWorldTimeTicks(kdto.worldTimeTicks);
                                tk.setUseNaturalTime(kdto.useNaturalTime);
                                tk.setSunElevation(kdto.sunElevation);
                                tk.setSunAzimuth(kdto.sunAzimuth);
                                if (kdto.timePathInterpolation != null) {
                                    try { tk.setPathInterpolation(TimeKeyframe.SegmentInterpolation.valueOf(kdto.timePathInterpolation)); } catch (Exception ignored) {}
                                }
                                if (kdto.timeAngleInterpolation != null) {
                                    try { tk.setAngleInterpolation(TimeKeyframe.AngleInterpolation.valueOf(kdto.timeAngleInterpolation)); } catch (Exception ignored) {}
                                }
                                track.addAutomationKeyframe(tk);
                            }
                            case "WeatherKeyframe" -> {
                                WeatherKeyframe wk = new WeatherKeyframe(kdto.name != null ? kdto.name : "Weather Keyframe", kdto.time);
                                if (kdto.weatherType != null) {
                                    try { wk.setWeatherType(WeatherKeyframe.WeatherType.valueOf(kdto.weatherType)); } catch (Exception ignored) {}
                                }
                                if (kdto.weatherInterpolation != null) {
                                    try { wk.setInterpolation(TimelineInterpolation.valueOf(kdto.weatherInterpolation)); } catch (Exception ignored) {}
                                }
                                wk.setRainStrength(kdto.rainStrength);
                                if (kdto.rainType != null) {
                                    try { wk.setRainType(WeatherKeyframe.RainType.valueOf(kdto.rainType)); } catch (Exception ignored) {}
                                }
                                wk.setRainParticleDensity(kdto.rainParticleDensity);
                                wk.setPuddleLevel(kdto.puddleLevel);
                                wk.setThunderStrength(kdto.thunderStrength);
                                wk.setLightningFrequency(kdto.lightningFrequency);
                                wk.setWindStrength(kdto.windStrength);
                                wk.setStormDarkness(kdto.stormDarkness);
                                wk.setCloudDensity(kdto.cloudDensity);
                                wk.setCloudSpeed(kdto.cloudSpeed);
                                wk.setFogDensity(kdto.fogDensity);
                                if (kdto.fogColor != null) wk.setFogColor(kdto.fogColor.x, kdto.fogColor.y, kdto.fogColor.z);
                                wk.setTempBias(kdto.tempBias);
                                wk.setHumidityBias(kdto.humidityBias);
                                if (kdto.specialWeatherType != null) {
                                    try { wk.setSpecialWeatherType(WeatherKeyframe.SpecialWeatherType.valueOf(kdto.specialWeatherType)); } catch (Exception ignored) {}
                                }
                                wk.setSpecialWeatherIntensity(kdto.specialWeatherIntensity);
                                track.addAutomationKeyframe(wk);
                            }
                            case "SeasonKeyframe" -> {
                                SeasonKeyframe sk = new SeasonKeyframe(kdto.name != null ? kdto.name : "Season Keyframe", kdto.time);
                                if (kdto.seasonType != null) {
                                    try { sk.applySeasonPreset(SeasonKeyframe.SeasonType.valueOf(kdto.seasonType)); } catch (Exception ignored) {}
                                }
                                sk.setSnowlineHeight(kdto.snowlineHeight);
                                sk.setSnowAmount(kdto.snowAmount);
                                sk.setSnowThickness(kdto.snowThickness);
                                sk.setFreezeStrength(kdto.freezeStrength);
                                if (kdto.grassTint != null) sk.setGrassTint(new org.joml.Vector3f(kdto.grassTint.x, kdto.grassTint.y, kdto.grassTint.z));
                                if (kdto.foliageTint != null) sk.setFoliageTint(new org.joml.Vector3f(kdto.foliageTint.x, kdto.foliageTint.y, kdto.foliageTint.z));
                                if (kdto.skyTint != null) sk.setSkyTint(new org.joml.Vector3f(kdto.skyTint.x, kdto.skyTint.y, kdto.skyTint.z));
                                if (kdto.fogTint != null) sk.setFogTint(new org.joml.Vector3f(kdto.fogTint.x, kdto.fogTint.y, kdto.fogTint.z));
                                if (kdto.lightTint != null) sk.setLightTint(new org.joml.Vector3f(kdto.lightTint.x, kdto.lightTint.y, kdto.lightTint.z));
                                if (kdto.seasonInterpolation != null) {
                                    try { sk.setInterpolation(SeasonKeyframe.Interpolation.valueOf(kdto.seasonInterpolation)); } catch (Exception ignored) {}
                                }
                                track.addAutomationKeyframe(sk);
                            }
                            case "AudioEvent" -> {
                                AudioEvent ae = getAudioEvent(kdto);
                                track.addAutomationKeyframe(ae);
                            }
                            default -> {
                                // 未知类型：忽略，避免加载失败
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("[TimelineModelPersistence] 恢复自动化关键帧失败: {} @ {}s ({})",
                                kdto.type, kdto.time, e.getMessage());
                    }
                }
            }
            
            // 恢复所有片段
            for (ClipDTO clipDTO : trackDTO.clips) {
                // ✅ AUDIO 轨道上的片段必须是 AudioTimelineClip，否则会被轨道约束误判并拒绝加入轨道，导致“重启后音频面板丢失”。
                TimelineClip clip = (track.getType() == TrackType.AUDIO)
                        ? new AudioTimelineClip(clipDTO.label, clipDTO.startTime, clipDTO.endTime, clipDTO.color)
                        : new TimelineClip(clipDTO.label, clipDTO.startTime, clipDTO.endTime, clipDTO.color);
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

                // 恢复新版 ClipKeyframe
                if (clipDTO.clipKeyframes != null) {
                    for (ClipKeyframeDTO ckfdto : clipDTO.clipKeyframes) {
                        if (ckfdto == null) continue;
                        TimelineInterpolation interp = TimelineInterpolation.LINEAR;
                        if (ckfdto.interpolation != null) {
                            try { interp = TimelineInterpolation.valueOf(ckfdto.interpolation); } catch (Exception ignored) {}
                        }
                        ClipKeyframe ckf;
                        if (track.getType() == TrackType.AUDIO || ckfdto.audioVolume != null) {
                            float volume = ckfdto.audioVolume != null ? ckfdto.audioVolume : 1.0f;
                            ckf = new AudioClipKeyframe(ckfdto.time, interp, volume);
                        } else {
                            ckf = new ClipKeyframe(ckfdto.time, interp);
                        }
                        clip.addKeyframe(ckf);
                    }
                }

                // 恢复可见性关键帧
                if (clipDTO.visibilityKeyframes != null) {
                    for (VisibilityKeyframeDTO vdto : clipDTO.visibilityKeyframes) {
                        if (vdto == null) continue;
                        VisibilityKeyframe.VisibilityMode mode = VisibilityKeyframe.VisibilityMode.NORMAL;
                        if (vdto.mode != null) {
                            try { mode = VisibilityKeyframe.VisibilityMode.valueOf(vdto.mode); } catch (Exception ignored) {}
                        }
                        try {
                            clip.addVisibilityKeyframe(new VisibilityKeyframe(vdto.time, vdto.alpha, mode));
                        } catch (Exception ignored) {
                            // 保持加载流程不中断
                        }
                    }
                }
                
                track.addClip(clip);
            }
            
            model.addTrack(track);
        }
        
        LOGGER.info("[TimelineModelPersistence] 成功恢复 {} 条轨道", dto.tracks.size());
        loaded = true;
    
        } finally {
            audioService.setSuppressAutoCreate(prevSuppress);
        }
        if (loaded) {
            audioService.setTimelineModel(model);
        }
}

    private static TrackType resolveTrackType(TrackDTO trackDTO) {
        if (trackDTO == null) {
            return TrackType.CONTAINER;
        }
        TrackType type = null;
        if (trackDTO.typeName != null) {
            try {
                type = TrackType.valueOf(trackDTO.typeName);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[TimelineModelPersistence] 未知的轨道类型: {}", trackDTO.typeName);
            }
        }
        if (type != null) {
            return type;
        }
        if (looksLikeAudioTrack(trackDTO)) {
            return TrackType.AUDIO;
        }
        return TrackType.CONTAINER;
    }

    private static boolean looksLikeAudioTrack(TrackDTO trackDTO) {
        if (trackDTO == null) {
            return false;
        }
        if (trackDTO.automationKeyframes != null) {
            for (AutomationKeyframeDTO kdto : trackDTO.automationKeyframes) {
                if (kdto != null && "AudioEvent".equals(kdto.type)) {
                    return true;
                }
            }
        }
        if (trackDTO.clips != null) {
            for (ClipDTO clipDTO : trackDTO.clips) {
                if (clipDTO == null || clipDTO.clipKeyframes == null) {
                    continue;
                }
                for (ClipKeyframeDTO ckf : clipDTO.clipKeyframes) {
                    if (ckf != null && ckf.audioVolume != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static @NotNull AudioEvent getAudioEvent(AutomationKeyframeDTO kdto) {
        String label = (kdto.audioLabel != null && !kdto.audioLabel.isBlank())
                ? kdto.audioLabel
                : (kdto.name != null ? kdto.name : "音频事件");
        String soundId = kdto.audioSoundId != null ? kdto.audioSoundId : "";
        AudioEvent ae = new AudioEvent(label, soundId, kdto.time);
        ae.setVolume(kdto.audioVolume);
        ae.setPitch(kdto.audioPitch);
        ae.setEnabled(kdto.audioEnabled);
        return ae;
    }

    private static @NotNull TimelineTrack getTimelineTrack(TrackDTO trackDTO, TrackType type) {
        TimelineTrack track;
        if (type == TrackType.AUDIO) {
            track = new AudioTrack(trackDTO.name);
        } else {
            track = new TimelineTrack(trackDTO.name, type);
        }
        track.setLocked(trackDTO.locked);
        track.setVisible(trackDTO.visible);
        track.setZOrder(trackDTO.zOrder);
        track.setMuted(trackDTO.muted);
        track.setSolo(trackDTO.solo);
        track.setBoundContainerName(trackDTO.boundContainerName);
        track.setBoundContainerId(trackDTO.boundContainerId);
        return track;
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
            Path legacy = getLegacyTimelineFile();

            // 自动迁移：优先使用世界目录；若世界目录没有但旧 config 存在，则迁移一次
            if (!Files.exists(timelineFile) && Files.exists(legacy) && !legacy.equals(timelineFile)) {
                try {
                    if (!Files.exists(timelineFile.getParent())) {
                        Files.createDirectories(timelineFile.getParent());
                    }
                    Files.copy(legacy, timelineFile);
                    LOGGER.info("[TimelineModelPersistence] 已迁移时间线存档到世界目录: {} -> {}", legacy, timelineFile);
                } catch (Exception e) {
                    LOGGER.warn("[TimelineModelPersistence] 迁移时间线存档失败: {}", e.getMessage());
                }
            }
            
            if (Files.exists(timelineFile)) {
                String json = Files.readString(timelineFile);
                TimelineDTO dto = GSON.fromJson(json, TimelineDTO.class);

                // ⚠️ 历史问题修复：之前把 “目录修改时间” 放进 fingerprint，会导致关游戏/自动保存后指纹改变，误判并删档。
                // 现在仅做“软校验”：不匹配也不删除，最多记录警告并继续加载（因为文件本身已经按世界隔离）。
                if (dto != null && dto.worldFingerprint != null && !isFingerprintMatch(dto.worldFingerprint)) {
                    LOGGER.warn("[TimelineModelPersistence] 世界指纹不匹配（将继续尝试加载，不删除文件）: {}", timelineFile);
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
        Path primary = getTimelineFile();
        if (Files.exists(primary)) {
            return true;
        }
        // 兼容旧位置（用于升级迁移的首次启动）
        Path legacy = getLegacyTimelineFile();
        return Files.exists(legacy);
    }

    private static String getWorldFingerprint() {
        try {
            java.nio.file.Path path = com.chronoblocks.util.WorldIdentifier.getWorldSavePath();
            if (path != null && Files.exists(path)) {
                // ✅ 仅使用绝对路径作为稳定指纹（目录 mtime 会频繁变化，不适合作为身份）
                return path.toAbsolutePath().toString();
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

            // 兼容：同时删除旧位置的存档，避免“新建动画”后旧文件又被迁移回来
            Path legacy = getLegacyTimelineFile();
            if (Files.exists(legacy) && !legacy.equals(file)) {
                Files.delete(legacy);
                LOGGER.info("[TimelineModelPersistence] 已删除旧位置时间线存档: {}", legacy);
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
