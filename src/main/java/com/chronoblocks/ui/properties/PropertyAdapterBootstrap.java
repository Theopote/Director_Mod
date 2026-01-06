package com.chronoblocks.ui.properties;

import com.chronoblocks.core.animation.container.AnimationClip;
import com.chronoblocks.core.animation.container.Keyframe;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.ui.properties.adapters.*;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import com.chronoblocks.ui.timeline.model.audio.AudioClipKeyframe;
import com.chronoblocks.ui.timeline.model.audio.AudioTimelineClip;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import com.chronoblocks.ui.timeline.model.time.TimeKeyframe;
import com.chronoblocks.ui.timeline.model.selection.TimelineKeyframeMultiSelection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 属性适配器启动器 - ChronoBlocks Properties System v4.9
 * 
 * <p>在 ChronoBlocks 启动时自动注册所有可用的属性适配器。
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>自动注册所有已知的属性适配器</li>
 *   <li>保证单例初始化（防止重复注册）</li>
 *   <li>支持可选模块的动态发现</li>
 *   <li>完整的错误处理和日志记录</li>
 * </ul>
 * 
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 在 UIManager 或 EditorCore 初始化时调用
 * public void init() {
 *     PropertyAdapterBootstrap.initialize();
 *     // ... 其他初始化 ...
 * }
 * }</pre>
 * 
 * <h3>已注册的适配器（19个）</h3>
 * <ul>
 *   <li><b>时间线系统（3个）</b></li>
 *   <li>  - TimelineProjectPropertyAdapter - 项目编辑器</li>
 *   <li>  - TimelineSequencePropertyAdapter - 序列编辑器</li>
 *   <li>  - TimelineTrackPropertyAdapter - 轨道编辑器</li>
 *   <li><b>核心对象（3个）</b></li>
 *   <li>  - KeyframePropertyAdapter - 关键帧编辑器</li>
 *   <li>  - ContainerPropertyAdapter - 容器编辑器</li>
 *   <li>  - ClipPropertyAdapter - 片段编辑器</li>
 *   <li><b>扩展系统（6个）</b></li>
 *   <li>  - EffectPropertyAdapter - 特效编辑器</li>
 *   <li>  - ProjectEnvironmentPropertyAdapter - 环境编辑器</li>
 *   <li>  - MaterialLibraryPropertyAdapter - 材质库编辑器</li>
 *   <li>  - CameraPropertyAdapter - 相机编辑器</li>
 *   <li>  - CameraKeyframePropertyAdapter - 摄像机关键帧编辑器</li>
 *   <li>  - TimeKeyframePropertyAdapter - 时间关键帧编辑器</li>
 *   <li>  - SeasonKeyframePropertyAdapter - 季节关键帧编辑器</li>
 *   <li>  - WeatherKeyframePropertyAdapter - 天气关键帧编辑器</li>
 *   <li>  - PostProcessingPropertyAdapter - 后处理编辑器</li>
 *   <li>  - PhysicsEnvironmentPropertyAdapter - 物理编辑器</li>
 *   <li><b>扩展类型（3个）</b></li>
 *   <li>  - Vector3fPropertyAdapter - XYZ向量编辑器</li>
 *   <li>  - ColorPropertyAdapter - RGBA颜色拾色器</li>
 *   <li>  - ListPropertyAdapter - 列表展开器</li>
 *   <li><b>通用回退（1个）</b></li>
 *   <li>  - GenericPropertyAdapter - 反射通用编辑器</li>
 * </ul>
 * 
 * @version 4.9
 * @since 2025-10-16
 */
