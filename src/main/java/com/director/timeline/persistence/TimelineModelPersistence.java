package com.director.timeline.persistence;

import com.director.core.animation.ClipAnimationConfig;
import com.director.persistence.AtomicFileWriter;
import com.director.core.container.AnimationMode;
import com.director.core.animation.sequence.SequenceMode;
import com.director.core.animation.sequence.SequenceParameters;
import com.director.core.animation.sequence.evaluator.FillType;
import com.director.core.animation.sequence.evaluator.FlowType;
import com.director.core.animation.sequence.evaluator.OriginType;
import com.director.timeline.audio.TimelineAudioLoadHook;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineMarker;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.audio.AudioClipKeyframe;
import com.director.timeline.model.audio.AudioEvent;
import com.director.timeline.model.audio.AudioTrack;
import com.director.timeline.model.audio.AudioTimelineClip;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.camera.path.CranePathClip;
import com.director.timeline.model.camera.path.CustomPathClip;
import com.director.timeline.model.camera.path.OrbitPathClip;
import com.director.timeline.model.clip.ClipKeyframe;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.EntityClipType;
import com.director.timeline.model.season.SeasonKeyframe;
import com.director.timeline.model.time.TimeKeyframe;
import com.director.timeline.model.weather.WeatherKeyframe;
import com.director.timeline.model.VisibilityKeyframe;
import com.director.util.ParseUtils;
import com.director.util.WorldIdentifier;
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
 * @author Director Team
 * @version 1.0
 */