public final class PropertyAdapterBootstrap {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyAdapterBootstrap.class);
    
    /** 初始化标志 */
    private static boolean initialized = false;
    
    // 私有构造函数，防止实例化
    private PropertyAdapterBootstrap() {}
    
    /**
     * 初始化所有属性适配器
     * 
     * <p>此方法是幂等的，可以安全地多次调用。
     * 只有第一次调用会实际执行注册逻辑。
     */
    public static synchronized void initialize() {
        if (initialized) {
            LOGGER.warn("[PropertyAdapterBootstrap] 已初始化过，跳过重复注册");
            return;
        }
        
        LOGGER.info("[PropertyAdapterBootstrap] 正在注册属性适配器...");
        
        try {
            int registeredCount = 0;
            
            LOGGER.info("  ========== 时间线系统适配器 ==========");
            
            // ✅ 时间线项目适配器
            registerAdapter(new TimelineProjectPropertyAdapter(), "TimelineProject");
            registeredCount++;
            
            // ✅ 时间线序列适配器
            registerAdapter(new TimelineSequencePropertyAdapter(), "TimelineSequence");
            registeredCount++;
            
            // ✅ 时间线轨道适配器
            registerAdapter(new TimelineTrackPropertyAdapter(), "TimelineTrack");
            registeredCount++;
            
            LOGGER.info("  ========== 核心对象适配器 ==========");
            
            // ✅ 关键帧属性适配器
            registerAdapter(Keyframe.class, new KeyframePropertyAdapter());
            registeredCount++;
            
            // ✅ 容器属性适配器
            registerAdapter(Container.class, new ContainerPropertyAdapter());
            registeredCount++;
            
            // ✅ 动画片段属性适配器（新版动画系统）
            registerAdapter(AnimationClip.class, new ClipPropertyAdapter());
            registeredCount++;
            registerAdapter(TimelineClip.class, new TimelineClipPropertyAdapter());
            registeredCount++;
            registerAdapter(AudioTimelineClip.class, new AudioTimelineClipPropertyAdapter());
            registeredCount++;
            registerAdapter(AudioClipKeyframe.class, new AudioClipKeyframePropertyAdapter());
            registeredCount++;
            registerAdapter(AudioEvent.class, new AudioEventPropertyAdapter());
            registeredCount++;

            // ✅ 时间线关键帧多选占位适配器（避免多选时显示单个关键帧属性）
            registerAdapter(TimelineKeyframeMultiSelection.class, new TimelineKeyframeMultiSelectionPropertyAdapter());
            registeredCount++;
            
            LOGGER.info("  ========== 扩展系统适配器 ==========");
            
            // ✅ 特效适配器
            registerAdapter(new EffectPropertyAdapter(), "Effect");
            registeredCount++;
            
            // ✅ 项目环境适配器
            registerAdapter(new ProjectEnvironmentPropertyAdapter(), "ProjectEnvironment");
            registeredCount++;
            
            // ✅ 材质库适配器
            registerAdapter(new MaterialLibraryPropertyAdapter(), "MaterialLibrary");
            registeredCount++;
            
            LOGGER.info("  ========== 高级功能适配器 ==========");
            
            // ✅ 相机适配器
            registerAdapter(new CameraPropertyAdapter(), "Camera");
            registeredCount++;
            
            // ✅ 摄像机关键帧适配器
            registerAdapter(CameraKeyframe.class, new CameraKeyframePropertyAdapter());
            registeredCount++;
            
            // ✅ 时间关键帧适配器
            registerAdapter(TimeKeyframe.class, new TimeKeyframePropertyAdapter());
            registeredCount++;
            
            // ✅ 季节关键帧适配器
            registerAdapter(com.chronoblocks.ui.timeline.model.season.SeasonKeyframe.class, 
                    new SeasonKeyframePropertyAdapter());
            registeredCount++;
            
            // ✅ 天气关键帧适配器
            registerAdapter(com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe.class, 
                    new WeatherKeyframePropertyAdapter());
            registeredCount++;
            
            // ✅ 实体关键帧适配器
            registerAdapter(com.chronoblocks.ui.timeline.model.entity.EntityKeyframe.class,
                    new EntityKeyframePropertyAdapter());
            registeredCount++;

            // ✅ 实体片段适配器
            registerAdapter(com.chronoblocks.ui.timeline.model.entity.EntitySpawnClip.class,
                    new com.chronoblocks.ui.properties.adapters.EntitySpawnClipPropertyAdapter());
            registeredCount++;
            registerAdapter(com.chronoblocks.ui.timeline.model.entity.EntityTransformClip.class,
                    new com.chronoblocks.ui.properties.adapters.EntityTransformClipPropertyAdapter());
            registeredCount++;
            registerAdapter(com.chronoblocks.ui.timeline.model.entity.EntityAnimationClip.class,
                    new com.chronoblocks.ui.properties.adapters.EntityAnimationClipPropertyAdapter());
            registeredCount++;
            registerAdapter(com.chronoblocks.ui.timeline.model.entity.EntityActionClip.class,
                    new com.chronoblocks.ui.properties.adapters.EntityActionClipPropertyAdapter());
            registeredCount++;
            registerAdapter(com.chronoblocks.ui.timeline.model.entity.EntityStateClip.class,
                    new com.chronoblocks.ui.properties.adapters.EntityStateClipPropertyAdapter());
            registeredCount++;
            registerAdapter(com.chronoblocks.ui.timeline.model.entity.EntityLookAtClip.class,
                    new com.chronoblocks.ui.properties.adapters.EntityLookAtClipPropertyAdapter());
            registeredCount++;
            registerAdapter(com.chronoblocks.ui.timeline.model.entity.EntityDespawnClip.class,
                    new com.chronoblocks.ui.properties.adapters.EntityDespawnClipPropertyAdapter());
            registeredCount++;
            
            // ✅ 后处理适配器
            registerAdapter(new PostProcessingPropertyAdapter(), "PostProcessing");
            registeredCount++;
            
            // ✅ 物理环境适配器
            registerAdapter(new PhysicsEnvironmentPropertyAdapter(), "PhysicsEnvironment");
            registeredCount++;
            
            LOGGER.info("  ========== 扩展类型适配器 ==========");
            
            // ✅ Vector3f 属性适配器（JOML）
            try {
                Class.forName("org.joml.Vector3f");
                registerAdapter(org.joml.Vector3f.class, new Vector3fPropertyAdapter());
                registeredCount++;
            } catch (ClassNotFoundException e) {
                LOGGER.debug("[PropertyAdapterBootstrap] JOML 库未加载，跳过 Vector3f 适配器");
            }
            
            // ✅ Vector4f 颜色属性适配器（JOML）
            try {
                Class.forName("org.joml.Vector4f");
                registerAdapter(org.joml.Vector4f.class, new ColorPropertyAdapter());
                registeredCount++;
            } catch (ClassNotFoundException e) {
                LOGGER.debug("[PropertyAdapterBootstrap] JOML 库未加载，跳过 Vector4f 适配器");
            }
            
            // ✅ List 属性适配器（使用特殊注册）
            // 注意：List 使用 supports() 方法进行类型检查
            @SuppressWarnings("unchecked")
            Class<Object> listClass = (Class<Object>) (Class<?>) java.util.List.class;
            PropertyAdapterRegistry.registerAdapter(listClass, new ListPropertyAdapter());
            LOGGER.info("[PropertyAdapterBootstrap] ✅ 已注册: ListPropertyAdapter → List");
            registeredCount++;
            
            LOGGER.info("  ========== 通用回退适配器 ==========");
            
            // ✅ 通用属性适配器（用于未注册类型）
            PropertyAdapterRegistry.setFallbackAdapter(new GenericPropertyAdapter());
            LOGGER.info("[PropertyAdapterBootstrap] ✅ 已设置回退适配器: GenericPropertyAdapter");
            
            // ========== 初始化完成 ==========
            
            initialized = true;
            LOGGER.info("[PropertyAdapterBootstrap] 属性系统初始化完成，共注册 {} 个适配器", registeredCount);
            LOGGER.debug("[PropertyAdapterBootstrap] 注册详情:\n{}", 
                PropertyAdapterRegistry.getDebugInfo());
            
        } catch (Exception e) {
            LOGGER.error("[PropertyAdapterBootstrap] 初始化失败", e);
            throw new RuntimeException("Failed to initialize property adapter system", e);
        }
    }
    
    /**
     * 注册属性适配器（带日志）
     */
    private static <T> void registerAdapter(Class<T> type, IPropertyAdapter<T> adapter) {
        PropertyAdapterRegistry.registerAdapter(type, adapter);
        LOGGER.info("[PropertyAdapterBootstrap] ✅ 已注册: {} → {}", 
            adapter.getClass().getSimpleName(), 
            type.getSimpleName());
    }
    
    /**
     * 注册属性适配器（带自定义目标名称）
     */
    @SuppressWarnings("unchecked")
    private static <T> void registerAdapter(IPropertyAdapter<T> adapter, String targetName) {
        PropertyAdapterRegistry.registerAdapter((Class<T>) Object.class, adapter);
        LOGGER.info("[PropertyAdapterBootstrap] ✅ 已注册: {} → {}", 
            adapter.getClass().getSimpleName(), 
            targetName);
    }

    /**
     * 检查是否已初始化
     * 
     * @return 如果已初始化则返回 true
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