public class TimelineModelPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineModelPersistence.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // 配置文件路径
    private static final Path CONFIG_DIR = Paths.get("config", "director");
    private static final Set<String> RESERVED_SEQUENCE_KEYS = Set.of(
        "layerStep", "floodSeedMode", "concurrencyMode", "batchSize", "batchInterval",
        "timeMappingStrategy", "perBlockDuration", "globalDelay",
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
        Path worldDir = getWorldDirectorDir();
        if (worldDir != null) {
            // ✅ 最佳实践：把工程文件写进世界存档目录，天然“仅绑定当前存档”
            return worldDir.resolve("timeline_model.json");
        }
        return null;
    }

    /**
     * 旧版（config/director/timelines）路径：用于迁移。
     */
    private static Path getLegacyTimelineFile() {
        String worldId = WorldIdentifier.getCurrentWorldId();
        if (worldId == null || worldId.isEmpty() || "default".equals(worldId)) {
            return null;
        }
        return CONFIG_DIR.resolve("timelines").resolve(worldId + "_timeline_model.json");
    }

    /**
     * 获取当前世界的 Director 工作目录（saves/<world>/director），不存在则返回 null。
     */
    private static Path getWorldDirectorDir() {
        try {
            Path savePath = WorldIdentifier.getWorldSavePath();
            if (savePath != null) {
                return savePath.resolve("director");
            }
        } catch (RuntimeException e) {
            LOGGER.debug("[TimelineModelPersistence] Cannot resolve world save path: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 时间线数据传输对象
     */
    public static class TimelineDTO {
        public List<TrackDTO> tracks = new ArrayList<>();
        public List<com.director.lighting.PointLight> pointLights = new ArrayList<>();
        public int lightingVersion;
        public float pixelsPerSecond = 80.0f;
        public float scrollX = 0f;
        public boolean snapEnabled = true;
        public float snapInterval = 0.1f;
        public boolean snapToGrid = true;
        public boolean snapToClipEdges = true;
        public boolean snapToKeyframes = true;
        public float snapPixelThreshold = 8.0f;
        public float frameRate = 30.0f;
        public int timeUnit = 0;
        public boolean loopEnabled = false;
        public float playbackSpeed = 1.0f;
        public Float duration = null;
        public Float inPoint = null;
        public Float outPoint = null;
        public List<MarkerDTO> markers = new ArrayList<>();
        public long timestamp;
        public String worldFingerprint;
    }

    public static class MarkerDTO {
        public String id;
        public String name;
        public float time;
        public int color = 0xFFBBBBBB;
        public String note;
    }
    
    /**
     * 轨道数据传输对象
     */
    public static class TrackDTO {
        public String name;
        public String typeName;  // TrackType 的名称
        public List<ClipDTO> clips = new ArrayList<>();
        public List<AutomationKeyframeDTO> automationKeyframes = new ArrayList<>();
        /** 摄像机路径片段列表 */
        public List<PathClipDTO> pathClips = new ArrayList<>();
        public boolean locked = false;
        public boolean visible = true;
        public int zOrder = 0;
        public boolean muted = false;
        public boolean solo = false;
        public String boundContainerName;
        public String boundContainerId;
        /** 演员轨道 payload（type=ACTOR 时有效） */
        public ActorTrackDTO actorTrackPayload;
    }

    /**
     * 演员轨道 DTO
     */
    public static final class ActorTrackDTO {
        public String actorRefId;       // ActorRef UUID
        public String displayName;
        public EntityBindingDTO binding;
        public boolean enabled = true;
        public int colorRgba = 0xFF66CCFF;
        public List<EntityClipDTO> clips = new ArrayList<>();
    }

    /**
     * 演员绑定 DTO
     */
    public static final class EntityBindingDTO {
        public String mode;             // EntityBinding.Mode.name()
        public String targetEntityUuid;
        public String targetPlayerName;
        public String entityTypeId = "minecraft:villager";
        public boolean persistent = true;
    }

    /**
     * EntityClip DTO（覆盖所有 7 种 clip 类型的全部字段）
     */
    public static final class EntityClipDTO {
        // ===== 公共字段 =====
        public String id;               // UUID
        public String clipType;         // EntityClipType.name()
        public String name;
        public float startTime;
        public float endTime;
        public boolean muted = false;
        public float weight = 1.0f;

        // 关键帧（EntityClipKeyframe）
        public List<EntityClipKeyframeDTO> keyframes = new ArrayList<>();

        // ===== EntitySpawnClip =====
        public Float spawnX, spawnY, spawnZ;
        public Float yawDeg, pitchDeg;
        public String spawnBoneAnimationId;
        public EntityClipDTO spawnPreset;
        // Raw optional values isolate malformed blend fields from the rest of the clip DTO.
        public com.google.gson.JsonElement poseBlendMode,poseBlendWeight,poseBlendChannels,poseBlendAllBones,
                poseBlendBones,poseBlendFadeIn,poseBlendFadeOut;
        public void setPoseBlend(com.director.actor.rig.skeleton.PoseBlendSettings settings) {
            poseBlendMode=new com.google.gson.JsonPrimitive(settings.mode().name());
            poseBlendWeight=new com.google.gson.JsonPrimitive(settings.weight());
            poseBlendChannels=new com.google.gson.JsonPrimitive(settings.channels());
            poseBlendAllBones=new com.google.gson.JsonPrimitive(settings.allBones());
            var bones=new com.google.gson.JsonArray();settings.boneIds().stream().sorted().forEach(bones::add);poseBlendBones=bones;
            poseBlendFadeIn=new com.google.gson.JsonPrimitive(settings.fadeIn());
            poseBlendFadeOut=new com.google.gson.JsonPrimitive(settings.fadeOut());
        }
        public void restorePoseBlend(EntityClip clip) {
            var data=new com.google.gson.JsonObject();
            if(poseBlendMode!=null)data.add("poseBlendMode",poseBlendMode);
            if(poseBlendWeight!=null)data.add("poseBlendWeight",poseBlendWeight);
            if(poseBlendChannels!=null)data.add("poseBlendChannels",poseBlendChannels);
            if(poseBlendAllBones!=null)data.add("poseBlendAllBones",poseBlendAllBones);
            if(poseBlendBones!=null)data.add("poseBlendBones",poseBlendBones);
            if(poseBlendFadeIn!=null)data.add("poseBlendFadeIn",poseBlendFadeIn);
            if(poseBlendFadeOut!=null)data.add("poseBlendFadeOut",poseBlendFadeOut);
            var restored=ActorPoseBlendRecovery.read(data);
            if(clip instanceof com.director.timeline.model.entity.EntitySpawnClip spawn) spawn.setBlendSettings(restored.settings());
            else if(clip instanceof com.director.timeline.model.entity.EntityAnimationClip animation) animation.setBlendSettings(restored.settings());
            clip.setLoadWarnings(restored.issues());
            if(!restored.issues().isEmpty()) LOGGER.warn("[TimelineModelPersistence] 已恢复片段 {} 的骨骼混合参数：{}",name,String.join("；",restored.issues()));
        }
        public Float spawnRoll, spawnScaleX, spawnScaleY, spawnScaleZ;
        public Boolean visibleOnSpawn;

        // ===== EntityTransformClip =====
        public String transformInterpolation;
        public Vec3DTO fromPos, toPos;
        public Vec3DTO fromRot, toRot;
        public Vec3DTO fromScale, toScale;
        public String pathId;
        public Boolean followPath;

        // ===== EntityLookAtClip =====
        public String lookAtMode;       // EntityLookAtClip.TargetMode.name()
        public Vec3DTO lookAtWorldPoint;
        public String lookAtTargetEntityUuid;
        public Float lookAtStrength;

        // ===== EntityAnimationClip =====
        public String animationSource;  // AnimationSource.name()
        public String animationId;
        public Boolean animationLoop;
        public Float animationSpeed;

        // ===== EntityStateClip =====
        public Boolean sneaking, sprinting, glowing, invulnerable;
        public Float stateVisibility;

        // ===== EntityActionClip =====
        public String actionType;       // EntityActionClip.ActionType.name()
        public Map<String, Object> actionParams = new HashMap<>();
        public Boolean triggerOnce;

        // ===== EntityDespawnClip =====
        public String despawnMode;      // EntityDespawnClip.DespawnMode.name()

        // ===== ActorActionClip =====
        public String actorActionType;  // ActorActionType.name()
        public Float actorActionIntensity;
        public Boolean actorActionLoop;

        // ===== ActorPoseClip =====
        public Boolean poseOverrideBody;
        public Boolean poseOverrideHead;
        public Boolean poseOverrideArms;
        public Boolean poseOverrideLegs;
        public Float poseBodyPitch, poseBodyYaw, poseBodyRoll;
        public Float poseHeadPitch, poseHeadYaw;
        public Float poseLeftArmPitch, poseRightArmPitch;
        public Float poseLeftArmYaw, poseLeftArmRoll;
        public Float poseRightArmYaw, poseRightArmRoll;
        public Float poseLeftLegPitch, poseRightLegPitch;

        // ===== ActorRouteClip =====
        public List<Vec3DTO> routeWaypoints = new ArrayList<>();
        public String routeInterpolationMode;
        public String routeLoopMode;
        public Boolean routeSmoothTravel;
        public String routeSourcePathId;
        public Float routeSpeed;
        public Boolean routeAlignRotation;
        public Float routeJumpStart;
        public Float routeJumpDuration;
        public Float routeJumpHeight;

        // ===== ActorRelationClip =====
        public String relationTargetActorId;
        public String relationType;
        public Float relationDistance;
        public Float relationAngle;
        public Float relationHeightOffset;
        public Boolean relationMaintainLookAt;
    }

    /**
     * EntityClipKeyframe DTO
     */
    public static final class EntityClipKeyframeDTO {
        public int channels;
        public String id;               // UUID
        public float time;
        public String interpolation;    // TimelineInterpolation.name()
        public Vec3DTO position;
        public Vec3DTO rotation;
        public Vec3DTO scale;
        public String actionType;       // EntityKeyframe.ActionType.name()
        public Float actionSpeed;
        public Boolean actionLoop;
        public Float visibility;
        public Boolean active;
        public String displayName;
        public Boolean showNameTag;
        public String entityType;       // EntityKeyframe.EntityType.name()
    }
    
    /**
     * 片段数据传输对象
     */
    public static class ClipDTO {
        public String lightId;
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
        public String animationMode;
        public String animationScope;
        public String animationPhase;
        public boolean occlusionTransparencyEnabled = true;
        public float postAnimationFadeStart = 0;
        public boolean postAnimationFadeEnabled = false;
        public float postAnimationFadeEnd = 2;
        public List<com.director.core.animation.effect.EffectInstance> animationEffects = new ArrayList<>();
        public com.director.core.animation.WholeContainerAnimationConfig wholeContainerAnimation;
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

        public Vec3DTO(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public static Vec3DTO from(org.joml.Vector3f v) {
            if (v == null) return null;
            return new Vec3DTO(v.x, v.y, v.z);
        }
        
        public static Vec3DTO fromVec3d(net.minecraft.util.math.Vec3d v) {
            if (v == null) return null;
            return new Vec3DTO((float) v.x, (float) v.y, (float) v.z);
        }
        
        public net.minecraft.util.math.Vec3d toVec3d() {
            return new net.minecraft.util.math.Vec3d(x, y, z);
        }

        public org.joml.Vector3f toVector3f() {
            return new org.joml.Vector3f(x, y, z);
        }
    }
    
    /**
     * 摄像机路径片段 DTO
     */
    public static final class PathClipDTO {
        public String type; // CameraPathType.name() (ORBIT, DOLLY, CRANE, etc.)
        public String name;
        public float startTime;
        public float endTime;
        public boolean enabled = true;
        
        // OrbitPathClip 参数
        public Vec3DTO center;
        public Vec3DTO lookAt;
        public Float radius;
        public String radiusMode; // RadiusMode.name()
        public String heightMode; // HeightMode.name()
        public Float height;
        public Float cachedHeight;
        public Float startAngle;
        public Float endAngle;
        public String startAngleMode; // StartAngleMode.name()
        public Boolean clockwise;
        
        // DollyPathClip 参数
        public Float distance;
        public String directionMode; // DirectionMode.name()
        public Vec3DTO fixedDirection;
        public Boolean alignStartToCurrentPosition;
        public Vec3DTO startPosition;
        public Vec3DTO dollyDirection;
        
        // CranePathClip 参数
        public Float heightDelta;
        public Vec3DTO craneStartPosition;

        // CustomPathClip 参数
        public List<AutomationKeyframeDTO> cameraKeyframes = new ArrayList<>();
    }

    /**
     * ClipKeyframe DTO（新版片段关键帧）
     */
    public static final class ClipKeyframeDTO {
        public float time;
        public String interpolation; // TimelineInterpolation.name()
        public Float audioVolume;
        public com.director.lighting.PointLight lightValue;
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
        public float snowStrength;
        public String rainType;
        public float rainParticleDensity;
        public float puddleLevel;
        public float thunderStrength;
        public float lightningFrequency;
        public boolean visualOnlyThunder;
        public float windStrength;
        public float windDirectionDegrees;
        public float stormDarkness;
        public float cloudDensity;
        public float cloudSpeed;
        public float cloudBrightness;
        public float fogDensity;
        public Vec3DTO fogColor;
        public float tempBias;
        public float humidityBias;
        public String specialWeatherType;
        public float specialWeatherIntensity;

        // ===== SeasonKeyframe =====
        public String seasonType;
        public float seasonHumidity;
        public boolean seasonHumiditySet;
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
        dto.pointLights.addAll(model.getPointLights());
        dto.lightingVersion=1;
        
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
        dto.snapToGrid = model.getSnapSettings().isSnapToGrid();
        dto.snapToClipEdges = model.getSnapSettings().isSnapToClipEdges();
        dto.snapToKeyframes = model.getSnapSettings().isSnapToKeyframes();
        dto.snapPixelThreshold = model.getSnapSettings().getPixelThreshold();
        dto.frameRate = model.getFrameRate();
        dto.timeUnit = model.getTimeUnit();
        dto.loopEnabled = model.isLoopEnabled();
        dto.playbackSpeed = model.getPlaybackSpeed();
        dto.duration = model.getDuration();
        dto.inPoint = model.hasInPoint() ? model.getInPoint() : null;
        dto.outPoint = model.hasOutPoint() ? model.getOutPoint() : null;
        dto.timestamp = System.currentTimeMillis();
        dto.worldFingerprint = getWorldFingerprint();

        for (TimelineMarker marker : model.getMarkers()) {
            if (marker == null) continue;
            MarkerDTO markerDTO = new MarkerDTO();
            markerDTO.id = marker.getId().toString();
            markerDTO.name = marker.getName();
            markerDTO.time = marker.getTime();
            markerDTO.color = marker.getColor();
            markerDTO.note = marker.getNote();
            dto.markers.add(markerDTO);
        }
        
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
                        kdto.snowStrength = wk.getSnowStrength();
                        kdto.rainType = wk.getRainType() != null ? wk.getRainType().name() : null;
                        kdto.rainParticleDensity = wk.getRainParticleDensity();
                        kdto.puddleLevel = wk.getPuddleLevel();
                        kdto.thunderStrength = wk.getThunderStrength();
                        kdto.lightningFrequency = wk.getLightningFrequency();
                        kdto.visualOnlyThunder = wk.isVisualOnlyThunder();
                        kdto.windStrength = wk.getWindStrength();
                        kdto.windDirectionDegrees = wk.getWindDirectionDegrees();
                        kdto.stormDarkness = wk.getStormDarkness();
                        kdto.cloudDensity = wk.getCloudDensity();
                        kdto.cloudSpeed = wk.getCloudSpeed();
                        kdto.cloudBrightness = wk.getCloudBrightness();
                        // ⚠️ 雾功能已禁用：保留序列化以向后兼容，但值不会被使用
                        kdto.fogDensity = wk.getFogDensity();
                        kdto.fogColor = Vec3DTO.from(wk.getFogColor());
                        kdto.tempBias = wk.getTempBias();
                        kdto.humidityBias = wk.getHumidityBias();
                        kdto.specialWeatherType = wk.getSpecialWeatherType() != null ? wk.getSpecialWeatherType().name() : null;
                        kdto.specialWeatherIntensity = wk.getSpecialWeatherIntensity();
                    }
                    case SeasonKeyframe sk -> {
                        kdto.seasonType = sk.getSeasonType() != null ? sk.getSeasonType().name() : null;
                        kdto.seasonHumidity = sk.getHumidity();
                        kdto.seasonHumiditySet = true;
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
                if(clip instanceof com.director.lighting.LightTimelineClip light) clipDTO.lightId=light.lightId().toString();
                clipDTO.label = clip.getLabel();
                clipDTO.startTime = clip.getStartTime();
                clipDTO.endTime = clip.getEndTime();
                clipDTO.color = clip.getColor();
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
                    if(ckf instanceof com.director.lighting.LightKeyframe light) ckfdto.lightValue=light.value();
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
                clipDTO.animationScope = config.getScope().name();
                clipDTO.animationPhase = config.getPhase().name();
                clipDTO.occlusionTransparencyEnabled = config.isOcclusionTransparencyEnabled();
                clipDTO.postAnimationFadeStart = clip.getPostAnimationFadeStart();
                clipDTO.postAnimationFadeEnabled = clip.isPostAnimationFadeEnabled();
                clipDTO.postAnimationFadeEnd = clip.getPostAnimationFadeEnd();
                for (var effect : config.getEffects()) if (effect != null) clipDTO.animationEffects.add(effect.copy());
                clipDTO.wholeContainerAnimation = config.getWholeContainer().copy();
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
                    clipDTO.sequenceParams.put("layerStep", seqParams.getLayerStep());
                    clipDTO.sequenceParams.put("floodSeedMode", seqParams.getFloodSeedMode().name());
                    clipDTO.sequenceParams.put("concurrencyMode", seqParams.getConcurrencyMode().name());
                    clipDTO.sequenceParams.put("batchSize", seqParams.getBatchSize());
                    clipDTO.sequenceParams.put("batchInterval", seqParams.getBatchInterval());
                    clipDTO.sequenceParams.put("timeMappingStrategy", seqParams.getTimeMappingStrategy().name());
                    clipDTO.sequenceParams.put("perBlockDuration", seqParams.getPerBlockDuration());
                    clipDTO.sequenceParams.put("globalDelay", seqParams.getGlobalDelay());
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
            
            // 保存轨道的所有 PathClip
            for (com.director.timeline.model.camera.path.CameraPathClip pathClip : track.getCameraPathClips()) {
                if (pathClip == null) continue;
                PathClipDTO pathClipDTO = new PathClipDTO();
                pathClipDTO.type = pathClip.getPathType() != null ? pathClip.getPathType().name() : null;
                pathClipDTO.name = pathClip.getName();
                pathClipDTO.startTime = pathClip.getStartTime();
                pathClipDTO.endTime = pathClip.getEndTime();
                pathClipDTO.enabled = pathClip.isEnabled();
                
                // 根据类型保存特定参数
                switch (pathClip) {
                    case com.director.timeline.model.camera.path.CustomPathClip custom -> {
                        for (CameraKeyframe keyframe : custom.getKeyframes()) {
                            if (keyframe == null) continue;
                            pathClipDTO.cameraKeyframes.add(toCameraKeyframeDTO(keyframe));
                        }
                    }
                    case com.director.timeline.model.camera.path.OrbitPathClip orbit -> {
                        pathClipDTO.center = Vec3DTO.fromVec3d(orbit.getCenter());
                        pathClipDTO.lookAt = Vec3DTO.fromVec3d(orbit.getLookAt());
                        pathClipDTO.radius = orbit.getRadius();
                        pathClipDTO.radiusMode = orbit.getRadiusMode() != null ? orbit.getRadiusMode().name() : null;
                        pathClipDTO.heightMode = orbit.getHeightMode() != null ? orbit.getHeightMode().name() : null;
                        pathClipDTO.height = orbit.getHeight();
                        pathClipDTO.cachedHeight = orbit.getCachedHeight();
                        pathClipDTO.startAngle = orbit.getConfiguredStartAngle();
                        pathClipDTO.endAngle = orbit.getEndAngle();
                        pathClipDTO.startAngleMode = orbit.getStartAngleMode() != null ? orbit.getStartAngleMode().name() : null;
                        pathClipDTO.clockwise = orbit.isClockwise();
                    }
                    case com.director.timeline.model.camera.path.DollyPathClip dolly -> {
                        pathClipDTO.distance = dolly.getDistance();
                        pathClipDTO.directionMode = dolly.getDirectionMode() != null ? dolly.getDirectionMode().name() : null;
                        pathClipDTO.fixedDirection = Vec3DTO.fromVec3d(dolly.getFixedDirection());
                        pathClipDTO.alignStartToCurrentPosition = dolly.isAlignStartToCurrentPosition();
                        pathClipDTO.startPosition = Vec3DTO.fromVec3d(dolly.getStartPosition());
                        pathClipDTO.dollyDirection = Vec3DTO.fromVec3d(dolly.getDollyDirection());
                    }
                    case com.director.timeline.model.camera.path.CranePathClip crane -> {
                        pathClipDTO.heightDelta = crane.getHeightDelta();
                        pathClipDTO.alignStartToCurrentPosition = crane.isAlignStartToCurrentPosition();
                        pathClipDTO.craneStartPosition = Vec3DTO.fromVec3d(crane.getStartPosition());
                    }
                    default -> {
                        // 其他类型暂不支持序列化
                    }
                }
                
                trackDTO.pathClips.add(pathClipDTO);
            }

            // 保存演员轨道 payload
            if (track.getActorTrackPayload() != null) {
                com.director.timeline.model.entity.ActorTrack actorTrack = track.getActorTrackPayload();
                ActorTrackDTO etDTO = new ActorTrackDTO();
                etDTO.displayName = actorTrack.getDisplayName();
                etDTO.enabled = actorTrack.isEnabled();
                etDTO.colorRgba = actorTrack.getColorRgba();

                com.director.actor.ActorRef actorRef = actorTrack.getActorRef();
                if (actorRef != null) {
                    etDTO.actorRefId = actorRef.getActorId().toString();
                }

                com.director.timeline.model.entity.EntityBinding binding = actorTrack.getBinding();
                if (binding != null) {
                    EntityBindingDTO bdto = new EntityBindingDTO();
                    bdto.mode = binding.getMode() != null ? binding.getMode().name() : null;
                    bdto.targetEntityUuid = binding.getTargetEntityUuid() != null ? binding.getTargetEntityUuid().toString() : null;
                    bdto.targetPlayerName = binding.getTargetPlayerName();
                    bdto.entityTypeId = binding.getEntityTypeId();
                    bdto.persistent = binding.isPersistent();
                    etDTO.binding = bdto;
                }

                for (com.director.timeline.model.entity.EntityClip clip : actorTrack.getClips()) {
                    if (clip == null) continue;
                    etDTO.clips.add(entityClipToDTO(clip));
                }

                trackDTO.actorTrackPayload = etDTO;
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

        TimelineAudioLoadHook audioHook = model.getAudioLoadHook();
        boolean prevSuppress = audioHook.isSuppressAutoCreate();
        audioHook.setSuppressAutoCreate(true);
        boolean previousPersistenceSuppression = model.isSuppressPersistenceWrites();
        model.setSuppressPersistenceWrites(true);
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
        model.getSnapSettings().setSnapToGrid(dto.snapToGrid);
        model.getSnapSettings().setSnapToClipEdges(dto.snapToClipEdges);
        model.getSnapSettings().setSnapToKeyframes(dto.snapToKeyframes);
        model.getSnapSettings().setPixelThreshold(dto.snapPixelThreshold);
        model.setFrameRate(dto.frameRate);
        model.setTimeUnit(dto.timeUnit);
        model.setDuration(dto.duration != null && Float.isFinite(dto.duration) ? dto.duration : 20f);
        model.setLoopEnabled(dto.loopEnabled);
        model.setPlaybackSpeed(dto.playbackSpeed);
        
        // Replace scene lights too, including loading older files without lights.
        for (var light:model.getPointLights()) model.replacePointLight(light.id(),null);
        if (dto.pointLights != null) for (var light:dto.pointLights) {
            if (light != null) model.replacePointLight(light.id(),light);
        }
        // 清空现有轨道
        for (TimelineTrack existing : new ArrayList<>(model.getTracks())) model.removeTrack(existing);
        for (TimelineMarker marker : model.getMarkers()) {
            if (marker != null) {
                model.removeMarker(marker.getId());
            }
        }

        if (dto.markers != null) {
            for (MarkerDTO markerDTO : dto.markers) {
                if (markerDTO == null) {
                    continue;
                }
                UUID id = ParseUtils.uuidOrNull(markerDTO.id, "TimelineMarker.id");
                if (markerDTO.id != null && !markerDTO.id.isBlank() && id == null) {
                    LOGGER.warn("[TimelineModelPersistence] 无法解析标记 ID: {}", markerDTO.id);
                }
                TimelineMarker marker = new TimelineMarker(
                    id,
                    markerDTO.name != null ? markerDTO.name : "Marker",
                    markerDTO.time
                );
                marker.setColor(markerDTO.color);
                marker.setNote(markerDTO.note);
                model.addMarker(marker);
            }
        }
        
        int restoredTrackCount = 0;

        // 恢复所有轨道
        if (dto.tracks != null) {
        for (TrackDTO trackDTO : dto.tracks) {
            if (trackDTO == null) {
                continue;
            }
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
                                    ParseUtils.setEnumIfValid(kdto.cameraKeyType, CameraKeyframe.KeyType.class, "KeyType", ck::setKeyType);
                                }
                                if (kdto.cameraSegmentType != null) {
                                    ParseUtils.setEnumIfValid(kdto.cameraSegmentType, CameraKeyframe.SegmentPathType.class, "SegmentPathType", ck::setSegmentType);
                                }
                                if (kdto.cameraRotationInterpolation != null) {
                                    ParseUtils.setEnumIfValid(kdto.cameraRotationInterpolation, CameraKeyframe.RotationInterpolation.class, "RotationInterpolation", ck::setRotationInterpolation);
                                }
                                if (kdto.bezierInHandle != null) ck.setBezierInHandle(new org.joml.Vector3f(kdto.bezierInHandle.x, kdto.bezierInHandle.y, kdto.bezierInHandle.z));
                                if (kdto.bezierOutHandle != null) ck.setBezierOutHandle(new org.joml.Vector3f(kdto.bezierOutHandle.x, kdto.bezierOutHandle.y, kdto.bezierOutHandle.z));
                                if (kdto.cameraHandleMode != null) {
                                    ParseUtils.setEnumIfValid(kdto.cameraHandleMode, CameraKeyframe.HandleMode.class, "HandleMode", ck::setHandleMode);
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
                                    ParseUtils.setEnumIfValid(kdto.timePathInterpolation, TimeKeyframe.TimeInterpolation.class, "TimeInterpolation", tk::setPathInterpolation);
                                }
                                if (kdto.timeAngleInterpolation != null) {
                                    ParseUtils.setEnumIfValid(kdto.timeAngleInterpolation, TimeKeyframe.TimeInterpolation.class, "TimeInterpolation", tk::setAngleInterpolation);
                                }
                                track.addAutomationKeyframe(tk);
                            }
                            case "WeatherKeyframe" -> {
                                WeatherKeyframe wk = new WeatherKeyframe(kdto.name != null ? kdto.name : "Weather Keyframe", kdto.time);
                                // ✅ 修复：先设为 CUSTOM 阻止 applyWeatherDefaults()，
                                // 再逐个恢复参数，最后设置真正的 weatherType（不触发默认值覆盖）
                                wk.setWeatherType(WeatherKeyframe.WeatherType.CUSTOM);
                                if (kdto.weatherInterpolation != null) {
                                    ParseUtils.setEnumIfValid(kdto.weatherInterpolation, TimelineInterpolation.class, "TimelineInterpolation", wk::setInterpolation);
                                }
                                wk.setRainStrength(kdto.rainStrength);
                                wk.setSnowStrength(kdto.snowStrength);
                                if (kdto.rainType != null) {
                                    ParseUtils.setEnumIfValid(kdto.rainType, WeatherKeyframe.RainType.class, "RainType", wk::setRainType);
                                }
                                wk.setRainParticleDensity(kdto.rainParticleDensity);
                                wk.setPuddleLevel(kdto.puddleLevel);
                                wk.setThunderStrength(kdto.thunderStrength);
                                wk.setLightningFrequency(kdto.lightningFrequency);
                                // ✅ 新增：只视觉雷暴（如果不存在则使用默认值 true）
                                wk.setVisualOnlyThunder(kdto.visualOnlyThunder);
                                wk.setWindStrength(kdto.windStrength);
                                // ✅ 修复：直接设置风方向，setter 内部已做 [0,360) 归一化
                                wk.setWindDirectionDegrees(kdto.windDirectionDegrees);
                                wk.setStormDarkness(kdto.stormDarkness);
                                wk.setCloudDensity(kdto.cloudDensity);
                                wk.setCloudSpeed(kdto.cloudSpeed);
                                // ✅ 新增：云亮度（如果不存在则使用默认值 1.0）
                                if (kdto.cloudBrightness > 0f) {
                                    wk.setCloudBrightness(kdto.cloudBrightness);
                                } else {
                                    wk.setCloudBrightness(1.0f); // 默认值
                                }
                                // ⚠️ 雾功能已禁用：保留反序列化以向后兼容，但值不会被使用
                                wk.setFogDensity(kdto.fogDensity);
                                if (kdto.fogColor != null) wk.setFogColor(kdto.fogColor.x, kdto.fogColor.y, kdto.fogColor.z);
                                wk.setTempBias(kdto.tempBias);
                                wk.setHumidityBias(kdto.humidityBias);
                                if (kdto.specialWeatherType != null) {
                                    ParseUtils.setEnumIfValid(kdto.specialWeatherType, WeatherKeyframe.SpecialWeatherType.class, "SpecialWeatherType", wk::setSpecialWeatherType);
                                }
                                wk.setSpecialWeatherIntensity(kdto.specialWeatherIntensity);
                                // ✅ 修复：最后设置 weatherType（直接写字段，不触发 applyWeatherDefaults）
                                if (kdto.weatherType != null) {
                                    ParseUtils.setEnumIfValid(kdto.weatherType, WeatherKeyframe.WeatherType.class, "WeatherType", wk::setWeatherType);
                                }
                                track.addAutomationKeyframe(wk);
                            }
                            case "SeasonKeyframe" -> {
                                SeasonKeyframe sk = new SeasonKeyframe(kdto.name != null ? kdto.name : "Season Keyframe", kdto.time);
                                if (kdto.seasonType != null) {
                                    ParseUtils.setEnumIfValid(kdto.seasonType, SeasonKeyframe.SeasonType.class, "SeasonType", sk::applySeasonPreset);
                                }
                                if (kdto.seasonHumiditySet) {
                                    sk.setHumidity(kdto.seasonHumidity);
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
                                    ParseUtils.setEnumIfValid(kdto.seasonInterpolation, SeasonKeyframe.Interpolation.class, "Interpolation", sk::setInterpolation);
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
                java.util.UUID lightId=ParseUtils.uuidOrNull(clipDTO.lightId,"LightClip.id");
                if(track.getType()==TrackType.LIGHT && (lightId==null || model.getPointLight(lightId)==null)) continue;
                TimelineClip clip = track.getType()==TrackType.LIGHT
                        ? new com.director.lighting.LightTimelineClip(clipDTO.label,clipDTO.startTime,clipDTO.endTime,lightId)
                        : (track.getType() == TrackType.AUDIO)
                        ? new AudioTimelineClip(clipDTO.label, clipDTO.startTime, clipDTO.endTime, clipDTO.color)
                        : new TimelineClip(clipDTO.label, clipDTO.startTime, clipDTO.endTime, clipDTO.color);
                clip.setAutoFadePreviousClips(clipDTO.autoFadePreviousClips);
                clip.setAutoFadeOpacity(clipDTO.autoFadeOpacity);
                ClipAnimationConfig config = clip.getAnimationConfig();
                if (clipDTO.animationMode != null) {
                    AnimationMode mode = ParseUtils.enumOrNull(clipDTO.animationMode, AnimationMode.class, "Clip.animationMode");
                    if (mode != null) {
                        config.setMode(mode);
                    } else {
                        LOGGER.warn("[TimelineModelPersistence] 未知的动画模式: {}", clipDTO.animationMode);
                    }
                }
                config.setDuration(clipDTO.animationDuration > 0.0f ? clipDTO.animationDuration : config.getDuration());
                if (clipDTO.animationScope != null) {
                    var scope = ParseUtils.enumOrNull(clipDTO.animationScope,
                            ClipAnimationConfig.AnimationScope.class, "Clip.animationScope");
                    config.setScope(scope);
                }
                config.setWholeContainer(clipDTO.wholeContainerAnimation);
                config.setPhase(ParseUtils.enumFromObject(clipDTO.animationPhase,
                        ClipAnimationConfig.AnimationPhase.class, config.getPhase(), "Clip.animationPhase"));
                config.setOcclusionTransparencyEnabled(clipDTO.occlusionTransparencyEnabled);
                clip.setPostAnimationFadeStart(clipDTO.postAnimationFadeStart);
                clip.setPostAnimationFadeEnabled(clipDTO.postAnimationFadeEnabled);
                clip.setPostAnimationFadeEnd(clipDTO.postAnimationFadeEnd);
                config.clearEffects();
                if (clipDTO.animationEffects != null)
                    for (var effect : clipDTO.animationEffects) if (effect != null) config.addEffect(effect.copy());
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
                    seqParams.setFlowType(ParseUtils.enumFromObject(raw.get("flowType"), FlowType.class, seqParams.getFlowType(), "sequenceParams.flowType"));
                    seqParams.setFillType(ParseUtils.enumFromObject(raw.get("fillType"), FillType.class, seqParams.getFillType(), "sequenceParams.fillType"));
                    seqParams.setOriginType(ParseUtils.enumFromObject(raw.get("originType"), OriginType.class, seqParams.getOriginType(), "sequenceParams.originType"));
                    seqParams.setPlaneYaw(ParseUtils.asFloat(raw.get("planeYaw"), seqParams.getPlaneYaw(), "sequenceParams.planeYaw"));
                    seqParams.setPlanePitch(ParseUtils.asFloat(raw.get("planePitch"), seqParams.getPlanePitch(), "sequenceParams.planePitch"));
                    seqParams.setLayerStep(ParseUtils.asFloat(raw.get("layerStep"), seqParams.getLayerStep(), "sequenceParams.layerStep"));
                    seqParams.setFloodSeedMode(ParseUtils.enumFromObject(raw.get("floodSeedMode"), com.director.core.animation.sequence.evaluator.FloodSeedMode.class, seqParams.getFloodSeedMode(), "sequenceParams.floodSeedMode"));
                    seqParams.setConcurrencyMode(ParseUtils.enumFromObject(raw.get("concurrencyMode"), com.director.core.animation.sequence.ConcurrencyMode.class, seqParams.getConcurrencyMode(), "sequenceParams.concurrencyMode"));
                    seqParams.setBatchSize(ParseUtils.asInt(raw.get("batchSize"), seqParams.getBatchSize(), "sequenceParams.batchSize"));
                    seqParams.setBatchInterval(ParseUtils.asFloat(raw.get("batchInterval"), seqParams.getBatchInterval(), "sequenceParams.batchInterval"));
                    seqParams.setTimeMappingStrategy(ParseUtils.enumFromObject(raw.get("timeMappingStrategy"), com.director.core.animation.sequence.TimeMappingStrategy.class, seqParams.getTimeMappingStrategy(), "sequenceParams.timeMappingStrategy"));
                    seqParams.setPerBlockDuration(ParseUtils.asFloat(raw.get("perBlockDuration"), seqParams.getPerBlockDuration(), "sequenceParams.perBlockDuration"));
                    seqParams.setGlobalDelay(ParseUtils.asFloat(raw.get("globalDelay"), seqParams.getGlobalDelay(), "sequenceParams.globalDelay"));
                    int customX = ParseUtils.asInt(raw.get("customX"), seqParams.getCustomX(), "sequenceParams.customX");
                    int customY = ParseUtils.asInt(raw.get("customY"), seqParams.getCustomY(), "sequenceParams.customY");
                    int customZ = ParseUtils.asInt(raw.get("customZ"), seqParams.getCustomZ(), "sequenceParams.customZ");
                    seqParams.setCustomXYZ(customX, customY, customZ);
                    seqParams.setPriorityWeight(ParseUtils.asFloat(raw.get("priorityWeight"), seqParams.getPriorityWeight(), "sequenceParams.priorityWeight"));
                    seqParams.setReverse(ParseUtils.asBoolean(raw.get("reverse"), seqParams.isReverse()));
                    seqParams.setAlternating(ParseUtils.asBoolean(raw.get("alternating"), seqParams.isAlternating()));

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
                    UUID containerId = ParseUtils.uuidOrNull(clipDTO.boundContainerId, "Clip.boundContainerId");
                    if (containerId != null) {
                        clip.setBoundContainerId(containerId);
                    } else {
                        LOGGER.warn("[TimelineModelPersistence] 无法解析容器 ID: {}", clipDTO.boundContainerId);
                    }
                }
                clip.setBoundContainerName(clipDTO.boundContainerName);
                
                // 恢复关键帧
                if(track.getType()!=TrackType.LIGHT && clipDTO.keyframes!=null) for (Float keyframe : clipDTO.keyframes) {
                    clip.addKeyframe(keyframe);
                }

                // 恢复新版 ClipKeyframe
                if (clipDTO.clipKeyframes != null) {
                    for (ClipKeyframeDTO ckfdto : clipDTO.clipKeyframes) {
                        if (ckfdto == null) continue;
                        TimelineInterpolation interp = TimelineInterpolation.LINEAR;
                        if (ckfdto.interpolation != null) {
                            interp = ParseUtils.enumOr(ckfdto.interpolation, TimelineInterpolation.class, interp, "TimelineInterpolation");
                        }
                        ClipKeyframe ckf;
                        if (track.getType()==TrackType.LIGHT) {
                            if(ckfdto.lightValue==null || !ckfdto.lightValue.id().equals(lightId)) continue;
                            ckf=new com.director.lighting.LightKeyframe(ckfdto.time,ckfdto.lightValue);ckf.setInterpolation(interp);
                        } else if (track.getType() == TrackType.AUDIO || ckfdto.audioVolume != null) {
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
                            mode = ParseUtils.enumOr(vdto.mode, VisibilityKeyframe.VisibilityMode.class, mode, "VisibilityMode");
                        }
                        try {
                            clip.addVisibilityKeyframe(new VisibilityKeyframe(vdto.time, vdto.alpha, mode));
                        } catch (IllegalArgumentException e) {
                            LOGGER.debug("[TimelineModelPersistence] Skip invalid visibility keyframe at {}: {}",
                                    vdto.time, e.getMessage());
                        }
                    }
                }
                
                track.addClip(clip);
            }
            
            // 恢复轨道的所有 PathClip
            if (trackDTO.pathClips != null) {
                for (PathClipDTO pathClipDTO : trackDTO.pathClips) {
                    if (pathClipDTO == null || pathClipDTO.type == null) continue;
                    try {
                        com.director.timeline.model.camera.path.CameraPathClip pathClip = null;
                        
                        switch (pathClipDTO.type) {
                            case "CUSTOM", "FREE" -> {
                                CustomPathClip custom = new CustomPathClip(
                                    pathClipDTO.name != null ? pathClipDTO.name : "Custom Path",
                                    pathClipDTO.startTime,
                                    pathClipDTO.endTime
                                );
                                if (pathClipDTO.cameraKeyframes != null) {
                                    for (AutomationKeyframeDTO keyframeDTO : pathClipDTO.cameraKeyframes) {
                                        if (keyframeDTO != null) {
                                            custom.addKeyframe(fromCameraKeyframeDTO(keyframeDTO));
                                        }
                                    }
                                }
                                pathClip = custom;
                            }
                            case "ORBIT" -> {
                                OrbitPathClip orbit = getOrbitPathClip(pathClipDTO);

                                if (pathClipDTO.lookAt != null) orbit.setLookAt(pathClipDTO.lookAt.toVec3d());
                                if (pathClipDTO.radiusMode != null) {
                                    ParseUtils.setEnumIfValid(pathClipDTO.radiusMode, com.director.timeline.model.camera.path.OrbitPathClip.RadiusMode.class, "RadiusMode", orbit::setRadiusMode);
                                }
                                if (pathClipDTO.heightMode != null) {
                                    ParseUtils.setEnumIfValid(pathClipDTO.heightMode, com.director.timeline.model.camera.path.OrbitPathClip.HeightMode.class, "HeightMode", orbit::setHeightMode);
                                }
                                if (pathClipDTO.cachedHeight != null) orbit.setCachedHeight(pathClipDTO.cachedHeight);
                                if (pathClipDTO.startAngle != null) orbit.setStartAngle(pathClipDTO.startAngle);
                                if (pathClipDTO.endAngle != null) orbit.setEndAngle(pathClipDTO.endAngle);
                                if (pathClipDTO.startAngleMode != null) {
                                    ParseUtils.setEnumIfValid(pathClipDTO.startAngleMode, com.director.timeline.model.camera.path.OrbitPathClip.StartAngleMode.class, "StartAngleMode", orbit::setStartAngleMode);
                                }
                                if (pathClipDTO.clockwise != null) orbit.setClockwise(pathClipDTO.clockwise);
                                
                                pathClip = orbit;
                            }
                            case "DOLLY" -> {
                                com.director.timeline.model.camera.path.DollyPathClip dolly = 
                                    new com.director.timeline.model.camera.path.DollyPathClip(
                                        pathClipDTO.name != null ? pathClipDTO.name : "Dolly Path",
                                        pathClipDTO.startTime,
                                        pathClipDTO.endTime
                                    );
                                
                                if (pathClipDTO.distance != null) dolly.setDistance(pathClipDTO.distance);
                                if (pathClipDTO.directionMode != null) {
                                    ParseUtils.setEnumIfValid(pathClipDTO.directionMode, com.director.timeline.model.camera.path.DollyPathClip.DirectionMode.class, "DirectionMode", dolly::setDirectionMode);
                                }
                                if (pathClipDTO.fixedDirection != null) dolly.setFixedDirection(pathClipDTO.fixedDirection.toVec3d());
                                if (pathClipDTO.startPosition != null) dolly.setStartPosition(pathClipDTO.startPosition.toVec3d());
                                if (pathClipDTO.dollyDirection != null) dolly.setDollyDirection(pathClipDTO.dollyDirection.toVec3d());
                                if (pathClipDTO.startPosition != null && pathClipDTO.dollyDirection != null) {
                                    dolly.setAlignStartToCurrentPosition(false);
                                } else if (pathClipDTO.alignStartToCurrentPosition != null) {
                                    dolly.setAlignStartToCurrentPosition(pathClipDTO.alignStartToCurrentPosition);
                                }
                                
                                pathClip = dolly;
                            }
                            case "CRANE" -> {
                                com.director.timeline.model.camera.path.CranePathClip crane = getCranePathClip(pathClipDTO);
                                if (pathClipDTO.craneStartPosition != null) {
                                    crane.setStartPosition(pathClipDTO.craneStartPosition.toVec3d());
                                    crane.setAlignStartToCurrentPosition(false);
                                }
                                pathClip = crane;
                            }
                            default -> LOGGER.warn("[TimelineModelPersistence] 未知的 PathClip 类型: {}", pathClipDTO.type);
                        }
                        
                        if (pathClip != null) {
                            pathClip.setEnabled(pathClipDTO.enabled);
                            track.addCameraPathClip(pathClip);
                        }
                    } catch (Exception e) {
                        LOGGER.error("[TimelineModelPersistence] 加载 PathClip 时发生错误: {}", pathClipDTO.name, e);
                    }
                }
            }
            
            // 恢复演员轨道 payload
            if (trackDTO.actorTrackPayload != null) {
                try {
                    ActorTrackDTO etDTO = trackDTO.actorTrackPayload;
                    com.director.timeline.model.entity.ActorTrack actorTrack = new com.director.timeline.model.entity.ActorTrack(etDTO.displayName);
                    actorTrack.setEnabled(etDTO.enabled);
                    actorTrack.setColorRgba(etDTO.colorRgba);

                    if (etDTO.actorRefId != null) {
                        { UUID actorRefId = ParseUtils.uuidOrNull(etDTO.actorRefId, "ActorTrack.actorRefId"); if (actorRefId != null) actorTrack.setActorRef(new com.director.actor.ActorRef(actorRefId)); }
                    }

                    if (etDTO.binding != null) {
                        EntityBindingDTO bdto = etDTO.binding;
                        com.director.timeline.model.entity.EntityBinding binding = new com.director.timeline.model.entity.EntityBinding();
                        if (bdto.mode != null) {
                            ParseUtils.setEnumIfValid(bdto.mode, com.director.timeline.model.entity.EntityBinding.Mode.class, "Mode", binding::setMode);
                        }
                        if (bdto.targetEntityUuid != null) {
                            ParseUtils.setUuidIfValid(bdto.targetEntityUuid, "binding.setTargetEntityUuid", binding::setTargetEntityUuid);
                        }
                        binding.setTargetPlayerName(bdto.targetPlayerName);
                        if (bdto.entityTypeId != null) binding.setEntityTypeId(bdto.entityTypeId);
                        binding.setPersistent(bdto.persistent);
                        actorTrack.setBinding(binding);
                    }

                    if (etDTO.clips != null) {
                        for (EntityClipDTO cdto : etDTO.clips) {
                            if (cdto == null || cdto.clipType == null) continue;
                            try {
                                actorTrack.addClip(entityClipFromDTO(cdto));
                            } catch (Exception e) {
                                LOGGER.error("[TimelineModelPersistence] 加载 EntityClip 时发生错误: {}", cdto.name, e);
                            }
                        }
                    }

                    track.setActorTrackPayload(actorTrack);
                    // Actor payload is authoritative; generic deserialized clips cannot edit it.
                    for(var stale:new java.util.ArrayList<>(track.getClips()))track.removeClip(stale);
                    for(var entity:actorTrack.getClips())
                        track.addClip(new com.director.timeline.model.entity.EntityTimelineClip(entity,actorTrack.getColorRgba()));
                } catch (Exception e) {
                    LOGGER.error("[TimelineModelPersistence] 加载 ActorTrack payload 时发生错误: {}", e.getMessage(), e);
                }
            }

            model.addTrack(track);
            restoredTrackCount++;
        }
        }

        if(dto.lightingVersion==0) com.director.lighting.LightTimelineSampler.migrateLegacy(model);
        else com.director.lighting.LightLifecycle.cleanLoadedProject(model);
        model.clearInOut();
        if (dto.inPoint != null) {
            model.setInPoint(dto.inPoint);
        }
        if (dto.outPoint != null) {
            model.setOutPoint(dto.outPoint);
        }
        
        LOGGER.info("[TimelineModelPersistence] 成功恢复 {} 条轨道", restoredTrackCount);
    
        } finally {
            model.setSuppressPersistenceWrites(previousPersistenceSuppression);
            audioHook.setSuppressAutoCreate(prevSuppress);
        }
        audioHook.setTimelineModel(model);
    }


    public static EntityClipDTO entityClipToDTO(EntityClip clip) {
        EntityClipDTO cdto = new EntityClipDTO();
        cdto.id = clip.getId().toString();
        cdto.clipType = clip.getClipType().name();
        cdto.name = clip.getName();
        cdto.startTime = clip.getStartTime();
        cdto.endTime = clip.getEndTime();
        cdto.muted = clip.isMuted();
        cdto.weight = clip.getWeight();

        // 关键帧
        for (com.director.timeline.model.entity.EntityClipKeyframe kf : clip.getKeyframes()) {
            if (kf == null) continue;
            EntityClipKeyframeDTO kfdto = new EntityClipKeyframeDTO();
            kfdto.id = kf.getId().toString();
            kfdto.time = kf.getTime();
            kfdto.interpolation = kf.getInterpolation() != null ? kf.getInterpolation().name() : null;
            kfdto.position = Vec3DTO.from(kf.getPosition());
            kfdto.rotation = Vec3DTO.from(kf.getRotation());
            kfdto.scale = Vec3DTO.from(kf.getScale());
            kfdto.channels = kf.getChannels();
            kfdto.actionType = kf.getActionType() != null ? kf.getActionType().name() : null;
            kfdto.actionSpeed = kf.getActionSpeed();
            kfdto.actionLoop = kf.isActionLoop();
            kfdto.visibility = kf.getVisibility();
            kfdto.active = kf.isActive();
            kfdto.displayName = kf.getDisplayName();
            kfdto.showNameTag = kf.isShowNameTag();
            kfdto.entityType = kf.getEntityType() != null ? kf.getEntityType().name() : null;
            cdto.keyframes.add(kfdto);
        }

        // 类型特定字段
        switch (clip) {
            case com.director.timeline.model.entity.EntitySpawnClip sc -> {
                cdto.spawnX = sc.getPosition().x;
                cdto.spawnY = sc.getPosition().y;
                cdto.spawnZ = sc.getPosition().z;
                cdto.yawDeg = sc.getYawDeg();
                cdto.pitchDeg = sc.getPitchDeg();
                cdto.spawnBoneAnimationId = sc.getBoneAnimationId();
                if(sc.getPreset()!=null) cdto.spawnPreset=entityClipToDTO(sc.getPreset().asClip(sc.getStartTime(),sc.getEndTime()));
                cdto.setPoseBlend(sc.getBlendSettings());
                cdto.spawnRoll = sc.getRollDeg();
                cdto.spawnScaleX = sc.getScale().x; cdto.spawnScaleY = sc.getScale().y; cdto.spawnScaleZ = sc.getScale().z;
                cdto.visibleOnSpawn = sc.isVisibleOnSpawn();
            }
            case com.director.timeline.model.entity.EntityTransformClip tc -> {
                cdto.transformInterpolation = tc.getInterpolation() != null ? tc.getInterpolation().name() : null;
                cdto.fromPos = Vec3DTO.from(tc.getFromPos());
                cdto.toPos = Vec3DTO.from(tc.getToPos());
                cdto.fromRot = Vec3DTO.from(tc.getFromRot());
                cdto.toRot = Vec3DTO.from(tc.getToRot());
                cdto.fromScale = Vec3DTO.from(tc.getFromScale());
                cdto.toScale = Vec3DTO.from(tc.getToScale());
                cdto.pathId = tc.getPathId();
                cdto.followPath = tc.isFollowPath();
            }
            case com.director.timeline.model.entity.EntityLookAtClip lc -> {
                cdto.lookAtMode = lc.getMode() != null ? lc.getMode().name() : null;
                cdto.lookAtWorldPoint = Vec3DTO.from(lc.getWorldPoint());
                cdto.lookAtTargetEntityUuid = lc.getTargetEntityUuid() != null ? lc.getTargetEntityUuid().toString() : null;
                cdto.lookAtStrength = lc.getStrength();
            }
            case com.director.timeline.model.entity.EntityAnimationClip ac -> {
                cdto.animationSource = ac.getSource() != null ? ac.getSource().name() : null;
                cdto.animationId = ac.getAnimationId();
                cdto.animationLoop = ac.isLoop();
                cdto.animationSpeed = ac.getSpeed();
                cdto.setPoseBlend(ac.getBlendSettings());
            }
            case com.director.timeline.model.entity.EntityStateClip stc -> {
                cdto.sneaking = stc.isSneaking();
                cdto.sprinting = stc.isSprinting();
                cdto.glowing = stc.isGlowing();
                cdto.invulnerable = stc.isInvulnerable();
                cdto.stateVisibility = stc.getVisibility();
            }
            case com.director.timeline.model.entity.EntityActionClip xac -> {
                cdto.actionType = xac.getActionType() != null ? xac.getActionType().name() : null;
                cdto.actionParams.putAll(com.director.actor.prop.PropEventParameters.copy(xac.getParams(), false));
                cdto.triggerOnce = xac.isTriggerOnce();
            }
            case com.director.timeline.model.entity.EntityDespawnClip dc -> cdto.despawnMode = dc.getMode() != null ? dc.getMode().name() : null;
            case com.director.timeline.model.entity.ActorActionClip aac -> {
                cdto.actorActionType = aac.getActionType() != null ? aac.getActionType().name() : null;
                cdto.actorActionIntensity = aac.getIntensity();
                cdto.actorActionLoop = aac.isLoop();
            }
            case com.director.timeline.model.entity.ActorPoseClip pc -> {
                cdto.poseOverrideBody = pc.isOverrideBody();
                cdto.poseOverrideHead = pc.isOverrideHead();
                cdto.poseOverrideArms = pc.isOverrideArms();
                cdto.poseOverrideLegs = pc.isOverrideLegs();
                cdto.poseBodyPitch = pc.getBodyPitch();
                cdto.poseBodyYaw = pc.getBodyYaw();
                cdto.poseBodyRoll = pc.getBodyRoll();
                cdto.poseHeadPitch = pc.getHeadPitch();
                cdto.poseHeadYaw = pc.getHeadYaw();
                cdto.poseLeftArmPitch = pc.getLeftArmPitch();
                cdto.poseLeftArmYaw = pc.getLeftArmYaw();
                cdto.poseLeftArmRoll = pc.getLeftArmRoll();
                cdto.poseRightArmPitch = pc.getRightArmPitch();
                cdto.poseRightArmYaw = pc.getRightArmYaw();
                cdto.poseRightArmRoll = pc.getRightArmRoll();
                cdto.poseLeftLegPitch = pc.getLeftLegPitch();
                cdto.poseRightLegPitch = pc.getRightLegPitch();
            }
            case com.director.timeline.model.entity.ActorRouteClip rc -> {
                for (org.joml.Vector3f waypoint : rc.getWaypoints()) {
                    cdto.routeWaypoints.add(Vec3DTO.from(waypoint));
                }
                cdto.routeInterpolationMode = rc.getInterpolationMode() != null ? rc.getInterpolationMode().name() : null;
                cdto.routeLoopMode = rc.getLoopMode() != null ? rc.getLoopMode().name() : null;
                cdto.routeSourcePathId = rc.getSourcePathId() == null ? null : rc.getSourcePathId().toString();
                cdto.routeSmoothTravel = rc.isSmoothTravel();
                cdto.routeSpeed = rc.getSpeed();
                cdto.routeAlignRotation = rc.isAlignRotationToRoute();
                cdto.routeJumpStart = rc.getJumpStart();
                cdto.routeJumpDuration = rc.getJumpDuration();
                cdto.routeJumpHeight = rc.getJumpHeight();
            }
            case com.director.timeline.model.entity.ActorRelationClip rel -> {
                cdto.relationTargetActorId = rel.getTargetActorId() != null ? rel.getTargetActorId().toString() : null;
                cdto.relationType = rel.getRelationType() != null ? rel.getRelationType().name() : null;
                cdto.relationDistance = rel.getDistance();
                cdto.relationAngle = rel.getAngle();
                cdto.relationHeightOffset = rel.getHeightOffset();
                cdto.relationMaintainLookAt = rel.isMaintainLookAt();
            }
            default -> {
            }
        }

        return cdto;
    }

    public static EntityClip entityClipFromDTO(EntityClipDTO cdto) {
        com.director.timeline.model.entity.EntityClipType clipType =
            com.director.timeline.model.entity.EntityClipType.valueOf(cdto.clipType);
        EntityClip clip = getEntityClip(cdto, clipType);

        // 恢复关键帧
        if (cdto.keyframes != null) {
            for (EntityClipKeyframeDTO kfdto : cdto.keyframes) {
                if (kfdto == null) continue;
                try {
                    com.director.timeline.model.entity.EntityClipKeyframe kf = new com.director.timeline.model.entity.EntityClipKeyframe(kfdto.time);
                    if (kfdto.interpolation != null) {
                        ParseUtils.setEnumIfValid(kfdto.interpolation, com.director.timeline.model.TimelineInterpolation.class, "TimelineInterpolation", kf::setInterpolation);
                    }
                    if (kfdto.position != null) kf.setPosition(kfdto.position.x, kfdto.position.y, kfdto.position.z);
                    if (kfdto.rotation != null) kf.setRotation(kfdto.rotation.x, kfdto.rotation.y, kfdto.rotation.z);
                    if (kfdto.scale != null) kf.setScale(kfdto.scale.x, kfdto.scale.y, kfdto.scale.z);
                    kf.setChannels(kfdto.channels);
                    if (kfdto.actionType != null) {
                        ParseUtils.setEnumIfValid(kfdto.actionType, com.director.timeline.model.entity.EntityKeyframe.ActionType.class, "ActionType", kf::setActionType);
                    }
                    if (kfdto.actionSpeed != null) kf.setActionSpeed(kfdto.actionSpeed);
                    if (kfdto.actionLoop != null) kf.setActionLoop(kfdto.actionLoop);
                    if (kfdto.visibility != null) kf.setVisibility(kfdto.visibility);
                    if (kfdto.active != null) kf.setActive(kfdto.active);
                    if (kfdto.displayName != null) kf.setDisplayName(kfdto.displayName);
                    if (kfdto.showNameTag != null) kf.setShowNameTag(kfdto.showNameTag);
                    if (kfdto.entityType != null) {
                        ParseUtils.setEnumIfValid(kfdto.entityType, com.director.timeline.model.entity.EntityKeyframe.EntityType.class, "EntityType", kf::setEntityType);
                    }
                    clip.addKeyframe(kf);
                } catch (RuntimeException e) {
                    LOGGER.debug("[TimelineModelPersistence] Skip invalid entity clip keyframe at {}: {}",
                            kfdto.time, e.getMessage());
                }
            }
        }

        // 类型特定字段
        switch (clip) {
            case com.director.timeline.model.entity.EntitySpawnClip sc -> {
                if (cdto.spawnX != null && cdto.spawnY != null && cdto.spawnZ != null)
                    sc.setPosition(cdto.spawnX, cdto.spawnY, cdto.spawnZ);
                if (cdto.yawDeg != null) sc.setYawDeg(cdto.yawDeg);
                if (cdto.pitchDeg != null) sc.setPitchDeg(cdto.pitchDeg);
                sc.setBoneAnimationId(cdto.spawnBoneAnimationId);
                if(cdto.spawnPreset!=null) {
                    var preset=cdto.spawnPreset;
                    try {
                        // Read only animation fields; nested Spawn data is never recursively followed.
                        var animation=new com.director.timeline.model.entity.EntityAnimationClip("演员预设",sc.getStartTime(),sc.getEndTime());
                        preset.restorePoseBlend(animation);
                        sc.setPreset(new com.director.timeline.model.entity.ActorPresetBinding(preset.animationId,
                                preset.animationLoop==null || preset.animationLoop,
                                preset.animationSpeed==null?1:preset.animationSpeed,animation.getBlendSettings()));
                    } catch(IllegalArgumentException invalid) {
                        LOGGER.warn("[TimelineModelPersistence] 已跳过无效演员预设: {}",invalid.getMessage());
                    }
                }
                cdto.restorePoseBlend(sc);
                if (cdto.spawnRoll != null) sc.setRollDeg(cdto.spawnRoll);
                if (cdto.spawnScaleX != null && cdto.spawnScaleY != null && cdto.spawnScaleZ != null)
                    sc.setScale(cdto.spawnScaleX, cdto.spawnScaleY, cdto.spawnScaleZ);
                if (cdto.visibleOnSpawn != null) sc.setVisibleOnSpawn(cdto.visibleOnSpawn);
            }
            case com.director.timeline.model.entity.EntityTransformClip tc -> {
                if (cdto.transformInterpolation != null) {
                    ParseUtils.setEnumIfValid(cdto.transformInterpolation, com.director.timeline.model.TimelineInterpolation.class, "TimelineInterpolation", tc::setInterpolation);
                }
                if (cdto.fromPos != null) tc.setFromPos(cdto.fromPos.x, cdto.fromPos.y, cdto.fromPos.z);
                if (cdto.toPos != null) tc.setToPos(cdto.toPos.x, cdto.toPos.y, cdto.toPos.z);
                if (cdto.fromRot != null) tc.setFromRot(cdto.fromRot.x, cdto.fromRot.y, cdto.fromRot.z);
                if (cdto.toRot != null) tc.setToRot(cdto.toRot.x, cdto.toRot.y, cdto.toRot.z);
                if (cdto.fromScale != null) tc.setFromScale(cdto.fromScale.x, cdto.fromScale.y, cdto.fromScale.z);
                if (cdto.toScale != null) tc.setToScale(cdto.toScale.x, cdto.toScale.y, cdto.toScale.z);
                if (cdto.pathId != null) tc.setPathId(cdto.pathId);
                if (cdto.followPath != null) tc.setFollowPath(cdto.followPath);
            }
            case com.director.timeline.model.entity.EntityLookAtClip lc -> {
                if (cdto.lookAtMode != null) {
                    ParseUtils.setEnumIfValid(cdto.lookAtMode, com.director.timeline.model.entity.EntityLookAtClip.TargetMode.class, "TargetMode", lc::setMode);
                }
                if (cdto.lookAtWorldPoint != null) lc.setWorldPoint(cdto.lookAtWorldPoint.x, cdto.lookAtWorldPoint.y, cdto.lookAtWorldPoint.z);
                if (cdto.lookAtTargetEntityUuid != null) {
                    ParseUtils.setUuidIfValid(cdto.lookAtTargetEntityUuid, "lc.setTargetEntityUuid", lc::setTargetEntityUuid);
                }
                if (cdto.lookAtStrength != null) lc.setStrength(cdto.lookAtStrength);
            }
            case com.director.timeline.model.entity.EntityAnimationClip ac -> {
                if (cdto.animationSource != null) {
                    ParseUtils.setEnumIfValid(cdto.animationSource, com.director.timeline.model.entity.EntityAnimationClip.AnimationSource.class, "AnimationSource", ac::setSource);
                }
                if (cdto.animationId != null) ac.setAnimationId(cdto.animationId);
                if (cdto.animationLoop != null) ac.setLoop(cdto.animationLoop);
                if (cdto.animationSpeed != null) ac.setSpeed(cdto.animationSpeed);
                cdto.restorePoseBlend(ac);
            }
            case com.director.timeline.model.entity.EntityStateClip stc -> {
                if (cdto.sneaking != null) stc.setSneaking(cdto.sneaking);
                if (cdto.sprinting != null) stc.setSprinting(cdto.sprinting);
                if (cdto.glowing != null) stc.setGlowing(cdto.glowing);
                if (cdto.invulnerable != null) stc.setInvulnerable(cdto.invulnerable);
                if (cdto.stateVisibility != null) stc.setVisibility(cdto.stateVisibility);
            }
            case com.director.timeline.model.entity.EntityActionClip xac -> {
                if (cdto.actionType != null) {
                    ParseUtils.setEnumIfValid(cdto.actionType, com.director.timeline.model.entity.EntityActionClip.ActionType.class, "ActionType", xac::setActionType);
                }
                if (cdto.actionParams != null) cdto.actionParams.forEach(xac::setParam);
                if (cdto.triggerOnce != null) xac.setTriggerOnce(cdto.triggerOnce);
            }
            case com.director.timeline.model.entity.EntityDespawnClip dc -> {
                if (cdto.despawnMode != null) {
                    ParseUtils.setEnumIfValid(cdto.despawnMode, com.director.timeline.model.entity.EntityDespawnClip.DespawnMode.class, "DespawnMode", dc::setMode);
                }
            }
            case com.director.timeline.model.entity.ActorActionClip aac -> {
                if (cdto.actorActionType != null) {
                    ParseUtils.setEnumIfValid(cdto.actorActionType, com.director.actor.action.ActorActionType.class, "ActorActionType", aac::setActionType);
                }
                if (cdto.actorActionIntensity != null) aac.setIntensity(cdto.actorActionIntensity);
                if (cdto.actorActionLoop != null) aac.setLoop(cdto.actorActionLoop);
            }
            case com.director.timeline.model.entity.ActorPoseClip pc -> {
                if (cdto.poseOverrideBody != null) pc.setOverrideBody(cdto.poseOverrideBody);
                if (cdto.poseOverrideHead != null) pc.setOverrideHead(cdto.poseOverrideHead);
                if (cdto.poseOverrideArms != null) pc.setOverrideArms(cdto.poseOverrideArms);
                if (cdto.poseOverrideLegs != null) pc.setOverrideLegs(cdto.poseOverrideLegs);
                if (cdto.poseBodyPitch != null) pc.setBodyPitch(cdto.poseBodyPitch);
                if (cdto.poseBodyYaw != null) pc.setBodyYaw(cdto.poseBodyYaw);
                if (cdto.poseBodyRoll != null) pc.setBodyRoll(cdto.poseBodyRoll);
                if (cdto.poseHeadPitch != null) pc.setHeadPitch(cdto.poseHeadPitch);
                if (cdto.poseHeadYaw != null) pc.setHeadYaw(cdto.poseHeadYaw);
                if (cdto.poseLeftArmPitch != null) pc.setLeftArmPitch(cdto.poseLeftArmPitch);
                if (cdto.poseLeftArmYaw != null) pc.setLeftArmYaw(cdto.poseLeftArmYaw);
                if (cdto.poseLeftArmRoll != null) pc.setLeftArmRoll(cdto.poseLeftArmRoll);
                if (cdto.poseRightArmPitch != null) pc.setRightArmPitch(cdto.poseRightArmPitch);
                if (cdto.poseRightArmYaw != null) pc.setRightArmYaw(cdto.poseRightArmYaw);
                if (cdto.poseRightArmRoll != null) pc.setRightArmRoll(cdto.poseRightArmRoll);
                if (cdto.poseLeftLegPitch != null) pc.setLeftLegPitch(cdto.poseLeftLegPitch);
                if (cdto.poseRightLegPitch != null) pc.setRightLegPitch(cdto.poseRightLegPitch);
            }
            case com.director.timeline.model.entity.ActorRouteClip rc -> {
                if (cdto.routeWaypoints != null) {
                    java.util.List<org.joml.Vector3f> points = new java.util.ArrayList<>();
                    for (Vec3DTO point : cdto.routeWaypoints) {
                        if (point != null) {
                            points.add(point.toVector3f());
                        }
                    }
                    rc.setWaypoints(points);
                }
                if (cdto.routeInterpolationMode != null) {
                    ParseUtils.setEnumIfValid(cdto.routeInterpolationMode, com.director.timeline.model.entity.ActorRouteClip.InterpolationMode.class, "InterpolationMode", rc::setInterpolationMode);
                }
                if (cdto.routeLoopMode != null) {
                    ParseUtils.setEnumIfValid(cdto.routeLoopMode, com.director.timeline.model.entity.ActorRouteClip.LoopMode.class, "LoopMode", rc::setLoopMode);
                }
                if (cdto.routeSourcePathId != null) {
                    try { rc.setSourcePathId(java.util.UUID.fromString(cdto.routeSourcePathId)); }
                    catch (IllegalArgumentException invalid) { /* Keep old geometry fallback. */ }
                }
                if (cdto.routeSmoothTravel != null) rc.setSmoothTravel(cdto.routeSmoothTravel);
                if (cdto.routeSpeed != null) rc.setSpeed(cdto.routeSpeed);
                if (cdto.routeAlignRotation != null) rc.setAlignRotationToRoute(cdto.routeAlignRotation);
                if (cdto.routeJumpStart != null) rc.setJumpStart(cdto.routeJumpStart);
                if (cdto.routeJumpDuration != null) rc.setJumpDuration(cdto.routeJumpDuration);
                if (cdto.routeJumpHeight != null) rc.setJumpHeight(cdto.routeJumpHeight);
            }
            case com.director.timeline.model.entity.ActorRelationClip rel -> {
                if (cdto.relationTargetActorId != null) {
                    ParseUtils.setUuidIfValid(cdto.relationTargetActorId, "rel.setTargetActorId", rel::setTargetActorId);
                }
                if (cdto.relationType != null) {
                    ParseUtils.setEnumIfValid(cdto.relationType, com.director.timeline.model.entity.ActorRelationClip.RelationType.class, "RelationType", rel::setRelationType);
                }
                if (cdto.relationDistance != null) rel.setDistance(cdto.relationDistance);
                if (cdto.relationAngle != null) rel.setAngle(cdto.relationAngle);
                if (cdto.relationHeightOffset != null) rel.setHeightOffset(cdto.relationHeightOffset);
                if (cdto.relationMaintainLookAt != null) rel.setMaintainLookAt(cdto.relationMaintainLookAt);
            }
            default -> {
            }
        }

        return clip;
    }
    private static @NotNull EntityClip getEntityClip(EntityClipDTO cdto, EntityClipType clipType) {
        String cname = cdto.name != null ? cdto.name : cdto.clipType;
        EntityClip clip = switch (clipType) {
            case SPAWN -> new com.director.timeline.model.entity.EntitySpawnClip(cname, cdto.startTime, cdto.endTime);
            case TRANSFORM -> new com.director.timeline.model.entity.EntityTransformClip(cname, cdto.startTime, cdto.endTime);
            case LOOK_AT -> new com.director.timeline.model.entity.EntityLookAtClip(cname, cdto.startTime, cdto.endTime);
            case ANIMATION -> new com.director.timeline.model.entity.EntityAnimationClip(cname, cdto.startTime, cdto.endTime);
            case STATE -> new com.director.timeline.model.entity.EntityStateClip(cname, cdto.startTime, cdto.endTime);
            case ACTION -> new com.director.timeline.model.entity.EntityActionClip(cname, cdto.startTime, cdto.endTime);
            case DESPAWN -> new com.director.timeline.model.entity.EntityDespawnClip(cname, cdto.startTime, cdto.endTime);
            case ACTOR_ACTION -> new com.director.timeline.model.entity.ActorActionClip(cname, cdto.startTime, cdto.endTime);
            case ACTOR_POSE -> new com.director.timeline.model.entity.ActorPoseClip(cname, cdto.startTime, cdto.endTime);
            case ACTOR_ROUTE -> new com.director.timeline.model.entity.ActorRouteClip(cname, cdto.startTime, cdto.endTime);
            case ACTOR_RELATION -> new com.director.timeline.model.entity.ActorRelationClip(cname, cdto.startTime, cdto.endTime);
        };
        clip.setMuted(cdto.muted);
        clip.setWeight(cdto.weight);
        return clip;
    }

    private static @NotNull OrbitPathClip getOrbitPathClip(PathClipDTO pathClipDTO) {
        net.minecraft.util.math.Vec3d center = pathClipDTO.center != null ? pathClipDTO.center.toVec3d() : net.minecraft.util.math.Vec3d.ZERO;
        float radius = pathClipDTO.radius != null ? pathClipDTO.radius : 10.0f;
        float height = pathClipDTO.height != null ? pathClipDTO.height : 64.0f;

        return new OrbitPathClip(
            pathClipDTO.name != null ? pathClipDTO.name : "Orbit Path",
            pathClipDTO.startTime,
            pathClipDTO.endTime,
            center,
            radius,
            height
        );
    }

    private static AutomationKeyframeDTO toCameraKeyframeDTO(CameraKeyframe ck) {
        AutomationKeyframeDTO kdto = new AutomationKeyframeDTO();
        kdto.type = ck.getType();
        kdto.name = ck.getName();
        kdto.time = ck.getTime();
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
        return kdto;
    }

    private static CameraKeyframe fromCameraKeyframeDTO(AutomationKeyframeDTO kdto) {
        CameraKeyframe ck = new CameraKeyframe(kdto.name != null ? kdto.name : "Camera Keyframe", kdto.time);
        if (kdto.position != null) ck.setPosition(new org.joml.Vector3f(kdto.position.x, kdto.position.y, kdto.position.z));
        if (kdto.rotation != null) ck.setRotation(new org.joml.Vector3f(kdto.rotation.x, kdto.rotation.y, kdto.rotation.z));
        ck.setFov(kdto.fov);
        ck.setNearPlane(kdto.nearPlane);
        ck.setFarPlane(kdto.farPlane);
        if (kdto.cameraKeyType != null) {
            ParseUtils.setEnumIfValid(kdto.cameraKeyType, CameraKeyframe.KeyType.class, "KeyType", ck::setKeyType);
        }
        if (kdto.cameraSegmentType != null) {
            ParseUtils.setEnumIfValid(kdto.cameraSegmentType, CameraKeyframe.SegmentPathType.class, "SegmentPathType", ck::setSegmentType);
        }
        if (kdto.cameraRotationInterpolation != null) {
            ParseUtils.setEnumIfValid(kdto.cameraRotationInterpolation, CameraKeyframe.RotationInterpolation.class, "RotationInterpolation", ck::setRotationInterpolation);
        }
        if (kdto.bezierInHandle != null) ck.setBezierInHandle(new org.joml.Vector3f(kdto.bezierInHandle.x, kdto.bezierInHandle.y, kdto.bezierInHandle.z));
        if (kdto.bezierOutHandle != null) ck.setBezierOutHandle(new org.joml.Vector3f(kdto.bezierOutHandle.x, kdto.bezierOutHandle.y, kdto.bezierOutHandle.z));
        if (kdto.cameraHandleMode != null) {
            ParseUtils.setEnumIfValid(kdto.cameraHandleMode, CameraKeyframe.HandleMode.class, "HandleMode", ck::setHandleMode);
        }
        ck.setDepthOfFieldEnabled(kdto.dofEnabled);
        ck.setFocusDistance(kdto.focusDistance);
        ck.setAperture(kdto.aperture);
        return ck;
    }

    private static @NotNull CranePathClip getCranePathClip(PathClipDTO pathClipDTO) {
        CranePathClip crane = 
            new CranePathClip(
                pathClipDTO.name != null ? pathClipDTO.name : "Crane Path",
                pathClipDTO.startTime,
                pathClipDTO.endTime
            );

        if (pathClipDTO.heightDelta != null) crane.setHeightDelta(pathClipDTO.heightDelta);
        if (pathClipDTO.alignStartToCurrentPosition != null) crane.setAlignStartToCurrentPosition(pathClipDTO.alignStartToCurrentPosition);
        return crane;
    }

    private static TrackType resolveTrackType(TrackDTO trackDTO) {
        if (trackDTO == null) {
            return TrackType.CONTAINER;
        }
        TrackType type = null;
        if (trackDTO.typeName != null) {
            try {
                // ✅ 向后兼容：将旧的 ERASE 轨道类型自动转换为 CONTAINER
                if ("ERASE".equals(trackDTO.typeName)) {
                    LOGGER.info("[TimelineModelPersistence] 检测到旧的 ERASE 轨道类型，自动转换为 CONTAINER: {}", trackDTO.name);
                    type = TrackType.CONTAINER;
                } else {
                    type = TrackType.valueOf(trackDTO.typeName);
                }
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
     *
     * @return 是否成功写入当前存档目录
     */
    public static boolean saveToFile(TimelineModel model, TimelineViewportState viewportState) {
        try {
            Path timelineFile = getTimelineFile();
            if (timelineFile == null) {
                LOGGER.debug("[TimelineModelPersistence] 当前无活动存档目录，跳过保存");
                return false;
            }

            // 确保配置目录存在
            if (!Files.exists(timelineFile.getParent())) {
                Files.createDirectories(timelineFile.getParent());
            }

            // 转换为 DTO 并保存
            TimelineDTO dto = toDTO(model, viewportState);
            String json = GSON.toJson(dto);
            AtomicFileWriter.writeString(timelineFile, json);
            return true;
        } catch (IOException e) {
            LOGGER.error("[TimelineModelPersistence] 保存时间线数据失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 从文件加载 TimelineModel（按世界）
     */
    public static void loadFromFile(TimelineModel model, TimelineViewportState viewportState) {
        try {
            Path timelineFile = getTimelineFile();
            Path legacy = getLegacyTimelineFile();
            if (timelineFile == null) {
                LOGGER.debug("[TimelineModelPersistence] 当前无活动存档目录，跳过加载");
                return;
            }

            // 自动迁移：优先使用世界目录；若世界目录没有但旧 config 存在，则迁移一次
            if (!Files.exists(timelineFile) && legacy != null && Files.exists(legacy) && !legacy.equals(timelineFile)) {
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
        if (primary != null && Files.exists(primary)) {
            return true;
        }
        // 兼容旧位置（用于升级迁移的首次启动）
        Path legacy = getLegacyTimelineFile();
        return legacy != null && Files.exists(legacy);
    }

    private static String getWorldFingerprint() {
        try {
            java.nio.file.Path path = com.director.util.WorldIdentifier.getWorldSavePath();
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
            if (file != null && Files.exists(file)) {
                Files.delete(file);
                LOGGER.info("[TimelineModelPersistence] 已删除世界 {} 的时间线存档: {}",
                        WorldIdentifier.getCurrentWorldId(), file);
            }

            // 兼容：同时删除旧位置的存档，避免“新建动画”后旧文件又被迁移回来
            Path legacy = getLegacyTimelineFile();
            if (legacy != null && Files.exists(legacy) && !legacy.equals(file)) {
                Files.delete(legacy);
                LOGGER.info("[TimelineModelPersistence] 已删除旧位置时间线存档: {}", legacy);
            }
        } catch (IOException e) {
            LOGGER.warn("[TimelineModelPersistence] 删除时间线存档失败: {}", e.getMessage(), e);
        }
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
